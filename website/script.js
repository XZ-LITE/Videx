/* ==========================================================================
   💎 VIDEX V6 — MINIMAL SCRIPT (No bloat, pure performance)
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
  initMobileMenu();
  initScrollReveal();
  initFaqAccordion();
  fetchLatestGitHubRelease();
});

/**
 * 📱 МОБИЛЬНОЕ МЕНЮ
 */
function initMobileMenu() {
  const menuBtn = document.getElementById('menu-btn');
  const mobileMenu = document.getElementById('mobile-menu');
  if (!menuBtn || !mobileMenu) return;

  const toggleMenu = () => {
    mobileMenu.classList.toggle('open');
  };

  menuBtn.addEventListener('click', toggleMenu);

  mobileMenu.querySelectorAll('a').forEach(link => {
    link.addEventListener('click', () => {
      mobileMenu.classList.remove('open');
    });
  });
}

/**
 * ✨ ПЛАВНОЕ ПОЯВЛЕНИЕ ЭЛЕМЕНТОВ (Intersection Observer)
 */
function initScrollReveal() {
  const elements = document.querySelectorAll('.fade-up');

  const observer = new IntersectionObserver((entries, obs) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('active');
        obs.unobserve(entry.target);
      }
    });
  }, { threshold: 0.1 });

  elements.forEach(el => observer.observe(el));

  // Главный экран выплывает сразу
  setTimeout(() => {
    document.querySelectorAll('.hero .fade-up').forEach(el => el.classList.add('active'));
  }, 50);
}

/**
 * ❓ FAQ АККОРДЕОН
 */
function initFaqAccordion() {
  const faqButtons = document.querySelectorAll('.faq-btn');

  faqButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const item = btn.parentElement;
      const isActive = item.classList.contains('active');

      document.querySelectorAll('.faq-item').forEach(el => el.classList.remove('active'));

      if (!isActive) {
        item.classList.add('active');
      }
    });
  });
}

/**
 * 📡 GITHUB API (Свежий релиз)
 */
async function fetchLatestGitHubRelease() {
  const btnDownload = document.getElementById('btn-download');
  const apkBtnText = document.getElementById('apk-btn-text');
  const releaseMeta = document.getElementById('release-meta');

  try {
    const response = await fetch('https://api.github.com/repos/XZ-LITE/Videx/releases/latest');
    if (!response.ok) throw new Error('API Error');

    const data = await response.json();
    const tagName = data.tag_name || 'v1.1.4';
    const apkAsset = data.assets?.find(asset => asset.name.endsWith('.apk'));

    if (apkAsset) {
      if (btnDownload) btnDownload.href = apkAsset.browser_download_url;
      const sizeMb = (apkAsset.size / (1024 * 1024)).toFixed(1);
      if (apkBtnText) apkBtnText.textContent = `Скачать APK (${sizeMb} МБ)`;
      if (releaseMeta) releaseMeta.textContent = `Версия ${tagName} • Android 8.0+`;
    }
  } catch (error) {
    console.warn('Fallback to standard github link.');
  }
}
