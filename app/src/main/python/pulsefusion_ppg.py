"""
On-device PPG pipeline — runs as REAL Python (via Chaquopy) inside the Android app.

This mirrors pulsefusionnet/datasets/real_pipeline.py (RealPhysiologicalPreprocessor)
and pulsefusionnet/ppg/classical.py (ClassicalPPGExtractor) as closely as possible,
using only numpy — scipy and PyWavelets have no Android build, so `butter`/`filtfilt`
and `pywt.wavedec/waverec` are reimplemented here with plain numpy. Everything else
(the FFT-based multi-harmonic spectrum, parabolic peak interpolation, PCA-combined
channel, 3-vote ensemble) is numpy's own `rfft`/`linalg.eigh` — the same primitives
the original backend uses, not a hand-rolled substitute.

Two pieces ARE hand-rolled substitutes for scipy/pywt, and are the parts to watch:
  - _butter_bandpass / _filtfilt   (stands in for scipy.signal.butter + filtfilt)
  - _wavelet_denoise               (stands in for pywt.wavedec/waverec, sym4)
Both are validated against the real scipy/pywt backend by
scripts/validate_numpy_ppg_port.py before being trusted on-device.
"""

import numpy as np

# ── Symlet-4 (sym4) filter coefficients — same wavelet the backend uses ────────
_SYM4_DEC_LO = np.array([
    -0.07576571478927333, -0.02963552764599851, 0.49761866763201545, 0.8037387518059161,
    0.29785779560527736, -0.09921954357684722, -0.012603967262037833, 0.03222310060404270
])
_SYM4_DEC_HI = np.array([((-1.0) ** i) * _SYM4_DEC_LO[7 - i] for i in range(8)])
_SYM4_REC_LO = _SYM4_DEC_LO[::-1]
_SYM4_REC_HI = _SYM4_DEC_HI[::-1]
_SYM4_LEN = 8

_SG11 = np.array([-36.0, 9.0, 44.0, 69.0, 84.0, 89.0, 84.0, 69.0, 44.0, 9.0, -36.0])
_SG11_NORM = 429.0


# ── Butterworth bandpass (analog prototype -> lp2bp -> bilinear transform) ─────

def _butter_bandpass(order: int, low: float, high: float):
    lo = max(0.001, min(low, 0.95))
    hi = max(lo + 0.01, min(high, 0.99))

    proto_poles = np.array([
        np.exp(1j * np.pi * (2 * k + order + 1) / (2.0 * order)) for k in range(order)
    ])

    fs = 2.0
    warped_low = 2.0 * fs * np.tan(np.pi * lo / fs)
    warped_high = 2.0 * fs * np.tan(np.pi * hi / fs)
    bw = warped_high - warped_low
    w0 = np.sqrt(warped_low * warped_high)

    p_scaled = proto_poles * (bw / 2.0)
    disc = np.sqrt(p_scaled ** 2 - w0 ** 2 + 0j)
    bp_poles = np.concatenate([p_scaled + disc, p_scaled - disc])
    bp_zeros = np.zeros(order, dtype=complex)
    k_bp = bw ** order

    fs2 = 2.0 * fs
    z_digital = (fs2 + bp_zeros) / (fs2 - bp_zeros)
    p_digital = (fs2 + bp_poles) / (fs2 - bp_poles)
    degree = len(p_digital) - len(z_digital)
    z_digital = np.concatenate([z_digital, -np.ones(degree, dtype=complex)])

    gain = np.prod(fs2 - bp_zeros) / np.prod(fs2 - bp_poles)
    k_digital = (k_bp * gain).real

    b = (np.poly(z_digital) * k_digital).real
    a = np.poly(p_digital).real
    return b / a[0], a / a[0]


def _lfilter(b: np.ndarray, a: np.ndarray, x: np.ndarray) -> np.ndarray:
    n = max(len(a), len(b))
    bp = np.zeros(n); bp[:len(b)] = b
    ap = np.zeros(n); ap[:len(a)] = a
    z = np.zeros(n - 1)
    y = np.zeros(len(x))
    for i, xi in enumerate(x):
        yi = bp[0] * xi + (z[0] if n > 1 else 0.0)
        y[i] = yi
        for j in range(n - 2):
            z[j] = bp[j + 1] * xi + z[j + 1] - ap[j + 1] * yi
        if n > 1:
            z[n - 2] = bp[n - 1] * xi - ap[n - 1] * yi
    return y


