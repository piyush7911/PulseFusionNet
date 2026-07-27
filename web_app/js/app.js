import { CameraController } from './camera.js';
import { FingerDetector, MovementDetector } from './detectors.js';
import { analyze } from './ppg.js';

const MEASURE_DURATION_SEC = 60;
const STABLE_FRAMES_NEEDED = 60; // ~2s at ~30fps
const ABSENT_PAUSE_FRAMES = 15;
const ABSENT_ABORT_FRAMES = 150;
const MAX_BUFFER = 1800; // 60s at 30fps
const ANALYSIS_INTERVAL_MS = 3000;

const $ = (id) => document.getElementById(id);
const screens = ['permission', 'home', 'detecting', 'measuring', 'result', 'failed'];
function showScreen(name) {
  for (const s of screens) $(`screen-${s}`).classList.toggle('active', s === name);
}

function median(values) {
  if (values.length === 0) return 0;
  const s = [...values].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
}

const DEBUG = new URLSearchParams(location.search).has('debug');

class App {
  constructor() {
    this.camera = new CameraController((stats) => this.onFrame(stats));
    this.fingerDetector = new FingerDetector();
    this.movementDetector = new MovementDetector();
    this.journey = 'permission';
    this.resetAll();
    this.bindUi();
    this.init();
    if (DEBUG) $('debug').classList.remove('hidden');
  }

  async init() {
    let granted = false;
    try {
      if (navigator.permissions && navigator.permissions.query) {
        const status = await navigator.permissions.query({ name: 'camera' });
        granted = status.state === 'granted';
      }
    } catch (e) {
      // Permissions API for 'camera' unsupported (e.g. iOS Safari) — fall through to the ask screen.
    }
    showScreen(granted ? 'home' : 'permission');
  }

  resetAll() {
    this.fingerDetector.reset();
    this.movementDetector.reset();
    this.greenBuffer = [];
    this.redBuffer = [];
    this.timestamps = [];
    this.bpmReadings = [];
    this.confReadings = [];
    this.sqiReadings = [];
    this.qualityFlags = [];
    this.stableFrames = 0;
    this.absentFrames = 0;
    this.measurementStarted = false;
    this.fingerOnLens = false;
    this.secondsRemaining = MEASURE_DURATION_SEC;
    this.fingerPresent = true;
    this.movementWarning = false;
    this.isPaused = false;
    this.emaBpm = 0;
    this.emaCount = 0;
    this.countdownTimer = null;
    this.analysisTimer = null;
    this.waveformTimer = null;
    this.isFlashEnabled = false;
  }

  bindUi() {
    $('btn-grant').addEventListener('click', async () => {
      const ok = await this.ensureCamera();
      if (ok) showScreen('home');
    });
    $('btn-start').addEventListener('click', () => this.startScan());
    $('btn-cancel').addEventListener('click', () => this.cancelAndReturnHome());
    $('btn-cancel-measuring').addEventListener('click', () => this.cancelAndReturnHome());
    $('btn-done').addEventListener('click', () => this.cancelAndReturnHome());
    $('btn-again').addEventListener('click', () => this.startScan());
    $('btn-retry').addEventListener('click', () => this.startScan());
    $('btn-failed-home').addEventListener('click', () => this.cancelAndReturnHome());
    $('btn-flash').addEventListener('click', () => this.toggleFlash());
    $('btn-flash-m').addEventListener('click', () => this.toggleFlash());
    $('how-it-works-toggle').addEventListener('click', () => {
      const body = $('how-it-works-body');
      const chevron = $('how-chevron');
      const open = body.classList.toggle('hidden') === false;
      chevron.classList.toggle('open', open);
    });
  }

  async ensureCamera() {
    if (this.camera.stream) return true;
    try {
      await this.camera.start();
      const hasTorch = this.camera.hasTorch();
      $('btn-flash').classList.toggle('hidden', !hasTorch);
      $('btn-flash-m').classList.toggle('hidden', !hasTorch);
      $('camera-preview').srcObject = this.camera.stream;
      $('camera-preview-m').srcObject = this.camera.stream;
      $('permission-error').classList.add('hidden');
      return true;
    } catch (e) {
      $('permission-error').textContent = 'Camera access is required to measure your pulse. ' +
        'Check your browser/site permissions and try again. (' + (e.message || e) + ')';
      $('permission-error').classList.remove('hidden');
      showScreen('permission');
      return false;
    }
  }

