// Direct port of app/src/main/python/pulsefusion_ppg.py (the numpy-only Android/Chaquopy
// port of pulsefusionnet's RealPhysiologicalPreprocessor + ClassicalPPGExtractor) to plain
// JS, so the browser build runs the identical algorithm entirely client-side. Function names
// and array-ordering conventions mirror the Python source 1:1 — see that file for the "why"
// behind each step; comments here only note JS-specific translation details.
import { rfft, rfftfreq, irfft, nextPow2 } from './fft.js';

const SYM4_DEC_LO = [
  -0.07576571478927333, -0.02963552764599851, 0.49761866763201545, 0.8037387518059161,
  0.29785779560527736, -0.09921954357684722, -0.012603967262037833, 0.03222310060404270,
];
const SYM4_DEC_HI = SYM4_DEC_LO.map((_, i) => Math.pow(-1, i) * SYM4_DEC_LO[7 - i]);
const SYM4_REC_LO = [...SYM4_DEC_LO].reverse();
const SYM4_REC_HI = [...SYM4_DEC_HI].reverse();
const SYM4_LEN = 8;
const SG11 = [-36, 9, 44, 69, 84, 89, 84, 69, 44, 9, -36];
const SG11_NORM = 429.0;

// ---- tiny complex-number helpers (order-3 Butterworth design only) ----
const cAdd = (a, b) => [a[0] + b[0], a[1] + b[1]];
const cSub = (a, b) => [a[0] - b[0], a[1] - b[1]];
const cMul = (a, b) => [a[0] * b[0] - a[1] * b[1], a[0] * b[1] + a[1] * b[0]];
const cDiv = (a, b) => {
  const d = b[0] * b[0] + b[1] * b[1];
  return [(a[0] * b[0] + a[1] * b[1]) / d, (a[1] * b[0] - a[0] * b[1]) / d];
};
const cScale = (a, s) => [a[0] * s, a[1] * s];
function cSqrt(a) {
  const r = Math.hypot(a[0], a[1]);
  const re = Math.sqrt((r + a[0]) / 2);
  let im = Math.sqrt((r - a[0]) / 2);
  if (a[1] < 0) im = -im;
  return [re, im];
}
function polyFromRoots(roots) {
  let coeffs = [[1, 0]];
  for (const r of roots) {
    const next = new Array(coeffs.length + 1).fill(0).map(() => [0, 0]);
    for (let i = 0; i < coeffs.length; i++) {
      next[i] = cAdd(next[i], coeffs[i]);
      next[i + 1] = cSub(next[i + 1], cMul(r, coeffs[i]));
    }
    coeffs = next;
  }
  return coeffs;
}

function butterBandpass(order, low, high) {
  const lo = Math.max(0.001, Math.min(low, 0.95));
  const hi = Math.max(lo + 0.01, Math.min(high, 0.99));

  const protoPoles = [];
  for (let k = 0; k < order; k++) {
    const ang = (Math.PI * (2 * k + order + 1)) / (2 * order);
    protoPoles.push([Math.cos(ang), Math.sin(ang)]);
  }

  const fs = 2.0;
  const warpedLow = 2 * fs * Math.tan((Math.PI * lo) / fs);
  const warpedHigh = 2 * fs * Math.tan((Math.PI * hi) / fs);
  const bw = warpedHigh - warpedLow;
  const w0 = Math.sqrt(warpedLow * warpedHigh);

  const pScaled = protoPoles.map((p) => cScale(p, bw / 2));
  const disc = pScaled.map((p) => cSqrt(cSub(cMul(p, p), [w0 * w0, 0])));
  const bpPoles = pScaled.map((p, i) => cAdd(p, disc[i])).concat(pScaled.map((p, i) => cSub(p, disc[i])));
  const bpZeros = new Array(order).fill(0).map(() => [0, 0]);
  const kBp = Math.pow(bw, order);

  const fs2 = 2 * fs;
  const zDigital = bpZeros.map((z) => cDiv(cAdd([fs2, 0], z), cSub([fs2, 0], z)));
  const pDigital = bpPoles.map((p) => cDiv(cAdd([fs2, 0], p), cSub([fs2, 0], p)));
  const degree = pDigital.length - zDigital.length;
  for (let i = 0; i < degree; i++) zDigital.push([-1, 0]);

  let gainNum = [1, 0], gainDen = [1, 0];
  for (const z of bpZeros) gainNum = cMul(gainNum, cSub([fs2, 0], z));
  for (const p of bpPoles) gainDen = cMul(gainDen, cSub([fs2, 0], p));
  const gain = cDiv(gainNum, gainDen);
  const kDigital = cMul([kBp, 0], gain)[0];

  const bC = polyFromRoots(zDigital).map((c) => cScale(c, kDigital));
  const aC = polyFromRoots(pDigital);
  const b = bC.map((c) => c[0]);
  const a = aC.map((c) => c[0]);
  const a0 = a[0];
  return [b.map((v) => v / a0), a.map((v) => v / a0)];
}

