(() => {
  'use strict';

  const token = new URLSearchParams(location.search).get('token') || '';
  const statusEl = document.getElementById('status');
  const statusText = document.getElementById('status-text');
  const transcriptEl = document.getElementById('transcript');
  const micBtn = document.getElementById('mic');
  const textInput = document.getElementById('text-input');
  const sendBtn = document.getElementById('send');

  let ws = null;
  let reconnectDelay = 1000;

  /* ---------- WebSocket ---------- */

  function setStatus(connected, label) {
    statusEl.classList.toggle('status--on', connected);
    statusEl.classList.toggle('status--off', !connected);
    statusText.textContent = label;
  }

  function connect() {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    ws = new WebSocket(`${proto}://${location.host}/ws?token=${encodeURIComponent(token)}`);

    ws.onopen = () => {
      reconnectDelay = 1000;
      setStatus(true, 'Connected \u2713');
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === 'status' && msg.connected) {
          setStatus(true, 'Connected \u2713');
        }
      } catch (_) { /* ignore malformed frames */ }
    };

    ws.onclose = () => {
      setStatus(false, 'Reconnecting\u2026');
      setTimeout(connect, reconnectDelay);
      reconnectDelay = Math.min(reconnectDelay * 2, 10000);
    };

    ws.onerror = () => ws.close();
  }

  function send(payload) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(payload));
      if (navigator.vibrate) navigator.vibrate(12);
      return true;
    }
    return false;
  }

  const sendKey = (command) => send({ type: 'key', command });
  const sendText = (value) => send({ type: 'text', value });

  /* ---------- Buttons: tap + hold-to-repeat ---------- */

  const ARROWS = new Set(['UP', 'DOWN', 'LEFT', 'RIGHT']);
  const HOLD_DELAY = 400;
  const REPEAT_INTERVAL = 110;

  function setupKeyButton(btn) {
    const key = btn.dataset.key;
    btn.addEventListener('contextmenu', (e) => e.preventDefault());

    if (!ARROWS.has(key)) {
      btn.addEventListener('click', () => sendKey(key));
      return;
    }

    let holdTimeout = null;
    let repeatInterval = null;

    const stopRepeat = () => {
      clearTimeout(holdTimeout);
      clearInterval(repeatInterval);
      holdTimeout = null;
      repeatInterval = null;
    };

    btn.addEventListener('pointerdown', (e) => {
      e.preventDefault();
      try { btn.setPointerCapture(e.pointerId); } catch (_) {}
      sendKey(key);
      holdTimeout = setTimeout(() => {
        repeatInterval = setInterval(() => sendKey(key), REPEAT_INTERVAL);
      }, HOLD_DELAY);
    });

    btn.addEventListener('pointerup', stopRepeat);
    btn.addEventListener('pointercancel', stopRepeat);
    btn.addEventListener('pointerleave', stopRepeat);
  }

  document.querySelectorAll('[data-key]').forEach(setupKeyButton);

  /* ---------- Swipe area: switch windows ---------- */

  const swipeArea = document.getElementById('swipe-area');
  if (swipeArea) {
    let startX = 0;
    const SWIPE_THRESHOLD = 60;

    swipeArea.addEventListener('touchstart', (e) => {
      startX = e.touches[0].clientX;
    }, { passive: true });

    swipeArea.addEventListener('touchend', (e) => {
      const dx = e.changedTouches[0].clientX - startX;
      if (Math.abs(dx) < SWIPE_THRESHOLD) return;
      if (dx > 0) sendKey('ALT_TAB');
      else sendKey('ALT_SHIFT_TAB');
      swipeArea.classList.add('swiped');
      setTimeout(() => swipeArea.classList.remove('swiped'), 300);
    });
  }

  /* ---------- Text mode (live character sending) ---------- */

  textInput.addEventListener('input', (e) => {
    const char = e.data;
    if (char) sendText(char);
  });

  textInput.addEventListener('blur', () => { textInput.value = ''; });

  sendBtn.addEventListener('click', () => sendKey('BACKSPACE'));

  /* ---------- Voice mode (live speech-to-text) ---------- */

  function showTranscript(text, active = false) {
    transcriptEl.textContent = text;
    transcriptEl.classList.toggle('active', active);
  }

  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

  if (!SpeechRecognition) {
    micBtn.addEventListener('click', () =>
      showTranscript('Voice is not supported in this browser.'));
  } else if (!window.isSecureContext) {
    micBtn.addEventListener('click', () =>
      showTranscript('Voice needs HTTPS. Start Orbion with start-https.bat and re-scan the QR code.'));
  } else {
    const recognition = new SpeechRecognition();
    recognition.lang = 'en-US';
    recognition.interimResults = true;
    recognition.continuous = false;

    let listening = false;

    recognition.onstart = () => {
      listening = true;
      micBtn.classList.add('listening');
      showTranscript('Listening…', true);
    };

    recognition.onresult = (event) => {
      const result = event.results[0];
      showTranscript(`"${result[0].transcript}"`, true);
      if (result.isFinal) {
        sendText(result[0].transcript);
      }
    };

    recognition.onend = () => {
      // Auto-restart if still in listening mode
      if (listening) {
        try { recognition.start(); } catch (_) {}
      } else {
        micBtn.classList.remove('listening');
      }
    };

    recognition.onerror = (event) => {
      listening = false;
      micBtn.classList.remove('listening');
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        showTranscript('Microphone blocked. Use start-https.bat and allow mic access.');
      } else {
        showTranscript('Voice recognition error. Try again.');
      }
    };

    micBtn.addEventListener('click', () => {
      if (listening) {
        listening = false;
        micBtn.classList.remove('listening');
        recognition.abort();
        showTranscript('Stopped.');
      } else {
        recognition.start();
      }
    });
  }

  /* ---------- Mouse Pad ---------- */

  const mousepadBtn = document.getElementById('mousepad-btn');
  const mousepadOverlay = document.getElementById('mousepad-overlay');
  const mousepadClose = document.getElementById('mousepad-close');
  const mousepadArea = document.getElementById('mousepad-area');
  const mainUI = document.getElementById('main-ui');
  const mouseLeftBtn = document.getElementById('mouse-left');
  const mouseRightBtn = document.getElementById('mouse-right');

  mousepadBtn.addEventListener('click', () => {
    mainUI.classList.add('hidden');
    mousepadOverlay.classList.remove('hidden');
  });

  mousepadClose.addEventListener('click', () => {
    mousepadOverlay.classList.add('hidden');
    mainUI.classList.remove('hidden');
  });

  // Touch tracking for mouse movement (throttled to avoid WS flooding)
  // 1 finger = move cursor, 2 fingers = scroll, 3 fingers drag = move cursor
  let lastTouchX = null, lastTouchY = null;
  let accumDx = 0, accumDy = 0, scrollAccum = 0, moveQueued = false;
  let touchFingers = 0, touchMoved = false;

  function flushMove() {
    if (touchFingers === 1 && (accumDx !== 0 || accumDy !== 0)) {
      send({ type: 'mouse_move', dx: accumDx, dy: accumDy });
    } else if (touchFingers === 2 && Math.abs(scrollAccum) >= 20) {
      send({ type: 'scroll', dy: scrollAccum > 0 ? 1 : -1 });
      scrollAccum = 0;
    } else if (touchFingers === 3 && (accumDx !== 0 || accumDy !== 0)) {
      send({ type: 'mouse_move', dx: accumDx, dy: accumDy });
    }
    accumDx = 0;
    accumDy = 0;
    moveQueued = false;
  }

  mousepadArea.addEventListener('touchstart', (e) => {
    touchFingers = e.touches.length;
    const t = e.touches[0];
    lastTouchX = t.clientX;
    lastTouchY = t.clientY;
    accumDx = 0;
    accumDy = 0;
    scrollAccum = 0;
    touchMoved = false;
  });

  mousepadArea.addEventListener('touchmove', (e) => {
    e.preventDefault();
    touchFingers = e.touches.length;
    const t = e.touches[0];
    const dx = t.clientX - lastTouchX;
    const dy = t.clientY - lastTouchY;
    lastTouchX = t.clientX;
    lastTouchY = t.clientY;
    accumDx += Math.round(dx * 1.5);
    accumDy += Math.round(dy * 1.5);
    scrollAccum += dy;
    touchMoved = true;
    if (!moveQueued) {
      moveQueued = true;
      requestAnimationFrame(flushMove);
    }
  });

  mousepadArea.addEventListener('touchend', (e) => {
    flushMove();
    if (!touchMoved) {
      if (touchFingers === 2) send({ type: 'mouse_click', button: 'left' });
      else if (touchFingers === 3) send({ type: 'mouse_click', button: 'right' });
    }
    if (e.touches.length === 0) {
      lastTouchX = null;
      lastTouchY = null;
      touchFingers = 0;
    }
  });

  mouseLeftBtn.addEventListener('click', () => send({ type: 'mouse_click', button: 'left' }));
  mouseRightBtn.addEventListener('click', () => send({ type: 'mouse_click', button: 'right' }));

  /* ---------- Init ---------- */

  if (!token) {
    setStatus(false, 'Missing token');
    showTranscript('Open this page by scanning the QR code on your desktop.');
  } else {
    connect();
  }
})();
