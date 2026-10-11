/* ==========================================================================
   💎 VIDEX V8 — Interactions
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
  initMobileMenu();
  initScrollReveal();
  initFaqAccordion();
  initNavScrollState();
  initScrollProgress();
  initActiveNavLink();
  initSpotlight();
  initTiltCards();
  initMagnetic();
  initCursorGlow();
  fetchLatestGitHubRelease();
});

/* ============== MOBILE MENU ============== */
function initMobileMenu() {
  const btn = document.getElementById('menu-btn');
  const menu = document.getElementById('mobile-menu');
  if (!btn || !menu) return;

  btn.addEventListener('click', () => menu.classList.toggle('open'));
  menu.querySelectorAll('a').forEach(a =>
    a.addEventListener('click', () => menu.classList.remove('open'))
  );
}

/* ============== SCROLL REVEAL ============== */
function initScrollReveal() {
  const observer = new IntersectionObserver((entries, obs) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('active');
        obs.unobserve(entry.target);
      }
    });
  }, { threshold: 0.12, rootMargin: '0px 0px -60px 0px' });

  document.querySelectorAll('.fade-up').forEach(el => observer.observe(el));

  // Hero — reveal instantly
  requestAnimationFrame(() => {
    document.querySelectorAll('.hero .fade-up').forEach(el => el.classList.add('active'));
  });
}

/* ============== FAQ ============== */
function initFaqAccordion() {
  const items = document.querySelectorAll('.faq-item');
  items.forEach(item => {
    const btn = item.querySelector('.faq-btn');
    const body = item.querySelector('.faq-body');

    btn.addEventListener('click', () => {
      const isActive = item.classList.contains('active');

      items.forEach(i => {
        i.classList.remove('active');
        i.querySelector('.faq-body').style.maxHeight = null;
      });

      if (!isActive) {
        item.classList.add('active');
        body.style.maxHeight = body.scrollHeight + 'px';
      }
    });
  });
}

/* ============== NAVBAR SCROLL STATE ============== */
function initNavScrollState() {
  const navbar = document.getElementById('navbar');
  if (!navbar) return;

  const update = () => {
    navbar.classList.toggle('scrolled', window.scrollY > 20);
  };
  update();
  window.addEventListener('scroll', update, { passive: true });
}

/* ============== SCROLL PROGRESS BAR ============== */
function initScrollProgress() {
  const bar = document.getElementById('scroll-progress');
  if (!bar) return;

  const update = () => {
    const h = document.documentElement.scrollHeight - window.innerHeight;
    const p = h > 0 ? (window.scrollY / h) * 100 : 0;
    bar.style.width = p + '%';
  };
  update();
  window.addEventListener('scroll', update, { passive: true });
  window.addEventListener('resize', update);
}

/* ============== ACTIVE NAV LINK ============== */
function initActiveNavLink() {
  const links = document.querySelectorAll('.nav-links a[data-nav]');
  const sections = [...links]
    .map(l => document.querySelector(l.getAttribute('href')))
    .filter(Boolean);
  if (!sections.length) return;

  const observer = new IntersectionObserver(entries => {
    entries.forEach(e => {
      if (e.isIntersecting) {
        const id = '#' + e.target.id;
        links.forEach(l => l.classList.toggle('active', l.getAttribute('href') === id));
      }
    });
  }, { rootMargin: '-45% 0px -50% 0px' });

  sections.forEach(s => observer.observe(s));
}

/* ============== SPOTLIGHT EFFECT ============== */
function initSpotlight() {
  document.querySelectorAll('.spotlight-card').forEach(card => {
    card.addEventListener('mousemove', e => {
      const r = card.getBoundingClientRect();
      card.style.setProperty('--mouse-x', (e.clientX - r.left) + 'px');
      card.style.setProperty('--mouse-y', (e.clientY - r.top) + 'px');
    });
  });
}

/* ============== 3D TILT ============== */
function initTiltCards() {
  if (window.matchMedia('(hover: none)').matches) return;

  document.querySelectorAll('.tilt-card').forEach(card => {
    card.addEventListener('mousemove', e => {
      const r = card.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width - 0.5;
      const y = (e.clientY - r.top) / r.height - 0.5;
      card.style.transform = `perspective(900px) rotateY(${x * 5}deg) rotateX(${-y * 5}deg) translateY(-4px)`;
    });
    card.addEventListener('mouseleave', () => {
      card.style.transform = '';
    });
  });
}

/* ============== MAGNETIC BUTTONS ============== */
function initMagnetic() {
  if (window.matchMedia('(hover: none)').matches) return;

  document.querySelectorAll('.magnetic').forEach(el => {
    el.addEventListener('mousemove', e => {
      const r = el.getBoundingClientRect();
      const x = e.clientX - r.left - r.width / 2;
      const y = e.clientY - r.top - r.height / 2;
      el.style.transform = `translate(${x * 0.15}px, ${y * 0.25}px) scale(1.02)`;
    });
    el.addEventListener('mouseleave', () => {
      el.style.transform = '';
    });
  });
}

/* ============== CURSOR GLOW ============== */
function initCursorGlow() {
  const glow = document.getElementById('cursor-glow');
  if (!glow || window.matchMedia('(hover: none)').matches) return;

  let tx = 0, ty = 0, cx = 0, cy = 0;

  window.addEventListener('mousemove', e => { tx = e.clientX; ty = e.clientY; });

  const loop = () => {
    cx += (tx - cx) * 0.12;
    cy += (ty - cy) * 0.12;
    glow.style.transform = `translate(${cx}px, ${cy}px) translate(-50%, -50%)`;
    requestAnimationFrame(loop);
  };
  loop();
}

/* ============== GITHUB RELEASE ============== */
async function fetchLatestGitHubRelease() {
  const btnDownload = document.getElementById('btn-download');
  const apkBtnText = document.getElementById('apk-btn-text');
  const releaseMeta = document.getElementById('release-meta');

  try {
    const response = await fetch('https://api.github.com/repos/XZ-LITE/Videx/releases/latest');
    if (!response.ok) throw new Error('API Error');

    const data = await response.json();
    const tagName = data.tag_name || 'v1.1.4';
    const apkAsset = data.assets?.find(a => a.name.endsWith('.apk'));

    if (apkAsset) {
      if (btnDownload) btnDownload.href = apkAsset.browser_download_url;
      const sizeMb = (apkAsset.size / (1024 * 1024)).toFixed(1);
      if (apkBtnText) apkBtnText.textContent = `Скачать APK · ${sizeMb} МБ`;
      if (releaseMeta) releaseMeta.textContent = `Версия ${tagName} • Android 8.0+ • Без рекламы`;
    }
  } catch {
    console.warn('Fallback to standard GitHub link.');
  }
}