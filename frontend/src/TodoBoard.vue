<script setup lang="ts">
// 首页待办：按组列出此刻还没处理完的事，每条点一下直达处理的地方。数据来自 /courses/{cid}/today。
import { computed } from "vue";
import { Check, ChevronRight, Clock, MessageSquare, PenLine, ShieldAlert, ShieldCheck, Send } from "lucide-vue-next";
import { dimLabel, actionLabel } from "./feedbackLabels";

const props = defineProps<{ groups: any[]; staff: boolean; loading?: boolean; error?: string; summary?: boolean }>();
const emit = defineEmits<{ act: [key: string, item: any]; more: [key: string]; retry: [] }>();

type Meta = { label: string; tag?: string; empty?: string; icon: any; action: (i: any) => string; title: (i: any) => string; sub: (i: any) => string; more?: string };
function ago(d: string) {
  const t = Date.parse(d);
  if (!Number.isFinite(t)) return "";
  const m = Math.max(0, Math.round((Date.now() - t) / 60000));
  if (m < 60) return m <= 1 ? "刚刚" : `${m} 分钟前`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} 小时前`;
  return `${Math.round(h / 24)} 天前`;
}
function dueIn(d: string) {
  const t = Date.parse(d);
  if (!Number.isFinite(t)) return "";
  const h = Math.max(0, Math.round((t - Date.now()) / 3600000));
  return h < 1 ? "1 小时内截止" : h < 24 ? `${h} 小时后截止` : `${Math.round(h / 24)} 天后截止`;
}
const short = (t: unknown, n = 46) => { const s = String(t ?? "").replace(/\s+/g, " ").trim(); return s.length > n ? s.slice(0, n) + "…" : s; };
const META: Record<string, Meta> = {
  review: { label: "待批改", tag: "待批改", icon: PenLine, action: () => "开始批改", title: (i) => i.title, sub: (i) => `${i.author_name} · ${i.assignment_title} · ${ago(i.created_at)}交来`, more: "批改中心" },
  responses: { label: "学生回应待确认", tag: "待确认回应", icon: MessageSquare, action: () => "去确认", title: (i) => short(i.feedback_body), sub: (i) => `${i.author_name} · 《${short(i.title, 24)}》 · ${actionLabel(i.action_type)}${i.reply ? "：" + short(i.reply, 30) : ""}` },
  publications: { label: "发布申请待审核", tag: "待审核发布", icon: Send, action: () => "审核", title: (i) => i.title, sub: (i) => `${i.author_name} · v${i.version_no} · ${ago(i.requested_at)}申请`, more: "发布审核与记录" },
  disputes: { label: "有争议的证据", tag: "争议证据", icon: ShieldAlert, action: () => "裁定", title: (i) => short(i.source_title), sub: (i) => `${i.author_name} · 《${short(i.title, 24)}》`, more: "证据核验" },
  due: {
    label: props.staff ? "快截止的作业" : "快截止、还没交",
    tag: "快截止",
    icon: Clock,
    action: (i) => (props.staff ? "查看提交" : i.started ? "继续写" : "开始写"),
    title: (i) => i.title,
    sub: (i) => (props.staff ? `${dueIn(i.due_at)} · 还有 ${i.missing} / ${i.students} 人没交` : `${dueIn(i.due_at)} · ${i.started ? "已开始写，还没提交" : "还没开始"}`),
  },
  feedback: { label: "老师批注待处理", icon: MessageSquare, action: () => "去修改", title: (i) => short(i.feedback_body), sub: (i) => `《${short(i.title, 24)}》 · ${dimLabel(i.indicator_code)}` },
  verify: { label: "可以帮同学核验的证据", icon: ShieldCheck, action: () => "去核验", title: (i) => short(i.source_title), sub: (i) => `${i.author_name} · 《${short(i.title, 24)}》`, more: "证据核验" },
};
const shown = computed(() => props.groups.filter((g) => META[g.key]));
const active = computed(() => shown.value.filter((g) => g.count > 0));
const total = computed(() => active.value.reduce((n, g) => n + g.count, 0));
function jump(key: string) {
  document.getElementById("todo-" + key)?.scrollIntoView({ behavior: "smooth", block: "start" });
}
defineExpose({ total });
</script>

<template>
  <section class="todo-board" :aria-busy="!!loading">
    <div v-if="summary && staff" class="todo-counters" role="group" aria-label="待办分类" :style="{ gridTemplateColumns: `repeat(${shown.length}, minmax(0, 1fr))` }">
      <button v-for="g in shown" :key="g.key" type="button" class="todo-counter" :class="{ zero: !g.count }" :disabled="!g.count" @click="jump(g.key)">
        <component :is="META[g.key].icon" :size="16" />
        <span>{{ META[g.key].tag || META[g.key].label }}</span>
        <strong>{{ g.count }}</strong>
      </button>
    </div>

    <p v-if="error" class="todo-error" role="alert">{{ error }} <button type="button" class="text-btn" @click="emit('retry')">重新读取</button></p>
    <p v-else-if="loading && !groups.length" class="todo-loading" role="status">正在读取待办…</p>

    <div v-else-if="!total" class="todo-done">
      <Check :size="20" />
      <div>
        <strong>{{ staff ? "都处理完了" : "当前没有待处理事项" }}</strong>
        <p>{{ staff ? "没有待批改的提交、待确认的回应或待审核的申请。新的事项会出现在这里。" : "继续创作，也可以看看课程里的好解释。" }}</p>
      </div>
    </div>

    <section v-for="g in active" :id="'todo-' + g.key" :key="g.key" class="dashboard-card todo-group">
      <header class="card-header">
        <h2><component :is="META[g.key].icon" :size="17" />{{ META[g.key].label }}<span class="todo-count">{{ g.count }}</span></h2>
        <button v-if="META[g.key].more" type="button" class="text-btn" @click="emit('more', g.key)">{{ META[g.key].more }}<ChevronRight :size="14" /></button>
      </header>
      <div class="todo-items">
        <div v-for="(item, i) in g.items" :key="g.key + '-' + i" class="todo-item">
          <div class="todo-item-main">
            <strong>{{ META[g.key].title(item) }}</strong>
            <p>{{ META[g.key].sub(item) }}</p>
          </div>
          <button type="button" class="btn secondary compact" @click="emit('act', g.key, item)">{{ META[g.key].action(item) }}</button>
        </div>
      </div>
      <p v-if="g.count > g.items.length" class="todo-more">还有 {{ g.count - g.items.length }} 项<template v-if="META[g.key].more">，在「{{ META[g.key].more }}」里查看全部</template>。</p>
    </section>
  </section>
</template>

<style scoped>
.todo-board { display: flex; flex-direction: column; gap: 16px; }
.todo-counters { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; }
.todo-counter { display: flex; align-items: center; gap: 7px; min-width: 0; padding: 12px 12px; border: 1px solid var(--border); border-radius: 12px; background: var(--paper); color: var(--text); font: inherit; font-size: 13px; text-align: left; cursor: pointer; }
.todo-counter:hover:not(:disabled) { border-color: var(--blue); }
.todo-counter span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.todo-counter strong { margin-left: auto; font-size: 20px; font-weight: 650; color: var(--blue-text); }
.todo-counter.zero { cursor: default; color: var(--muted); }
.todo-counter.zero strong { color: var(--subtle); }
.todo-group .card-header h2 { display: flex; align-items: center; gap: 8px; }
.todo-count { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; height: 20px; padding: 0 6px; border-radius: 999px; background: var(--blue-soft); color: var(--blue-text); font-size: 12px; font-weight: 650; }
.todo-items { display: flex; flex-direction: column; }
.todo-item { display: flex; align-items: center; gap: 14px; padding: 12px 22px; border-top: 1px solid var(--line-soft); }
.todo-item:first-child { border-top: 0; }
.todo-item-main { flex: 1 1 auto; min-width: 0; }
.todo-item-main strong { display: block; font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.todo-item-main p { margin: 3px 0 0; font-size: 12.5px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.todo-more { margin: 0; padding: 10px 22px 14px; font-size: 12.5px; color: var(--muted); border-top: 1px solid var(--line-soft); }
.todo-done { display: flex; align-items: flex-start; gap: 12px; padding: 18px 22px; border: 1px solid var(--border); border-radius: 14px; background: var(--paper); color: var(--green, #16a34a); }
.todo-done strong { color: var(--text); font-size: 14.5px; }
.todo-done p { margin: 4px 0 0; color: var(--muted); font-size: 13px; }
.todo-error, .todo-loading { margin: 0; padding: 14px 18px; border: 1px solid var(--border); border-radius: 12px; background: var(--paper); font-size: 13px; color: var(--muted); }
.todo-error { color: var(--red); }
</style>
