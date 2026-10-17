<script setup lang="ts">
import { computed } from 'vue';
import { ArrowUpRight, SlidersHorizontal } from 'lucide-vue-next';
const props = defineProps<{ evaluation: any; version: number; draft?: boolean }>();
defineEmits(['rubric']);
const dims = [{ code: 'S', name: '科学性', detail: '依据、方法与证据匹配' }, { code: 'A', name: '准确性', detail: '事实、数据与表达规范' }, { code: 'C', name: '完整性', detail: '主题覆盖与必要内容' }, { code: 'L', name: '逻辑性', detail: '结构、推理与内容一致' }];
const score = computed(() => props.evaluation?.q_final == null ? null : Number(props.evaluation.q_final));
const scoreText = computed(() => score.value === null ? '—' : score.value.toFixed(1));
</script>
<template>
  <section class="quality-summary">
    <div class="quality-hero">
      <div><span>知识帖综合质量</span><div class="quality-total">{{ scoreText }}<small>/100</small></div><p>已提交 v{{ version }} · 教师确认</p></div>
      <div class="quality-ring" :style="{ '--quality-progress': `${score === null ? 0 : score * 3.6}deg` }"><span>{{ score === null ? '—' : Math.round(score) }}</span></div>
    </div>
    <div class="quality-dimensions">
      <div class="quality-section-title"><h3>四维指标</h3><span>教师评分 · 0–100</span></div>
      <div v-for="(d, i) in dims" :key="d.code" class="quality-bar-row">
        <span :title="d.detail">{{ d.name }}</span><div class="quality-bar"><i :class="{ amber: Number(evaluation.dimensions[d.code]) < 75, mint: i === 3 }" :style="{ width: `${evaluation.dimensions[d.code] == null ? 0 : evaluation.dimensions[d.code]}%` }"></i></div><strong>{{ evaluation.dimensions[d.code] == null ? '—' : Number(evaluation.dimensions[d.code]).toFixed(1) }}</strong>
      </div>
      <p class="quality-explanation">维度内取有效指标平均，四维各占 <strong>25%</strong>。<template v-if="draft">此分数对应已提交版本，<strong>不是当前草稿</strong>。</template><template v-else>本页展示冻结的提交版本。</template></p>
      <p v-if="evaluation.gate_reason" class="error-note">质量门槛：{{ evaluation.gate_reason }}</p>
      <button class="quality-rubric-link" @click="$emit('rubric')"><SlidersHorizontal :size="15" />查看本课量规<ArrowUpRight :size="15" /></button>
    </div>
  </section>
</template>