def _filtfilt(b: np.ndarray, a: np.ndarray, x: np.ndarray) -> np.ndarray:
    pad_len = min(3 * max(len(a), len(b)), len(x) - 1)
    pad_len = max(pad_len, 0)
    if pad_len == 0 or len(x) <= pad_len:
        y = _lfilter(b, a, x)
        return _lfilter(b, a, y[::-1])[::-1]

    left = 2 * x[0] - x[pad_len:0:-1]
    right = 2 * x[-1] - x[-2:-2 - pad_len:-1]
    padded = np.concatenate([left, x, right])

    forward = _lfilter(b, a, padded)
    backward = _lfilter(b, a, forward[::-1])[::-1]
    return backward[pad_len:pad_len + len(x)]


# ── sym4 DWT / IDWT — self-consistent (own dwt/idwt are exact inverses) ────────

def _reflect_index(idx: int, n: int) -> int:
    if n == 1:
        return 0
    period = 2 * n
    i = idx % period
    if i < 0:
        i += period
    return i if i < n else period - 1 - i


def _dwt_level(x: np.ndarray):
    flen = _SYM4_LEN
    padded = np.array([x[_reflect_index(i - (flen - 1), len(x))] for i in range(len(x) + 2 * (flen - 1))])
    out_len = (len(x) + flen - 1) // 2
    cA = np.zeros(out_len)
    cD = np.zeros(out_len)
    for k in range(out_len):
        window = padded[2 * k: 2 * k + flen]
        if len(window) < flen:
            window = np.pad(window, (0, flen - len(window)))
        cA[k] = np.dot(_SYM4_DEC_LO[::-1], window)
        cD[k] = np.dot(_SYM4_DEC_HI[::-1], window)
    return cA, cD


