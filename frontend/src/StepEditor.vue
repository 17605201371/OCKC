<script setup lang="ts">
import { ref, watch, computed } from "vue";
import Editor from "./Editor.vue";
import { uid } from "./uid";

// 步骤型作业的写作面：每一步一张卡，卡头就是「这一步要做什么」的名字。
// 底层仍是同一份 ProseMirror 文档（标题 + 段落），所以版本对比、批注锚点、
// 导出都不需要改；卡片只是在编辑时把标题显式化成可填写的步骤名。
const props = defineProps<{
  content: any;
  evidence?: any[];
  highlightId?: string;
  annotations?: Array<{ id: number; blockId: string; quote: string; dimension: string }>;
  aiMarks?: Array<{ blockId: string; quote: string; state: string }>;
  entryId?: number;
  evidenceCount?: number;
  scores?: any[];
  feedback?: any[];
}>();
const emit = defineEmits(["update:content", "evidence", "selected", "annotation", "uploading"]);

type Step = { heading: any; title: string; nodes: any[] };
const intro = ref<any[]>([]);
const steps = ref<Step[]>([]);
const stepLevel = ref(2);

function textOf(node: any): string {
  return (node?.content || []).map((x: any) => x.text || "").join("");
}
function parse(doc: any) {
  const nodes = doc?.content || [];
  const levels = nodes.filter((n: any) => n?.type === "heading").map((n: any) => n?.attrs?.level || 1);
  const level = levels.includes(2) ? 2 : levels.length ? Math.min(...levels) : 2;
  stepLevel.value = level;
  const nextIntro: any[] = [];
  const nextSteps: Step[] = [];
  let current: Step | null = null;
  for (const node of nodes) {
    if (node?.type === "heading" && (node?.attrs?.level || 1) === level) {
      current = { heading: node, title: textOf(node), nodes: [] };
      nextSteps.push(current);
    } else if (current) {
      current.nodes.push(node);
    } else {
      nextIntro.push(node);
    }
  }
  intro.value = nextIntro;
  steps.value = nextSteps;
}
function buildDoc() {
  const content = [
    ...intro.value,
    ...steps.value.flatMap((s) => [
      { ...s.heading, attrs: { ...s.heading.attrs, level: stepLevel.value }, content: s.title ? [{ type: "text", text: s.title }] : [] },
      ...s.nodes,
    ]),
  ];
  // 新建的步骤标题不经过编辑器，不会自动获得 blockId；这里统一补齐并去重，
  // 否则服务端会以「段落标识缺失或重复」拒绝保存草稿。
  return { type: "doc", content: sanitize(content) };
}
const BLOCK_TYPES = ["paragraph", "heading", "codeBlock", "figure", "mathBlock"];
function sanitize(nodes: any[], seen = new Set<string>()): any[] {
  return (nodes || []).map((node) => {
    const copy = { ...node };
    if (BLOCK_TYPES.includes(copy.type)) {
      const attrs = { ...(copy.attrs || {}) };
      if (!attrs.blockId || seen.has(attrs.blockId)) attrs.blockId = uid();
      seen.add(attrs.blockId);
      copy.attrs = attrs;
    }
    if (Array.isArray(copy.content)) copy.content = sanitize(copy.content, seen);
    return copy;
  });
}
function jsonOf(doc: any) {
  return JSON.stringify(doc || {});
}
function push() {
  emit("update:content", buildDoc());
}

let lastEmitted = "";
watch(
  () => props.content,
  (value) => {
    if (lastEmitted && jsonOf(value) === lastEmitted) return;
    parse(value);
    lastEmitted = "";
  },
  { immediate: true, deep: true },
);
function commit() {
  const doc = buildDoc();
  lastEmitted = jsonOf(doc);
  emit("update:content", doc);
}
function setTitle(index: number, value: string) {
  steps.value[index].title = value;
  commit();
}
const introVisible = computed(() => {
  const hasText = (nodes: any[]): boolean =>
    (nodes || []).some((n) => (n.text || "").trim() || (n.type && n.type !== "paragraph") || hasText(n.content || []));
  return intro.value.length > 0 && hasText(intro.value);
});
function removeIntro() {
  intro.value = [];
  commit();
}
function setIntro(doc: any) {
  intro.value = doc?.content || [];
  commit();
}
function introDoc() {
  return { type: "doc", content: intro.value };
}
function setBody(index: number, doc: any) {
  steps.value[index].nodes = doc?.content || [];
  commit();
}
function bodyDoc(index: number) {
  return { type: "doc", content: steps.value[index].nodes };
}
function blockIds(nodes: any[], out: string[] = []): string[] {
  for (const n of nodes || []) {
    if (n?.attrs?.blockId) out.push(n.attrs.blockId);
    blockIds(n?.content || [], out);
  }
  return out;
}
function firstBlockId(index: number): string {
  return blockIds(steps.value[index].nodes)[0] || "";
}
function stepScore(index: number) {
  const row = props.scores?.find((x: any) => Number(x.index) === index);
  if (!row || row.score === null || row.score === undefined || row.score === "") return null;
  return { score: Number(row.score), max: Number(row.max) || 10, tags: row.tags || [], comment: row.comment || "" };
}
function feedbackOf(index: number) {
  const ids = new Set(blockIds(steps.value[index].nodes));
  return (props.feedback || []).filter((f: any) => ids.has(f.paragraph_id));
}
function evidenceOf(index: number) {
  const ids = new Set(blockIds(steps.value[index].nodes));
  return (props.evidence || []).filter((e: any) => ids.has(e.paragraph_id));
}
function addStep() {
  steps.value.push({
    heading: { type: "heading", attrs: { level: stepLevel.value }, content: [] },
    title: "",
    nodes: [{ type: "paragraph", attrs: { blockId: uid() }, content: [] }],
  });
  commit();
  requestAnimationFrame(() => {
    const el = document.querySelector<HTMLInputElement>(`#step-name-${steps.value.length - 1}`);
    el?.focus();
  });
}
function removeStep(index: number) {
  if (!confirm(`删除「${steps.value[index].title || "步骤 " + (index + 1)}」这一步？这一步的正文会一起删除。`)) return;
  steps.value.splice(index, 1);
  commit();
}
const totalChars = computed(() => steps.value.reduce((n, s) => n + blockIds(s.nodes).length, 0));
</script>

