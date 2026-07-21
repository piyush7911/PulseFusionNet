package com.pulsefusionnet.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsefusionnet.app.ppg.FingerDetector
import com.pulsefusionnet.app.ppg.FrameStats
import com.pulsefusionnet.app.ppg.MovementDetector
import com.pulsefusionnet.app.ppg.PyPpgBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class Journey { LOADING, PERMISSION, HOME, DETECTING, MEASURING, FAILED, RESULT }

enum class ZoneLabel { BRADYCARDIA, NORMAL, ELEVATED, HIGH }

data class ResultData(
    val bpm: Double,
    val confidence: Double,
    val samples: Int,
    val zone: ZoneLabel
)

private const val MEASURE_DURATION_SEC = 60
private const val STABLE_FRAMES_NEEDED = 60 // ~2s at ~30fps
private const val ABSENT_PAUSE_FRAMES = 15
private const val ABSENT_ABORT_FRAMES = 150
private const val MAX_BUFFER = 1800 // 60s at 30fps
private const val ANALYSIS_INTERVAL_MS = 3000L

class MeasurementViewModel : ViewModel() {

    var journey by mutableStateOf(Journey.LOADING)
        private set

    // Detecting screen state
    var stabilizationPct by mutableStateOf(0)
        private set
    var fingerOnLens by mutableStateOf(false)
        private set

    // Measuring screen state
    var secondsRemaining by mutableStateOf(MEASURE_DURATION_SEC)
        private set
    var liveBpmText by mutableStateOf("--")
        private set
    var signalQualityPct by mutableStateOf(0)
        private set
    var fingerPresent by mutableStateOf(true)
        private set
    var movementWarning by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set

    var failedReason by mutableStateOf("")
        private set
    var result by mutableStateOf<ResultData?>(null)
        private set
    var waveformSamples by mutableStateOf<List<Float>>(emptyList())
        private set

    private val fingerDetector = FingerDetector()
    private val movementDetector = MovementDetector()

    private val greenBuffer = ArrayDeque<Double>()
    private val redBuffer = ArrayDeque<Double>()
    private val timestamps = ArrayDeque<Long>()
    private val bpmReadings = mutableListOf<Double>()
    private val confReadings = mutableListOf<Double>()

    private var stableFrames = 0
    private var absentFrames = 0
    private var measurementStarted = false
    private var countdownJob: Job? = null
    private var analysisJob: Job? = null
    private var waveformJob: Job? = null
    private var emaBpm = 0.0
    private var emaCount = 0

    fun onPermissionGranted() {
        journey = Journey.HOME
    }

    fun onPermissionDenied() {
        journey = Journey.PERMISSION
    }

    /** Called once the branded loading screen has shown for its minimum duration. */
    fun finishLoading(hasCameraPermission: Boolean) {
        if (journey != Journey.LOADING) return
        journey = if (hasCameraPermission) Journey.HOME else Journey.PERMISSION
    }

    fun startScan() {
        resetAll()
        journey = Journey.DETECTING
        startWaveformTicker()
    }

    private fun startWaveformTicker() {
        waveformJob?.cancel()
        waveformJob = viewModelScope.launch {
            while (journey == Journey.DETECTING || journey == Journey.MEASURING) {
                delay(120)
                val n = greenBuffer.size
                if (n >= 5) {
                    waveformSamples = greenBuffer.toList().takeLast(150).map { it.toFloat() }
                }
            }
        }
    }

    fun cancelAndReturnHome() {
        stopTimers()
        resetAll()
        journey = Journey.HOME
    }

    /** Called from the camera analyzer thread for every captured frame. */
    fun onCameraFrame(stats: FrameStats) {
        val present = fingerDetector.update(stats)
        when (journey) {
            Journey.DETECTING -> handleDetectingFrame(present, stats)
            Journey.MEASURING -> handleMeasuringFrame(present, stats)
            else -> {}
        }
    }

    private fun handleDetectingFrame(present: Boolean, stats: FrameStats) {
        fingerOnLens = present
        if (!present) {
            stableFrames = 0
            stabilizationPct = 0
            greenBuffer.clear(); redBuffer.clear(); timestamps.clear()
            return
        }
        stableFrames++
        pushSample(stats)
        stabilizationPct = ((stableFrames.toFloat() / STABLE_FRAMES_NEEDED) * 100).roundToInt().coerceAtMost(100)
        if (stableFrames >= STABLE_FRAMES_NEEDED) {
            startMeasurement()
        }
    }

    private fun handleMeasuringFrame(present: Boolean, stats: FrameStats) {
        fingerPresent = present
        if (!present) {
            absentFrames++
            movementDetector.reset()
            if (absentFrames >= ABSENT_PAUSE_FRAMES && !isPaused) pauseMeasurement()
            if (absentFrames >= ABSENT_ABORT_FRAMES) {
                abort("Finger was lifted from the camera for too long.")
            }
            return
        }
        absentFrames = 0
        if (isPaused) resumeMeasurement()

        val isMovement = movementDetector.update(stats.avgG)
        movementWarning = isMovement
        if (movementDetector.consecutiveMovementFrames >= movementDetector.abortFrames) {
            abort("Excessive movement detected. Keep your finger and phone completely still.")
            return
        }

        pushSample(stats)
    }

