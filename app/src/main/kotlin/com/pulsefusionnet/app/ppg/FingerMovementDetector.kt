package com.pulsefusionnet.app.ppg

/**
 * Per-frame RGB statistics from the camera analyzer.
 * spatialStdR = spatial std-dev of the red channel across the sampled frame — low means a
 * uniform surface (finger), high means scene texture/edges (table, room, background).
 */
data class FrameStats(val avgR: Double, val avgG: Double, val avgB: Double, val spatialStdR: Double)

/**
 * Finger-on-lens detector, multi-criteria + hysteresis — same thresholds as the web app's
 * detector (app.js). No torch/flash is used; this reads ambient light transmitted through
 * the fingertip, exactly like the browser build, so the tuning carries over unchanged.
 */
class FingerDetector(
    private val rgRatioMin: Double = 2.3,
    private val rbRatioMin: Double = 2.8,
    private val minRed: Double = 85.0,
    private val maxRed: Double = 248.0,
    private val maxSpatialStd: Double = 38.0,
    private val scoreUp: Int = 3,
    private val scoreDown: Int = 5,
    private val onThreshold: Int = 55,
    private val offThreshold: Int = 20
) {
    var score = 0
        private set
    var isPresent = false
        private set

    fun update(stats: FrameStats): Boolean {
        val rgRatio = stats.avgR / (stats.avgG + 0.01)
        val rbRatio = stats.avgR / (stats.avgB + 0.01)
        val criteria = rgRatio > rgRatioMin &&
            rbRatio > rbRatioMin &&
            stats.avgR > minRed &&
            stats.avgR < maxRed &&
            stats.spatialStdR < maxSpatialStd

        score = (score + if (criteria) scoreUp else -scoreDown).coerceIn(0, 100)
        if (!isPresent && score > onThreshold) isPresent = true
        if (isPresent && score < offThreshold) isPresent = false
        return isPresent
    }

    fun reset() {
        score = 0
        isPresent = false
    }
}

/**
 * Adaptive MAD-based movement detector — a frame is "movement" when its green-channel
 * frame-to-frame delta exceeds the recent Median Absolute Deviation by a wide margin.
 * MAD adapts to each person's own pulse amplitude, so it doesn't need per-user tuning.
 */
class MovementDetector(
    private val historyFrames: Int = 90,
    private val madMultiplier: Double = 5.0,
    private val minThreshold: Double = 10.0,
    val abortFrames: Int = 20
) {
    private val diffBuffer = ArrayDeque<Double>()
    private var prevGreen: Double? = null
    var consecutiveMovementFrames = 0
        private set

    fun update(avgG: Double): Boolean {
        val prev = prevGreen
        prevGreen = avgG
        if (prev == null) return false

        val diff = kotlin.math.abs(avgG - prev)
        diffBuffer.addLast(diff)
        if (diffBuffer.size > historyFrames) diffBuffer.removeFirst()

        val sorted = diffBuffer.sorted()
        val mad = sorted[sorted.size / 2]
        val threshold = maxOf(mad * madMultiplier, minThreshold)

        val isMovement = diff > threshold && diffBuffer.size >= historyFrames / 2
        consecutiveMovementFrames = if (isMovement) consecutiveMovementFrames + 1 else maxOf(0, consecutiveMovementFrames - 2)
        return isMovement
    }

    fun reset() {
        diffBuffer.clear()
        prevGreen = null
        consecutiveMovementFrames = 0
    }
}
