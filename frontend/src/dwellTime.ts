import { onBeforeUnmount, watch, type Ref } from "vue";
import { apiKeepalive } from "./api";

/**
 * 停留时长：学生在编辑器里写作品（WRITE）、教师在批改页批改某个提交（GRADE）。
 * 只在「标签页可见、且最近 2 分钟内有键盘/鼠标/滚动操作」时每秒计 1 秒，
 * 攒够 30 秒上报一次；切走标签页、离开页面或换到另一个对象时把零头也送出去。
 * 服务端按真实间隔封顶，所以多开窗口不会重复计时。
 */
const IDLE_MS = 120_000;
const FLUSH_EVERY = 30;
const MAX_BEAT = 60;
const INPUT_EVENTS = ["keydown", "pointerdown", "pointermove", "wheel", "scroll", "touchstart"] as const;

export function useDwellTime(kind: "WRITE" | "GRADE", target: Ref<number | null | undefined>) {
  let current: number | null = null;
  let pending = 0;
  let lastInput = Date.now();
  const rejected = new Set<number>();

  const mark = () => { lastInput = Date.now(); };
  const active = () => document.visibilityState === "visible" && Date.now() - lastInput < IDLE_MS;

  // 上报失败不提示，也不走登录失效流程（例如刚退出登录时最后一次上报会是 401）。
  // 没有权限（被移出课程、不是作者）就不再为这个对象上报。
  function flush() {
    const id = current;
    const seconds = Math.min(pending, MAX_BEAT);
    pending = 0;
    if (!id || seconds < 1 || rejected.has(id)) return;
    apiKeepalive("/activity/heartbeat", { kind, targetId: id, seconds })
      .then((status) => { if ([400, 403, 404].includes(status)) rejected.add(id); })
      .catch(() => {});
  }

  const tick = window.setInterval(() => {
    if (!current || !active()) return;
    pending++;
    if (pending >= FLUSH_EVERY) flush();
  }, 1000);
  const onVisibility = () => { if (document.visibilityState === "hidden") flush(); };
  const onPageHide = () => flush();

  INPUT_EVENTS.forEach((e) => window.addEventListener(e, mark, { passive: true, capture: true }));
  document.addEventListener("visibilitychange", onVisibility);
  window.addEventListener("pagehide", onPageHide);

  watch(target, (next) => {
    flush();
    current = next ? Number(next) : null;
    lastInput = Date.now();
  }, { immediate: true });

  onBeforeUnmount(() => {
    flush();
    window.clearInterval(tick);
    INPUT_EVENTS.forEach((e) => window.removeEventListener(e, mark, { capture: true }));
    document.removeEventListener("visibilitychange", onVisibility);
    window.removeEventListener("pagehide", onPageHide);
  });
}

/** 秒数转成「12 分钟」「1 小时 5 分」；0 或缺失显示「—」。 */
export function durationLabel(seconds: unknown): string {
  const s = Number(seconds) || 0;
  if (s <= 0) return "—";
  if (s < 60) return "不足 1 分钟";
  const minutes = Math.round(s / 60);
  if (minutes < 60) return `${minutes} 分钟`;
  const h = Math.floor(minutes / 60), m = minutes % 60;
  return m ? `${h} 小时 ${m} 分` : `${h} 小时`;
}
