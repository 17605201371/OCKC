<script setup lang="ts">
// 学生的创作与修订页。草稿数据、保存与提交逻辑都留在 App（离开页面、权限变化时要统一处理），
// 这里只负责展示和把操作交回 App。
import { ref, computed, defineAsyncComponent } from "vue";
import {
  AlertCircle,
  ArrowLeft,
  ArrowUpRight,
  BookOpen,
  CheckCircle2,
  ChevronRight,
  Clock,
  History,
  ListOrdered,
  ListTree,
  MessageSquare,
  PanelLeftClose,
  Plus,
  Save,
  Send,
  X,
} from "lucide-vue-next";
import QualitySummary from "./QualitySummary.vue";
import StepEditor from "./StepEditor.vue";
import AiFeedback from "./AiFeedback.vue";
import { fmt, number } from "./format";
import { assignmentState } from "./deadlines";
import { anchorOptions, documentHeadings, outlineNumbered } from "./docOutline";
import { dimLabel, actionLabel, studentAction } from "./feedbackLabels";
import { vWorkspaceActions } from "./workspaceActions";
const Editor = defineAsyncComponent(() => import("./Editor.vue"));

const props = defineProps<{
  entry: any;
  latest: any;
  me: any;
  currentAssignment: any;
  deadlineNow: number;
  saving: boolean;
  dirty: boolean;
  savedAt: string;
  busy: boolean;
  conflicted: boolean;
  submitLimit: number;
  submittedCount: number;
  submitExhausted: boolean;
  ruleRefreshError: string;
  backup: any;
  isStepAssignment: boolean;
  selectedAnchor: string;
  highlightFeedbackId: number;
}>();
// 面板开关、版本记录展开、当前段落与上传状态由 App 持有：阅读页和保存逻辑也要用。
const panelVisible = defineModel<boolean>("panelVisible", { required: true });
const outlineVisible = defineModel<boolean>("outlineVisible", { required: true });
const feedbackTab = defineModel<string>("feedbackTab", { required: true });
const selectedParagraph = defineModel<string>("selectedParagraph", { required: true });
const uploadingMedia = defineModel<boolean>("uploadingMedia", { required: true });
const emit = defineEmits<{
  back: [];
  save: [];
  submit: [];
  changed: [];
  title: [value: string];
  content: [doc: any];
  "ai-content": [doc: any];
  "remove-evidence": [index: number];
  "restore-backup": [];
  "dismiss-backup": [];
  "focus-anchor": [id: string, original: string];
  modal: [kind: string, data?: Record<string, any>];
  "open-version": [id: number];
  "jump-feedback": [id: number];
  rubric: [];
}>();

// 标题输入沿用 v-model 的输入法处理：拼音组字结束后才写回草稿。
const title = computed({
  get: () => props.entry.title,
  set: (value: string) => emit("title", value),
});
function setEntryContent(doc: any) { emit("content", doc); }
function focusAnchor(id: string, original = "") { emit("focus-anchor", id, original); }
function showModal(kind: string, data?: Record<string, any>) { emit("modal", kind, data); }
function openStepEvidence(paragraphId: string) { showModal("evidence", paragraphId ? { paragraphId } : {}); }
function openVersion(id: number) { emit("open-version", id); }
function openRubric() { emit("rubric"); }
function jumpToFeedback(id: number) { emit("jump-feedback", id); }

// AI 建议的页边标记（哪一段有几条），由侧栏算好后传给正文。
const aiMarks = ref<Array<{ blockId: string; quote: string; state: string }>>([]);
const aiSidebarVisible = ref(false);
// 点正文页边标记时，让侧栏滚到对应那条建议。
const aiPanel = ref<any>(null);

