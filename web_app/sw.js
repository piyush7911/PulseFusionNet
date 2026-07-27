// App-shell cache so the PWA launches (and re-measures) with no network — the DSP runs
// entirely client-side, so once cached this needs no connectivity at all.
const CACHE = 'pulsefusion-v5';
const ASSETS = [
  './', './index.html', './style.css', './manifest.webmanifest',
  './js/app.js', './js/camera.js', './js/detectors.js', './js/ppg.js', './js/fft.js',
  './icons/icon-192.png', './icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  event.respondWith(
    caches.match(event.request).then((cached) => cached || fetch(event.request).then((res) => {
      const copy = res.clone();
      caches.open(CACHE).then((c) => c.put(event.request, copy));
      return res;
    }).catch(() => cached))
  );
});
