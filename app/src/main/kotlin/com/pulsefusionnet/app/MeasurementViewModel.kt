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
    val zone: ZoneLabel,
    val sqiPct: Int,          // Signal Quality Index 0–100 from this actual scan
    /** True when the reading lands in 65–85 BPM — the zone where a halved
     *  130–170 BPM tachycardia would appear. A known algorithm limitation
     *  (see roadmap.md Priority A) means readings above ~135 BPM can be
     *  halved with no quality flag. This flag prompts the user to re-measure
     *  if they felt their heart racing. It is NOT shown for confirmed normal
     *  sessions (high confidence + ELEVATED/HIGH zone already detected). */
    val tachycardiaWarning: Boolean
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

    // Flash / Torch state — OFF by default (0.0% torch forced unless user toggles)
    var isFlashEnabled by mutableStateOf(false)
        private set

    fun toggleFlash() {
        isFlashEnabled = !isFlashEnabled
    }

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

    /**
     * Called once per captured camera frame. MainActivity marshals this onto the main
     * thread (Handler.post), so all buffer access here and in the coroutines below is
     * single-threaded — but a single malformed frame must never crash the live capture
     * loop, so the whole body is guarded. A dropped frame is invisible at ~30fps.
     */
    fun onCameraFrame(stats: FrameStats) {
        try {
            val present = fingerDetector.update(stats)
            when (journey) {
                Journey.DETECTING -> handleDetectingFrame(present, stats)
                Journey.MEASURING -> handleMeasuringFrame(present, stats)
                else -> {}
            }
        } catch (e: Exception) {
            // Never let one bad frame tear down the capture loop; the next frame arrives in ~33ms.
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

        val isMovement = movementDetector.update(stats)
        movementWarning = isMovement
        if (movementDetector.consecutiveMovementFrames >= movementDetector.abortFrames && secondsRemaining > 3) {
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

        analysisJob = viewModelScope.launch {
            // Analyze every 3s for as long as the (pause-aware) countdown is still running.
            // Tying the loop to secondsRemaining — which only decrements while NOT paused —
            // means a pause correctly EXTENDS the measurement rather than burning the
            // analysis budget on the frozen buffer; runAnalysis() also skips paused ticks.
            while (secondsRemaining > 0) {
                delay(ANALYSIS_INTERVAL_MS)
                runAnalysis()
            }
        }
        countdownJob = viewModelScope.launch {
            while (secondsRemaining > 0) {
                delay(1000)
                if (!isPaused) secondsRemaining--
            }
            // The last analysis tick can still be mid-flight (its Python processing pushes
            // it past the 60s mark) — join instead of letting stopTimers() cancel it, so a
            // clean run reliably includes its final reading (fixes the old 19-vs-20 race).
            analysisJob?.join()
            finishMeasurement()
        }
    }

    private fun pauseMeasurement() {
        isPaused = true
    }

    private fun resumeMeasurement() {
        isPaused = false
        absentFrames = 0
    }

    private val sqiReadings = mutableListOf<Double>()
    private val qualityFlags = mutableListOf<String>()

    private suspend fun runAnalysis() {
        // Skip while paused (finger lifted): the buffer is frozen, so analyzing it again
        // would just append duplicate stale readings that pollute the final median. A
        // measurement that's mostly paused legitimately ends with fewer/no readings.
        if (isPaused) return

        val realFps = estimateFps()
        // The extractor needs >=3s of signal to produce a real estimate (below that it now
        // returns NaN, never a fabricated number). Gate here, fps-aware, so we don't even
        // spend a Python call on a buffer that can't yield a genuine reading — the first
        // live number the user sees is always a real one.
        val minSamples = kotlin.math.ceil(realFps * 3.0).toInt()
        if (greenBuffer.size < minSamples) return
        // Snapshot on the (main) capture thread before handing to a worker. Defensive copy:
        // if the frame-producer thread ever races this (it shouldn't, given main-thread
        // marshalling), a copy failure skips this tick instead of crashing.
        val green: DoubleArray
        val red: DoubleArray
        try {
            green = greenBuffer.toDoubleArray()
            red = redBuffer.toDoubleArray()
        } catch (e: Exception) {
            return
        }
        if (green.isEmpty() || red.size != green.size) return

        // The embedded Python interpreter call is synchronous — keep it off the main thread.
        // Any failure inside Python (a degenerate live buffer, a Chaquopy hiccup) must only
        // cost THIS 3-second tick, never kill the analysis loop and waste the whole scan.
        val ensemble = try {
            withContext(Dispatchers.Default) {
                PyPpgBridge.analyze(green, red, realFps)
            }
        } catch (e: Exception) {
            return
        }

        // Reject non-finite / out-of-range results defensively before they reach the UI or
        // the final median (a NaN here would poison every downstream statistic).
        val bpm = ensemble.consensusBpm
        if (bpm.isNaN() || bpm.isInfinite() || bpm <= 0.0) return

        emaBpm = if (emaCount == 0) bpm else 0.30 * bpm + 0.70 * emaBpm
        emaCount++

        bpmReadings.add(emaBpm)
        confReadings.add(ensemble.confidence)
        sqiReadings.add(ensemble.signalQualityIndex)
        qualityFlags.add(ensemble.qualityFlag)

        liveBpmText = "%.1f".format(emaBpm)
        signalQualityPct = ensemble.signalQualityIndex.roundToInt().coerceIn(0, 100)
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
        // Use the last 5 readings (representing the 48s-60s high-resolution stable window)
        // to ignore noisy early-session estimates while maintaining robust outlier filtering via median.
        val stableBpms = bpmReadings.takeLast(5)
        val stableConfs = confReadings.takeLast(5)
        val stableSqi = sqiReadings.takeLast(5)
        val passCount = qualityFlags.takeLast(5).count { it == "PASS" }

        val finalBpm = median(stableBpms)
        val finalConf = median(stableConfs)
        val finalSqi = median(stableSqi)

        if (finalSqi < 35.0) {
            abort("Signal corrupted by finger motion or low optical contrast. Please hold your finger still and re-measure.")
            return
        }

        val zone = when {
            finalBpm < 60 -> ZoneLabel.BRADYCARDIA
            finalBpm <= 100 -> ZoneLabel.NORMAL
            finalBpm <= 120 -> ZoneLabel.ELEVATED
            else -> ZoneLabel.HIGH
        }

        // Priority A safeguard (roadmap v2): readings in 65–85 BPM may be a halved
        // tachycardia (130–170 BPM) due to a known harmonic-disambiguation limitation.
        // The algorithm cannot self-detect this case reliably; instead, surface a
        // user-facing advisory so they can re-measure if they felt their heart racing.
        // Only applied to NORMAL zone readings in the suspicious BPM range — if the
        // algorithm already detected ELEVATED/HIGH there's no half-rate concern.
        val tachycardiaWarning = zone == ZoneLabel.NORMAL && finalBpm in 65.0..85.0

        result = ResultData(
            bpm = finalBpm,
            confidence = finalConf,
            samples = bpmReadings.size,
            zone = zone,
            sqiPct = finalSqi.roundToInt().coerceIn(0, 100),
            tachycardiaWarning = tachycardiaWarning
        )
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
        sqiReadings.clear(); qualityFlags.clear()
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
        isFlashEnabled = false
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