function lfilter(b, a, x) {
  const n = Math.max(a.length, b.length);
  const bp = new Float64Array(n); bp.set(b);
  const ap = new Float64Array(n); ap.set(a);
  const z = new Float64Array(Math.max(n - 1, 0));
  const y = new Float64Array(x.length);
  for (let i = 0; i < x.length; i++) {
    const xi = x[i];
    const yi = bp[0] * xi + (n > 1 ? z[0] : 0.0);
    y[i] = yi;
    for (let j = 0; j < n - 2; j++) {
      z[j] = bp[j + 1] * xi + z[j + 1] - ap[j + 1] * yi;
    }
    if (n > 1) z[n - 2] = bp[n - 1] * xi - ap[n - 1] * yi;
  }
  return y;
}

function filtfilt(b, a, x) {
  const n = x.length;
  let padLen = Math.min(3 * Math.max(a.length, b.length), n - 1);
  padLen = Math.max(padLen, 0);
  if (padLen === 0 || n <= padLen) {
    const y = lfilter(b, a, x);
    return lfilter(b, a, y.slice().reverse()).reverse();
  }
  const left = new Float64Array(padLen);
  for (let i = 0; i < padLen; i++) left[i] = 2 * x[0] - x[padLen - i];
  const right = new Float64Array(padLen);
  for (let i = 0; i < padLen; i++) right[i] = 2 * x[n - 1] - x[n - 2 - i];
  const padded = new Float64Array(padLen + n + padLen);
  padded.set(left, 0); padded.set(x, padLen); padded.set(right, padLen + n);

  const forward = lfilter(b, a, padded);
  const backward = lfilter(b, a, forward.slice().reverse()).reverse();
  return backward.slice(padLen, padLen + n);
}

// ---- sym4 DWT / IDWT ----
function reflectIndex(idx, n) {
  if (n === 1) return 0;
  const period = 2 * n;
  let i = idx % period;
  if (i < 0) i += period;
  return i < n ? i : period - 1 - i;
}

function dwtLevel(x) {
  const flen = SYM4_LEN;
  const n = x.length;
  const padded = new Float64Array(n + 2 * (flen - 1));
  for (let i = 0; i < padded.length; i++) padded[i] = x[reflectIndex(i - (flen - 1), n)];
  const outLen = Math.floor((n + flen - 1) / 2);
  const cA = new Float64Array(outLen);
  const cD = new Float64Array(outLen);
  for (let k = 0; k < outLen; k++) {
    let a = 0, d = 0;
    for (let t = 0; t < flen; t++) {
      const idx = 2 * k + t;
      const v = idx < padded.length ? padded[idx] : 0;
      a += SYM4_DEC_LO[flen - 1 - t] * v;
      d += SYM4_DEC_HI[flen - 1 - t] * v;
    }
    cA[k] = a; cD[k] = d;
  }
  return [cA, cD];
}