// 教师终审后帖子进入终态：PASSED 不能再交，REVISION 是打回待改。
const entryPassed = computed(() => props.latest?.entry_state === "PASSED");
const entryRevision = computed(() => props.latest?.entry_state === "REVISION");
// 学生编辑页看的是最新提交版本，上一轮没解决的批注同样要显示，否则改完没处声明。
const draftPrior = computed<any[]>(() => (props.latest?.priorFeedback || []).filter((f: any) => !f.resolved));
// 本版批注 + 上一版遗留未解决的批注都要标在正文上。
// 只取本版的话，学生改完一版就看不到上一版的问题标在哪里了，右侧列表和正文会脱节。
const draftAnnotations = computed(() =>
  [
    ...(props.latest?.feedback || []).filter((f: any) => !f.resolved),
    ...(props.latest?.priorFeedback || []).filter((f: any) => !f.resolved && !f.stale),
  ].map((f: any) => ({
    id: f.id,
    blockId: f.paragraph_id,
    quote: f.quote_text || "",
    dimension: String(f.indicator_code || "C").charAt(0),
  })),
);
const entryOutline = computed(() => outlineNumbered(props.entry?.content));
const activeOutlineId = computed(() => {
  const headings = documentHeadings(props.entry?.content); if (!headings.length) return '';
  let current = headings[0].id, found = false;
  function walk(n: any) { if (found) return; if (n.type === 'heading' && n.attrs?.blockId) current = n.attrs.blockId; if (n.attrs?.blockId === selectedParagraph.value) { found = true; return; } for (const c of n.content || []) walk(c); }
  if (props.entry?.content && selectedParagraph.value) walk(props.entry.content);
  return found ? current : headings[0].id;
});

function sidebarJump(id: string) { const target = document.getElementById(id), panel = target?.closest<HTMLElement>('.feedback-sidebar'); if (target && panel) panel.scrollTo({ top: panel.scrollTop + target.getBoundingClientRect().top - panel.getBoundingClientRect().top - (panel.querySelector<HTMLElement>('.feedback-sticky-header')?.offsetHeight || 0) - 16, behavior: 'smooth' }); }
</script>

