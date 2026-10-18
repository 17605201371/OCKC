<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue';
import { Sparkles, RefreshCw, ShieldCheck, AlertCircle } from 'lucide-vue-next';
import { api } from './api';
const props = defineProps<{ entryId: number; assignmentEnabled?: boolean }>();
const emit = defineEmits<{ visibility: [enabled: boolean] }>();
const enabled = ref(false), loading = ref(false), failed = ref(false);
let sequence = 0, timer: ReturnType<typeof setInterval>;
async function refresh() {
  const current = ++sequence; loading.value = true; failed.value = false;
  try {
    const data = await api(`/entries/${props.entryId}/ai-feedback`);
    if (current !== sequence) return;
    enabled.value = data.enabled === true;
    emit('visibility', enabled.value);
  } catch {
    if (current !== sequence) return;
    // Fail closed: never retain feedback after an authorization/network failure.
    enabled.value = false; failed.value = true; emit('visibility', false);
  } finally { if (current === sequence) loading.value = false; }
}
watch(() => props.entryId, () => { enabled.value = false; emit('visibility',false); refresh(); });
watch(() => props.assignmentEnabled, value => { if (!value) { enabled.value = false; emit('visibility',false); } refresh(); });
onMounted(() => { emit('visibility',false); refresh(); timer = setInterval(refresh, 15000); window.addEventListener('focus', refresh); });
onBeforeUnmount(() => { sequence++; clearInterval(timer); window.removeEventListener('focus', refresh); });
</script>
<template>
  <section v-if="enabled" id="sidebar-ai" class="sidebar-section ai-feedback-section" aria-label="AI 反馈">
    <header class="sidebar-section-heading"><h3><Sparkles :size="15" />AI 反馈</h3><span class="small-label">教师已开启</span></header>
    <div class="ai-service-state"><span class="ai-state-icon"><Sparkles :size="19" /></span><h4>AI 服务尚未配置</h4><p>教师已为本作业开放 AI 反馈入口。接通模型服务后，才会提供真实的修改建议。</p></div>
    <button class="text-btn ai-refresh" :disabled="loading" @click="refresh"><RefreshCw :size="13" :class="{ spinning: loading }" />重新检查状态</button>
    <p class="sidebar-trust-note"><ShieldCheck :size="13" /><span>AI 建议仅供修订参考，不参与正式评分；引用来源仍需核验。当前未向任何模型发送正文。</span></p>
  </section>
  <div v-else-if="failed && assignmentEnabled" class="ai-status-error"><AlertCircle :size="14" /><span>暂时无法确认 AI 开放状态。</span><button class="text-btn" @click="refresh">重试</button></div>
</template>