function convolveFull(x, h) {
  const out = new Float64Array(x.length + h.length - 1);
  for (let i = 0; i < x.length; i++) {
    if (x[i] === 0) continue;
    for (let j = 0; j < h.length; j++) out[i + j] += x[i] * h[j];
  }
  return out;
}

function idwtLevel(cA, cD, outLen) {
  const n = cA.length;
  const upA = new Float64Array(2 * n); for (let i = 0; i < n; i++) upA[2 * i] = cA[i];
  const upD = new Float64Array(2 * n); for (let i = 0; i < n; i++) upD[2 * i] = cD[i];
  const convA = convolveFull(upA, SYM4_REC_LO);
  const convD = convolveFull(upD, SYM4_REC_HI);
  const convLen = convA.length;
  const conv = new Float64Array(convLen);
  for (let i = 0; i < convLen; i++) conv[i] = convA[i] + convD[i];
  const start = Math.max(0, Math.floor((convLen - outLen) / 2));
  const end = Math.min(convLen, start + outLen);
  const result = new Float64Array(outLen);
  for (let i = start; i < end; i++) result[i - start] = conv[i];
  return result;
}

function wavedec(x, level) {
  let current = x;
  const details = [];
  const lengths = [];
  for (let l = 0; l < level; l++) {
    if (current.length < SYM4_LEN) break;
    lengths.push(current.length);
    const [cA, cD] = dwtLevel(current);
    details.push(cD);
    current = cA;
  }
  return [current, details.reverse(), lengths.reverse()];
}

function waverec(approx, details, lengths) {
  let current = approx;
  for (let i = 0; i < details.length; i++) current = idwtLevel(current, details[i], lengths[i]);
  return current;
}

function adaptiveDwtLevel(fps, highcut) {
  let level = 1;
  const nyq = fps / 2.0;
  while (nyq / Math.pow(2, level + 1) > highcut) level++;
  return Math.max(1, Math.min(level, 4));
}

function median(arr) {
  const s = Array.from(arr).sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
}

function waveletDenoise(signal, level) {
  if (signal.length < 64) return signal;
  try {
    const [approx, details, lengths] = wavedec(signal, level);
    if (details.length === 0) return signal;
    const finest = details[details.length - 1];
    const medFinest = median(finest);
    const mad = median(finest.map((v) => Math.abs(v - medFinest)));
    const sigma = (1.0 / 0.6745) * mad;
    const threshold = sigma * Math.sqrt(2.0 * Math.log(signal.length));
    const denoisedDetails = details.map((d) => d.map((v) => Math.sign(v) * Math.max(Math.abs(v) - threshold, 0.0)));
    const out = waverec(approx, denoisedDetails, lengths);
    return out.slice(0, signal.length);
  } catch (e) {
    return signal;
  }
}

function savgol11(signal) {
  if (signal.length < 11) return signal;
  const n = signal.length;
  const padded = new Float64Array(n + 10);
  for (let i = 0; i < 5; i++) padded[i] = signal[0];
  for (let i = 0; i < n; i++) padded[5 + i] = signal[i];
  for (let i = 0; i < 5; i++) padded[5 + n + i] = signal[n - 1];
  const out = new Float64Array(n);
  for (let i = 0; i < n; i++) {
    let acc = 0;
    for (let k = 0; k < 11; k++) acc += SG11[k] * padded[i + k];
    out[i] = acc / SG11_NORM;
  }
  return out;
}

function mean(arr) {
  let s = 0; for (let i = 0; i < arr.length; i++) s += arr[i];
  return s / arr.length;
}
function std(arr, m) {
  const mu = m === undefined ? mean(arr) : m;
  let s = 0; for (let i = 0; i < arr.length; i++) s += (arr[i] - mu) * (arr[i] - mu);
  return Math.sqrt(s / arr.length);
}
function allFinite(arr) {
  for (let i = 0; i < arr.length; i++) if (!Number.isFinite(arr[i])) return false;
  return true;
}

