<script setup lang="ts">
// 阅读页右侧的「质量与版本」：正式评价、步骤分和教师批注。批改页用的是 GradingPanel。
import { ArrowUpRight, ChevronRight, Clock } from "lucide-vue-next";
import QualitySummary from "./QualitySummary.vue";
import { fmt, number } from "./format";
import { scrollToStep } from "./stepGrading";
defineProps<{
  version: any;
  feedbackVisible: boolean;
  readerAsVisitor: boolean;
  priorOpen: any[];
  publishedStepScores: any[];
  publishedStepTotal: number;
  publishedStepMax: number;
}>();
const panelVisible = defineModel<boolean>("panelVisible", { required: true });
const emit = defineEmits<{
  "focus-anchor": [id: string, original: string];
  rubric: [];
}>();
</script>

<template>
<aside v-show="panelVisible" class="feedback-sidebar">
  <div class="panel-title">
    <h2>质量与版本</h2>
    <span class="badge">v{{ version.version_no }}</span>
    <button class="panel-collapse" title="收起反馈面板" aria-label="收起反馈面板" @click="panelVisible = false"><ChevronRight :size="17" /></button>
  </div>
  <template v-if="version.evaluation"><QualitySummary v-if="Object.keys(version.evaluation.dimensions || {}).length" :evaluation="version.evaluation" :version="version.version_no" :dimensions="version.rubricSnapshot?.dimensions" @rubric="emit('rubric')" /><div v-if="publishedStepScores.length" class="step-readonly"><div class="step-readonly-head"><h4>步骤分</h4><span>{{ number(publishedStepTotal) }} / {{ number(publishedStepMax) }}</span></div><div v-for="s in publishedStepScores" :key="s.index" class="step-readonly-row" role="button" tabindex="0" :aria-label="'定位到第 ' + (Number(s.index) + 1) + ' 步'" @click="scrollToStep(Number(s.index))" @keydown.enter="scrollToStep(Number(s.index))"><span class="step-index">{{ Number(s.index) + 1 }}</span><div class="step-readonly-main"><strong>{{ s.title || '步骤 ' + (Number(s.index) + 1) }}</strong><div v-if="s.tags && s.tags.length" class="step-tags static"><span v-for="t in s.tags" :key="t" class="step-tag on">{{ t }}</span></div></div><em>{{ number(Number(s.score) || 0) }} / {{ number(Number(s.max) || 10) }}</em></div></div><p class="teacher-summary">{{ version.evaluation.summary }}</p><p class="quality-teacher">{{ version.evaluation.teacher_name }} · {{ fmt(version.evaluation.created_at) }}</p></template>
  <div v-else class="empty">
    <Clock :size="26" />
    <h3>等待教师评审</h3>
  </div>
  <p v-if="!feedbackVisible" class="feedback-empty">该知识贴未展示反馈信息</p>
  <template v-else-if="!readerAsVisitor">
  <h3 class="sub-panel-title">教师反馈</h3>
  <p v-if="!version.feedback.length" class="feedback-empty">本版暂无段落批注。</p>
  <article
    v-for="f in version.feedback"
    :key="f.id"
    class="feedback-card"
  >
    <span class="indicator-tag">{{ f.indicator_code }}</span>
    <p>{{ f.body }}</p>
    <button
      class="text-btn"
      @click="emit('focus-anchor', f.paragraph_id, f.original_text)"
    >
      定位正文<ArrowUpRight :size="14" />
    </button>
  </article>
  <template v-if="priorOpen.length">
    <h3 class="sub-panel-title">历史未解决批注 · {{ priorOpen.length }}</h3>
    <article
      v-for="f in priorOpen"
      :key="'prior-' + f.id"
      class="feedback-card is-prior"
    >
      <span class="indicator-tag">v{{ f.source_version_no }} 提出</span>
      <p>{{ f.body }}</p>
      <p v-if="f.student_action === 'ADOPT'" class="prior-declared">你已声明修改，等老师确认。</p>
      <button
        v-if="!f.stale"
        class="text-btn"
        @click="emit('focus-anchor', f.paragraph_id, f.original_text)"
      >
        定位正文<ArrowUpRight :size="14" />
      </button>
      <p v-else class="muted">这段正文已改动，原批注仅作提醒。</p>
    </article>
  </template>
  </template>

</aside>
</template>
