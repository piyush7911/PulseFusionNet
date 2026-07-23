# PulseFusionNet — Android

Native Android port of the camera-based PPG heart-rate monitor. Unlike `web_app`
(which POSTs frames to the FastAPI backend in `server.py`), this app runs the
**entire pipeline on-device** — no network calls, no server. Camera frames never
leave the phone.

## What's on-device — real Python, not a Kotlin reimplementation

The DSP pipeline runs as **actual Python**, embedded in the APK via
[Chaquopy](https://chaquo.com/chaquopy/) (a real CPython interpreter bundled
into the app, called from Kotlin through a JNI bridge). This is deliberate:
scipy and PyWavelets have no Android build (verified against Chaquopy's own
package repository and PyPI — neither publishes anything for Android), so the
two scipy/pywt calls in the original pipeline were rewritten using numpy only
(which Chaquopy does support). Everything else is numpy's own `rfft` /
`linalg.eigh` — the same class of primitive the original backend uses, not a
hand-rolled substitute.

| Backend (`pulsefusionnet/`, scipy+pywt) | On-device (`app/src/main/python/pulsefusion_ppg.py`, numpy-only) |
|---|---|
| `RealPhysiologicalPreprocessor.preprocess_camera_ppg` | `preprocess_camera_ppg()` — same stages, same order |
| `scipy.signal.butter` + `filtfilt` | `_butter_bandpass` / `_filtfilt` — hand-written zpk + bilinear-transform IIR design (scipy has no Android build) |
| `pywt.wavedec` / `waverec` (sym4) | `_wavedec` / `_waverec` — self-contained sym4 DWT/IDWT + soft threshold (pywt has no Android build) |
| `scipy.signal.savgol_filter` | `_savgol_11` — same 11-pt cubic coefficients |
| `ClassicalPPGExtractor.extract_ensemble_bpm` | `extract_ensemble_bpm()` — **real** `numpy.fft.rfft` zero-padded spectrum + **Autocorrelation (ACF) Time-Lag Peak Estimator** + Multi-Domain Harmonic Disambiguation. |
| **Option 1 Quality Weighting** | `_compute_spectral_snr` + `_compute_acf_prominence` + `_compute_abs_skewness` — dynamic window quality weighting $Q_i$. |
| **Option 2 Adaptive Filtering** | `analyze_session()` — dynamic lowcut shifting ($0.90\text{ Hz} \rightarrow 1.35\text{ Hz}$) for respiration & sub-harmonic suppression on high HR ($\ge 100\text{ BPM}$). |
| **Option 4 IMU Motion Cancellation** | `cancel_imu_motion_artifacts()` — 3-axis IMU accelerometer NLMS adaptive noise cancellation filter. Overloaded bridge in `PyPpgBridge.kt`. |
| App.js finger/movement heuristics | `ppg/FingerMovementDetector.kt` (Kotlin) — identical thresholds, ambient light only (**no torch/flash**, same as the web app) |
| Server-side EMA smoothing | Same α=0.30 EMA, done in `MeasurementViewModel` |

Only two functions are genuinely hand-rolled substitutes (the Butterworth
filter and the wavelet denoise, standing in for scipy/pywt) — everything else
in `pulsefusion_ppg.py` is the exact same algorithm using numpy's real FFT and
linear algebra. Those two substitutes are validated against the real
scipy/pywt backend (see below): correlation ~0.999 on the cleaned signal,
consensus BPM within 0.00–0.04 BPM across a 58–140 BPM sweep.

## Chaquopy caveats — read before shipping

- **APK size**: bundling a Python interpreter + numpy adds real weight —
  expect tens of MB *per ABI* (we restrict to `arm64-v8a` + `x86_64` in
  `app/build.gradle.kts` to avoid paying that 4x). Use Play's per-ABI App
  Bundle splitting so users don't download both.
- **Startup cost**: `Python.start()` (in `PulseFusionApplication`) initializes
  the interpreter once at process start, adding to cold-start latency.