    private fun pushSample(stats: FrameStats) {
        greenBuffer.addLast(stats.avgG)
        redBuffer.addLast(stats.avgR)
        timestamps.addLast(System.nanoTime())
        if (greenBuffer.size > MAX_BUFFER) {
            greenBuffer.removeFirst(); redBuffer.removeFirst(); timestamps.removeFirst()
        }
    }

    private fun startMeasurement() {
        if (measurementStarted) return
        measurementStarted = true
        absentFrames = 0
        movementDetector.reset()
        bpmReadings.clear(); confReadings.clear()
        emaBpm = 0.0; emaCount = 0
        secondsRemaining = MEASURE_DURATION_SEC
        liveBpmText = "--"
        signalQualityPct = 0
        journey = Journey.MEASURING

        countdownJob = viewModelScope.launch {
            while (secondsRemaining > 0) {
                delay(1000)
                if (!isPaused) secondsRemaining--
            }
            finishMeasurement()
        }
        analysisJob = viewModelScope.launch {
            while (true) {
                delay(ANALYSIS_INTERVAL_MS)
                runAnalysis()
            }
        }
    }

    private fun pauseMeasurement() {
        isPaused = true
    }

    private fun resumeMeasurement() {
        isPaused = false
        absentFrames = 0
    }

    private suspend fun runAnalysis() {
        if (greenBuffer.size < 60) return // need >= 2s

        val realFps = estimateFps()
        val green = greenBuffer.toDoubleArray()
        val red = redBuffer.toDoubleArray()

        // The embedded Python interpreter call is synchronous — keep it off the main thread.
        val ensemble = withContext(Dispatchers.Default) {
            PyPpgBridge.analyze(green, red, realFps)
        }

        emaBpm = if (emaCount == 0) ensemble.consensusBpm else 0.30 * ensemble.consensusBpm + 0.70 * emaBpm
        emaCount++

        bpmReadings.add(emaBpm)
        confReadings.add(ensemble.confidence)
        liveBpmText = "%.1f".format(emaBpm)
        signalQualityPct = ensemble.confidence.roundToInt()
    }

    private fun estimateFps(): Double {
        if (timestamps.size < 2) return 30.0
        val elapsedSec = (timestamps.last() - timestamps.first()) / 1_000_000_000.0
        if (elapsedSec <= 0) return 30.0
        return ((timestamps.size - 1) / elapsedSec).coerceIn(10.0, 60.0)
    }

    private fun finishMeasurement() {
        stopTimers()
        if (bpmReadings.isEmpty()) {
            abort("No valid readings collected. Ensure the camera is fully covered.")
            return
        }
        val sorted = bpmReadings.sorted()
        val trim = maxOf(1, (sorted.size * 0.15).toInt())
        val trimmed = if (sorted.size > 2 * trim) sorted.subList(trim, sorted.size - trim) else sorted
        val finalBpm = median(trimmed)
        val finalConf = median(confReadings)

        val zone = when {
            finalBpm < 60 -> ZoneLabel.BRADYCARDIA
            finalBpm <= 100 -> ZoneLabel.NORMAL
            finalBpm <= 120 -> ZoneLabel.ELEVATED
            else -> ZoneLabel.HIGH
        }

        result = ResultData(finalBpm, finalConf, bpmReadings.size, zone)
        journey = Journey.RESULT
    }

    private fun abort(reason: String) {
        stopTimers()
        failedReason = reason
        journey = Journey.FAILED
    }

    private fun stopTimers() {
        countdownJob?.cancel(); countdownJob = null
        analysisJob?.cancel(); analysisJob = null
        waveformJob?.cancel(); waveformJob = null
    }

    private fun resetAll() {
        fingerDetector.reset()
        movementDetector.reset()
        greenBuffer.clear(); redBuffer.clear(); timestamps.clear()
        bpmReadings.clear(); confReadings.clear()
        stableFrames = 0; absentFrames = 0
        measurementStarted = false
        stabilizationPct = 0
        fingerOnLens = false
        secondsRemaining = MEASURE_DURATION_SEC
        liveBpmText = "--"
        signalQualityPct = 0
        fingerPresent = true
        movementWarning = false
        isPaused = false
        emaBpm = 0.0; emaCount = 0
        waveformSamples = emptyList()
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val m = sorted.size / 2
        return if (sorted.size % 2 != 0) sorted[m] else (sorted[m - 1] + sorted[m]) / 2.0
    }

    override fun onCleared() {
        super.onCleared()
        stopTimers()
    }
}
