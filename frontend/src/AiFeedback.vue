<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue';
import { Sparkles, RefreshCw, ShieldCheck, AlertCircle, Wand2 } from 'lucide-vue-next';
import { api } from './api';
const props = defineProps<{ entryId: number; assignmentEnabled?: boolean }>();
const emit = defineEmits<{ visibility: [enabled: boolean] }>();
const enabled = ref(false), loading = ref(false), failed = ref(false);
const status = ref(''), message = ref(''), model = ref('');
const suggestions = ref<Array<{ dimension: string; title: string; body: string }>>([]);
let sequence = 0;
const DIM: Record<string, string> = { S: '科学性', A: '准确性', C: '完整性', L: '逻辑性' };
function dimName(code: string) { return DIM[String(code || 'C').charAt(0)] || '建议'; }
async function load(generate = false) {
  const current = ++sequence; loading.value = true; failed.value = false;
  try {
    const data = await api(`/entries/${props.entryId}/ai-feedback${generate ? '?generate=true' : ''}`);
    if (current !== sequence) return;
    enabled.value = data.enabled === true;
    status.value = data.status || '';
    message.value = data.message || '';
    model.value = data.model || '';
    suggestions.value = Array.isArray(data.suggestions) ? data.suggestions : [];
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
    <header class="sidebar-section-heading"><h3><Sparkles :size="15" />AI 反馈</h3><span class="small-label">{{ status === 'READY' ? (model || '已生成') : '教师已开启' }}</span></header>

    <div v-if="status === 'READY' && suggestions.length" class="ai-suggestion-list">
      <article v-for="(s, i) in suggestions" :key="i" class="ai-suggestion">
        <div class="ai-suggestion-top"><span class="ai-dim">{{ dimName(s.dimension) }}</span><strong>{{ s.title }}</strong></div>
        <p>{{ s.body }}</p>
      </article>
      <button class="text-btn ai-refresh" :disabled="loading" @click="load(true)"><RefreshCw :size="13" :class="{ spinning: loading }" />重新生成建议</button>
    </div>

    <div v-else-if="status === 'NOT_GENERATED'" class="ai-service-state">
      <span class="ai-state-icon"><Sparkles :size="19" /></span>
      <h4>还没有生成建议</h4>
      <p>将把当前草稿正文发送给模型，生成四维修改建议；不会上传图片与证据原文。</p>
      <button class="btn secondary" :disabled="loading" @click="load(true)"><Wand2 :size="15" />{{ loading ? '生成中…' : '生成 AI 建议' }}</button>
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
      <h4>AI 服务尚未配置</h4>
      <p>教师已为本作业开放 AI 反馈入口。接通模型服务后，才会提供真实的修改建议。</p>
      <button class="text-btn ai-refresh" :disabled="loading" @click="load()"><RefreshCw :size="13" :class="{ spinning: loading }" />重新检查状态</button>
    </div>

    <p class="sidebar-trust-note"><ShieldCheck :size="13" /><span>AI 建议仅供修订参考，不参与正式评分；引用来源仍需核验。</span></p>
  </section>
  <div v-else-if="failed && assignmentEnabled" class="ai-status-error"><AlertCircle :size="14" /><span>暂时无法确认 AI 开放状态。</span><button class="text-btn" @click="load()">重试</button></div>
</template>
