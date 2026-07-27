// Direct port of app/src/main/kotlin/.../ppg/FingerMovementDetector.kt — same thresholds,
// so tuning validated on the Android build carries over unchanged.
'use strict';

export class FingerDetector {
  constructor({
    rgRatioMin = 2.3, rbRatioMin = 2.8, minRed = 85.0, maxRed = 248.0,
    maxSpatialStd = 38.0, scoreUp = 3, scoreDown = 5, onThreshold = 55, offThreshold = 20,
  } = {}) {
    Object.assign(this, { rgRatioMin, rbRatioMin, minRed, maxRed, maxSpatialStd, scoreUp, scoreDown, onThreshold, offThreshold });
    this.score = 0;
    this.isPresent = false;
  }

  update(stats) {
    const rgRatio = stats.avgR / (stats.avgG + 0.01);
    const rbRatio = stats.avgR / (stats.avgB + 0.01);
    const criteria = rgRatio > this.rgRatioMin && rbRatio > this.rbRatioMin &&
      stats.avgR > this.minRed && stats.avgR < this.maxRed && stats.spatialStdR < this.maxSpatialStd;

    this.score = Math.min(100, Math.max(0, this.score + (criteria ? this.scoreUp : -this.scoreDown)));
    if (!this.isPresent && this.score > this.onThreshold) this.isPresent = true;
    if (this.isPresent && this.score < this.offThreshold) this.isPresent = false;
    return this.isPresent;
  }

  reset() {
    this.score = 0;
    this.isPresent = false;
  }
}

export class MovementDetector {
  constructor({ historyFrames = 90, madMultiplier = 5.0, minThreshold = 35.0, abortFrames = 90 } = {}) {
    Object.assign(this, { historyFrames, madMultiplier, minThreshold, abortFrames });
    this.spatialDiffBuffer = [];
    this.prevRed = null;
    this.prevGreen = null;
    this.prevSpatialStd = null;
    this.consecutiveMovementFrames = 0;
    this.motionQualityScore = 100;
  }

  update(stats) {
    const { avgR, avgG, spatialStdR: spatialStd } = stats;
    const prevR = this.prevRed, prevG = this.prevGreen, prevS = this.prevSpatialStd;
    this.prevRed = avgR; this.prevGreen = avgG; this.prevSpatialStd = spatialStd;

    if (prevR === null || prevG === null || prevS === null) return false;

    const diffR = Math.abs(avgR - prevR);
    const diffG = Math.abs(avgG - prevG);
    const diffS = Math.abs(spatialStd - prevS);

    this.spatialDiffBuffer.push(diffS);
    if (this.spatialDiffBuffer.length > this.historyFrames) this.spatialDiffBuffer.shift();

    const sortedS = [...this.spatialDiffBuffer].sort((a, b) => a - b);
    const madS = sortedS[Math.floor(sortedS.length / 2)];
    const thresholdS = Math.max(madS * this.madMultiplier, 35.0);

    const isSpatialShift = diffS > thresholdS && this.spatialDiffBuffer.length >= this.historyFrames / 2;
    const isGrossMotionJump = diffR > 45.0 && diffG > 45.0;
    const isMovement = isSpatialShift || isGrossMotionJump;

    const normS = Math.min(Math.max(diffS / (thresholdS + 1e-6), 0.0), 2.0);
    const normJ = Math.min(Math.max(Math.max(diffR, diffG) / 45.0, 0.0), 2.0);
    const penalty = Math.floor(normS * 30.0 + normJ * 30.0);
    this.motionQualityScore = Math.min(100, Math.max(0, 100 - penalty));

    this.consecutiveMovementFrames = isMovement ? this.consecutiveMovementFrames + 1 : Math.max(0, this.consecutiveMovementFrames - 8);
    return isMovement;
  }

  reset() {
    this.spatialDiffBuffer = [];
    this.prevRed = null;
    this.prevGreen = null;
    this.prevSpatialStd = null;
    this.consecutiveMovementFrames = 0;
    this.motionQualityScore = 100;
  }
}