export function preprocessCameraPpg(rawPpg, fps, lowcut = 0.9, highcut = 3.5) {
  const raw = Float64Array.from(rawPpg);
  const n = raw.length;
  if (n === 0) return raw;
  if (n < 2) return raw.map((v) => v - mean(raw));

  const idx = Float64Array.from({ length: n }, (_, i) => i);
  const meanIdx = mean(idx);
  const meanRaw = mean(raw);
  let denom = 0, num = 0;
  for (let i = 0; i < n; i++) {
    const d = idx[i] - meanIdx;
    denom += d * d;
    num += d * (raw[i] - meanRaw);
  }
  const slope = denom > 1e-12 ? num / denom : 0.0;
  const intercept = meanRaw - slope * meanIdx;
  const detrended = raw.map((v, i) => v - (slope * idx[i] + intercept));

  const nyquist = 0.5 * fps;
  const low = lowcut / nyquist;
  const high = highcut / nyquist;
  const [b, a] = butterBandpass(3, low, high);
  const filtered = filtfilt(b, a, detrended);

  const level = adaptiveDwtLevel(fps, highcut);
  const denoised = waveletDenoise(filtered, level);
  const smoothed = savgol11(denoised);

  const stdVal = std(smoothed);
  if (stdVal < 1e-9) return smoothed;
  const m = mean(smoothed);
  return smoothed.map((v) => (v - m) / stdVal);
}

// ---- BPM extraction ----
function parabolicPeak(sig, idx) {
  if (idx <= 0 || idx >= sig.length - 1) return idx;
  const alpha = sig[idx - 1], beta = sig[idx], gamma = sig[idx + 1];
  const denom = alpha - 2 * beta + gamma;
  if (Math.abs(denom) < 1e-6) return idx;
  const delta = (alpha - gamma) / (2 * denom);
  return idx + delta;
}

export function extractChannelBpm(signal, fps, minBpm = 54.0, maxBpm = 170.0) {
  const sigArr = Float64Array.from(signal);
  const n = sigArr.length;
  if (n < Math.floor(fps * 3.0)) return NaN;
  if (!allFinite(sigArr)) return NaN;

  const m = mean(sigArr);
  const sig = sigArr.map((v) => v - m);
  const nFft = n * 4;
  const spec = rfft(sig, nFft);
  const fftVals = new Float64Array(spec.re.length);
  for (let i = 0; i < spec.re.length; i++) fftVals[i] = Math.hypot(spec.re[i], spec.im[i]);
  const freqs = rfftfreq(spec.n, 1.0 / fps);

  const minFreq = minBpm / 60.0;
  const maxFreq = maxBpm / 60.0;
  const idxs = [];
  for (let i = 0; i < freqs.length; i++) if (freqs[i] >= minFreq && freqs[i] <= maxFreq) idxs.push(i);
  if (idxs.length === 0) return NaN;

  const harmonicFft = fftVals.slice();
  for (const i of idxs) {
    const f = freqs[i];
    let best = 0, bestDiff = Infinity;
    for (let j = 0; j < freqs.length; j++) {
      const d = Math.abs(freqs[j] - 2.0 * f);
      if (d < bestDiff) { bestDiff = d; best = j; }
    }
    harmonicFft[i] += 0.5 * fftVals[best];
  }

  const validFreqs = idxs.map((i) => freqs[i]);
  const validVals = idxs.map((i) => harmonicFft[i]);

  let maxIdx = 0;
  for (let i = 1; i < validVals.length; i++) if (validVals[i] > validVals[maxIdx]) maxIdx = i;
  let fCand = validFreqs[maxIdx];

  const nearestIdx = (target) => {
    let best = 0, bestDiff = Infinity;
    for (let i = 0; i < validFreqs.length; i++) {
      const d = Math.abs(validFreqs[i] - target);
      if (d < bestDiff) { bestDiff = d; best = i; }
    }
    return best;
  };

  if (fCand * 60.0 > 135.0) {
    const halfF = fCand / 2.0;
    if (halfF >= minFreq) {
      const halfIdx = nearestIdx(halfF);
      if (validVals[halfIdx] > 0.40 * validVals[maxIdx]) fCand = validFreqs[halfIdx];
    }
  } else if (fCand * 60.0 < 52.0) {
    const doubleF = fCand * 2.0;
    if (doubleF <= maxFreq) {
      const doubleIdx = nearestIdx(doubleF);
      if (validVals[doubleIdx] > 0.50 * validVals[maxIdx]) fCand = validFreqs[doubleIdx];
    }
  }

  const peakPos = nearestIdx(fCand);
  const interpIdx = parabolicPeak(validVals, peakPos);
  const bestFreq = validFreqs[0] + (interpIdx / (validFreqs.length - 1 + 1e-6)) * (validFreqs[validFreqs.length - 1] - validFreqs[0]);
  return Math.min(Math.max(bestFreq * 60.0, minBpm), maxBpm);
}