  async toggleFlash() {
    this.isFlashEnabled = !this.isFlashEnabled;
    await this.camera.setTorch(this.isFlashEnabled);
    for (const btn of [$('btn-flash'), $('btn-flash-m')]) {
      btn.classList.toggle('on', this.isFlashEnabled);
      btn.querySelector('span').textContent = this.isFlashEnabled ? 'Flash ON' : 'Flash OFF';
    }
  }

  async startScan() {
    const ok = await this.ensureCamera();
    if (!ok) return;
    this.resetAll();
    this.journey = 'detecting';
    showScreen('detecting');
    $('detecting-title').textContent = 'Waiting for finger…';
    $('detecting-status').textContent = 'Cover the camera lens completely with your fingertip';
    $('detecting-dot').classList.remove('on');
    $('detecting-ring').classList.remove('active');
    $('stabilization-bar').style.width = '0%';
    $('detecting-wave-card').classList.add('hidden');
    this.startWaveformTicker();
  }

  startWaveformTicker() {
    clearInterval(this.waveformTimer);
    this.waveformTimer = setInterval(() => {
      if (this.journey !== 'detecting' && this.journey !== 'measuring') return;
      if (this.greenBuffer.length < 5) return;
      if (this.journey === 'detecting') {
        $('detecting-wave-card').classList.remove('hidden');
        this.drawWaveform('wave-detecting-card');
      } else {
        $('measuring-wave-card').classList.remove('hidden');
        this.drawWaveform('wave-measuring');
      }
    }, 120);
  }

