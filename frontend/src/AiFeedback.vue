<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { Sparkles, RefreshCw, ShieldCheck, AlertCircle, Wand2, Check } from 'lucide-vue-next';
import { api } from './api';
import { applySuggestion, restoreParagraph } from './aiReplace';
const props = defineProps<{ entryId: number; assignmentEnabled?: boolean; stepMode?: boolean; doc?: any }>();
const emit = defineEmits<{ visibility: [enabled: boolean]; locate: [paragraphId: string]; content: [doc: any]; marks: [list: Array<{ blockId: string; quote: string; state: string }>] }>();
const enabled = ref(false), loading = ref(false), failed = ref(false);
const status = ref(''), message = ref(''), model = ref('');
// 今天还能生成几轮；-1 表示不限，null 表示服务端没返回。
const remaining = ref<number | null>(null);
const suggestions = ref<Array<{ id?: number; paragraph_id?: string | null; quote_text?: string | null; replacement_text?: string | null; dimension: string; title: string; body: string; state?: string; step_index?: number | null }>>([]);
// 替换前的段落快照：撤销时按 blockId 原样换回去。
const undo = ref<Record<number, { paragraphId: string; before: any }>>({});
const applyError = ref('');
let sequence = 0;
const DIM: Record<string, string> = { S: '科学性', A: '准确性', C: '完整性', L: '逻辑性' };
function dimName(code: string) { return DIM[String(code || 'C').charAt(0)] || '建议'; }
// 正文引文与替换文本都按 LaTeX 源码显示，去掉包裹用的 $ 保持一致。
function shown(s: any) { return String(s?.replacement_text || '').replace(/\$/g, ''); }
async function setState(s: any, next: string) {
  if (!s.id) return false;
  try { await api(`/ai-suggestions/${s.id}/state`, 'POST', { state: next }); return true; }
  catch { return false; }
}
// 有可替换文本的建议：先把正文改掉，再记状态；改不动就只提示，不碰正文。
async function adoptWithReplace(s: any) {
  if (!s.id) return;
  applyError.value = '';
  const result = applySuggestion(props.doc, s.paragraph_id || '', s.quote_text || '', s.replacement_text || '');
  if (!result) {
    applyError.value = '原句已被改动，无法自动替换，请按建议手动修改。';
    return;
  }
  undo.value = { ...undo.value, [s.id]: { paragraphId: s.paragraph_id || '', before: result.before } };
  emit('content', result.doc);
  const previous = s.state || 'OPEN';
  s.state = 'ADOPTED';
  // 状态没记上也不能丢快照：正文已经改了，撤销必须还能用。
  if (!(await setState(s, 'ADOPTED'))) {
    s.state = previous;
    applyError.value = '正文已替换，但处理状态没能记录，请刷新后再看。';
  }
}
async function undoReplace(s: any) {
  if (!s.id) return;
  const snapshot = undo.value[s.id];
  if (!snapshot) return;
  emit('content', restoreParagraph(props.doc, snapshot.paragraphId, snapshot.before));
  const next = { ...undo.value };
  delete next[s.id];
  undo.value = next;
  const previous = s.state || 'ADOPTED';
  s.state = 'OPEN';
  if (!(await setState(s, 'OPEN'))) s.state = previous;
}
async function toggleAdopt(s: any) {
  if (!s.id) return;
  if (s.replacement_text && s.quote_text && s.paragraph_id) {
    // 快照只在本次页面里有效：刷新后正文已是替换结果，没有可还原的原文，按钮退回「已替换」。
    if (s.state === 'ADOPTED') { if (undo.value[s.id]) await undoReplace(s); return; }
    await adoptWithReplace(s);
    return;
  }
  const previous = s.state || 'OPEN';
  const next = previous === 'ADOPTED' ? 'OPEN' : 'ADOPTED';
  s.state = next;
  if (!(await setState(s, next))) s.state = previous;
}
function adoptLabel(s: any) {
  if (s.state === 'ADOPTED') {
    if (!s.replacement_text) return '已采纳';
    return undo.value[s.id] ? '撤销替换' : '已替换';
  }
  return s.replacement_text ? '采纳并替换' : '采纳';
}
function locate(s: any) { if (s.paragraph_id) emit('locate', s.paragraph_id); }
// 正文页边标记点一下 → 右侧滚到那条建议并闪一下，学生不用自己找。
const listEl = ref<HTMLElement | null>(null);
let flashTimer: ReturnType<typeof setTimeout> | undefined;
function reveal(blockId: string) {
  if (!blockId) return;
  const index = visible.value.findIndex((s: any) => s.paragraph_id === blockId);
  if (index < 0) return;
  const card = listEl.value?.querySelectorAll('.ai-suggestion')[index] as HTMLElement | undefined;
  if (!card) return;
  card.scrollIntoView({ block: 'center', behavior: 'smooth' });
  card.classList.add('is-flash');
  clearTimeout(flashTimer);
  flashTimer = setTimeout(() => card.classList.remove('is-flash'), 1600);
}
defineExpose({ reveal });
// 忽略只是隐藏这条建议，不删除记录；教师端仍能看到学生处理过。
async function dismiss(s: any) {
  if (!s.id) return;
  const previous = s.state || 'OPEN';
  s.state = 'DISMISSED';
  try { await api(`/ai-suggestions/${s.id}/state`, 'POST', { state: 'DISMISSED' }); }
  catch { s.state = previous; }
}
const visible = computed(() => suggestions.value.filter((s: any) => s.state !== 'DISMISSED'));
// 正文页边只认「哪一段有几条」：把列表压成 Editor 能用的最小结构，状态变了就重发。
const marks = computed(() =>
  visible.value
    .filter((s: any) => s.paragraph_id)
    .map((s: any) => ({ blockId: String(s.paragraph_id), quote: s.quote_text || '', state: s.state || 'OPEN' })),
);
watch(marks, list => emit('marks', list), { deep: true, immediate: true });
async function load(generate = false) {
  const current = ++sequence; loading.value = true; failed.value = false;
  try {
    const data = await api(`/entries/${props.entryId}/ai-feedback${generate ? '?generate=true' : ''}`);
    if (current !== sequence) return;
    enabled.value = data.enabled === true;
    status.value = data.status || '';
    message.value = data.message || '';
    model.value = data.model || '';
    remaining.value = typeof data.remaining === 'number' ? data.remaining : null;
    suggestions.value = Array.isArray(data.suggestions) ? data.suggestions : [];
    // 次数用完时上一轮建议仍然有效：照常显示，只提示不能再生成。
    if (data.status === 'LIMITED' && suggestions.value.length) { status.value = 'READY'; applyError.value = data.message || ''; }
    emit('visibility', enabled.value);
  } catch {
    if (current !== sequence) return;
    // Fail closed: never retain feedback after an authorization/network failure.
    enabled.value = false; failed.value = true; suggestions.value = []; emit('visibility', false);
  } finally { if (current === sequence) loading.value = false; }
}
watch(() => props.entryId, () => { enabled.value = false; suggestions.value = []; emit('visibility', false); load(); });
watch(() => props.assignmentEnabled, value => { if (!value) { enabled.value = false; suggestions.value = []; emit('visibility', false); } else load(); });
onMounted(() => { emit('visibility', false); load(); });
onBeforeUnmount(() => { sequence++; });
</script>
<template>
  <section v-if="enabled" id="sidebar-ai" class="sidebar-section ai-feedback-section" aria-label="AI 反馈">
    <header class="sidebar-section-heading"><h3><Sparkles :size="15" />AI 反馈</h3><span class="small-label">{{ status === 'READY' ? (stepMode ? '逐步骤建议' : (model || '已生成')) : '教师已开启' }}</span></header>

    <div v-if="status === 'READY' && visible.length" ref="listEl" class="ai-suggestion-list">
      <article v-for="(s, i) in visible" :key="s.id || i" class="ai-suggestion" :class="{ adopted: s.state === 'ADOPTED' }">
        <div class="ai-suggestion-top">
          <span v-if="s.step_index !== null && s.step_index !== undefined" class="ai-dim step">第 {{ Number(s.step_index) + 1 }} 步</span>
          <span v-else class="ai-dim">{{ dimName(s.dimension) }}</span>
          <strong>{{ s.title }}</strong>
        </div>
        <p>{{ s.body }}</p>
        <blockquote v-if="s.quote_text" class="ai-quote">{{ s.quote_text }}</blockquote>
        <p v-if="s.replacement_text" class="ai-replace"><span>改为</span>{{ shown(s) }}</p>
        <div class="ai-suggestion-actions">
          <button v-if="s.paragraph_id" class="ai-act locate" @click="locate(s)">定位正文</button>
          <button class="ai-act adopt" :class="{ on: s.state === 'ADOPTED' }" @click="toggleAdopt(s)">{{ adoptLabel(s) }}</button>
          <button class="ai-act ignore" @click="dismiss(s)">忽略</button>
        </div>
      </article>
      <p v-if="applyError" class="ai-apply-error">{{ applyError }}</p>
      <button class="text-btn ai-refresh" :disabled="loading || remaining === 0" @click="load(true)"><RefreshCw :size="13" :class="{ spinning: loading }" />重新生成建议</button>
      <p v-if="remaining !== null && remaining >= 0" class="ai-act-note">今天还可生成 {{ remaining }} 次</p>
      <p class="ai-act-note">采纳会直接改正文，可点「撤销替换」还原。</p>
    </div>

    <div v-else-if="status === 'READY'" class="ai-service-state">
      <span class="ai-state-icon"><Check :size="19" /></span>
      <h4>建议都已处理</h4>
      <p>你可以重新生成一轮，或按已采纳的建议继续修改。</p>
      <button class="btn secondary" :disabled="loading" @click="load(true)"><RefreshCw :size="15" />重新生成建议</button>
    </div>

    <div v-else-if="status === 'NOT_GENERATED'" class="ai-service-state">
      <span class="ai-state-icon"><Sparkles :size="19" /></span>
      <h4>还没有生成建议</h4>
      <p v-if="stepMode">按步骤把正文发送给模型，逐条指出每一步的具体问题与最小改法；不代写答案，也不上传图片与证据原文。</p>
      <p v-else>将把当前草稿正文发送给模型，生成四维修改建议；不会上传图片与证据原文。</p>
      <button class="btn secondary" :disabled="loading || remaining === 0" @click="load(true)"><Wand2 :size="15" />{{ loading ? '生成中…' : '生成 AI 建议' }}</button>
      <p v-if="remaining !== null && remaining >= 0" class="ai-act-note">今天还可生成 {{ remaining }} 次</p>
    </div>

    <div v-else-if="status === 'LIMITED'" class="ai-service-state">
      <span class="ai-state-icon"><AlertCircle :size="19" /></span>
      <h4>今天的次数已用完</h4>
      <p>{{ message || '明天再试。' }}</p>
    </div>

    <div v-else-if="status === 'FAILED'" class="ai-service-state">
      <span class="ai-state-icon"><AlertCircle :size="19" /></span>
      <h4>暂时无法生成建议</h4>
      <p>{{ message || '模型服务未返回结果，请稍后重试。' }}</p>
      <button class="text-btn" :disabled="loading" @click="load(true)">重试</button>
    </div>

    <div v-else-if="status === 'EMPTY'" class="ai-service-state">
      <span class="ai-state-icon"><Sparkles :size="19" /></span>
      <h4>正文还是空的</h4>
      <p>先写下一段解释，再生成建议。</p>
    </div>

    <div v-else class="ai-service-state">
      <span class="ai-state-icon"><Sparkles :size="19" /></span>
      <h4>AI 服务暂不可用</h4>
      <button class="text-btn ai-refresh" :disabled="loading" @click="load()"><RefreshCw :size="13" :class="{ spinning: loading }" />重新检查状态</button>
    </div>

    <p class="sidebar-trust-note"><ShieldCheck :size="13" /><span>AI 建议仅供参考，不计入评分</span></p>
  </section>
  <div v-else-if="failed && assignmentEnabled" class="ai-status-error"><AlertCircle :size="14" /><span>暂时无法确认 AI 开放状态。</span><button class="text-btn" @click="load()">重试</button></div>
</template>