<template>
  <section class="editing-page knowledge-workbench">
    <div class="editor-heading" v-workspace-actions>
      <div>
        <button class="text-btn muted" @click="emit('back')">
          <ArrowLeft :size="16" />返回作业
        </button>
        <h1>创作与修订</h1>
        <p>
          {{
            saving
              ? "正在保存…"
              : dirty
                ? "有未保存的修改"
                : `已保存 ${fmt(savedAt)}`
          }}
          · 草稿 r{{ entry.revision }}
        </p>
      </div>
      <div class="button-row"><button class="text-btn history-trigger" @click="panelVisible = true; feedbackTab = 'versions'"><History :size="16" />历史版本</button>
        <button
          v-if="!isStepAssignment"
          class="btn secondary"
          :class="{ pressed: outlineVisible }"
          @click="outlineVisible = !outlineVisible"
        >
          <ListTree :size="16" />写作大纲</button
        ><button
          class="btn secondary"
          :class="{ pressed: panelVisible }"
          @click="panelVisible = !panelVisible"
        >
          <PanelLeftClose :size="16" />评分与反馈</button
        ><button
          class="btn secondary"
          :disabled="saving || busy || uploadingMedia"
          @click="emit('save')"
        >
          <Save :size="17" />保存草稿</button
        ><span
          v-if="submitLimit > 0"
          class="submit-quota"
          :class="{ done: submitExhausted }"
          :title="submitExhausted ? '本作业的提交次数已用完，仍可保存草稿' : '本作业最多可提交 ' + submitLimit + ' 个版本'"
        >已提交 {{ submittedCount }}/{{ submitLimit }}</span
        ><button
          class="btn primary"
          :disabled="saving || busy || conflicted || uploadingMedia || submitExhausted || entryPassed || (currentAssignment && assignmentState(currentAssignment,deadlineNow)==='CLOSED')"
          @click="emit('submit')"
        >
          <Send :size="17" />{{ entryPassed ? '已通过定稿' : submitExhausted ? '提交次数已用完' : (currentAssignment && assignmentState(currentAssignment,deadlineNow)==='CLOSED' ? (currentAssignment?.manually_closed?'作业已结束':'作业已截止') : '提交评审') }}
        </button>
      </div>
    </div>
    <p v-if="ruleRefreshError" class="notice-error" role="alert">{{ ruleRefreshError }}</p>
    <div v-if="currentAssignment && assignmentState(currentAssignment,deadlineNow)!=='OPEN'" class="deadline-banner" :class="assignmentState(currentAssignment,deadlineNow).toLowerCase()"><Clock :size="16" /><span>{{ assignmentState(currentAssignment,deadlineNow)==='CLOSED' ? (currentAssignment.manually_closed?'教师已结束本作业。已有草稿仍可整理和保存，但不能提交新版本。':'本作业已截止。已有草稿仍可整理和保存，但不能提交新版本。') : '本作业将在 24 小时内截止，请及时保存并提交评审。' }}</span></div>
    <div v-if="entryRevision" class="deadline-banner revision"><AlertCircle :size="16" /><span><strong>老师打回了这一稿：</strong>{{ latest?.entry_state_reason || '请按批注继续修订，改完重新提交' }}</span></div>
    <div v-else-if="entryPassed" class="deadline-banner passed"><CheckCircle2 :size="16" /><span>这篇帖子已通过定稿，不能再提交新版本；如需继续修改，请联系老师撤销定稿。</span></div>
    <div v-if="backup" class="backup-banner">
      <AlertCircle :size="18" />发现未同步的本地备份。<button
        class="text-btn"
        @click="emit('restore-backup')"
      >
        恢复到编辑器</button
      ><button class="text-btn muted" @click="emit('dismiss-backup')">
        暂不恢复
      </button>
    </div>
    <div
      class="editor-layout"
      :class="{
        'has-outline': outlineVisible && !isStepAssignment,
        'without-panel': !panelVisible,
      }"
    >
      <button v-if="!panelVisible" class="panel-reopen" title="展开反馈面板" aria-label="展开反馈面板" @click="panelVisible = true"><MessageSquare :size="16" /><span>反馈</span></button>
      <aside v-if="outlineVisible && !isStepAssignment" class="document-outline studio-outline">
        <div class="outline-section-label"><ListTree :size="16" /><h3>写作大纲</h3><span>{{ entryOutline.length }}</span></div>
        <nav aria-label="文章章节">
          <button v-for="a in entryOutline" :key="a.id" :class="{ selected: activeOutlineId === a.id, 'sub-heading': a.level > 1 }" :style="{ paddingLeft: 12 + a.depth * 16 + 'px' }" @click="focusAnchor(a.id)"><span>{{ a.number }}</span><span>{{ a.text }}</span></button>
        </nav>
        <p v-if="!entryOutline.length" class="outline-empty">用 H1 / H2 / H3 组织文章，章节将按层级出现在这里。</p>
        <section class="outline-evidence"><div class="outline-section-label"><BookOpen :size="16" /><h3>挂载证据</h3><span>{{ entry.evidence.length }}</span></div>
          <button v-for="(e, i) in entry.evidence" :key="i" class="outline-source" @click="focusAnchor(e.paragraphId)"><span>E{{ i + 1 }}</span><span>{{ e.sourceTitle }}</span></button>
          <p v-if="!entry.evidence.length" class="outline-empty">让每一个关键论断，都有可靠的来源。</p>
          <button class="outline-add" @click="showModal('evidence')"><Plus :size="15" />挂载新证据</button>
        </section>
        <section class="outline-assignment"><span class="eyebrow">当前任务</span><strong>{{ currentAssignment?.title }}</strong><p><Clock :size="13" />{{ fmt(currentAssignment?.due_at) }} 截止</p><details><summary>查看任务要求</summary><p>{{ currentAssignment?.requirements }}</p></details></section>

      </aside>
      <div class="document-column">
        <div class="document-paper">
          <span class="eyebrow document-label"><span class="draft-dot"></span>{{ isStepAssignment ? '步骤作答草稿' : '知识帖草稿' }} <span class="grow"></span><span>仅你与任课教师可见</span></span
          ><input
            v-model="title"
            maxlength="160"
            class="document-title"
            aria-label="知识帖标题"
            placeholder="为你的解释起个标题"
            @input="emit('changed')"
          />
          <div class="document-meta">
            <span class="avatar small">{{
              me.display_name.slice(0, 1)
            }}</span
            ><span>{{ me.display_name }}</span
            ><span>未提交的修改只保存在草稿中</span>
          </div>
          <section v-if="isStepAssignment && currentAssignment" class="step-task-card">
            <h3>题目</h3>
            <strong>{{ currentAssignment.title }}</strong>
            <p>{{ currentAssignment.requirements }}</p>
            <div class="step-task-meta">
              <span>{{ fmt(currentAssignment.due_at) }} 截止</span>
              <span>步骤由你划分</span>
            </div>
          </section>
          <StepEditor
            v-if="isStepAssignment"
            :content="entry.content"
            :evidence="entry.evidence"
            :highlight-id="selectedAnchor"
            :annotations="draftAnnotations"
            :entry-id="entry.id"
            :evidence-count="entry.evidence.length"
            :scores="latest?.evaluation?.stepScores"
            :feedback="latest?.feedback"
            :ai-marks="aiMarks"
            @update:content="setEntryContent"
            @evidence="openStepEvidence"
            @selected="selectedParagraph = $event"
            @annotation="jumpToFeedback"
            @aimark="aiPanel?.reveal($event)"
            @uploading="uploadingMedia = $event"
          />
          <Editor
            v-else
            :highlight-id="selectedAnchor"
            :annotations="draftAnnotations"
            :document-title="entry.title"
            :entry-id="entry.id" :evidence-count="entry.evidence.length" @uploading="uploadingMedia = $event"
            :ai-marks="aiMarks"
            @evidence="showModal('evidence')"
            :model-value="entry.content"
            @update:model-value="setEntryContent"
            @selected="selectedParagraph = $event"
            @annotation="jumpToFeedback"
            @aimark="aiPanel?.reveal($event)"
          />
        </div>
        <section class="card source-panel">
          <div class="section-title">
            <h3>论断与证据</h3>
            <button class="text-btn" @click="showModal('evidence')">
              <Plus :size="16" />挂载证据
            </button>
          </div>
          <p v-if="!entry.evidence.length" class="muted">
            先选择正文段落，再挂载能够支持该论断的来源。
          </p>
          <div
            v-for="(e, i) in entry.evidence"
            :key="i"
            class="draft-source"
          >
            <div>
              <strong>{{ e.sourceTitle }}</strong>
              <p>{{ e.claim }}</p><p v-if="!anchorOptions(entry.content).some(a => a.id === e.paragraphId)" class="orphan-source-warning">关联内容已删除：请撤销删除或移除此证据。</p>
              <span class="muted"
                >证据 E{{ i + 1 }} · 提交后待核验</span
              >
            </div>
            <button
              class="icon-btn"
              aria-label="移除此证据"
              @click="emit('remove-evidence', i)"
            >
              <X :size="17" />
            </button>
          </div>
        </section>
      </div>
      <aside v-show="panelVisible" class="feedback-sidebar">
        <div class="feedback-sticky-header">
        <div class="panel-title">
          <h2>质量与反馈</h2>
          <span class="badge">{{
            latest ? `最近提交 v${latest.version_no}` : "尚未提交"
          }}</span>
          <button class="panel-collapse" title="收起反馈面板" aria-label="收起反馈面板" @click="panelVisible = false"><ChevronRight :size="17" /></button>
        </div>
        <nav class="feedback-jump-nav" aria-label="反馈分区导航"><button @click="sidebarJump('sidebar-quality')">{{ isStepAssignment ? '步骤评分' : '质量评分' }}</button><button @click="sidebarJump('sidebar-teacher')">教师批注<span>{{ latest?.feedback?.length || 0 }}</span></button><button v-if="aiSidebarVisible" @click="sidebarJump('sidebar-ai')">AI 反馈</button></nav>
        </div>
        <div v-if="!isStepAssignment" id="sidebar-quality"><QualitySummary :evaluation="latest?.evaluation" :version="latest?.version_no" :dimensions="latest?.rubricSnapshot?.dimensions" draft @rubric="openRubric" /></div>
        <section v-else id="sidebar-quality" class="sidebar-section" aria-label="步骤评分">
          <header class="sidebar-section-heading"><h3><ListOrdered :size="15" />步骤评分</h3><span class="small-label">{{ latest?.evaluation?.stepScores?.length || 0 }} 步</span></header>
          <div v-if="latest?.evaluation?.stepScores?.length">
            <div v-for="s in latest.evaluation.stepScores" :key="s.index" class="step-readonly-row">
              <span class="step-index">{{ Number(s.index) + 1 }}</span>
              <div class="step-readonly-main"><strong>{{ s.title || '第 ' + (Number(s.index) + 1) + ' 步' }}</strong><div v-if="s.tags && s.tags.length" class="step-tags static"><span v-for="t in s.tags" :key="t" class="step-tag on">{{ t }}</span></div></div>
              <em>{{ number(Number(s.score) || 0) }} / {{ number(Number(s.max) || 10) }}</em>
            </div>
          </div>
          <p v-else class="muted">提交后由教师按步骤给分，并标注问题类型。</p>
        </section>
        <section v-if="latest?.evaluation?.summary" class="sidebar-section sidebar-summary" aria-label="整体评语">
          <header class="sidebar-section-heading"><h3><MessageSquare :size="15" />整体评语</h3><span class="small-label">{{ latest?.evaluation?.teacher_name || '任课教师' }}</span></header>
          <p class="teacher-summary">{{ latest.evaluation.summary }}</p>
          <p class="sidebar-summary-meta">针对整份作答· 提交 v{{ latest?.version_no }}</p>
        </section>
        <section id="sidebar-teacher" class="sidebar-section teacher-feedback-section" :aria-label="isStepAssignment ? '段落批注' : '教师批注'">
          <header class="sidebar-section-heading"><h3><MessageSquare :size="15" />{{ isStepAssignment ? '段落批注' : '教师批注' }}</h3><span class="small-label">{{ (latest?.feedback?.length || 0) + draftPrior.length }} 条</span></header>
          <article
            v-for="f in latest?.feedback || []"
            :key="f.id"
            :id="'feedback-' + f.id"
            class="feedback-card"
            :class="{ 'is-target': highlightFeedbackId === f.id }"
            @click="focusAnchor(f.paragraph_id, f.original_text)"
          >
            <div class="card-top">
              <span class="indicator-tag">{{ dimLabel(f.indicator_code) }}</span
              ><span
                class="badge"
                :class="f.resolved ? 'green' : studentAction(f) === 'ADOPT' ? 'blue' : 'orange'"
                >{{ f.resolved ? "已解决" : studentAction(f) === "ADOPT" ? "已声明修改" : "待修订" }}</span
              >
            </div>
            <p class="feedback-version-meta">{{ latest?.evaluation?.teacher_name || '任课教师' }} · 提交 v{{ latest?.version_no }}</p>
            <h3 class="feedback-highlight">{{ f.body }}</h3>
            <button
              class="text-btn"
              @click.stop="focusAnchor(f.paragraph_id, f.original_text)"
            >
              定位正文<ArrowUpRight :size="14" />
            </button>
            <blockquote>{{ f.quote_text || f.original_text }}</blockquote>
            <div
              class="feedback-replies"
              v-for="a in f.actions"
              :key="a.id"
            >
              <strong>{{ a.actor_name }}</strong
              ><span>{{ actionLabel(a.action_type) }} · {{ a.body }}</span>
            </div>
            <div class="feedback-controls">
              <button
                class="btn secondary"
                @click="
                  showModal('action', { id: f.id, action: 'REPLY' })
                "
              >
                回应</button
              ><button
                v-if="studentAction(f) !== 'ADOPT'"
                class="text-btn"
                @click="
                  showModal('action', { id: f.id, action: 'ADOPT' })
                "
              >
                我已修改</button
              ><span v-else class="declared-tag">已声明修改 · 待老师确认</span
              ><button
                class="text-btn muted"
                @click="
                  showModal('action', { id: f.id, action: 'IGNORE' })
                "
              >
                忽略
              </button>
            </div>
          </article>
          <!-- 上一版遗留、老师还没确认的批注：学生在新版本里也要看得见，否则改完没处声明。 -->
          <article
            v-for="f in draftPrior"
            :key="'dp-' + f.id"
            class="feedback-card is-prior"
            :class="{ 'is-target': highlightFeedbackId === f.id }"
            @click="!f.stale && focusAnchor(f.paragraph_id, f.original_text)"
          >
            <div class="card-top">
              <span class="indicator-tag">{{ dimLabel(f.indicator_code) }}</span>
              <span class="badge" :class="f.stale ? 'grey' : studentAction(f) === 'ADOPT' ? 'blue' : 'orange'">{{
                f.stale ? "段落已改动" : studentAction(f) === "ADOPT" ? "已声明修改" : "待修订"
              }}</span>
            </div>
            <p class="feedback-version-meta">v{{ f.source_version_no }} 提出 · 尚未确认</p>
            <h3 class="feedback-highlight">{{ f.body }}</h3>
            <blockquote v-if="f.quote_text">{{ f.quote_text }}</blockquote>
            <div class="feedback-controls">
              <button
                v-if="studentAction(f) !== 'ADOPT'"
                class="text-btn"
                @click.stop="showModal('action', { id: f.id, action: 'ADOPT' })"
              >
                我已修改</button
              ><span v-else class="declared-tag">已声明修改 · 待老师确认</span>
            </div>
          </article>
          <div v-if="!latest?.feedback?.length && !draftPrior.length" class="sidebar-empty"><MessageSquare :size="21" /><p>{{ latest ? (isStepAssignment ? '还没有段落批注' : '还没有教师批注') : '提交后，在这里查看教师批注' }}</p><span>{{ isStepAssignment ? '老师可在批改页选中文字添加段落批注；每一步的点评见上方「步骤评分」。' : '批注会定位到对应段落或图片。' }}</span></div>
        </section>
        <AiFeedback ref="aiPanel" :key="entry.id" :entry-id="entry.id" :assignment-enabled="!!currentAssignment?.ai_feedback_enabled" :step-mode="isStepAssignment" :doc="entry.content" @content="emit('ai-content', $event)" @visibility="aiSidebarVisible = $event" @locate="focusAnchor" @marks="aiMarks = $event" />
        <details class="sidebar-version-history" :open="feedbackTab === 'versions'"><summary><History :size="14" />版本记录<span>{{ entry.versions.length }}</span><ChevronRight :size="14" class="history-chevron" /></summary>
        <div class="version-list">
          <button
            v-for="v in entry.versions"
            :key="v.id"
            @click="openVersion(v.id)"
          >
            <span class="version-symbol">v{{ v.version_no }}</span>
            <div>
              <strong>{{
                v.q_final === null
                  ? "等待评审"
                  : `质量分 ${number(v.q_final)}`
              }}</strong>
              <p>{{ fmt(v.created_at) }}</p>
            </div>
            <ChevronRight :size="17" />
          </button>
          <p v-if="!entry.versions.length" class="empty-text">
            还没有提交过版本。
          </p>
        </div>
        </details>

      </aside>
    </div>
    <div class="task-requirements">
      <h3>本次任务要求</h3>
      <p>{{ currentAssignment?.requirements }}</p>
    </div>
  </section>
</template>