function eigh2x2(a, b, d) {
  // symmetric [[a,b],[b,d]] -> {val: larger eigenvalue, vec: [v0,v1] normalized}
  const tr = a + d, diff = (a - d) / 2;
  const disc = Math.sqrt(diff * diff + b * b);
  const lambda = tr / 2 + disc;
  let v0, v1;
  if (Math.abs(b) > 1e-12) {
    v0 = b; v1 = lambda - a;
  } else {
    if (a >= d) { v0 = 1; v1 = 0; } else { v0 = 0; v1 = 1; }
  }
  const norm = Math.hypot(v0, v1) || 1;
  return { val: lambda, vec: [v0 / norm, v1 / norm] };
}

export function extractPcaBpm(green, red, fps, minBpm = 54.0, maxBpm = 170.0) {
  const g = Float64Array.from(green), r = Float64Array.from(red);
  if (g.length !== r.length || g.length === 0) return extractChannelBpm(g, fps, minBpm, maxBpm);

  const stdG = std(g), stdR = std(r);
  if (stdG < 1e-9 || stdR < 1e-9) {
    return extractChannelBpm(stdG >= stdR ? g : r, fps, minBpm, maxBpm);
  }

  const mg = mean(g), mr = mean(r);
  let varG = 0, varR = 0, covGR = 0;
  const n = g.length;
  for (let i = 0; i < n; i++) {
    varG += (g[i] - mg) * (g[i] - mg);
    varR += (r[i] - mr) * (r[i] - mr);
    covGR += (g[i] - mg) * (r[i] - mr);
  }
  const ddof = n - 1 > 0 ? n - 1 : 1;
  varG /= ddof; varR /= ddof; covGR /= ddof;
  if (![varG, varR, covGR].every(Number.isFinite)) return extractChannelBpm(g, fps, minBpm, maxBpm);

  const { vec } = eigh2x2(varG, covGR, varR);
  const pc1 = new Float64Array(n);
  for (let i = 0; i < n; i++) pc1[i] = vec[0] * g[i] + vec[1] * r[i];
  return extractChannelBpm(pc1, fps, minBpm, maxBpm);
}

function findSimplePeaks(x, prominence = 0.05) {
  if (x.length < 3) return [];
  const dx = new Float64Array(x.length - 1);
  for (let i = 0; i < dx.length; i++) dx[i] = x[i + 1] - x[i];
  const peakIdxs = [];
  for (let i = 0; i < dx.length - 1; i++) {
    if (dx[i] > 0 && dx[i + 1] <= 0) peakIdxs.push(i + 1);
  }
  if (peakIdxs.length === 0) return [];
  const minVal = Math.min(...x);
  return peakIdxs.filter((i) => x[i] - minVal >= prominence);
}