  drawWaveform(canvasId) {
    const canvas = $(canvasId);
    if (!canvas) return;
    const w = canvas.width, h = canvas.height;
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, w, h);
    const samples = this.greenBuffer.slice(-150);
    const min = Math.min(...samples), max = Math.max(...samples);
    const range = Math.max(max - min, 1e-6);
    ctx.strokeStyle = '#ff4d5e';
    ctx.lineWidth = 2;
    ctx.beginPath();
    samples.forEach((v, i) => {
      const x = (i / (samples.length - 1 || 1)) * w;
      const y = h - ((v - min) / range) * (h - 6) - 3;
      i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    });
    ctx.stroke();
  }

  cancelAndReturnHome() {
    this.stopTimers();
    this.camera.setTorch(false);
    this.resetAll();
    this.journey = 'home';
    showScreen('home');
  }

  onFrame(stats) {
    try {
      const present = this.fingerDetector.update(stats);
      if (this.journey === 'detecting') this.handleDetectingFrame(present, stats);
      else if (this.journey === 'measuring') this.handleMeasuringFrame(present, stats);
    } catch (e) {
      const dbg = $('debug');
      if (dbg) dbg.textContent = 'onFrame error: ' + (e && e.stack || e);
    }
  }

  handleDetectingFrame(present, stats) {
    this.fingerOnLens = present;
    $('detecting-dot').classList.toggle('on', present);
    $('detecting-ring').classList.toggle('active', present);
    $('finger-icon').style.color = present ? 'var(--ok)' : '';
    $('detecting-title').textContent = present ? 'Finger detected!' : 'Waiting for finger…';
    $('detecting-status').textContent = present ? 'Hold still — calibrating signal quality' : 'Cover the camera lens completely with your fingertip';

    if (DEBUG) {
      $('debug').textContent =
        `R=${stats.avgR.toFixed(1)} G=${stats.avgG.toFixed(1)} B=${stats.avgB.toFixed(1)} spatialStdR=${stats.spatialStdR.toFixed(1)}\n` +
        `score=${this.fingerDetector.score} present=${present} stableFrames=${this.stableFrames}`;
    }

    if (!present) {
      this.stableFrames = 0;
      $('stabilization-bar').style.width = '0%';
      this.greenBuffer = []; this.redBuffer = []; this.timestamps = [];
      return;
    }
    this.stableFrames++;
    this.pushSample(stats);
    const pct = Math.min(100, Math.round((this.stableFrames / STABLE_FRAMES_NEEDED) * 100));
    $('stabilization-bar').style.width = `${pct}%`;
    if (this.stableFrames >= STABLE_FRAMES_NEEDED) this.startMeasurement();
  }

  handleMeasuringFrame(present, stats) {
    this.fingerPresent = present;
    const pill = $('finger-status-pill');
    const text = $('finger-status-text');
    const dot = pill.querySelector('.dot');
    if (!present) {
      this.absentFrames++;
      this.movementDetector.reset();
      if (this.absentFrames >= ABSENT_PAUSE_FRAMES && !this.isPaused) this.isPaused = true;
      pill.className = 'status-pill error';
      dot.className = 'dot';
      text.textContent = 'Finger missing — replace to resume';
      $('seconds-label').textContent = 'PAUSED';
      $('seconds-label').classList.add('paused');
      if (this.absentFrames >= ABSENT_ABORT_FRAMES) this.abort('Finger was lifted from the camera for too long.');
      return;
    }
    this.absentFrames = 0;
    if (this.isPaused) {
      this.isPaused = false;
      $('seconds-label').textContent = 'sec left';
      $('seconds-label').classList.remove('paused');
    }

    const isMovement = this.movementDetector.update(stats);
    this.movementWarning = isMovement;
    if (isMovement) {
      pill.className = 'status-pill warn';
      dot.className = 'dot';
      dot.style.background = 'var(--warn)';
      text.textContent = 'Movement detected — hold still!';
    } else {
      pill.className = 'status-pill';
      dot.className = 'dot on';
      dot.style.background = '';
      text.textContent = 'Finger detected — measuring';
    }
    if (this.movementDetector.consecutiveMovementFrames >= this.movementDetector.abortFrames && this.secondsRemaining > 3) {
      this.abort('Excessive movement detected. Keep your finger and phone completely still.');
      return;
    }
    this.pushSample(stats);
  }

  pushSample(stats) {
    this.greenBuffer.push(stats.avgG);
    this.redBuffer.push(stats.avgR);
    this.timestamps.push(performance.now());
    if (this.greenBuffer.length > MAX_BUFFER) {
      this.greenBuffer.shift(); this.redBuffer.shift(); this.timestamps.shift();
    }
  }

  startMeasurement() {
    if (this.measurementStarted) return;
    this.measurementStarted = true;
    this.absentFrames = 0;
    this.movementDetector.reset();
    this.bpmReadings = []; this.confReadings = []; this.sqiReadings = []; this.qualityFlags = [];
    this.emaBpm = 0; this.emaCount = 0;
    this.secondsRemaining = MEASURE_DURATION_SEC;
    this.journey = 'measuring';
    showScreen('measuring');
    $('live-bpm').textContent = '--';
    $('signal-quality-bar').style.width = '0%';
    $('signal-quality-text').textContent = '0%';
    $('measuring-wave-card').classList.add('hidden');
    $('seconds-remaining').textContent = String(MEASURE_DURATION_SEC);
    $('seconds-label').textContent = 'sec left';
    $('seconds-label').classList.remove('paused');

    this.analysisTimer = setInterval(() => this.runAnalysis(), ANALYSIS_INTERVAL_MS);
    this.countdownTimer = setInterval(() => {
      if (!this.isPaused) this.secondsRemaining--;
      $('seconds-remaining').textContent = String(this.secondsRemaining);
      if (this.secondsRemaining <= 0) this.finishMeasurement();
    }, 1000);
  }

  estimateFps() {
    if (this.timestamps.length < 2) return 30.0;
    const elapsedSec = (this.timestamps[this.timestamps.length - 1] - this.timestamps[0]) / 1000.0;
    if (elapsedSec <= 0) return 30.0;
    return Math.min(Math.max((this.timestamps.length - 1) / elapsedSec, 10.0), 60.0);
  }

  runAnalysis() {
    if (this.isPaused) return;
    const realFps = this.estimateFps();
    const minSamples = Math.ceil(realFps * 3.0);
    if (this.greenBuffer.length < minSamples) return;

    const green = this.greenBuffer.slice();
    const red = this.redBuffer.slice();
    if (green.length === 0 || red.length !== green.length) return;

    let ensemble;
    try {
      ensemble = analyze(green, red, realFps);
    } catch (e) {
      return;
    }

    const bpm = ensemble.consensusBpm;
    if (!Number.isFinite(bpm) || bpm <= 0.0) return;

    this.emaBpm = this.emaCount === 0 ? bpm : 0.30 * bpm + 0.70 * this.emaBpm;
    this.emaCount++;

    this.bpmReadings.push(this.emaBpm);
    this.confReadings.push(ensemble.confidence);
    this.sqiReadings.push(ensemble.signalQualityIndex);
    this.qualityFlags.push(ensemble.qualityFlag);

    $('live-bpm').textContent = this.emaBpm.toFixed(1);
    const sqiPct = Math.min(Math.max(Math.round(ensemble.signalQualityIndex), 0), 100);
    $('signal-quality-bar').style.width = `${sqiPct}%`;
    $('signal-quality-text').textContent = `${sqiPct}%`;
  }

  finishMeasurement() {
    this.stopTimers();
    if (this.bpmReadings.length === 0) {
      this.abort('No valid readings collected. Ensure the camera is fully covered.');
      return;
    }
    const stableBpms = this.bpmReadings.slice(-5);
    const stableConfs = this.confReadings.slice(-5);
    const stableSqi = this.sqiReadings.slice(-5);

    const finalBpm = median(stableBpms);
    const finalConf = median(stableConfs);
    const finalSqi = median(stableSqi);

    if (finalSqi < 35.0) {
      this.abort('Signal corrupted by finger motion or low optical contrast. Please hold your finger still and re-measure.');
      return;
    }

    let zone;
    if (finalBpm < 60) zone = 'BRADYCARDIA';
    else if (finalBpm <= 100) zone = 'NORMAL';
    else if (finalBpm <= 120) zone = 'ELEVATED';
    else zone = 'HIGH';

    const tachycardiaWarning = zone === 'NORMAL' && finalBpm >= 65.0 && finalBpm <= 85.0;

    this.showResult({
      bpm: finalBpm, confidence: finalConf, samples: this.bpmReadings.length,
      zone, sqiPct: Math.min(Math.max(Math.round(finalSqi), 0), 100), tachycardiaWarning,
    });
  }

  showResult(result) {
    this.stopTimers();
    this.camera.setTorch(false);
    this.journey = 'result';
    showScreen('result');
    const zoneColors = { BRADYCARDIA: '#60a5fa', NORMAL: 'var(--ok)', ELEVATED: 'var(--warn)', HIGH: 'var(--pulse)' };
    $('result-bpm').textContent = result.bpm.toFixed(1);
    $('result-zone').textContent = result.zone;
    $('result-zone').className = `zone-badge zone-${result.zone.toLowerCase()}`;
    $('result-icon').style.color = zoneColors[result.zone];
    $('result-confidence').textContent = `${Math.round(result.confidence)}%`;
    $('result-sqi').textContent = `${result.sqiPct}%`;
    $('result-samples').textContent = String(result.samples);
    $('result-tachycardia').classList.toggle('hidden', !result.tachycardiaWarning);
  }

  abort(reason) {
    this.stopTimers();
    this.camera.setTorch(false);
    this.journey = 'failed';
    showScreen('failed');
    $('failed-reason').textContent = reason;
  }

  stopTimers() {
    clearInterval(this.countdownTimer); this.countdownTimer = null;
    clearInterval(this.analysisTimer); this.analysisTimer = null;
    clearInterval(this.waveformTimer); this.waveformTimer = null;
  }
}

window.addEventListener('DOMContentLoaded', () => {
  new App();
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }
});
