/* Playback of recordings through the MediaMTX playback server (/playback/get). */
(() => {
  const root = document.getElementById('playback');
  if (!root) return;

  const CLIP_SECONDS = 30 * 60; // play/download at most 30 minutes at a time
  const path = root.dataset.path;
  const base = root.dataset.base;
  const day = root.dataset.day; // YYYY-MM-DD in the camera time zone
  const zone = root.dataset.zone;
  const video = document.getElementById('pb');
  const label = document.getElementById('clipLabel');
  const download = document.getElementById('download');

  const blocks = [...root.querySelectorAll('.block')].map(el => ({
    el,
    start: new Date(el.dataset.start).getTime(),
    end: new Date(el.dataset.start).getTime() + Number(el.dataset.seconds) * 1000
  }));

  const fmt = new Intl.DateTimeFormat('en-GB', { timeZone: zone, hour: '2-digit', minute: '2-digit', second: '2-digit' });

  function clipUrl(startMs, seconds) {
    const q = new URLSearchParams({
      path,
      start: new Date(startMs).toISOString(),
      duration: String(seconds),
      format: 'fmp4'
    });
    return `${base}/get?${q}`;
  }

  function play(startMs) {
    const block = blocks.find(b => startMs >= b.start && startMs < b.end)
      || blocks.find(b => b.start > startMs);
    if (!block) {
      label.textContent = 'No recording at or after that time.';
      return;
    }
    const from = Math.max(startMs, block.start);
    const seconds = Math.max(1, Math.min(CLIP_SECONDS, Math.floor((block.end - from) / 1000)));
    const url = clipUrl(from, seconds);
    video.src = url;
    video.play().catch(() => {});
    download.href = url;
    download.download = `${path}_${new Date(from).toISOString().replace(/[:.]/g, '-')}.mp4`;
    download.hidden = false;
    label.textContent = `${fmt.format(from)} – ${fmt.format(from + seconds * 1000)}`;
    blocks.forEach(b => b.el.classList.toggle('active', b === block));
    // Continue with the next clip automatically.
    video.onended = () => play(from + seconds * 1000);
  }

  // Click on the bar: play from the clicked time.
  root.querySelector('.bar').addEventListener('click', e => {
    const bar = e.currentTarget.getBoundingClientRect();
    const dayStart = zonedToEpoch(day, '00:00');
    const dayEnd = zonedToEpoch(nextDay(day), '00:00');
    const ratio = Math.min(Math.max((e.clientX - bar.left) / bar.width, 0), 1);
    play(dayStart + ratio * (dayEnd - dayStart));
  });

  const jumpBtn = document.getElementById('jumpBtn');
  if (jumpBtn) {
    jumpBtn.addEventListener('click', () => {
      const t = document.getElementById('jumpTime').value;
      if (t) play(zonedToEpoch(day, t));
    });
  }

  /** Epoch millis of a wall-clock date + time in the camera time zone. */
  function zonedToEpoch(dateStr, timeStr) {
    const [y, m, d] = dateStr.split('-').map(Number);
    const [hh, mm] = timeStr.split(':').map(Number);
    const guess = Date.UTC(y, m - 1, d, hh, mm);
    // Two passes handle DST transitions.
    let ts = guess - offsetMs(guess);
    ts = guess - offsetMs(ts);
    return ts;
  }

  function offsetMs(ts) {
    const parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
      timeZone: zone, hourCycle: 'h23',
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit'
    }).formatToParts(new Date(ts)).map(p => [p.type, p.value]));
    const asUtc = Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute, +parts.second);
    return asUtc - Math.floor(ts / 1000) * 1000;
  }

  function nextDay(dateStr) {
    const [y, m, d] = dateStr.split('-').map(Number);
    return new Date(Date.UTC(y, m - 1, d + 1)).toISOString().slice(0, 10);
  }
})();