export function extractAcfBpm(signal, fps, minBpm = 54.0, maxBpm = 170.0) {
  const sigArr = Float64Array.from(signal);
  const n = sigArr.length;
  if (n < Math.floor(fps * 3.0) || !allFinite(sigArr)) return NaN;

  const m = mean(sigArr);
  const sig = sigArr.map((v) => v - m);
  const nFft = nextPow2(2 * n);
  const spec = rfft(sig, nFft);
  const power = { re: new Float64Array(spec.re.length), im: new Float64Array(spec.re.length) };
  for (let i = 0; i < spec.re.length; i++) {
    // fx * conj(fx) => |fx|^2, purely real
    power.re[i] = spec.re[i] * spec.re[i] + spec.im[i] * spec.im[i];
    power.im[i] = 0;
  }
  const acfFull = irfft(power, nFft);
  const acf = acfFull.slice(0, n);
  if (acf[0] <= 1e-9) return NaN;
  for (let i = 0; i < acf.length; i++) acf[i] /= acf[0];

  const minLag = Math.floor((fps * 60.0) / maxBpm);
  let maxLag = Math.ceil((fps * 60.0) / minBpm);
  maxLag = Math.min(maxLag, n - 1);
  if (minLag >= maxLag) return NaN;

  const searchAcf = acf.slice(minLag, maxLag + 1);
  if (searchAcf.length === 0) return NaN;

  const peaks = findSimplePeaks(searchAcf, 0.05);
  let bestLagIdx;
  if (peaks.length === 0) {
    bestLagIdx = 0;
    for (let i = 1; i < searchAcf.length; i++) if (searchAcf[i] > searchAcf[bestLagIdx]) bestLagIdx = i;
  } else {
    bestLagIdx = peaks[0];
    for (const p of peaks) if (searchAcf[p] > searchAcf[bestLagIdx]) bestLagIdx = p;
  }
  const bestLag = minLag + bestLagIdx;

  let refinedLag;
  if (bestLag > 0 && bestLag < n - 1) {
    const y0 = acf[bestLag - 1], y1 = acf[bestLag], y2 = acf[bestLag + 1];
    const denom = y0 - 2 * y1 + y2;
    const delta = Math.abs(denom) > 1e-6 ? (y0 - y2) / (2 * denom + 1e-9) : 0.0;
    refinedLag = bestLag + delta;
  } else {
    refinedLag = bestLag;
  }

  const bpm = (60.0 * fps) / (refinedLag + 1e-9);
  return Math.min(Math.max(bpm, minBpm), maxBpm);
}

function computeSpectralSnr(sig, fps, bpm, minBpm = 54.0, maxBpm = 170.0) {
  if (!Number.isFinite(bpm) || sig.length < Math.floor(fps * 3.0) || !allFinite(sig)) return 0.5;
  const m = mean(sig);
  const sDetrend = sig.map((v) => v - m);
  const nFft = 4096;
  const spec = rfft(sDetrend, nFft);
  const fftVals = new Float64Array(spec.re.length);
  for (let i = 0; i < spec.re.length; i++) fftVals[i] = Math.hypot(spec.re[i], spec.im[i]);
  const freqs = rfftfreq(spec.n, 1.0 / fps);
  const candHz = bpm / 60.0, h2Hz = 2.0 * candHz;

  let peakPwr = 0, h2Pwr = 0, totalPwr = 0;
  for (let i = 0; i < freqs.length; i++) {
    const f = freqs[i], p = fftVals[i] * fftVals[i];
    if (f >= candHz - 0.15 && f <= candHz + 0.15) peakPwr += p;
    if (f >= h2Hz - 0.20 && f <= h2Hz + 0.20) h2Pwr += p;
    if (f >= minBpm / 60.0 && f <= maxBpm / 60.0) totalPwr += p;
  }
  const total = totalPwr + 1e-9;
  const ratio = ((peakPwr + 0.6 * h2Pwr) / total) * 1.8;
  return Math.min(Math.max(ratio, 0.0), 1.0);
}

