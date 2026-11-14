/* Live HLS playback (hls.js, or native HLS on Safari/iOS). */
const Live = (() => {
  const players = new Map();

  function attach(video) {
    const src = video.dataset.src;
    detach(video);
    if (window.Hls && Hls.isSupported()) {
      const hls = new Hls({ liveSyncDurationCount: 2, maxLiveSyncPlaybackRate: 1.5, lowLatencyMode: true });
      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) {
          // Camera offline or session expired: retry a bit later.
          detach(video);
          setTimeout(() => attach(video), 5000);
        }
      });
      hls.loadSource(src);
      hls.attachMedia(video);
      players.set(video, hls);
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = src;
      video.addEventListener('error', () => setTimeout(() => { video.src = src; }, 5000), { once: true });
    }
    video.play().catch(() => {});
  }

  function detach(video) {
    const hls = players.get(video);
    if (hls) {
      hls.destroy();
      players.delete(video);
    }
  }

  function pollStatus() {
    fetch('/api/status', { credentials: 'same-origin' })
      .then(r => (r.ok ? r.json() : {}))
      .then(statuses => {
        for (const [slug, online] of Object.entries(statuses)) {
          const dot = document.querySelector(`[data-status="${slug}"]`);
          if (dot) {
            dot.classList.toggle('on', online);
            dot.classList.toggle('off', !online);
          }
        }
      })
      .catch(() => {});
  }

  return {
    grid() {
      const videos = document.querySelectorAll('.tile video');
      // Only stream tiles that are on screen, to save bandwidth on phones.
      const io = new IntersectionObserver(entries => {
        for (const e of entries) {
          if (e.isIntersecting) attach(e.target); else detach(e.target);
        }
      });
      videos.forEach(v => io.observe(v));
      document.addEventListener('visibilitychange', () => {
        if (document.hidden) videos.forEach(detach);
        else videos.forEach(v => { io.unobserve(v); io.observe(v); });
      });
      setInterval(pollStatus, 30000);
    },
    single(video) {
      attach(video);
      video.addEventListener('click', () => video.requestFullscreen && video.requestFullscreen());
    }
  };
})();
