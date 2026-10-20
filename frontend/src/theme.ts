import { ref, onBeforeUnmount } from 'vue';
export type ThemePreference = 'system' | 'light' | 'dark';
declare global { interface Window { courseTheme: { set(value: ThemePreference): void } } }
export function useTheme() {
  const theme = ref<ThemePreference>((document.documentElement.dataset.themePreference as ThemePreference) || 'system');
  const sync = () => { theme.value = document.documentElement.dataset.themePreference as ThemePreference; };
  window.addEventListener('ockc-theme-change', sync);
  onBeforeUnmount(() => window.removeEventListener('ockc-theme-change', sync));
  function cycle() {
    window.courseTheme.set(theme.value === 'system' ? 'light' : theme.value === 'light' ? 'dark' : 'system');
  }
  return { theme, cycle };
}