function computeAcfProminence(sig, fps, bpm) {
  if (!Number.isFinite(bpm) || sig.length < Math.floor(fps * 3.0) || !allFinite(sig)) return 0.5;
  const lag = Math.round((fps * 60.0) / bpm);
  const n = sig.length;
  if (lag <= 0 || lag >= n) return 0.5;
  const m = mean(sig);
  const sDetrend = sig.map((v) => v - m);
  const nFft = nextPow2(2 * n);
  const spec = rfft(sDetrend, nFft);
  const power = { re: new Float64Array(spec.re.length), im: new Float64Array(spec.re.length) };
  for (let i = 0; i < spec.re.length; i++) power.re[i] = spec.re[i] * spec.re[i] + spec.im[i] * spec.im[i];
  const acfFull = irfft(power, nFft);
  const acf = acfFull.slice(0, n);
  if (acf[0] <= 1e-9) return 0.5;
  return Math.min(Math.max(acf[lag] / acf[0], 0.0), 1.0);
}

function computeAbsSkewness(sig) {
  if (sig.length < 10 || !allFinite(sig)) return 0.0;
  const m = mean(sig);
  const stdS = std(sig, m) + 1e-9;
  if (stdS < 1e-8) return 0.0;
  let acc = 0;
  for (let i = 0; i < sig.length; i++) acc += Math.pow(sig[i] - m, 3);
  acc /= sig.length;
  return Math.abs(acc / Math.pow(stdS, 3));
}

export function extractEnsembleBpm(green, red, fps, minBpm = 54.0, maxBpm = 170.0) {
  const bpmGreen = extractChannelBpm(green, fps, minBpm, maxBpm);
  const bpmRed = extractChannelBpm(red, fps, minBpm, maxBpm);
  const bpmPca = extractPcaBpm(green, red, fps, minBpm, maxBpm);

  const validFft = [bpmGreen, bpmRed, bpmPca].filter(Number.isFinite);
  if (validFft.length === 0) {
    return { consensusBpm: NaN, greenBpm: bpmGreen, redBpm: bpmRed, pcaBpm: bpmPca, acfBpm: NaN, confidence: 0.0 };
  }
  const bFft = median(validFft);

  const bAcfG = extractAcfBpm(green, fps, minBpm, maxBpm);
  const bAcfR = extractAcfBpm(red, fps, minBpm, maxBpm);
  const validAcf = [bAcfG, bAcfR].filter(Number.isFinite);

  let bAcf = NaN, consensus;
  if (validAcf.length > 0) {
    bAcf = median(validAcf);
    const diff = Math.abs(bFft - bAcf);
    if (diff <= 4.0) consensus = 0.75 * bFft + 0.25 * bAcf;
    else if (Math.abs(bFft - 2.0 * bAcf) <= 6.0) consensus = bAcf;
    else if (Math.abs(bFft - 0.5 * bAcf) <= 6.0) consensus = bAcf;
    else consensus = bFft;
  } else {
    consensus = bFft;
  }

  const snrSqi = computeSpectralSnr(green, fps, consensus, minBpm, maxBpm);
  const acfProm = computeAcfProminence(green, fps, consensus);
  const skew = computeAbsSkewness(green);

  const qBase = snrSqi * 50.0 + acfProm * 50.0;

  let agreementBoost = 1.0;
  if (Number.isFinite(bAcf) && Math.abs(bFft - bAcf) <= 3.0) {
    agreementBoost = 1.25;
    if (Number.isFinite(bpmPca) && Math.abs(bFft - bpmPca) <= 3.0) agreementBoost = 1.35;
  } else if (Number.isFinite(bAcf) && Math.abs(bFft - bAcf) <= 5.0) {
    agreementBoost = 1.15;
  }

  let qWeight = qBase * agreementBoost;
  if (skew > 2.0) qWeight *= 0.5;

  const confidence = Math.min(Math.max(qWeight, 25.0), 99.0);

  return { consensusBpm: consensus, greenBpm: bpmGreen, redBpm: bpmRed, pcaBpm: bpmPca, acfBpm: bAcf, confidence };
}

function noEstimate() {
  return { consensusBpm: NaN, confidence: 0.0, signalQualityIndex: 0.0, qualityFlag: 'RETRY', numWindows: 0 };
}

