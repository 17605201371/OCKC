/* Blocking, same-origin bootstrap: applies before the app/CSS first paint; CSP needs no inline script. */
(() => {
  const root = document.documentElement;
  const media = matchMedia('(prefers-color-scheme: dark)');
  const normalize = value => ['light', 'dark', 'system'].includes(value) ? value : 'system';
  let preference = 'system';
  try { preference = normalize(localStorage.getItem('ockc-theme')); } catch {}
  function apply() {
    const effective = preference === 'system' ? (media.matches ? 'dark' : 'light') : preference;
    root.dataset.themePreference = preference;
    root.dataset.theme = effective;
    root.style.colorScheme = effective;
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = effective === 'dark' ? '#191919' : '#ffffff';
    window.dispatchEvent(new CustomEvent('ockc-theme-change', {detail: preference}));
  }
  window.courseTheme = { set(value) {
    preference = normalize(value);
    try { localStorage.setItem('ockc-theme', preference); } catch {}
    apply();
  }};
  media.addEventListener('change', () => { if (preference === 'system') apply(); });
  window.addEventListener('storage', event => {
    if (event.key === 'ockc-theme' || event.key === null) {
      preference = normalize(event.newValue); apply();
    }
  });
  apply();
})();