def _idwt_level(cA: np.ndarray, cD: np.ndarray, out_len: int) -> np.ndarray:
    flen = _SYM4_LEN
    n = len(cA)
    up_a = np.zeros(2 * n); up_a[0::2] = cA
    up_d = np.zeros(2 * n); up_d[0::2] = cD
    conv = np.convolve(up_a, _SYM4_REC_LO) + np.convolve(up_d, _SYM4_REC_HI)
    conv_len = len(conv)
    start = max(0, (conv_len - out_len) // 2)
    end = min(conv_len, start + out_len)
    result = conv[start:end]
    if len(result) < out_len:
        result = np.pad(result, (0, out_len - len(result)))
    return result


def _wavedec(x: np.ndarray, level: int):
    current = x
    details = []
    lengths = []
    for _ in range(level):
        if len(current) < _SYM4_LEN:
            break
        lengths.append(len(current))
        cA, cD = _dwt_level(current)
        details.append(cD)
        current = cA
    return current, details[::-1], lengths[::-1]


def _waverec(approx: np.ndarray, details, lengths) -> np.ndarray:
    current = approx
    for cD, out_len in zip(details, lengths):
        current = _idwt_level(current, cD, out_len)
    return current


def _adaptive_dwt_level(fps: float, highcut: float) -> int:
    level = 1
    nyq = fps / 2.0
    while nyq / (2.0 ** (level + 1)) > highcut:
        level += 1
    return max(1, min(level, 4))


def _wavelet_denoise(signal: np.ndarray, level: int) -> np.ndarray:
    if len(signal) < 64:
        return signal
    try:
        approx, details, lengths = _wavedec(signal, level)
        if not details:
            return signal
        finest = details[-1]
        sigma = (1.0 / 0.6745) * np.median(np.abs(finest - np.median(finest)))
        threshold = sigma * np.sqrt(2.0 * np.log(len(signal)))
        denoised_details = [np.sign(d) * np.maximum(np.abs(d) - threshold, 0.0) for d in details]
        out = _waverec(approx, denoised_details, lengths)
        return out[:len(signal)]
    except Exception:
        return signal


def _savgol_11(signal: np.ndarray) -> np.ndarray:
    if len(signal) < 11:
        return signal
    padded = np.pad(signal, (5, 5), mode="edge")
    out = np.zeros(len(signal))
    for i in range(len(signal)):
        window = padded[i:i + 11]
        out[i] = np.dot(_SG11, window) / _SG11_NORM
    return out


def cancel_imu_motion_artifacts(
    raw_ppg: np.ndarray,
    accel_x: np.ndarray = None,
    accel_y: np.ndarray = None,
    accel_z: np.ndarray = None,
    fps: float = 30.0,
    filter_order: int = 12,
    mu: float = 0.05
) -> np.ndarray:
    """
    Normalized Least Mean Squares (NLMS) Adaptive Motion Cancellation Filter (Option 4).
    Cancels motion-correlated optical noise from raw PPG signals using 3-axis IMU accelerometer inputs.
    
    Mathematical Formulation:
      y[n] = sum_{k=0}^{P-1} (w_{x,k} A_x[n-k] + w_{y,k} A_y[n-k] + w_{z,k} A_z[n-k])
      e[n] = ppg[n] - y[n]   (Clean Motion-Cancelled Signal)
      w[n+1] = w[n] + (mu / (||A[n]||^2 + eps)) * e[n] * A[n]
    """
    sig = np.asarray(raw_ppg, dtype=np.float64)
    if accel_x is None or accel_y is None or accel_z is None:
        return sig
    
    ax = np.asarray(accel_x, dtype=np.float64)
    ay = np.asarray(accel_y, dtype=np.float64)
    az = np.asarray(accel_z, dtype=np.float64)

    if len(ax) != len(sig) or len(ay) != len(sig) or len(az) != len(sig) or len(sig) < filter_order:
        return sig  # Fallback gracefully if array lengths mismatch or signal is short

    # Bandpass filter IMU acceleration signals to cardiac/motion band (0.5 - 5.0 Hz)
    nyquist = 0.5 * fps
    low = 0.5 / nyquist
    high = min(0.95, 5.0 / nyquist)
    b, a = _butter_bandpass(3, low, high)
    
    ax_f = _filtfilt(b, a, ax - np.mean(ax))
    ay_f = _filtfilt(b, a, ay - np.mean(ay))
    az_f = _filtfilt(b, a, az - np.mean(az))

    n = len(sig)
    clean_ppg = np.zeros(n, dtype=np.float64)

    # Weights for 3 axes x filter_order
    weights = np.zeros((3, filter_order), dtype=np.float64)

    for i in range(filter_order, n):
        # Extract delay buffers
        buf_x = ax_f[i - filter_order + 1 : i + 1][::-1]
        buf_y = ay_f[i - filter_order + 1 : i + 1][::-1]
        buf_z = az_f[i - filter_order + 1 : i + 1][::-1]

        # Predicted motion artifact estimate
        motion_est = (
            np.dot(weights[0], buf_x) +
            np.dot(weights[1], buf_y) +
            np.dot(weights[2], buf_z)
        )

        # Error signal (Desired PPG - Motion Estimate)
        err = sig[i] - motion_est
        clean_ppg[i] = err

        # Normalization energy
        norm_factor = np.sum(buf_x**2) + np.sum(buf_y**2) + np.sum(buf_z**2) + 1e-6

        # Weight update
        step = (mu / norm_factor) * err
        weights[0] += step * buf_x
        weights[1] += step * buf_y
        weights[2] += step * buf_z

    clean_ppg[:filter_order] = sig[:filter_order]
    return clean_ppg


# ── Preprocessing pipeline — mirrors preprocess_camera_ppg exactly ─────────────

def preprocess_camera_ppg(raw_ppg, fps: float, lowcut: float = 0.9, highcut: float = 3.5):
    raw = np.asarray(raw_ppg, dtype=np.float64)
    if len(raw) == 0:
        return raw

    # Closed-form ordinary-least-squares linear detrend. Mathematically identical to
    # np.polyfit(idx, raw, 1) on well-formed signals, but polyfit routes through LAPACK
    # lstsq, which on a constant/saturated channel (real, common in the BUT PPG RGB data —
    # scripts/test_butppg_dataset.py) emits LAPACK errors and can return NaN or raise. This
    # form degrades gracefully: if the signal is constant (zero variance in idx is
    # impossible for len>=2, but the signal itself may be flat), slope is 0 and it just
    # subtracts the mean.
    n = len(raw)
    if n < 2:
        return raw - np.mean(raw)
    idx = np.arange(n, dtype=np.float64)
    mean_idx = idx.mean()
    mean_raw = raw.mean()
    d_idx = idx - mean_idx
    denom = np.sum(d_idx * d_idx)
    slope = np.sum(d_idx * (raw - mean_raw)) / denom if denom > 1e-12 else 0.0
    intercept = mean_raw - slope * mean_idx
    detrended = raw - (slope * idx + intercept)

    nyquist = 0.5 * fps
    low = lowcut / nyquist
    high = highcut / nyquist
    b, a = _butter_bandpass(3, low, high)
    filtered = _filtfilt(b, a, detrended)

    level = _adaptive_dwt_level(fps, highcut)
    denoised = _wavelet_denoise(filtered, level)

    smoothed = _savgol_11(denoised)

    std_val = np.std(smoothed)
    if std_val < 1e-9:
        return smoothed
    return (smoothed - np.mean(smoothed)) / std_val


# ── BPM extraction — real zero-padded FFT, same as ClassicalPPGExtractor ───────

def _parabolic_peak(sig: np.ndarray, idx: int) -> float:
    if idx <= 0 or idx >= len(sig) - 1:
        return float(idx)
    alpha, beta, gamma = sig[idx - 1], sig[idx], sig[idx + 1]
    denom = alpha - 2.0 * beta + gamma
    if abs(denom) < 1e-6:
        return float(idx)
    delta = (alpha - gamma) / (2.0 * denom)
    return idx + delta


def extract_channel_bpm(signal, fps: float, min_bpm: float = 54.0, max_bpm: float = 170.0) -> float:
    # Returns NaN ("no valid estimate") rather than any fabricated number when the signal
    # is too short or has no spectral energy in the cardiac band. NEVER invent a value: a
    # NaN propagates up and the caller (Kotlin runAnalysis) skips the tick, so the user is
    # only ever shown a real measurement, never a placeholder.
    sig_arr = np.asarray(signal, dtype=np.float64)
    n = len(sig_arr)
    if n < int(fps * 3.0):
        return float("nan")

    if not np.all(np.isfinite(sig_arr)):
        return float("nan")

    sig = sig_arr - np.mean(sig_arr)
    n_fft = n * 4
    fft_vals = np.abs(np.fft.rfft(sig, n=n_fft))
    freqs = np.fft.rfftfreq(n_fft, 1.0 / fps)

    min_freq = min_bpm / 60.0
    max_freq = max_bpm / 60.0
    mask = (freqs >= min_freq) & (freqs <= max_freq)
    if not np.any(mask):
        return float("nan")

    harmonic_fft = fft_vals.copy()
    idxs = np.where(mask)[0]
    for i in idxs:
        f = freqs[i]
        idx_2f = int(np.argmin(np.abs(freqs - 2.0 * f)))
        harmonic_fft[i] += 0.5 * fft_vals[idx_2f]

    valid_freqs = freqs[mask]
    valid_vals = harmonic_fft[mask]
    
    max_idx = int(np.argmax(valid_vals))
    f_cand = valid_freqs[max_idx]
    
    # Sub-harmonic / Super-harmonic disambiguation for physiological resting range (50-130 BPM)
    #
    # This 0.40 half-rate threshold is LOAD-BEARING — do not "tighten" it. Two attempts:
    #  1. Widening the BPM cutoffs 135/52 -> 120/65 (scripts/audit_diagnose_failures.py):
    #     MAE 3.92 -> 5.81 on BIDMC. Reverted.
    #  2. Raising the power threshold 0.40 -> 1.0 to stop it halving legitimate tachycardia
    #     (a clean 140/150/160 comes out 70/75/80; scripts/audit_tachycardia_disambig.py):
    #     fixed the synthetic case but REGRESSED BIDMC 3.92 -> 6.23, r 0.70 -> 0.34, because
    #     several real BIDMC subjects have a picked peak >135 where 0.40 correctly catches a
    #     genuine 2x-harmonic error. Reverted.
    # Net: the >135 branch legitimately fixes more real recordings than the synthetic
    # tachycardia it mishandles. True >135 BPM tachycardia being halved is a KNOWN, ACCEPTED
    # limitation (logged in audit.md), not fixable by tuning this constant — it needs a
    # genuinely better peak/harmonic discriminator, which the real data has repeatedly shown
    # is not a one-line change.
    if f_cand * 60.0 > 135.0:
        half_f = f_cand / 2.0
        if half_f >= min_freq:
            half_idx = int(np.argmin(np.abs(valid_freqs - half_f)))
            if valid_vals[half_idx] > 0.40 * valid_vals[max_idx]:
                f_cand = valid_freqs[half_idx]
    elif f_cand * 60.0 < 52.0:
        double_f = f_cand * 2.0
        if double_f <= max_freq:
            double_idx = int(np.argmin(np.abs(valid_freqs - double_f)))
            if valid_vals[double_idx] > 0.50 * valid_vals[max_idx]:
                f_cand = valid_freqs[double_idx]

    peak_pos = int(np.argmin(np.abs(valid_freqs - f_cand)))
    interp_idx = _parabolic_peak(valid_vals, peak_pos)
    best_freq = valid_freqs[0] + (interp_idx / (len(valid_freqs) - 1 + 1e-6)) * (valid_freqs[-1] - valid_freqs[0])
    return float(np.clip(best_freq * 60.0, min_bpm, max_bpm))


def extract_pca_bpm(green, red, fps: float, min_bpm: float = 54.0, max_bpm: float = 170.0) -> float:
    g = np.asarray(green, dtype=np.float64)
    r = np.asarray(red, dtype=np.float64)
    if len(g) != len(r) or len(g) == 0:
        return extract_channel_bpm(g, fps, min_bpm, max_bpm)

    # Guard against a degenerate covariance before eigendecomposition. On real camera data
    # a channel can be constant or clipped (e.g. a saturated channel), which makes np.cov
    # produce NaN/Inf and np.linalg.eigh emit LAPACK errors and return garbage. Found via
    # the real BUT PPG RGB dataset (scripts/test_butppg_dataset.py). In that case there's no
    # meaningful principal component to combine, so fall back to the green channel alone.
    if np.std(g) < 1e-9 or np.std(r) < 1e-9:
        return extract_channel_bpm(g if np.std(g) >= np.std(r) else r, fps, min_bpm, max_bpm)

    stacked = np.vstack([g, r])
    cov = np.cov(stacked)
    if not np.all(np.isfinite(cov)):
        return extract_channel_bpm(g, fps, min_bpm, max_bpm)
    eigvals, eigvecs = np.linalg.eigh(cov)
    w = eigvecs[:, int(np.argmax(eigvals))]
    pc1 = w[0] * g + w[1] * r
    return extract_channel_bpm(pc1, fps, min_bpm, max_bpm)


def _find_simple_peaks(x, prominence: float = 0.05):
    """Pure NumPy 1D local peak detector with prominence threshold for Chaquopy compatibility."""
    if len(x) < 3:
        return np.array([], dtype=int)
    dx = np.diff(x)
    is_max = (dx[:-1] > 0) & (dx[1:] <= 0)
    peak_idxs = np.where(is_max)[0] + 1
    if len(peak_idxs) == 0:
        return np.array([], dtype=int)
    min_val = np.min(x)
    valid_peaks = [i for i in peak_idxs if (x[i] - min_val) >= prominence]
    return np.array(valid_peaks, dtype=int)


def extract_acf_bpm(signal, fps: float, min_bpm: float = 54.0, max_bpm: float = 170.0) -> float:
    """
    Time-domain Autocorrelation (ACF) Heart Rate Estimator.
    Measures pulse lag directly in the time domain, providing harmonic-independent
    disambiguation to complement frequency-domain FFT peak picking.
    Pure NumPy implementation for zero-dependency Chaquopy deployment.
    """
    sig = np.asarray(signal, dtype=np.float64)
    n = len(sig)
    if n < int(fps * 3.0) or not np.all(np.isfinite(sig)):
        return float("nan")

    sig = sig - np.mean(sig)
    n_fft = 2 ** int(np.ceil(np.log2(2 * n)))
    fx = np.fft.rfft(sig, n=n_fft)
    acf = np.fft.irfft(fx * np.conj(fx))[:n]
    if acf[0] <= 1e-9:
        return float("nan")
    acf = acf / acf[0]

    min_lag = int(np.floor(fps * 60.0 / max_bpm))
    max_lag = int(np.ceil(fps * 60.0 / min_bpm))
    max_lag = min(max_lag, n - 1)

    if min_lag >= max_lag:
        return float("nan")

    search_acf = acf[min_lag : max_lag + 1]
    if len(search_acf) == 0:
        return float("nan")

    peaks = _find_simple_peaks(search_acf, prominence=0.05)
    if len(peaks) == 0:
        best_lag_idx = int(np.argmax(search_acf))
    else:
        best_lag_idx = int(peaks[np.argmax(search_acf[peaks])])

    best_lag = min_lag + best_lag_idx

    if 0 < best_lag < n - 1:
        y0, y1, y2 = acf[best_lag - 1], acf[best_lag], acf[best_lag + 1]
        denom = y0 - 2.0 * y1 + y2
        delta = (y0 - y2) / (2.0 * denom + 1e-9) if abs(denom) > 1e-6 else 0.0
        refined_lag = best_lag + delta
    else:
        refined_lag = float(best_lag)

    bpm = 60.0 * fps / (refined_lag + 1e-9)
    return float(np.clip(bpm, min_bpm, max_bpm))


def _compute_spectral_snr(sig, fps: float, bpm: float, min_bpm: float = 54.0, max_bpm: float = 170.0) -> float:
    """Computes spectral peak power ratio (SNR SQI) around candidate BPM."""
    if not np.isfinite(bpm) or len(sig) < int(fps * 3.0) or not np.all(np.isfinite(sig)):
        return 0.5
    s_detrend = sig - np.mean(sig)
    n_fft = 4096
    fft_vals = np.abs(np.fft.rfft(s_detrend, n=n_fft))
    freqs = np.fft.rfftfreq(n_fft, d=1.0 / fps)
    cand_hz = bpm / 60.0
    peak_mask = (freqs >= cand_hz - 0.15) & (freqs <= cand_hz + 0.15)
    band_mask = (freqs >= min_bpm / 60.0) & (freqs <= max_bpm / 60.0)
    peak_pwr = np.sum(fft_vals[peak_mask] ** 2)
    total_pwr = np.sum(fft_vals[band_mask] ** 2) + 1e-9
    return float(np.clip(peak_pwr / total_pwr, 0.0, 1.0))


def _compute_acf_prominence(sig, fps: float, bpm: float) -> float:
    """Computes autocorrelation lag peak prominence at candidate BPM."""
    if not np.isfinite(bpm) or len(sig) < int(fps * 3.0) or not np.all(np.isfinite(sig)):
        return 0.5
    lag = int(round(fps * 60.0 / bpm))
    n = len(sig)
    if lag <= 0 or lag >= n:
        return 0.5
    s_detrend = sig - np.mean(sig)
    n_fft = 2 ** int(np.ceil(np.log2(2 * n)))
    fx = np.fft.rfft(s_detrend, n=n_fft)
    acf = np.fft.irfft(fx * np.conj(fx))[:n]
    if acf[0] <= 1e-9:
        return 0.5
    acf = acf / acf[0]
    return float(np.clip(acf[lag], 0.0, 1.0))


def _compute_abs_skewness(sig) -> float:
    """Computes absolute skewness of window signal for motion artifact penalization."""
    if len(sig) < 10 or not np.all(np.isfinite(sig)):
        return 0.0
    std_s = np.std(sig) + 1e-9
    if std_s < 1e-8:
        return 0.0
    return float(np.abs(np.mean((sig - np.mean(sig)) ** 3) / (std_s ** 3)))


def extract_ensemble_bpm(green, red, fps: float, min_bpm: float = 54.0, max_bpm: float = 170.0) -> dict:
    bpm_green = extract_channel_bpm(green, fps, min_bpm, max_bpm)
    bpm_red = extract_channel_bpm(red, fps, min_bpm, max_bpm)
    bpm_pca = extract_pca_bpm(green, red, fps, min_bpm, max_bpm)

    valid_fft = [v for v in (bpm_green, bpm_red, bpm_pca) if np.isfinite(v)]
    if len(valid_fft) == 0:
        return {
            "consensus_bpm": float("nan"),
            "green_bpm": bpm_green,
            "red_bpm": bpm_red,
            "pca_bpm": bpm_pca,
            "acf_bpm": float("nan"),
            "confidence": 0.0,
        }

    b_fft = float(np.median(valid_fft))

    # Multi-Domain Fusion: Autocorrelation (ACF) lag peak
    b_acf_g = extract_acf_bpm(green, fps, min_bpm, max_bpm)
    b_acf_r = extract_acf_bpm(red, fps, min_bpm, max_bpm)
    valid_acf = [v for v in (b_acf_g, b_acf_r) if np.isfinite(v)]

    if len(valid_acf) > 0:
        b_acf = float(np.median(valid_acf))
        diff = abs(b_fft - b_acf)

        if diff <= 4.0:
            consensus = float(0.75 * b_fft + 0.25 * b_acf)
        elif abs(b_fft - 2.0 * b_acf) <= 6.0:
            # FFT locked to 2nd harmonic, ACF found true fundamental
            consensus = float(b_acf)
        elif abs(b_fft - 0.5 * b_acf) <= 6.0:
            # FFT locked to sub-harmonic, ACF found true fundamental
            consensus = float(b_acf)
        else:
            consensus = b_fft
    else:
        b_acf = float("nan")
        consensus = b_fft

    # Option 1: Learned Feature Quality Weighting Q_i
    snr_sqi = _compute_spectral_snr(green, fps, consensus, min_bpm, max_bpm)
    acf_prom = _compute_acf_prominence(green, fps, consensus)
    skew = _compute_abs_skewness(green)

    q_weight = (snr_sqi * 60.0) + (acf_prom * 40.0)
    if skew > 2.0:
        q_weight *= 0.5
    confidence = float(np.clip(q_weight, 20.0, 99.0))

    return {
        "consensus_bpm": consensus,
        "green_bpm": bpm_green,
        "red_bpm": bpm_red,
        "pca_bpm": bpm_pca,
        "acf_bpm": b_acf,
        "confidence": confidence,
    }


def _no_estimate() -> dict:
    """The one canonical 'I could not measure this' result. consensus_bpm is NaN so the
    caller skips it; nothing here is ever shown to a user as a real reading."""
    return {
        "consensus_bpm": float("nan"),
        "confidence": 0.0,
        "signal_quality_index": 0.0,
        "quality_flag": "RETRY",
        "num_windows": 0,
    }


def _single_window_result(clean_green, clean_red, fps) -> dict:
    """Early-tick path (buffer shorter than one full window): one ensemble estimate, or a
    no-estimate result if that came back NaN. Always returns the full dict shape."""
    res = extract_ensemble_bpm(clean_green, clean_red, fps)
    bpm = res["consensus_bpm"]
    if not np.isfinite(bpm):
        return _no_estimate()
    conf = res["confidence"]
    return {
        "consensus_bpm": bpm,
        "confidence": conf,
        "signal_quality_index": conf,  # no window spread yet; fall back to agreement confidence
        "quality_flag": "PASS" if conf >= 50.0 else "RETRY",
        "num_windows": 1,
    }


def analyze_session(
    green,
    red,
    fps: float,
    accel_x: np.ndarray = None,
    accel_y: np.ndarray = None,
    accel_z: np.ndarray = None,
    win_sec: float = 6.0,
    step_sec: float = 1.0
) -> dict:
    """
    Full-session sliding-window analysis:
    Applies Option 4 (IMU Accelerometer Motion Cancellation) if 3-axis IMU data is provided,
    splits the signal into sliding 6-second windows with 1-second step, extracts per-window
    ensemble BPMs with Option 1 (Quality Weighting) & Option 2 (Adaptive Respiration Filter),
    trims top/bottom 20% outliers, and returns a confidence-weighted consensus.
    """
    # Option 4: IMU Accelerometer Motion Cancellation Filter
    if accel_x is not None and accel_y is not None and accel_z is not None:
        green = cancel_imu_motion_artifacts(green, accel_x, accel_y, accel_z, fps)
        red = cancel_imu_motion_artifacts(red, accel_x, accel_y, accel_z, fps)

    clean_green = preprocess_camera_ppg(green, fps)
    clean_red = preprocess_camera_ppg(red, fps)

    # Option 2: Adaptive Lowcut Filtering for Respiration & Sub-harmonic Suppression.
    # ONLY applied when both FFT and ACF confidently confirm high resting HR (>100 BPM).
    # This prevents cutting off legitimate resting bradycardia/low HR (54-60 BPM).
    coarse_fft = extract_channel_bpm(clean_green, fps)
    coarse_acf = extract_acf_bpm(clean_green, fps)
    if np.isfinite(coarse_fft) and np.isfinite(coarse_acf):
        if coarse_fft > 100.0 and coarse_acf > 100.0 and abs(coarse_fft - coarse_acf) <= 6.0:
            adapt_lowcut = min(1.35, max(0.9, (coarse_fft * 0.55) / 60.0))
            clean_green = preprocess_camera_ppg(green, fps, lowcut=adapt_lowcut)
            clean_red = preprocess_camera_ppg(red, fps, lowcut=adapt_lowcut)

    n_samples = len(clean_green)
    win_len = int(fps * win_sec)
    step_len = max(1, int(fps * step_sec))

    if n_samples < win_len:
        return _single_window_result(clean_green, clean_red, fps)

    window_bpms = []
    window_confs = []
    for start in range(0, n_samples - win_len + 1, step_len):
        res = extract_ensemble_bpm(clean_green[start:start + win_len], clean_red[start:start + win_len], fps)
        bpm = res["consensus_bpm"]
        if np.isfinite(bpm):  # drop windows with no valid estimate; never fabricate one
            window_bpms.append(bpm)
            window_confs.append(res["confidence"])

    if len(window_bpms) == 0:
        return _no_estimate()

    bpms = np.array(window_bpms)
    confs = np.array(window_confs)

    k = len(bpms)
    trim_cnt = int(np.floor(k * 0.20))
    if trim_cnt > 0 and (k - 2 * trim_cnt) >= 3:
        sort_idxs = np.argsort(bpms)
        trimmed_idxs = sort_idxs[trim_cnt : k - trim_cnt]
        trimmed_bpms = bpms[trimmed_idxs]
        trimmed_confs = confs[trimmed_idxs]
    else:
        trimmed_bpms = bpms
        trimmed_confs = confs
        
    weights = trimmed_confs / (np.sum(trimmed_confs) + 1e-6)
    final_bpm = float(np.sum(trimmed_bpms * weights))
    avg_conf = float(np.mean(confs))

    # Final safety net: if aggregation somehow produced a non-finite value, return a
    # no-estimate result rather than let a fabricated/NaN number reach the UI.
    if not np.isfinite(final_bpm):
        return _no_estimate()

    bpm_std = float(np.std(trimmed_bpms))
    raw_bpm_std = float(np.std(bpms))
    raw_bpm_spread = float(np.max(bpms) - np.min(bpms))

    # Multiplier recalibrated from 5.0 -> 2.0 against real BIDMC data
    # (scripts/audit_sqi_recalibration.py): at 5.0, subjects with genuinely good real-world
    # accuracy (<1 BPM error) scored only 83-86%, which reads as "bad signal" to a user even
    # when the reading was fine. NOTE: rescaling the multiplier can't fix the underlying
    # correlation between bpm_std and real error (r stays ~-0.45 regardless — a property of
    # bpm_std itself, not this constant) but it does fix the miscalibrated absolute scale, so
    # a truly good reading no longer displays as mediocre.
    sqi = float(np.clip(100.0 - bpm_std * 2.0, 10.0, 99.0))

    # Quality gate: PASS requires high mean confidence, low trimmed bpm_std (<= 8.0),
    # AND zero harmonic split across windows (raw spread <= 25 BPM & raw std <= 10 BPM).
    # This catches catastrophic half-rate locks like bidmc52 where outlier trimming
    # hid the dissenting windows that jumped between 55 BPM and 112/130 BPM.
    is_stable = (bpm_std <= 8.0) and (raw_bpm_spread <= 25.0) and (raw_bpm_std <= 10.0)
    quality_flag = "PASS" if (avg_conf >= 50.0 and is_stable) else "RETRY"
    
    return {
        "consensus_bpm": final_bpm,
        "confidence": avg_conf,
        "signal_quality_index": sqi,
        "quality_flag": quality_flag,
        "num_windows": len(bpms)
    }


def analyze(green, red, fps: float) -> dict:
    """Single entry point the Kotlin bridge calls: full session sliding window analysis with 20% trimming."""
    return analyze_session(green, red, fps)
