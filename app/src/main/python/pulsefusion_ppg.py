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


# ── Preprocessing pipeline — mirrors preprocess_camera_ppg exactly ─────────────

def preprocess_camera_ppg(raw_ppg, fps: float, lowcut: float = 0.9, highcut: float = 3.5):
    raw = np.asarray(raw_ppg, dtype=np.float64)
    if len(raw) == 0:
        return raw

    idx = np.arange(len(raw))
    slope, intercept = np.polyfit(idx, raw, 1)
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
    sig_arr = np.asarray(signal, dtype=np.float64)
    n = len(sig_arr)
    if n < int(fps * 3.0):
        return 70.0

    sig = sig_arr - np.mean(sig_arr)
    n_fft = n * 4
    fft_vals = np.abs(np.fft.rfft(sig, n=n_fft))
    freqs = np.fft.rfftfreq(n_fft, 1.0 / fps)

    min_freq = min_bpm / 60.0
    max_freq = max_bpm / 60.0
    mask = (freqs >= min_freq) & (freqs <= max_freq)
    if not np.any(mask):
        return 70.0

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

    stacked = np.vstack([g, r])
    cov = np.cov(stacked)
    eigvals, eigvecs = np.linalg.eigh(cov)
    w = eigvecs[:, int(np.argmax(eigvals))]
    pc1 = w[0] * g + w[1] * r
    return extract_channel_bpm(pc1, fps, min_bpm, max_bpm)


def extract_ensemble_bpm(green, red, fps: float, min_bpm: float = 54.0, max_bpm: float = 170.0) -> dict:
    bpm_green = extract_channel_bpm(green, fps, min_bpm, max_bpm)
    bpm_red = extract_channel_bpm(red, fps, min_bpm, max_bpm)
    bpm_pca = extract_pca_bpm(green, red, fps, min_bpm, max_bpm)

    values = sorted([bpm_green, bpm_red, bpm_pca])
    consensus = values[1]
    spread = values[2] - values[0]
    confidence = float(np.clip(100.0 - spread * 3.0, 20.0, 99.0))

    return {
        "consensus_bpm": consensus,
        "green_bpm": bpm_green,
        "red_bpm": bpm_red,
        "pca_bpm": bpm_pca,
        "confidence": confidence,
    }


def analyze_session(green, red, fps: float, win_sec: float = 6.0, step_sec: float = 1.0) -> dict:
    """
    Full-session sliding-window analysis:
    Splits 60s PPG signals into sliding 6-second windows with 1-second step,
    extracts per-window ensemble BPMs, trims top/bottom 15% outlier estimates,
    and returns confidence-weighted session consensus.
    """
    clean_green = preprocess_camera_ppg(green, fps)
    clean_red = preprocess_camera_ppg(red, fps)
    
    n_samples = len(clean_green)
    win_len = int(fps * win_sec)
    step_len = int(fps * step_sec)
    
    if n_samples < win_len:
        return extract_ensemble_bpm(clean_green, clean_red, fps)
        
    window_bpms = []
    window_confs = []
    
    for start in range(0, n_samples - win_len + 1, step_len):
        g_win = clean_green[start : start + win_len]
        r_win = clean_red[start : start + win_len]
        res = extract_ensemble_bpm(g_win, r_win, fps)
        window_bpms.append(res["consensus_bpm"])
        window_confs.append(res["confidence"])
        
    if len(window_bpms) == 0:
        return extract_ensemble_bpm(clean_green, clean_red, fps)
        
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
    
    bpm_std = float(np.std(trimmed_bpms))
    sqi = float(np.clip(100.0 - bpm_std * 5.0, 10.0, 99.0))
    quality_flag = "PASS" if (avg_conf >= 50.0 and bpm_std <= 8.0) else "RETRY"
    
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