function singleWindowResult(cleanGreen, cleanRed, fps) {
  const res = extractEnsembleBpm(cleanGreen, cleanRed, fps);
  if (!Number.isFinite(res.consensusBpm)) return noEstimate();
  const conf = res.confidence;
  return {
    consensusBpm: res.consensusBpm,
    confidence: conf,
    signalQualityIndex: conf,
    qualityFlag: conf >= 50.0 ? 'PASS' : 'RETRY',
    numWindows: 1,
  };
}

export function analyzeSession(green, red, fps, winSec = 6.0, stepSec = 1.0) {
  let cleanGreen = preprocessCameraPpg(green, fps);
  let cleanRed = preprocessCameraPpg(red, fps);

  const coarseFft = extractChannelBpm(cleanGreen, fps);
  const coarseAcf = extractAcfBpm(cleanGreen, fps);
  if (Number.isFinite(coarseFft) && Number.isFinite(coarseAcf)) {
    if (coarseFft > 100.0 && coarseAcf > 100.0 && Math.abs(coarseFft - coarseAcf) <= 6.0) {
      const adaptLowcut = Math.min(1.35, Math.max(0.9, (coarseFft * 0.55) / 60.0));
      cleanGreen = preprocessCameraPpg(green, fps, adaptLowcut);
      cleanRed = preprocessCameraPpg(red, fps, adaptLowcut);
    }
  }

  const nSamples = cleanGreen.length;
  const winLen = Math.floor(fps * winSec);
  const stepLen = Math.max(1, Math.floor(fps * stepSec));

  if (nSamples < winLen) return singleWindowResult(cleanGreen, cleanRed, fps);

  const windowBpms = [];
  const windowConfs = [];
  for (let start = 0; start + winLen <= nSamples; start += stepLen) {
    const res = extractEnsembleBpm(cleanGreen.slice(start, start + winLen), cleanRed.slice(start, start + winLen), fps);
    if (Number.isFinite(res.consensusBpm)) {
      windowBpms.push(res.consensusBpm);
      windowConfs.push(res.confidence);
    }
  }
  if (windowBpms.length === 0) return noEstimate();

  const k = windowBpms.length;
  const trimCnt = Math.floor(k * 0.20);
  let trimmedBpms, trimmedConfs;
  if (trimCnt > 0 && k - 2 * trimCnt >= 3) {
    const order = windowBpms.map((_, i) => i).sort((i, j) => windowBpms[i] - windowBpms[j]);
    const trimmedIdxs = order.slice(trimCnt, k - trimCnt);
    trimmedBpms = trimmedIdxs.map((i) => windowBpms[i]);
    trimmedConfs = trimmedIdxs.map((i) => windowConfs[i]);
  } else {
    trimmedBpms = windowBpms;
    trimmedConfs = windowConfs;
  }

  const confSum = trimmedConfs.reduce((s, v) => s + v, 0) + 1e-6;
  const weights = trimmedConfs.map((c) => c / confSum);
  const finalBpm = trimmedBpms.reduce((s, v, i) => s + v * weights[i], 0);
  const avgConf = mean(windowConfs);

  if (!Number.isFinite(finalBpm)) return noEstimate();

  const bpmStd = std(trimmedBpms);
  const rawBpmStd = std(windowBpms);
  const rawBpmSpread = Math.max(...windowBpms) - Math.min(...windowBpms);

  const sqi = Math.min(Math.max(100.0 - bpmStd * 2.0, 10.0), 99.0);
  const isStable = bpmStd <= 8.0 && rawBpmSpread <= 25.0 && rawBpmStd <= 10.0;
  const qualityFlag = avgConf >= 50.0 && isStable ? 'PASS' : 'RETRY';

  return { consensusBpm: finalBpm, confidence: avgConf, signalQualityIndex: sqi, qualityFlag, numWindows: windowBpms.length };
}

export function analyze(green, red, fps) {
  return analyzeSession(green, red, fps);
}
