// Rear camera capture -> per-frame RGB stats. Mirrors CameraController.kt: no preview
// surface needed for the algorithm, downsample to a small grid (same reasoning as the
// original web app's 80x60 canvas / the Android build's stride-6 pixel sampling).
'use strict';

const SAMPLE_W = 80;
const SAMPLE_H = 60;

export class CameraController {
  constructor(onFrame) {
    this.onFrame = onFrame;
    this.stream = null;
    this.track = null;
    this.video = document.createElement('video');
    this.video.setAttribute('playsinline', '');
    this.video.setAttribute('autoplay', '');
    this.video.muted = true;
    // Must be attached + actually rendered or mobile Safari/Chrome won't decode frames
    // (a detached or display:none video never produces pixels for drawImage). Kept
    // effectively invisible instead: 1x1, opacity 0, no pointer events.
    Object.assign(this.video.style, {
      position: 'fixed', top: '0', left: '0', width: '1px', height: '1px',
      opacity: '0', pointerEvents: 'none', zIndex: '-1',
    });
    document.body.appendChild(this.video);
    this.canvas = document.createElement('canvas');
    this.canvas.width = SAMPLE_W;
    this.canvas.height = SAMPLE_H;
    this.ctx = this.canvas.getContext('2d', { willReadFrequently: true });
    this._running = false;
    this._rafId = null;
  }

  async start() {
    this.stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 320 }, height: { ideal: 240 } },
      audio: false,
    });
    this.track = this.stream.getVideoTracks()[0];
    this.video.srcObject = this.stream;
    await this.video.play();
    this._running = true;
    this._loop();
  }

  hasTorch() {
    if (!this.track) return false;
    const caps = this.track.getCapabilities ? this.track.getCapabilities() : {};
    return !!caps.torch;
  }

  async setTorch(enabled) {
    if (!this.hasTorch()) return false;
    try {
      await this.track.applyConstraints({ advanced: [{ torch: enabled }] });
      return true;
    } catch (e) {
      return false;
    }
  }

  stop() {
    this._running = false;
    if (this._rafId) cancelAnimationFrame(this._rafId);
    if (this.track) { try { this.track.stop(); } catch (e) {} }
    this.stream = null;
    this.track = null;
  }

  _loop() {
    if (!this._running) return;
    if (this.video.readyState >= 2) {
      this.onFrame(this._extractStats());
    }
    this._rafId = requestAnimationFrame(() => this._loop());
  }

  _extractStats() {
    this.ctx.drawImage(this.video, 0, 0, SAMPLE_W, SAMPLE_H);
    const { data } = this.ctx.getImageData(0, 0, SAMPLE_W, SAMPLE_H);
    let sumR = 0, sumG = 0, sumB = 0, sumR2 = 0;
    const n = data.length / 4;
    for (let i = 0; i < data.length; i += 4) {
      const r = data[i], g = data[i + 1], b = data[i + 2];
      sumR += r; sumG += g; sumB += b; sumR2 += r * r;
    }
    const avgR = sumR / n, avgG = sumG / n, avgB = sumB / n;
    const spatialStdR = Math.sqrt(Math.max(0, sumR2 / n - avgR * avgR));
    return { avgR, avgG, avgB, spatialStdR };
  }
}
