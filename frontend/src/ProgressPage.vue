<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { TrendingUp, FileText, MessageSquare, ShieldCheck, ArrowUpRight, History, RefreshCw, AlertCircle, ChevronRight } from 'lucide-vue-next';
import { api } from './api';
const props = defineProps<{ courseId: number; staff: boolean }>();
const emit = defineEmits(['open-version', 'assignments']);
const data = ref<any>(null), error = ref(''), loading = ref(false), query = ref('');
async function load() { loading.value = true; error.value = ''; try { data.value = await api(`/courses/${props.courseId}/progress`); } catch(e: any) { error.value = e.message; } finally { loading.value = false; } }
watch(() => props.courseId, load, { immediate: true });
const entries = computed<any[]>(() => data.value?.entries || []);
const filtered = computed(() => entries.value.filter(e => `${e.title} ${e.author_name} ${e.assignment_title}`.toLowerCase().includes(query.value.toLowerCase())));
const lastGraded = (e: any) => [...e.versions].reverse().find((v: any) => v.q_final != null);
const graded = computed(() => entries.value.map(lastGraded).filter(Boolean));
const average = computed(() => graded.value.length ? (graded.value.reduce((s, v) => s + Number(v.q_final), 0) / graded.value.length).toFixed(1) : '—');
const submissions = computed(() => entries.value.reduce((s, e) => s + e.versions.length, 0));
const feedback = computed(() => entries.value.reduce((s, e) => s + e.feedback_total - e.feedback_resolved, 0));
const dimensions = [{ code: 'S', name: '科学性' }, { code: 'A', name: '准确性' }, { code: 'C', name: '完整性' }, { code: 'L', name: '逻辑性' }];
function date(s: string) { return new Date(s).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }); }
</script>
<template>
  <section class="page progress-page">
    <div class="page-heading"><div><span class="eyebrow">{{ staff ? '课程教学进展' : '每一次修订，都有迹可循' }}</span><h1>{{ staff ? '教学进展' : '学习轨迹' }}</h1><p>从真实提交、教师评价与反馈处理记录，回看知识如何逐步完善。</p></div><button class="btn secondary" :disabled="loading" @click="load"><RefreshCw :size="15" />刷新记录</button></div>
    <div v-if="error" class="global-error" role="alert"><AlertCircle :size="18" />{{ error }}</div>
    <p v-if="loading" class="empty-text">正在读取课程记录…</p>
    <template v-else-if="data">
      <div class="progress-stats"><article><span><FileText :size="17" />{{ staff ? '课程作品' : '已开始作品' }}</span><strong>{{ entries.length }}<small>{{ staff ? "·" : "/" }} {{ data.assignmentCount }} 项作业</small></strong><p>按作品计数，包含未提交草稿</p></article><article><span><History :size="17" />累计提交</span><strong>{{ submissions }}<small>个版本</small></strong><p>每次提交均保留独立记录</p></article><article><span><TrendingUp :size="17" />最近质量均分</span><strong>{{ average }}<small>/100</small></strong><p>取每篇作品最近一次已评分版本</p></article><article><span><MessageSquare :size="17" />未解决反馈</span><strong>{{ feedback }}<small>条</small></strong><p>由教师确认解决状态</p></article></div>
      <div class="section-title progress-list-title"><div><h2>{{ staff ? '作品与版本进展' : '我的作品档案' }}</h2><p class="muted">按作品查看版本、四维质量与反馈处理情况，点击版本可回看当时的内容。</p></div><input v-model="query" aria-label="搜索轨迹作品" placeholder="搜索作品、作业或作者" /></div>
      <article v-for="e in filtered" :key="e.id" class="progress-entry">
        <div class="progress-entry-head"><span class="progress-entry-symbol"><FileText :size="21" /></span><div><h2>{{ e.title }}</h2><p>{{ e.author_name }} · {{ e.assignment_title }}</p></div><span class="grow"></span><div class="progress-latest-score"><strong>{{ lastGraded(e) ? Number(lastGraded(e).q_final).toFixed(1) : '—' }}</strong><span>{{ lastGraded(e) ? '最近已评分版本' : '尚无教师评分' }}</span></div></div>
        <div class="progress-entry-body"><div class="version-journey"><div class="section-label"><History :size="15" />提交轨迹</div><div class="version-journey-nodes"><button v-for="v in e.versions" :key="v.id" @click="emit('open-version', v.id)"><span class="journey-point" :class="{ graded: v.q_final != null }">v{{ v.version_no }}</span><strong>{{ v.q_final == null ? '待评审' : `${Number(v.q_final).toFixed(1)} 分` }}</strong><small>{{ date(v.created_at) }}</small></button><div class="journey-draft"><span class="journey-point">r{{ e.revision }}</span><strong>当前草稿</strong><small>{{ date(e.updated_at) }}</small></div></div></div>
          <div v-if="lastGraded(e)" class="progress-dimension-grid"><div v-for="d in dimensions" :key="d.code"><span>{{ d.name }}</span><strong>{{ lastGraded(e).dimensions[d.code] == null ? '—' : Number(lastGraded(e).dimensions[d.code]).toFixed(1) }}</strong><div class="progress-mini-bar"><i :style="{ width: `${lastGraded(e).dimensions[d.code] || 0}%` }"></i></div></div></div>
          <p v-else class="progress-ungraded">提交并完成教师评审后，将显示四维质量记录。</p>
        </div>
        <div class="progress-entry-foot"><span><MessageSquare :size="14" />已解决 {{ e.feedback_resolved }} / {{ e.feedback_total }} 条反馈</span><span><ShieldCheck :size="14" />{{ e.evidence_passed }} 条历史证据已通过</span><button v-if="e.versions.length" class="text-btn" @click="emit('open-version', e.versions[e.versions.length - 1].id)">查看最近提交<ArrowUpRight :size="14" /></button><button v-else class="text-btn" @click="emit('assignments')">前往作业<ChevronRight :size="14" /></button></div>
      </article>
      <div v-if="!filtered.length" class="empty card"><TrendingUp :size="32" /><h2>{{ query ? '没有匹配的作品' : '轨迹从第一篇知识帖开始' }}</h2><p>{{ query ? '试试其他标题、作者或作业关键词。' : '开始作业并提交后，这里会记录你的每一次修订。' }}</p><button class="btn primary" @click="query ? query = '' : emit('assignments')">{{ query ? '清除搜索' : '查看课程作业' }}</button></div>
      <p class="progress-data-note"><ShieldCheck :size="14" />{{ staff ? '仅本课程教师与助教可查看全班作品进展。' : '本页仅展示你的作品与评价记录。' }}质量分不是最终课程成绩。</p>
    </template>
  </section>
</template>
