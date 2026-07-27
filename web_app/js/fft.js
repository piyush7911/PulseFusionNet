// Minimal radix-2 Cooley-Tukey FFT, real-input helpers (rfft/irfft) mirroring the
// numpy.fft.rfft/irfft calls used by pulsefusion_ppg.py. No external DSP libs.
'use strict';

function nextPow2(n) {
  let p = 1;
  while (p < n) p *= 2;
  return p;
}

// In-place iterative FFT. re/im are Float64Array of length n (power of 2).
// invert=true computes the inverse transform (unnormalized; caller divides by n).
function fftInPlace(re, im, invert) {
  const n = re.length;
  for (let i = 1, j = 0; i < n; i++) {
    let bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j ^= bit;
    if (i < j) {
      [re[i], re[j]] = [re[j], re[i]];
      [im[i], im[j]] = [im[j], im[i]];
    }
  }
  for (let len = 2; len <= n; len <<= 1) {
    const ang = (2 * Math.PI / len) * (invert ? 1 : -1);
    const wr = Math.cos(ang), wi = Math.sin(ang);
    for (let i = 0; i < n; i += len) {
      let curWr = 1, curWi = 0;
      for (let j = 0; j < len / 2; j++) {
        const ur = re[i + j], ui = im[i + j];
        const vr = re[i + j + len / 2] * curWr - im[i + j + len / 2] * curWi;
        const vi = re[i + j + len / 2] * curWi + im[i + j + len / 2] * curWr;
        re[i + j] = ur + vr; im[i + j] = ui + vi;
        re[i + j + len / 2] = ur - vr; im[i + j + len / 2] = ui - vi;
        const nwr = curWr * wr - curWi * wi;
        const nwi = curWr * wi + curWi * wr;
        curWr = nwr; curWi = nwi;
      }
    }
  }
  if (invert) {
    for (let i = 0; i < n; i++) { re[i] /= n; im[i] /= n; }
  }
}

// rfft(x, nFft) -> {re, im} of length nFft/2+1, matching np.fft.rfft(x, n=nFft).
// nFft is rounded up to the next power of 2 (this port's one deviation from numpy:
// frequency-bin resolution differs slightly, magnitude/shape does not).
function rfft(x, nFft) {
  const n = nextPow2(nFft);
  const re = new Float64Array(n);
  const im = new Float64Array(n);
  re.set(x.subarray ? x.subarray(0, Math.min(x.length, n)) : x.slice(0, Math.min(x.length, n)));
  fftInPlace(re, im, false);
  const half = n / 2 + 1;
  return { re: re.slice(0, half), im: im.slice(0, half), n };
}

function rfftfreq(n, d) {
  const half = Math.floor(n / 2) + 1;
  const out = new Float64Array(half);
  for (let i = 0; i < half; i++) out[i] = i / (n * d);
  return out;
}

// irfft({re,im}, n) -> real array of length n, matching np.fft.irfft(spectrum, n=n)
// where spectrum has length n/2+1 (conjugate-symmetric reconstruction).
function irfft(spec, n) {
  const re = new Float64Array(n);
  const im = new Float64Array(n);
  const half = spec.re.length;
  for (let i = 0; i < half; i++) { re[i] = spec.re[i]; im[i] = spec.im[i]; }
  for (let i = 1; i < n - half + 1; i++) {
    re[n - i] = spec.re[i];
    im[n - i] = -spec.im[i];
  }
  fftInPlace(re, im, true);
  return re;
}

export { nextPow2, fftInPlace, rfft, rfftfreq, irfft };