<template>
  <div class="step-writer">
    <div class="step-writer-head">
      <div>
        <h3>我的作答</h3>
        <p>{{ steps.length }} 步 · 每一步先起个名字，写清这一步要解决什么</p>
      </div>
      <button type="button" class="btn compact" @click="addStep">
        + 添加一步
      </button>
    </div>

    <p v-if="!steps.length" class="step-writer-empty">
      还没有分步。点「添加一步」，给这一步起个名字（例如「先定呼吸速率」），再写这一步的推导。
    </p>

    <section v-if="introVisible" class="step-writer-card intro">
      <div class="step-writer-head-row">
        <span class="step-num">·</span>
        <span class="step-intro-label">未分步的内容</span>
        <span class="grow"></span>
        <button type="button" class="text-btn muted step-remove" @click="removeIntro">删除</button>
      </div>
      <Editor
        :model-value="introDoc()"
        :entry-id="entryId"
        :evidence-count="evidenceCount"
        compact
        @update:model-value="setIntro"
        @selected="emit('selected', $event)"
        @annotation="emit('annotation', $event)"
        @uploading="emit('uploading', $event)"
      />
    </section>

    <section v-for="(step, i) in steps" :id="'step-card-' + i" :key="i" class="step-writer-card">
      <div class="step-writer-head-row">
        <span class="step-num">{{ i + 1 }}</span>
        <input
          :id="'step-name-' + i"
          class="step-name-input"
          :value="step.title"
          maxlength="80"
          placeholder="给这一步命名，例如：先定呼吸速率"
          :aria-label="`步骤 ${i + 1} 名称`"
          @input="setTitle(i, ($event.target as HTMLInputElement).value)"
        />
        <button
          type="button"
          class="text-btn muted step-remove"
          :aria-label="`删除步骤 ${i + 1}`"
          @click="removeStep(i)"
        >
          删除
        </button>
      </div>

      <Editor
        :model-value="bodyDoc(i)"
        :highlight-id="highlightId"
        :annotations="annotations"
        :entry-id="entryId"
        :evidence-count="evidenceCount"
        :ai-marks="aiMarks"
        compact
        @update:model-value="setBody(i, $event)"
        @selected="emit('selected', $event)"
        @annotation="emit('annotation', $event)"
        @uploading="emit('uploading', $event)"
      />

      <div v-if="stepScore(i)" class="step-card-foot">
        <span v-if="stepScore(i)!.tags.length" class="step-tags static">
          <span v-for="t in stepScore(i)!.tags" :key="t" class="step-tag on">{{ t }}</span>
        </span>
        <span class="grow"></span>
        <strong class="step-score-inline">{{ stepScore(i)!.score }} / {{ stepScore(i)!.max }}</strong>
      </div>

      <div v-for="f in feedbackOf(i)" :key="f.id" class="step-anno-block">
        <span class="step-anno-avatar">批</span>
        <p><strong>{{ f.teacher_name || "教师" }}：</strong>{{ f.body }}<em v-if="f.quote_text || f.original_text">「{{ f.quote_text || f.original_text }}」</em></p>
      </div>

      <div class="step-writer-foot">
        <span v-for="e in evidenceOf(i)" :key="e.id" class="step-evidence">
          依据：{{ e.source_title }} <em>· 提交后待核验</em>
        </span>
        <span class="grow"></span>
        <button type="button" class="text-btn" @click="emit('evidence', firstBlockId(i))">
          为这一步挂依据
        </button>
      </div>
    </section>

    <p v-if="steps.length" class="step-writer-note">
      步骤名会出现在批改页与结果页，教师按步给分、标注问题类型。
    </p>
  </div>
</template>