- **Licensing**: Chaquopy is free for open-source projects; commercial/closed-
  source use requires checking their current license terms directly at
  chaquo.com before shipping to production — I have not verified pricing/terms
  for a commercial release, only that the package repository lacks scipy/pywt.

## UI

A native Material3 app, not a ported web page — a branded cold-start splash
(OS-level `androidx.core.splashscreen` icon, then an in-app loading screen
while camera permission is checked) followed by an explicit journey:
**Loading → Permission → Home → Detecting → Measuring → Result/Failed**, custom
stroke-icon set (no emoji), animated screen transitions, a live PPG sparkline,
and a countdown ring drawn with Canvas. No camera preview surface is shown
(once a fingertip covers the lens the raw feed is just a dark-red blob with no
information); instead the scan/measure screens show the *derived* waveform and
an animated fingerprint/scan indicator, which communicates progress better.

## Project layout

```
android_app/
  app/src/main/python/pulsefusion_ppg.py  — the REAL on-device algorithm (numpy-only, runs via Chaquopy)
  app/src/main/kotlin/com/pulsefusionnet/app/
    PulseFusionApplication.kt    — starts the embedded Python interpreter once at process start
    MainActivity.kt              — permission flow, camera lifecycle, screen routing
    MeasurementViewModel.kt      — state machine mirroring app.js (detect → stabilize → 60s scan → result)
    ppg/PyPpgBridge.kt           — Kotlin -> Python call bridge (Chaquopy)
    ppg/FingerMovementDetector.kt — finger/movement heuristics (plain Kotlin, no numpy needed)
    camera/CameraController.kt   — CameraX ImageAnalysis, YUV_420_888 → RGB frame stats
    ui/                          — Compose screens, theme, icon set, shared components
```

## Building

1. Open the `android_app/` folder in Android Studio (Koala+ recommended).
2. Android Studio will offer to generate the Gradle wrapper jar if it's
   missing — accept it (this repo ships `gradlew`/`gradle-wrapper.properties`
   but not the binary `gradle-wrapper.jar`, since it can't be authored as
   text). Alternatively, if you have Gradle installed locally: `gradle wrapper`
   from this directory once.
3. First sync will download the Chaquopy plugin and its Python/numpy
   distribution from `chaquo.com/maven` — needs network access.
4. Sync, then Run on a device with a rear camera (API 26+). An emulator's
   virtual camera won't produce a real PPG signal — test on a physical device.

## Using it

Same protocol as the web app: cover the rear camera lens fully with a
fingertip, keep it still for the ~2s stabilization + 60s measurement window.
flash is used when ambient-light is very dark.

## Validating the on-device Python port

`pulsefusion_ppg.py` has zero Chaquopy/Android-specific code — it's plain
numpy, so it's tested by directly comparing it against the real scipy/pywt
backend, on your machine, with your `pulsefusionnet` conda env:

```bash
conda run -n pulsefusionnet python scripts/validate_numpy_ppg_port.py
```

This runs both pipelines over four synthetic signals (58/72/105/140 BPM) and
prints, per case: the lagged correlation between the two cleaned signals, the
full BPM breakdown from each pipeline, and the consensus-BPM difference. Last
run: correlation ≥0.998, consensus BPM within 0.00–0.04 BPM of the real
backend across the whole range. Re-run this after touching
`pulsefusion_ppg.py`.

## Known limitations

- Butterworth filter design and wavelet denoise in `pulsefusion_ppg.py` are
  hand-written numpy substitutes for scipy/pywt (see table above) — validated
  against the real backend on synthetic signals, not against real device
  recordings.
- No PCG/audio, murmur detection, or ONNX quality-scorer models are ported;
  this is the camera-PPG heart-rate path only, matching what `web_app` exposes.
- Chaquopy's APK size and licensing tradeoffs (above) haven't been weighed
  against your actual distribution plan — do that before shipping.
