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
 * Adaptive MAD-based movement and contact detector — a frame is flagged when its green-channel
 * frame-to-frame delta OR spatial contact-pressure variation (spatialStdR delta) exceeds the
 * recent Median Absolute Deviation by a wide margin.
 */
class MovementDetector(
    private val historyFrames: Int = 90,
    private val madMultiplier: Double = 5.0,
    private val minThreshold: Double = 18.0,
    val abortFrames: Int = 45
) {
    private val diffBuffer = ArrayDeque<Double>()
    private val spatialDiffBuffer = ArrayDeque<Double>()
    private var prevGreen: Double? = null
    private var prevSpatialStd: Double? = null

    var consecutiveMovementFrames = 0
        private set
    var motionQualityScore: Int = 100 // 100 = perfect stillness, 0 = severe motion/contact slip
        private set

    fun update(stats: FrameStats): Boolean {
        val avgG = stats.avgG
        val spatialStd = stats.spatialStdR

        val prevG = prevGreen
        val prevS = prevSpatialStd
        prevGreen = avgG
        prevSpatialStd = spatialStd

        if (prevG == null || prevS == null) return false

        val diffG = kotlin.math.abs(avgG - prevG)
        val diffS = kotlin.math.abs(spatialStd - prevS)

        diffBuffer.addLast(diffG)
        spatialDiffBuffer.addLast(diffS)

        if (diffBuffer.size > historyFrames) diffBuffer.removeFirst()
        if (spatialDiffBuffer.size > historyFrames) spatialDiffBuffer.removeFirst()

        val sortedG = diffBuffer.sorted()
        val madG = sortedG[sortedG.size / 2]
        val thresholdG = maxOf(madG * madMultiplier, minThreshold)

        val sortedS = spatialDiffBuffer.sorted()
        val madS = sortedS[sortedS.size / 2]
        val thresholdS = maxOf(madS * madMultiplier, 14.0)

        val isGreenMovement = diffG > thresholdG && diffBuffer.size >= historyFrames / 2
        val isSpatialShift = diffS > thresholdS && spatialDiffBuffer.size >= historyFrames / 2

        val isMovement = isGreenMovement || isSpatialShift

        // Continuous quality score computation (100 = steady, 0 = motion spike)
        val normG = (diffG / (thresholdG + 1e-6)).coerceIn(0.0, 3.0)
        val normS = (diffS / (thresholdS + 1e-6)).coerceIn(0.0, 2.0)
        val penalty = ((normG * 30.0) + (normS * 20.0)).toInt()
        motionQualityScore = (100 - penalty).coerceIn(0, 100)

        // Rapid decay when still (-5 per frame) so transient micro-ticks never accumulate across 60 seconds
        consecutiveMovementFrames = if (isMovement) consecutiveMovementFrames + 1 else maxOf(0, consecutiveMovementFrames - 5)
        return isMovement
    }

    fun update(avgG: Double): Boolean {
        return update(FrameStats(avgR = 150.0, avgG = avgG, avgB = 50.0, spatialStdR = 10.0))
    }

    fun reset() {
        diffBuffer.clear()
        spatialDiffBuffer.clear()
        prevGreen = null
        prevSpatialStd = null
        consecutiveMovementFrames = 0
        motionQualityScore = 100
    }
}
