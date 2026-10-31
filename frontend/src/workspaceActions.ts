// Measure wrapped toolbars so sticky side panels never sit beneath them.
const actionObservers = new WeakMap<HTMLElement, ResizeObserver>();
export const vWorkspaceActions = {
  mounted(el: HTMLElement) {
    const measure = () => el.parentElement?.style.setProperty('--workspace-action-height', Math.ceil(el.getBoundingClientRect().height) + 'px');
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    actionObservers.set(el, observer);
  },
  unmounted(el: HTMLElement) { actionObservers.get(el)?.disconnect(); actionObservers.delete(el); },
};
