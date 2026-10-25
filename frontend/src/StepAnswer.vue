<script setup lang="ts">
import { computed } from "vue";
import Editor from "./Editor.vue";

// 步骤型作业的作答视图：把正文按标题切成步骤卡，每步带得分、依据与点评。
// 只用现有文档结构切分（二级标题优先），不引入新的内容节点，避免破坏批注锚点与版本对比。
const props = defineProps<{
  content: any;
  evidence?: any[];
  scores?: any[];
  mode?: "read" | "grade";
  highlightId?: string;
  annotations?: Array<{ id: number; blockId: string; quote: string; dimension: string }>;
  canAnnotate?: boolean;
}>();
const emit = defineEmits(["selected", "annotation", "annotate"]);

const STATE_LABEL: Record<string, string> = {
  PASS: "老师已确认",
  APPROVED: "老师已确认",
  REJECT: "未通过",
  REJECTED: "未通过",
  PENDING: "待核验",
  DISPUTED: "有争议",
};

function textOf(node: any): string {
  return (node?.content || []).map((x: any) => x.text || "").join("");
}
function idsOf(nodes: any[], out: string[] = []): string[] {
  for (const n of nodes || []) {
    if (n?.attrs?.blockId) out.push(n.attrs.blockId);
    idsOf(n?.content || [], out);
  }
  return out;
}

const split = computed(() => {
  const nodes = props.content?.content || [];
  const headingLevels = nodes.filter((n: any) => n?.type === "heading").map((n: any) => n?.attrs?.level || 1);
  // 优先用二级标题切步；整篇没有二级标题时退化为实际用到的最高一级标题。
  const stepLevel = headingLevels.includes(2) ? 2 : headingLevels.length ? Math.min(...headingLevels) : 0;
  const intro: any[] = [];
  const steps: Array<{ title: string; nodes: any[] }> = [];
  let current: { title: string; nodes: any[] } | null = null;
  for (const node of nodes) {
    const isStepHeading = stepLevel > 0 && node?.type === "heading" && (node?.attrs?.level || 1) === stepLevel;
    if (isStepHeading) {
      current = { title: textOf(node) || "未命名步骤", nodes: [] };
      steps.push(current);
    } else if (current) {
      current.nodes.push(node);
    } else {
      intro.push(node);
    }
  }
  return { steps, intro, stepLevel };
});

const steps = computed(() => split.value.steps);
const intro = computed(() => split.value.intro);

function docOf(nodes: any[]) {
  return { type: "doc", content: nodes };
}
function scoreOf(index: number) {
  const row = props.scores?.find((s: any) => Number(s.index) === index);
  if (!row || row.score === null || row.score === undefined || row.score === "") return null;
  return { score: Number(row.score), max: Number(row.max) || 10, tags: row.tags || [], comment: row.comment || "" };
}
function evidenceOf(step: { nodes: any[] }) {
  const ids = new Set(idsOf(step.nodes));
  return (props.evidence || []).filter((e: any) => ids.has(e.paragraph_id));
}
function stateLabel(state: string) {
  return STATE_LABEL[state] || "待核验";
}
function scrollTo(index: number) {
  document.getElementById("step-card-" + index)?.scrollIntoView({ behavior: "smooth", block: "center" });
}
defineExpose({ scrollTo });
</script>

<template>
  <article class="step-answer">
    <header class="step-answer-head">
      <h3>我的作答</h3>
      <span>{{ steps.length }} 步 · 步骤由你划分</span>
    </header>

    <section v-if="intro.length" id="step-card-intro" class="step-card intro">
      <Editor
        :model-value="docOf(intro)"
        :highlight-id="highlightId"
        :annotations="annotations"
        :can-annotate="canAnnotate"
        readonly
        @selected="emit('selected', $event)"
        @annotation="emit('annotation', $event)"
        @annotate="emit('annotate', $event)"
      />
    </section>

    <p v-if="!steps.length" class="step-empty">
      这份作答还没有分步。用二级标题写下每一步的名字，系统会自动切成步骤卡。
    </p>

    <section
      v-for="(step, i) in steps"
      :id="'step-card-' + i"
      :key="i"
      class="step-card"
    >
      <div class="step-card-head">
        <span class="step-num">{{ i + 1 }}</span>
        <h4>{{ step.title }}</h4>
        <span v-if="scoreOf(i)" class="step-badge" :class="{ zero: scoreOf(i)!.score === 0 }">
          {{ scoreOf(i)!.score }} / {{ scoreOf(i)!.max }}
        </span>
        <span v-else-if="mode === 'grade'" class="step-badge pending">待给分</span>
      </div>

      <Editor
        v-if="step.nodes.length"
        :model-value="docOf(step.nodes)"
        :highlight-id="highlightId"
        :annotations="annotations"
        :can-annotate="canAnnotate"
        readonly
        @selected="emit('selected', $event)"
        @annotation="emit('annotation', $event)"
        @annotate="emit('annotate', $event)"
      />
      <p v-else class="step-body-empty">这一步还没有正文。</p>

      <div v-if="evidenceOf(step).length || scoreOf(i)?.tags.length" class="step-card-foot">
        <span v-for="e in evidenceOf(step)" :key="e.id" class="step-evidence">
          依据：{{ e.source_title }}
          <em :class="'ev-' + String(e.state).toLowerCase()">· {{ stateLabel(e.state) }}</em>
        </span>
        <span v-if="scoreOf(i)?.tags.length" class="step-tags static">
          <span v-for="t in scoreOf(i)!.tags" :key="t" class="step-tag on">{{ t }}</span>
        </span>
      </div>
      <div v-if="scoreOf(i)?.comment" class="step-comment-block">
        <span class="step-comment-avatar">师</span>
        <p><strong>老师要求：</strong>{{ scoreOf(i)!.comment }}</p>
      </div>
    </section>
  </article>
</template>
