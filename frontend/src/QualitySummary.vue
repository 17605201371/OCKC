<script setup lang="ts">
import { computed, ref } from 'vue';
import { ArrowUpRight, ChevronDown, SlidersHorizontal, ShieldCheck } from 'lucide-vue-next';
const props = defineProps<{ evaluation?: any; version?: number; draft?: boolean }>();
defineEmits(['rubric']);
const expanded = ref('');
const dims = [
  { code: 'S', name: '科学性', items: [['S1','依据可靠性'],['S2','方法合理性'],['S3','证据与结论匹配性']] },
  { code: 'A', name: '准确性', items: [['A1','事实准确性'],['A2','数据准确性'],['A3','表达规范性']] },
  { code: 'C', name: '完整性', items: [['C1','主题覆盖完整性'],['C2','必要内容要素完整性']] },
  { code: 'L', name: '逻辑性', items: [['L1','结构组织合理性'],['L2','论证推理连贯性'],['L3','内容关系一致性']] },
];
const score = computed(() => props.evaluation?.q_final == null ? null : Number(props.evaluation.q_final));
const scoreText = computed(() => score.value === null ? '—' : score.value.toFixed(1));
function dimScore(code: string) { const n = props.evaluation?.dimensions?.[code]; return n == null ? null : Number(n); }
function item(code: string) { return props.evaluation?.details?.find((i: any) => i.code === code); }
function itemText(code: string) { const i = item(code); return !i ? '待评' : i.status === 'NA' ? '不适用' : i.status === 'PENDING' ? '待核验' : i.score == null ? '—' : Number(i.score).toFixed(0); }
</script>
<template>
  <section class="quality-summary" aria-label="质量评分">
    <div class="quality-hero">
      <div><span>综合质量</span><div class="quality-total">{{ scoreText }}<small>/100</small></div><p><ShieldCheck :size="12" />{{ evaluation ? `教师确认 · v${version}` : version ? `v${version} 待教师评审` : '提交后由教师评分' }}</p></div>
      <div class="quality-ring" role="img" :aria-label="score === null ? '尚无正式质量分' : `综合质量 ${scoreText} 分`" :style="{ '--quality-progress': `${score === null ? 0 : Math.min(100, Math.max(0, score)) * 3.6}deg` }"><span>{{ score === null ? '—' : Math.round(score) }}</span></div>
    </div>
    <div class="quality-dimensions">
      <div class="quality-section-title"><h3>四维指标</h3><span>各占 25%</span></div>
      <div v-for="d in dims" :key="d.code" class="quality-dimension">
        <button class="quality-bar-row" :aria-expanded="expanded === d.code" :aria-label="`${d.name}评分明细`" @click="expanded = expanded === d.code ? '' : d.code">
          <span>{{ d.name }}</span><div class="quality-bar"><i :style="{ width: `${Math.min(100, Math.max(0, dimScore(d.code) || 0))}%` }"></i></div><strong>{{ dimScore(d.code) === null ? '—' : dimScore(d.code)!.toFixed(1) }}</strong><ChevronDown :size="13" :class="{ rotated: expanded === d.code }" />
        </button>
        <div v-if="expanded === d.code" class="quality-detail-list">
          <div v-for="[code,name] in d.items" :key="code"><code>{{ code }}</code><span>{{ name }}</span><strong>{{ itemText(code) }}</strong><p v-if="item(code)?.reason">{{ item(code).reason }}</p></div>
        </div>
      </div>
      <p class="quality-explanation">{{ evaluation ? '维度内取有效指标平均，按四维等权计算。' : '四维 11 项指标，分数由教师逐项确认。' }}<template v-if="draft && evaluation">分数对应已提交版本，<strong>不代表当前草稿</strong>。</template></p>
      <p v-if="evaluation?.gate_reason" class="error-note">质量门槛：{{ evaluation.gate_reason }}</p>
      <button class="quality-rubric-link" @click="$emit('rubric')"><SlidersHorizontal :size="14" />查看课程量规<ArrowUpRight :size="14" /></button>
    </div>
  </section>
</template>
