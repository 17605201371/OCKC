<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, defineAsyncComponent, nextTick } from "vue";
import { useTheme } from "./theme";
import { uid } from "./uid";
import EvidenceWorkbench from "./EvidenceWorkbench.vue";
import PublicationManager from "./PublicationManager.vue";
import ReviewWorkbench from "./ReviewWorkbench.vue";
import {defaultReviewQuery,reviewParams,reviewAdvanceBlocked,type ReviewQuery} from "./reviewQuery";
// Measure wrapped toolbars so sticky side panels never sit beneath them.
const actionObservers = new WeakMap<HTMLElement, ResizeObserver>();
const vWorkspaceActions = {
  mounted(el: HTMLElement) {
    const measure = () => el.parentElement?.style.setProperty('--workspace-action-height', Math.ceil(el.getBoundingClientRect().height) + 'px');
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    actionObservers.set(el, observer);
  },
  unmounted(el: HTMLElement) { actionObservers.get(el)?.disconnect(); actionObservers.delete(el); },
};
const reviewFilters=ref<ReviewQuery>(defaultReviewQuery()),reviewNavScope=ref<ReviewQuery|null>(null),reviewQueueSummary=ref<any>(null),nextReviewBusy=ref(false),nextReviewMessage=ref('');
async function openReviewRow(value:any){reviewFilters.value={...value.filters};if(await openVersion(value.id,true,true))reviewNavScope.value={...value.filters};}
async function nextReview(){if(nextReviewBusy.value||busy.value)return;nextReviewBusy.value=true;nextReviewMessage.value='';try{if(reviewAdvanceBlocked(review.state,isTeacher.value)){error.value='请先处理评分草稿的加载、恢复或冲突提示，再切换提交。';return;}if(!await flushBeforeLeave())return;const id=cid.value,uid=me.value?.id,epoch=accessEpoch,sid=version.value?.id,scope={...(reviewNavScope.value||defaultReviewQuery())};const params=reviewParams(scope,true);params.set('excludeId',String(sid));const result=await api(`/courses/${id}/review-workbench/next?${params}`);if(id!==cid.value||uid!==me.value?.id||epoch!==accessEpoch||page.value!=='grading'||version.value?.id!==sid)return;if(!result.item){nextReviewMessage.value='当前队列范围没有其他待评审提交。可返回队列调整筛选。';return;}await openVersion(result.item.id,true,true);reviewNavScope.value=scope;}catch(e:any){error.value=e.message||'无法读取下一份提交，当前评分仍保留';}finally{nextReviewBusy.value=false;}}
const publicationManager=ref<InstanceType<typeof PublicationManager>>();
async function publicationChanged(){const sid=version.value?.id,activeCourse=cid.value,activeUser=me.value?.id,epoch=accessEpoch;await refreshCourse();if(epoch!==accessEpoch||activeCourse!==cid.value||activeUser!==me.value?.id)return;if(sid&&version.value?.id===sid){const v=await api(`/submissions/${sid}`);if(epoch===accessEpoch&&activeCourse===cid.value&&activeUser===me.value?.id&&version.value?.id===sid)version.value=v;}if(epoch===accessEpoch&&activeUser===me.value?.id)notify("课程内发布处理已保存");}
const evidenceFocus=ref<{id:number;nonce:number}>();
async function openEvidence(id:number){if(!await flushBeforeLeave())return;evidenceFocus.value={id,nonce:Date.now()};page.value="evidence";}
async function progressFeedback(d:any){if(await openVersion(d.submissionId,isStaff.value)){await nextTick();await focusAnchor(d.paragraphId);}}
async function evidenceSubmission(d:any){if(await openVersion(d.id,d.review)){await nextTick();await focusAnchor(d.paragraphId);}}

import {
  BookOpen,
  ListOrdered,
  LayoutDashboard,
  ClipboardList,
  Library,
  SlidersHorizontal,
  PanelLeftClose,
  LogOut,
  Settings,
  ArrowUpRight,
  ArrowLeft,
  Plus,
  Check,
  ChevronRight,
  MessageSquare,
  FileText,
  Clock,
  ShieldCheck,
  GraduationCap,
  Search,
  X,
  Send,
  Save,
  History,
  CheckCircle2,
  AlertCircle,
  Users,
  PenLine,
  PanelLeft,
  ListTree,
  Sun,
  Moon,
  Monitor,
  Layers,
  CornerDownLeft,
  TrendingUp,
  Sparkles,
} from "lucide-vue-next";
import HomeDashboard from "./HomeDashboard.vue";
import CommunityPage from "./CommunityPage.vue";
import AssignmentsPage from "./AssignmentsPage.vue";
import { fmt } from "./format";
const Editor = defineAsyncComponent(() => import("./Editor.vue"));
import ProgressPage from "./ProgressPage.vue";
import MembersPage from "./MembersPage.vue";
const accessBackup=ref<any>(null),accessMessage=ref(''),backupDownloadRequired=ref(false);
import QualitySummary from "./QualitySummary.vue";
import StepAnswer from "./StepAnswer.vue";
import StepEditor from "./StepEditor.vue";
import AiFeedback from "./AiFeedback.vue";
import NotificationCenter from "./NotificationCenter.vue";
import AccountSettings from "./AccountSettings.vue";
import RubricWorkbench from "./RubricWorkbench.vue";
const rubricWorkbench=ref<InstanceType<typeof RubricWorkbench>>();
const rubricFocus=ref<any>(null),rubricFocusSource=ref('');
const assignmentRubrics=ref<any[]>([]),assignmentRubricError=ref(''),assignmentRubricLoading=ref(false);
let assignmentRubricGeneration=0;
async function loadAssignmentRubrics(){const g=++assignmentRubricGeneration,activeCid=cid.value,uid=me.value?.id;assignmentRubricLoading.value=true;assignmentRubricError.value='';assignmentRubrics.value=[];try{const data=await api(`/courses/${activeCid}/rubrics`);if(g!==assignmentRubricGeneration||activeCid!==cid.value||uid!==me.value?.id||modal.value!=='assignment')return;assignmentRubrics.value=data.items.filter((r:any)=>r.state==='ENABLED');form.value.rubricId=String(data.defaultId);}catch(e:any){if(g===assignmentRubricGeneration)assignmentRubricError.value=e.message;}finally{if(g===assignmentRubricGeneration)assignmentRubricLoading.value=false;}}
async function openRubric(){const fromVersion=['reader','grading'].includes(page.value),snapshot=fromVersion?version.value?.rubricSnapshot:entry.value?.rubric,source=fromVersion?version.value?.rubricSnapshotSource:'';if(!await flushBeforeLeave())return;rubricFocus.value=snapshot?JSON.parse(JSON.stringify(snapshot)):null;rubricFocusSource.value=source||'';page.value='rubric';}
function gradingCriterion(code:string){return version.value?.rubricSnapshot?.criteria?.find((i:any)=>i.code===code);}

const accountOpen=ref(false);
async function openAccount(){if(await flushBeforeLeave())accountOpen.value=true;}
function profileUpdated(data:any){if(me.value?.id===data.id)me.value={...me.value,...data};}
function sessionExpired(event:Event){sessionEnded((event as CustomEvent).detail);}
function sessionEnded(message="密码已修改，请使用新密码重新登录。本机草稿备份仍保留。"){
 if(!me.value)return;const uid=me.value.id,eid=entry.value?.id,reviewRecovery=review.suspend(),rubricRecovery=rubricWorkbench.value?.sessionBackup();accessChanged(message);
 backupDownloadRequired.value=false;if(accessBackup.value&&eid){try{backupDownloadRequired.value=localStorage.getItem(`community-draft-${uid}-${eid}`)!==JSON.stringify(accessBackup.value);}catch{backupDownloadRequired.value=true;}}
 if(reviewRecovery&&!reviewRecovery.persisted){accessBackup.value={type:'review-draft',submissionId:reviewRecovery.submissionId,...reviewRecovery.data};backupDownloadRequired.value=true;}
 if(rubricRecovery&&!rubricRecovery.persisted){accessBackup.value={type:'rubric-draft',...rubricRecovery.data};backupDownloadRequired.value=true;}
 accountOpen.value=false;me.value=null;courses.value=[];cid.value=0;page.value="home";loginError.value=backupDownloadRequired.value?'登录已失效；本机存储不可用，请先下载未同步草稿，再重新登录。':message;password.value="";resetApiSession();
}
import AssignmentManager from "./AssignmentManager.vue";
const managedAssignment=ref<any>(null);
const ruleRefreshError=ref('');
import { useReviewDraft } from "./useReviewDraft";
import { assignmentState, dueLabel } from "./deadlines";
import { criterionDescription } from "./rubricText";
import { useDwellTime } from "./dwellTime";
const clockNow = ref(Date.now()), serverOffset = ref(0);
let deadlineTimer: ReturnType<typeof setInterval>;
const deadlineNow = computed(() => clockNow.value + serverOffset.value);
const aiSidebarVisible = ref(false);
// AI 建议的页边标记（哪一段有几条），由侧栏算好后传给正文。
const aiMarks = ref<Array<{ blockId: string; quote: string; state: string }>>([]);
// 点正文页边标记时，让侧栏滚到对应那条建议。
const aiPanel = ref<any>(null);
import { api, login, ApiFailure, resetApiSession } from "./api";
const me = ref<any>(null),
  courses = ref<any[]>([]),
  cid = ref(0),
  assignments = ref<any[]>([]),
  queue = ref<any[]>([]),
  publications = ref<any[]>([]),
  pending = ref<any[]>([]),
  evidences = ref<any[]>([]);
const homeEntry = ref<any>(null),
  homeSubmission = ref<any>(null),
  sidebarCollapsed = ref(false),
  outlineVisible = ref(false),
  panelVisible = ref(true),
  commandOpen = ref(false),
  commandQuery = ref(""),
  assignmentFilter = ref("all");
const page = ref("home"),
  loading = ref(true),
  busy = ref(false),
  error = ref(""),
  toast = ref(""),
  menu = ref(false);
const entry = ref<any>(null),
  version = ref<any>(null),
  latest = ref<any>(null),
  feedbackTab = ref("feedback");
const email = ref(""),
  password = ref(""),
  loginError = ref("");
const modal = ref(""),
  form = ref<Record<string, any>>({});
const selectedAnchor = ref(""),
  selectedParagraph = ref(""),
  dirty = ref(false),
  saving = ref(false),
  conflicted = ref(false),
  uploadingMedia = ref(false),
  savedAt = ref(""),
  backup = ref<any>(null);
let saveTimer: ReturnType<typeof setTimeout> | undefined;
let inFlight: Promise<boolean> | null = null;
let toastTimer: ReturnType<typeof setTimeout>;
const rubric = [
  {
    d: "S",
    name: "科学性",
    desc: "论断有依据，方法与结论相称。",
    items: [
      ["S1", "依据可靠性"],
      ["S2", "方法合理性"],
      ["S3", "证据与结论匹配性"],
    ],
  },
  {
    d: "A",
    name: "准确性",
    desc: "事实、数据与表达准确规范。",
    items: [
      ["A1", "事实准确性"],
      ["A2", "数据准确性"],
      ["A3", "表达规范性"],
    ],
  },
  {
    d: "C",
    name: "完整性",
    desc: "覆盖主题，补齐必要内容。",
    items: [
      ["C1", "主题覆盖完整性"],
      ["C2", "必要内容要素完整性"],
    ],
  },
  {
    d: "L",
    name: "逻辑性",
    desc: "结构清楚，推理连贯而一致。",
    items: [
      ["L1", "结构组织合理性"],
      ["L2", "论证推理连贯性"],
      ["L3", "内容关系一致性"],
    ],
  },
];
const allItems = rubric.flatMap((d) =>
  d.items.map(([code, name]) => ({ code, name, dimension: d.d })),
);
// 维度名称与权重以该提交绑定的量规快照为准；历史快照没有维度记录时回退四维等权。
const dims = computed(() =>
  rubric.map((d) => {
    const snap = version.value?.rubricSnapshot?.dimensions;
    const row = Array.isArray(snap)
      ? snap.find((x: any) => x && x.code === d.d)
      : null;
    const weight = Number(row?.weight);
    const name =
      row?.name && String(row.name).trim() ? String(row.name).trim() : d.name;
    return { ...d, name, weight: Number.isFinite(weight) ? weight : 25 };
  }),
);
const grading = ref<any[]>([]),
  summary = ref(""),
  gateReason = ref("");
const course = computed(() => courses.value.find((c) => c.id === cid.value));
const isTeacher = computed(() => course.value?.role === "TEACHER");
// 停留时长：作者在编辑器里写自己的作品记「做题时间」，任课教师在批改页记「批改时间」。
useDwellTime("WRITE", computed(() => page.value === "editor" && me.value && entry.value?.author_id === me.value.id ? entry.value.id : null));
useDwellTime("GRADE", computed(() => page.value === "grading" && isTeacher.value && version.value?.id ? version.value.id : null));
// 开课权限来自账号本身（app_user.is_teacher），不是「当前这门课里是不是教师」。
const canCreateCourse = computed(() => me.value?.is_teacher === true);
const isStaff = computed(() =>
  ["TEACHER", "ASSISTANT"].includes(course.value?.role),
);
function reviewSnapshot() {
  // 步骤型作业只发步骤分，不带 11 项四维，避免后端按四维校验拦下草稿。
  const step = isStepAssignment.value;
  return {
    items: step ? [] : grading.value.map(i => ({code:i.code,score:i.score === '' || i.score == null ? null : Number(i.score),status:i.status,reason:i.reason || ''})),
    summary: summary.value,
    gateReason: gateReason.value,
    stepScores: step ? filledStepScores() : [],
  };
}
function applyReviewData(data:any) { grading.value = allItems.map(i => ({...i,score:data.items?.find((x:any)=>x.code===i.code)?.score ?? null,status:data.items?.find((x:any)=>x.code===i.code)?.status || 'SCORED',reason:data.items?.find((x:any)=>x.code===i.code)?.reason || ''})); summary.value=data.summary || '';gateReason.value=data.gateReason || '';initStepScores(data.stepScores); }
// review 的声明在下方 submitLimit 之后，原因见那里的注释。
const reviewPublishing=ref(false);

// ---- 步骤型作业：二级标题即一步，逐步给分并标注问题类型 ----
// 标签与后端 CommunityService.STEP_TAGS 保持一致，新增要两边同时改。
const STEP_TAGS = ["忽略题设条件", "计算或单位错误", "概念混淆", "论证跳跃", "表述不清"];
const STEP_DEFAULT_MAX = 10;
const stepScores = ref<Array<{ index: number; title: string; score: any; max: number; tags: string[]; comment: string }>>([]);
function stepHeadings(doc: any) {
  const all: { text: string; level: number }[] = [];
  function walk(n: any) {
    if (n?.type === 'heading') all.push({ text: (n.content || []).map((x: any) => x.text || '').join('') || '未命名步骤', level: n.attrs?.level || 1 });
    for (const c of n?.content || []) walk(c);
  }
  if (doc) walk(doc);
  // 优先二级标题；若整篇都用一级或三级，退化为「所有标题」，避免教师面对空面板。
  const h2 = all.filter((h) => h.level === 2);
  return h2.length ? h2 : all;
}
const gradingAssignment = computed(() => assignments.value.find((a) => a.id === version.value?.assignment_id));
// 编辑页看草稿所属作业，批改/阅读页看提交所属作业；历史提交再退到任务快照。
const isStepAssignment = computed(() => {
  const a = version.value ? gradingAssignment.value : currentAssignment.value;
  return (a?.kind || version.value?.assignmentSnapshot?.kind) === "STEP";
});
const gradingSteps = computed(() => (version.value?.content ? stepHeadings(version.value.content) : []));
const editorSteps = computed(() => (entry.value?.content ? stepHeadings(entry.value.content) : []));
const stepTotal = computed(() => stepScores.value.reduce((sum, s) => sum + (Number(s.score) || 0), 0));
const stepMax = computed(() => stepScores.value.reduce((sum, s) => sum + (Number(s.max) || STEP_DEFAULT_MAX), 0));
const scoredStepCount = computed(() => stepScores.value.filter((s) => s.score !== '' && s.score != null).length);
// 右侧只列步骤与分数；标签和点评写在下方卡片里，点哪一步就编辑哪一步。
const activeStep = ref(0);
function selectStep(index: number) {
  activeStep.value = index;
  scrollToStep(index);
}
// 已发布的步骤分（学生与教师都看这一份，来自 evaluation.stepScores）。
const publishedStepScores = computed<any[]>(() => version.value?.evaluation?.stepScores || []);
// 批改时卡片徽标跟随当前填写，阅读时看已发布的步骤分。
const stepScoresForDisplay = computed(() => (page.value === "grading" ? stepScores.value : publishedStepScores.value));
function scrollToStep(index: number) {
  const el = document.getElementById("step-card-" + index);
  if (!el) return;
  el.scrollIntoView({ behavior: "smooth", block: "center" });
  // 右侧点一下，正文里对应的那一步短暂高亮，避免滚过去还找不到。
  el.classList.add("step-card-focus");
  setTimeout(() => el.classList.remove("step-card-focus"), 1400);
}
const publishedStepTotal = computed(() => publishedStepScores.value.reduce((sum, s) => sum + (Number(s.score) || 0), 0));
const publishedStepMax = computed(() => publishedStepScores.value.reduce((sum, s) => sum + (Number(s.max) || STEP_DEFAULT_MAX), 0));
function initStepScores(saved?: any[]) {
  stepScores.value = gradingSteps.value.map((s, i) => {
    const prev = saved?.find((x: any) => Number(x.index) === i);
    return {
      index: i,
      title: s.text,
      score: prev?.score ?? '',
      max: Number(prev?.max ?? STEP_DEFAULT_MAX),
      tags: Array.isArray(prev?.tags) ? [...prev.tags] : [],
      comment: prev?.comment || '',
    };
  });
}
function toggleStepTag(row: any, tag: string) {
  if (!row) return;
  const at = row.tags.indexOf(tag);
  if (at >= 0) row.tags.splice(at, 1);
  else if (row.tags.length < 4) row.tags.push(tag);
  else notify('每步最多标注 4 个问题类型');
}
function filledStepScores() {
  return stepScores.value
    .filter((s) => s.score !== '' && s.score != null)
    .map((s) => ({ index: s.index, title: s.title, score: Number(s.score), max: Number(s.max) || STEP_DEFAULT_MAX, tags: [...s.tags], comment: s.comment || '' }));
}
// 步骤卡写作面把整份文档回传，落盘逻辑与富文本编辑一致，避免两套保存路径。
function setEntryContent(doc: any) {
  if (!entry.value) return;
  entry.value.content = doc;
  changed();
}
function openStepEvidence(paragraphId: string) {
  showModal('evidence', paragraphId ? { paragraphId } : {});
}
async function reloadReview() { await run(async()=>{version.value=await api(`/submissions/${version.value.id}`);applyReviewData({items:version.value.evaluation?.details,summary:version.value.evaluation?.summary,gateReason:version.value.evaluation?.gate_reason});await review.load(version.value.id);}); }
async function restoreReview(data:any) { if(confirm('将这份草稿恢复到当前评分表单？恢复后请重新核对各项分数与总评；不会直接发布正式评价。'))await review.restore(data); }
async function navigateNotice(n:any) {
  if(!await flushBeforeLeave())throw new Error('请先保存或处理当前评分/写作草稿，再打开消息。');
  if(!courses.value.some(c=>c.id===n.course_id))throw new Error('已无法访问这门课程，请刷新工作空间。');
  cid.value=n.course_id;await refreshCourse();
  if(n.target_type==='course'){page.value='home';}
  else if(n.target_type==='assignment'){page.value='assignments';assignmentFilter.value='all';await nextTick();document.querySelector(`[data-assignment-id="${n.target_id}"]`)?.scrollIntoView({behavior:'smooth',block:'center'});}
  else if(n.target_type==='evidence') {
    await openEvidence(n.target_id);
  } else if(n.target_type==='publication'&&isTeacher.value&&pending.value.some(p=>p.submission_id===n.target_id)) {page.value='community';await nextTick();document.querySelector('.comm-approve')?.scrollIntoView({behavior:'smooth'});}
  else {if(!await openVersion(n.target_id,isTeacher.value && ['SUBMISSION','FEEDBACK_REPLY'].includes(n.kind)))throw new Error('无法打开关联提交，请检查当前权限。');if(n.anchor_id){await nextTick();await focusAnchor(n.anchor_id);}}
}
const nav = computed(() => [
  { id: "home", name: "学习工作台", icon: LayoutDashboard },
  {
    id: "assignments",
    name: isStaff.value ? "课程作业" : "我的作业",
    icon: ClipboardList,
  },
  ...(isStaff.value ? [{ id: "review", name: "批改中心", icon: PenLine }] : []),
  { id: "community", name: "知识社区", icon: Library },
  { id: "evidence", name: "证据核验", icon: ShieldCheck },
  { id: "progress", name: isStaff.value ? "教学进展" : "学习轨迹", icon: TrendingUp },
  { id: "rubric", name: "课程量规", icon: SlidersHorizontal },
  ...(isTeacher.value ? [{id:'members',name:'课程成员',icon:Users}] : []),
]);
const navName = computed(
  () =>
    ({ editor: "创作与修订", reader: "知识条目", grading: "教师评审" })[
      page.value
    ] ||
    nav.value.find((x) => x.id === page.value)?.name ||
    "工作台",
);
const currentAssignment = computed(() =>
  assignments.value.find((a) => a.id === entry.value?.assignment_id),
);
// 提交次数上限（0 = 不限）：用于在编辑器里提示剩余版本，并在用尽后禁用提交。
const submitLimit = computed(() => Number(currentAssignment.value?.max_submissions ?? 0));
// review 必须放在这里：useReviewDraft 内部 watch(serialize) 会在 setup 阶段立刻求值一次
// reviewSnapshot，而它读 isStepAssignment → currentAssignment / gradingAssignment。
// 只要这三个 computed 还没初始化就调用，就会撞 TDZ；异常被 Vue 吞掉、watch 注册失败，
// 表现是「改分后不显示未保存、也不自动保存」，评分草稿会静默丢失。
const review = useReviewDraft(reviewSnapshot, applyReviewData, () => page.value === 'grading' && isTeacher.value, () => me.value?.id);
const submittedCount = computed(() => (entry.value?.versions || []).length);
const submitExhausted = computed(
  () => submitLimit.value > 0 && submittedCount.value >= submitLimit.value,
);
// 教师终审后帖子进入终态：PASSED 不能再交，REVISION 是打回待改。
const entryPassed = computed(() => latest.value?.entry_state === "PASSED");
const entryRevision = computed(() => latest.value?.entry_state === "REVISION");
const pendingQueue = computed(() => queue.value.filter((q) => q.q_final === null));
const pendingReviewCount=computed(()=>reviewQueueSummary.value?.pending_count??pendingQueue.value.length);
watch([()=>cid.value,()=>me.value?.id,()=>course.value?.role],()=>{reviewFilters.value=defaultReviewQuery();reviewNavScope.value=null;reviewQueueSummary.value=null;nextReviewMessage.value='';});
const myWork = computed(() => assignments.value.filter((a) => a.entry_id));
// Completion is only an editing aid; server validation and publish guards stay authoritative.
function criterionComplete(i: any) {
  return i.status === 'NA' ? !!i.reason?.trim() : i.status === 'SCORED' &&
    i.score !== null && i.score !== '' && Number.isFinite(Number(i.score)) && Number(i.score) >= 0 && Number(i.score) <= 100;
}
const completedCriteria = computed(() => grading.value.filter(criterionComplete).length);
async function jumpToMissingCriterion() {
  const item = grading.value.find(i => !criterionComplete(i));
  if (!item) return;
  panelVisible.value = true;
  await nextTick();
  const input = document.getElementById('score-' + item.code);
  const dimension = input?.closest('details');
  if (dimension) dimension.open = true;
  await nextTick();
  const target = item.status === 'NA' ? input?.closest('.grading-item')?.querySelector<HTMLInputElement>('input[placeholder="填写不适用理由"]') : item.status === 'PENDING' ? input?.parentElement?.querySelector('select') : input;
  target?.scrollIntoView({block:'nearest'});
  target?.focus({preventScroll:true});
}
const previewScore = computed(() => {
  const scores: Record<string, number> = {};
  for (const d of dims.value) {
    const v = grading.value.filter(
      (i) => i.code.startsWith(d.d) && i.status === "SCORED",
    );
    if (
      !v.length ||
      v.some(
        (i) =>
          i.score === null ||
          i.score === "" ||
          Number(i.score) < 0 ||
          Number(i.score) > 100,
      ) ||
      grading.value.some((i) => i.status === "PENDING")
    )
      return null;
    scores[d.d] = v.reduce((n, i) => n + Number(i.score), 0) / v.length;
  }
  let weighted = 0,
    totalWeight = 0;
  for (const d of dims.value) {
    weighted += scores[d.d] * d.weight;
    totalWeight += d.weight;
  }
  const raw = totalWeight ? weighted / totalWeight : 0;
  return {
    dims: scores,
    raw,
    total: gateReason.value.trim() ? Math.min(raw, 89) : raw,
  };
});

// 打分：先按维度评级（4 档），再按需逐项微调
const LEVELS = [
  { n: "优", v: 95 },
  { n: "良", v: 82 },
  { n: "合格", v: 67 },
  { n: "待改进", v: 50 },
];
const QUICK_NOTES = ["证据链完整", "因果衔接还需补一句", "注意限定语的范围", "图注要写清机制"];
function addNote(t: string) {
  const base = summary.value.trim().replace(/[；;。]\s*$/, "");
  summary.value = base ? `${base}；${t}` : t;
}
function dimLevel(code: string) {
  const items = grading.value.filter(
    (i) =>
      i.code.startsWith(code) &&
      i.status === "SCORED" &&
      i.score !== null &&
      i.score !== "" &&
      Number.isFinite(Number(i.score)),
  );
  if (!items.length) return null;
  const avg = items.reduce((n, i) => n + Number(i.score), 0) / items.length;
  return avg >= 90 ? 95 : avg >= 75 ? 82 : avg >= 60 ? 67 : 50;
}
function setDimLevel(code: string, v: number) {
  for (const i of grading.value) {
    if (!i.code.startsWith(code)) continue;
    if (i.status === "NA") continue; // 已说明的不适用项保持不变
    i.status = "SCORED";
    i.score = v;
  }
}
function dimDone(code: string) {
  const items = grading.value.filter((i) => i.code.startsWith(code));
  return (
    items.length > 0 &&
    items.every((i) =>
      i.status === "SCORED"
        ? i.score !== null && i.score !== "" && Number.isFinite(Number(i.score))
        : i.status === "NA"
          ? !!String(i.reason || "").trim()
          : false,
    )
  );
}
const dimsLeft = computed(
  () => rubric.filter((d: any) => !dimDone(d.d)).length,
);
function number(n: any) {
  return n == null ? "—" : Number(n).toFixed(1);
}
function notify(t: string) {
  toast.value = t;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (toast.value = ""), 4200);
}
async function run(fn: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    await fn();
  } catch (e: any) {
    error.value = e.message || "请求失败";
    if (e instanceof ApiFailure && e.status === 401) me.value = null;
  } finally {
    busy.value = false;
  }
}
async function doLogin() {
  if(backupDownloadRequired.value){password.value="";loginError.value="请先下载未同步草稿备份；当前浏览器无法持久保存，重新登录会切换工作区。";return;}
  loginError.value = "";
  busy.value = true;
  // Do not expose navigation until the authenticated workspace is ready.
  loading.value = true;
  try {
    me.value = await login(email.value, password.value);
    password.value = "";
    await initialize();
  } catch (e: any) {
    me.value = null;
    courses.value = [];
    cid.value = 0;
    loginError.value = e.message;
  } finally {
    busy.value = false;
    loading.value = false;
  }
}
async function initialize() {
  accessBackup.value=null;accessMessage.value='';
  courses.value = await api("/courses");
  cid.value = courses.value[0]?.id || 0;
  if (cid.value) await refreshCourse();
  page.value = "home";
}
let accessEpoch=0;
function accessChanged(message:string){
  accessEpoch++;
  if(page.value==='rubric'){const recovery=rubricWorkbench.value?.sessionBackup();if(recovery){accessBackup.value={type:'rubric-draft',...recovery.data};if(!recovery.persisted)backupDownloadRequired.value=true;}}
  if(page.value==='editor'&&entry.value){const copy={title:entry.value.title,content:entry.value.content,evidence:entry.value.evidence,revision:entry.value.revision};accessBackup.value=copy;try{localStorage.setItem(`community-draft-${me.value.id}-${entry.value.id}`,JSON.stringify(copy));}catch{}}
  clearTimeout(saveTimer);dirty.value=false;entry.value=null;latest.value=null;version.value=null;homeEntry.value=null;homeSubmission.value=null;assignments.value=[];queue.value=[];evidences.value=[];publications.value=[];pending.value=[];managedAssignment.value=null;menu.value=false;modal.value='';commandOpen.value=false;accessMessage.value=message;page.value='access';
}
function downloadAccessBackup(){if(!accessBackup.value)return;const url=URL.createObjectURL(new Blob([JSON.stringify(accessBackup.value,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='knowledge-draft-backup.json';a.click();backupDownloadRequired.value=false;setTimeout(()=>URL.revokeObjectURL(url),1000);}
async function resumeCourses(){if(backupDownloadRequired.value){notify('请先下载未同步草稿备份');return;}await run(async()=>{courses.value=await api('/courses');if(!courses.value.length){accessMessage.value='当前没有可访问的课程，请联系任课教师。';return;}accessBackup.value=null;cid.value=courses.value[0].id;await refreshCourse();page.value='home';});}
let ruleRefreshSequence=0;
async function refreshAssignmentRules(){
  if(!me.value||!cid.value)return;const id=cid.value,uid=me.value.id,oldRole=course.value?.role,g=++ruleRefreshSequence;
  try{const available=await api('/courses');if(g!==ruleRefreshSequence||id!==cid.value||uid!==me.value?.id)return;const active=available.find((c:any)=>c.id===id);courses.value=available;
    if(!active||active.role!==oldRole){accessChanged(!active?'你已不再是当前课程成员，课程内容已收起；已有作品和评价记录不会删除。':'你的课程身份已调整，请重新进入课程以使用新的权限。');return;}
    if(page.value==='access')return;
    const rows=await api(`/courses/${id}/assignments`);if(g!==ruleRefreshSequence||id!==cid.value||uid!==me.value?.id)return;assignments.value=rows;if(rows[0]?.server_now)serverOffset.value=Date.parse(rows[0].server_now)-Date.now();clockNow.value=Date.now();ruleRefreshError.value='';
    if(page.value==='home'&&!isStaff.value){const eid=rows[0]?.entry_id;if(!eid){homeEntry.value=null;homeSubmission.value=null;}else if(homeEntry.value?.id!==eid){const e=await api(`/entries/${eid}`);const v=e.versions[0]?await api(`/submissions/${e.versions[0].id}`):null;if(id===cid.value&&me.value&&page.value==='home'&&assignments.value[0]?.entry_id===eid){homeEntry.value=e;homeSubmission.value=v;}}}
  }catch(e:any){if(g!==ruleRefreshSequence||id!==cid.value||uid!==me.value?.id)return;if(e.status===403)accessChanged('当前课程权限已变化，请重新进入可访问的课程。');else ruleRefreshError.value='暂时无法刷新课程权限与作业规则，请检查网络；所有操作仍以服务器校验为准。';}
}
async function assignmentManaged(){await refreshAssignmentRules();notify('作业管理变更已保存');}
async function refreshCourse() {
  const activeCourse=cid.value,activeUser=me.value?.id,epoch=accessEpoch;
  const [a, p, e, q, n] = await Promise.all([
    api(`/courses/${cid.value}/assignments`),
    api(`/courses/${cid.value}/publications`),
    api(`/courses/${cid.value}/evidence`),
    isStaff.value
      ? api(`/courses/${cid.value}/review-workbench/dashboard`)
      : Promise.resolve([]),
    isTeacher.value
      ? api(`/courses/${cid.value}/publications?pending=true`)
      : Promise.resolve([]),
  ]);
  if(activeCourse!==cid.value||activeUser!==me.value?.id||epoch!==accessEpoch)return;
  assignments.value = a;
  if(a[0]?.server_now) serverOffset.value = Date.parse(a[0].server_now) - Date.now();
  clockNow.value=Date.now();
  publications.value = p;
  evidences.value = e;
  queue.value = Array.isArray(q)?q:q.items;
  reviewQueueSummary.value=Array.isArray(q)?null:q.summary;
  pending.value = n;
  homeEntry.value = null;
  homeSubmission.value = null;
  const current = a[0]?.entry_id ? a[0] : null;
  if (!isStaff.value && current) {
    const e=await api(`/entries/${current.entry_id}`);
    const v=e.versions[0]?await api(`/submissions/${e.versions[0].id}`):null;
    if(activeCourse!==cid.value||activeUser!==me.value?.id||epoch!==accessEpoch)return;
    homeEntry.value=e;homeSubmission.value=v;
  }
}
async function changeCourse(ev: Event) {
  if (!(await flushBeforeLeave())) {
    (ev.target as HTMLSelectElement).value = String(cid.value);
    return;
  }
  cid.value = Number((ev.target as HTMLSelectElement).value);
  await run(async () => {
    await refreshCourse();
    page.value = "home";
  });
}
async function selectCourse(id: number) {
  if (!(await flushBeforeLeave())) return;
  cid.value = id;
  await run(async () => {
    await refreshCourse();
    page.value = "home";
  });
  menu.value = false;
}
const commandItems = computed(() => {
  const items: any[] = [
    ...nav.value.map((n) => ({
      title: n.id === "home" ? "首页" : n.name,
      sub: "页面",
      kind: "page",
      id: n.id,
    })),
    ...courses.value.map((c) => ({
      title: c.title,
      sub: "课程",
      kind: "course",
      id: c.id,
    })),
    ...assignments.value.map((a) => ({
      title: a.title,
      sub: "课程作业",
      kind: "assignment",
      data: a,
    })),
    ...publications.value.map((p) => ({
      title: p.title,
      sub: "知识条目 · " + p.author_name,
      kind: "version",
      id: p.submission_id,
    })),
  ];
  return items
    .filter(
      (i) =>
        !commandQuery.value ||
        (i.title + i.sub)
          .toLowerCase()
          .includes(commandQuery.value.toLowerCase()),
    )
    .slice(0, 16);
});
function openCommand() {
  commandOpen.value = true;
  commandQuery.value = "";
  setTimeout(
    () =>
      document.querySelector<HTMLInputElement>(".command-input input")?.focus(),
    30,
  );
}
async function activateCommand(item: any) {
  commandOpen.value = false;
  if (item.kind === "page") await go(item.id);
  else if (item.kind === "course") await selectCourse(item.id);
  else if (item.kind === "assignment")
    isStaff.value ? await go("review") : await openAssignment(item.data);
  else await openVersion(item.id);
}
const { theme, cycle: cycleAppearance } = useTheme();
function cycleTheme() {
  cycleAppearance();
  notify(({ system: "跟随系统外观", light: "浅色外观", dark: "深色外观" })[theme.value]);
}
watch(page, () => {
  sidebarCollapsed.value = ["editor", "grading", "reader"].includes(page.value);
  // 知识社区是只读的成品面，右栏多数时候没有要看的东西，进去先收起来，需要时再点开。
  // 编辑页与批改页的右栏是干活的入口，保持展开。
  panelVisible.value = page.value !== "reader";
  outlineVisible.value = ["editor", "reader", "grading"].includes(page.value);
});
function dimensionScore(key: string) {
  const items = grading.value.filter(
    (i) =>
      i.code.startsWith(key) &&
      i.status === "SCORED" &&
      i.score !== null &&
      i.score !== "" &&
      Number(i.score) >= 0 &&
      Number(i.score) <= 100,
  );
  return items.length
    ? number(items.reduce((n, i) => n + Number(i.score), 0) / items.length)
    : "—";
}
async function go(target: string) {
  if (!(await flushBeforeLeave())) return;
  if(target==="evidence")evidenceFocus.value=undefined;
  if(target==='rubric'){rubricFocus.value=null;rubricFocusSource.value='';}
  page.value = target;
  menu.value = false;
  error.value = "";
  if (cid.value) await run(refreshCourse);
  window.scrollTo(0, 0);
}
async function flushBeforeLeave() {
  if(page.value==="rubric"&&rubricWorkbench.value&&!rubricWorkbench.value.canLeave())return false;
  if (page.value === "grading" && isTeacher.value && !await review.flush()) { error.value = review.state.error || "评分草稿尚未保存，请先处理保存失败或冲突。"; return false; }
  if (uploadingMedia.value) { notify("图片上传中，请稍候再离开"); return false; }
  if (page.value === "editor" && dirty.value) return await saveDraft();
  return true;
}
async function logout() {
  if (!(await flushBeforeLeave())) return;
  await run(async () => {
    await api("/auth/logout", "POST");
    review.suspend();resetApiSession();accessEpoch++;
    me.value = null;
    accessBackup.value=null;accessMessage.value='';
    courses.value = [];
    toast.value = "";
    page.value = "home";
  });
}
// 自由创作：直接新建知识帖，不挂在作业下；提交后仍走教师审核与正式评价。
// 标题不再用弹窗先问一遍——编辑器顶部就是标题输入框，先建再改，少一步打断。
const UNTITLED = "未命名知识帖";
async function newFreePost() {
  await run(async () => {
    if (!(await flushBeforeLeave())) return;
    const epoch = accessEpoch, uid = me.value?.id, courseId = cid.value;
    const e = await api(`/courses/${courseId}/free-entries`, "POST", {
      title: UNTITLED,
    });
    if (epoch !== accessEpoch || uid !== me.value?.id || courseId !== cid.value)
      return;
    entry.value = e;
    await loadEditorState();
    if (entry.value?.id !== e.id) return;
    page.value = "editor";
    menu.value = false;
    await refreshCourse();
    // 默认标题全选，直接打字即可替换，不用先手动清空。
    await nextTick();
    const box = document.querySelector<HTMLInputElement>(".document-title");
    if (box && box.value === UNTITLED) {
      box.focus();
      box.select();
    }
  });
}
async function openAssignment(a: any) {
  await run(async () => {
    if (!(await flushBeforeLeave())) return;
    const epoch=accessEpoch,uid=me.value?.id,courseId=cid.value;
    const e=await api(`/assignments/${a.id}/entries`, "POST");
    if(epoch!==accessEpoch||uid!==me.value?.id||courseId!==cid.value)return;
    entry.value=e;
    await loadEditorState();
    if(epoch!==accessEpoch||uid!==me.value?.id||entry.value?.id!==e.id)return;
    page.value = "editor";
    menu.value = false;
  });
}
async function loadEditorState() {
  if(!entry.value)return;const activeEntry=entry.value,uid=me.value?.id,epoch=accessEpoch;
  dirty.value = false;
  conflicted.value = false;
  selectedAnchor.value = "";
  selectedParagraph.value = "";
  savedAt.value = entry.value.updated_at;
  const v=activeEntry.versions.length?await api(`/submissions/${activeEntry.versions[0].id}`):null;
  if(epoch!==accessEpoch||uid!==me.value?.id||entry.value?.id!==activeEntry.id)return;
  latest.value=v;
  try {
    backup.value = JSON.parse(
      localStorage.getItem(
        `community-draft-${me.value.id}-${entry.value.id}`,
      ) || "null",
    );
  } catch {
    backup.value = null;
  }
  if (
    backup.value &&
    JSON.stringify(backup.value.content) ===
      JSON.stringify(entry.value.content) &&
    backup.value.title === entry.value.title &&
    JSON.stringify(backup.value.evidence) ===
      JSON.stringify(entry.value.evidence)
  )
    backup.value = null;
}
// AI 建议的「采纳」直接改正文：子组件算好新文档再回传，这里只负责落到 entry 上并触发自动保存。
function applyAiContent(doc: any) {
  if (!entry.value || !doc) return;
  entry.value.content = doc;
  changed();
}
function changed() {
  if (!entry.value) return;
  dirty.value = true;
  const data = {
    title: entry.value.title,
    content: entry.value.content,
    evidence: entry.value.evidence,
    revision: entry.value.revision,
  };
  try {
    localStorage.setItem(
      `community-draft-${me.value.id}-${entry.value.id}`,
      JSON.stringify(data),
    );
  } catch {
    error.value = "浏览器本地备份空间不足，请及时保存至服务器";
  }
  clearTimeout(saveTimer);
  if (!conflicted.value) saveTimer = setTimeout(() => void saveDraft(), 1600);
}
async function saveDraft(): Promise<boolean> {
  if (inFlight) {
    await inFlight;
    if (!dirty.value) return true;
  }
  if (!dirty.value || !entry.value) return true;
  if (entry.value.evidence.some((ev: any) => !anchorOptions(entry.value.content).some(a => a.id === ev.paragraphId))) {
    error.value = "有证据关联的正文已删除，请撤销删除，或在「论断与证据」中移除失效证据后再保存。";
    return false;
  }
  if (conflicted.value) {
    error.value = "存在并发冲突。请复制正文后重新加载，避免覆盖其他修改。";
    return false;
  }
  const snapshot = JSON.stringify({
    title: entry.value.title,
    content: entry.value.content,
    evidence: entry.value.evidence,
  });
  const data = { ...JSON.parse(snapshot), revision: entry.value.revision };
  const activeEntry=entry.value.id,activeUser=me.value.id,epoch=accessEpoch;
  saving.value = true;
  inFlight = (async () => {
    try {
      const saved = await api(`/entries/${activeEntry}/draft`, "PUT", data);
      if(entry.value?.id!==activeEntry||me.value?.id!==activeUser||epoch!==accessEpoch)return false;
      if (error.value.startsWith('有证据关联的正文已删除')) error.value = '';
      entry.value.revision = saved.revision;
      entry.value.updated_at = saved.updated_at;
      savedAt.value = saved.updated_at;
      if (
        snapshot ===
        JSON.stringify({
          title: entry.value.title,
          content: entry.value.content,
          evidence: entry.value.evidence,
        })
      ) {
        dirty.value = false;
        localStorage.removeItem(
          `community-draft-${me.value.id}-${entry.value.id}`,
        );
      }
      return true;
    } catch (e: any) {
      if(epoch!==accessEpoch||activeUser!==me.value?.id)return false;
      error.value = e.message;
      if (e.status === 409) conflicted.value = true;
      if (e.status === 403) void refreshAssignmentRules();
      return false;
    } finally {
      saving.value = false;
      inFlight = null;
    }
  })();
  return inFlight;
}
// 保存草稿后默认回到作业列表，避免停在编辑页误以为已经提交。
async function saveDraftAndReturn() {
  if (!(await saveDraft())) return;
  notify("草稿已保存");
  await go("assignments");
}
function restoreBackup() {
  if (!backup.value) return;
  entry.value.title = backup.value.title;
  entry.value.content = backup.value.content;
  entry.value.evidence = backup.value.evidence;
  backup.value = null;
  changed();
  notify("已恢复本地备份，等待保存");
}
async function submit() {
  if (uploadingMedia.value) { notify("请等待图片上传完成"); return; }
  await run(async () => {
    for (let i = 0; i < 5 && dirty.value; i++) {
      if (!(await saveDraft())) return;
    }
    if (dirty.value) throw new Error("正文仍有变化，请停止编辑后重试");
    if (
      !confirm(
        "提交将冻结当前正文与证据，之后的修改保存在新草稿中。确认提交评审？",
      )
    )
      return;
    if(!entry.value||page.value!=='editor')return;const activeEntry=entry.value.id,activeUser=me.value.id,epoch=accessEpoch;
    const storageKey = `community-submit-${activeUser}-${activeEntry}`;
    let operation: any = null;
    try {
      operation = JSON.parse(localStorage.getItem(storageKey) || "null");
    } catch {}
    if (!operation || operation.revision !== entry.value.revision)
      operation = { revision: entry.value.revision, key: uid() };
    localStorage.setItem(storageKey, JSON.stringify(operation));
    const k = operation.key;
    const v = await api(
      `/entries/${entry.value.id}/submissions`,
      "POST",
      { revision: entry.value.revision },
      k,
    );
    if(entry.value?.id!==activeEntry||me.value?.id!==activeUser||epoch!==accessEpoch)return;
    localStorage.removeItem(storageKey);
    const refreshed=await api(`/entries/${activeEntry}`);
    if(entry.value?.id!==activeEntry||me.value?.id!==activeUser||epoch!==accessEpoch)return;entry.value=refreshed;
    await loadEditorState();
    await refreshCourse();
    notify(`已提交 v${v.version_no}，等待教师评审`);
    // 提交成功后默认回到作业列表。
    await go("assignments");
  });
}
async function openVersion(sid: number, asReview = false, keepReviewScope=false) {
  if (!(await flushBeforeLeave())) return;
  let succeeded=false;
  await run(async () => {
    const activeCourse=cid.value,activeUser=me.value?.id,epoch=accessEpoch;
    const loadedVersion=await api(`/submissions/${sid}`);
    if(activeCourse!==cid.value||activeUser!==me.value?.id||epoch!==accessEpoch)return;
    version.value=loadedVersion;if(!keepReviewScope)reviewNavScope.value=null;nextReviewMessage.value='';
    void loadVersionHistory(loadedVersion.entry_id);
    selectedAnchor.value = "";
    page.value = asReview ? "grading" : "reader";
    feedbackTab.value = "feedback";
    if (asReview) {
      const details = version.value.evaluation?.details;
      grading.value = allItems.map((i) => ({
        ...i,
        score: details?.find((x: any) => x.code === i.code)?.score ?? null,
        status:
          details?.find((x: any) => x.code === i.code)?.status || "SCORED",
        reason: details?.find((x: any) => x.code === i.code)?.reason || "",
      }));
      summary.value = version.value.evaluation?.summary || "";
      gateReason.value = version.value.evaluation?.gate_reason || "";
      initStepScores(version.value.evaluation?.stepScores);
      if(isTeacher.value) await review.load(sid);
    }
    window.scrollTo(0, 0);succeeded=true;
  });
  return succeeded;
}
function anchorOptions(doc: any) {
  const out: { id: string; text: string }[] = [];
  function walk(n: any) {
    if (n.attrs?.blockId) {
      const texts = (x: any): string =>
        (x.text || "") + (x.content || []).map(texts).join("");
      out.push({
        id: n.attrs.blockId,
        text: (n.type === "figure" ? "[图片] " + (n.attrs.caption || n.attrs.alt || "未添加图注") : n.type === "mathBlock" ? "[公式] " + n.attrs.latex : texts(n)).slice(0, 60) || "空段落",
      });
    }
    for (const c of n.content || []) walk(c);
  }
  if (doc) walk(doc);
  return out;
}
function documentHeadings(doc: any) {
  const out: { id: string; text: string; level: number }[] = [];
  function walk(n: any) {
    if (n.type === 'heading' && n.attrs?.blockId) out.push({ id: n.attrs.blockId, text: (n.content || []).map((x: any) => x.text || '').join('') || '未命名章节', level: n.attrs.level || 1 });
    for (const c of n.content || []) walk(c);
  }
  if (doc) walk(doc);
  return out;
}
const activeOutlineId = computed(() => {
  const headings = documentHeadings(entry.value?.content); if (!headings.length) return '';
  let current = headings[0].id, found = false;
  function walk(n: any) { if (found) return; if (n.type === 'heading' && n.attrs?.blockId) current = n.attrs.blockId; if (n.attrs?.blockId === selectedParagraph.value) { found = true; return; } for (const c of n.content || []) walk(c); }
  if (entry.value?.content && selectedParagraph.value) walk(entry.value.content);
  return found ? current : headings[0].id;
});
async function focusAnchor(id: string, original = "") {
  const epoch=accessEpoch,activePage=page.value,activeId=page.value==='editor'?entry.value?.id:version.value?.id;
  const current=()=>epoch===accessEpoch&&page.value===activePage&&(page.value==='editor'?entry.value?.id:version.value?.id)===activeId;
  const find=()=>[...document.querySelectorAll<HTMLElement>("[data-block-id]")].find(x=>x.dataset.blockId===id);
  await nextTick();let el=find();
  const doc=page.value==='editor'?entry.value?.content:version.value?.content;
  if(!el&&anchorOptions(doc).some(a=>a.id===id)){
    await new Promise<void>(resolve=>{let done=false;const finish=()=>{if(done)return;done=true;observer.disconnect();clearTimeout(timer);resolve();};const observer=new MutationObserver(()=>{if(!current()||find())finish();});const timer=setTimeout(finish,3000);observer.observe(document.body,{childList:true,subtree:true});if(find()||!current())finish();});el=find();
  }
  if(!current())return false;
  if(el){selectedAnchor.value=id;if(error.value.startsWith('原段落已删除或拆分'))error.value='';await nextTick();el.scrollIntoView({behavior:'smooth',block:'center'});return true;}
  error.value=`原段落已删除或拆分，请对照历史原文：${original||id}`;return false;
}

// ---- 四维批注：正文选中 → 就地浮层 ----
const DIMENSIONS = [
  { code: "S", name: "科学性" },
  { code: "A", name: "准确性" },
  { code: "C", name: "完整性" },
  { code: "L", name: "逻辑性" },
];
const DIM_LABELS: Record<string, string> = {
  S: "科学性", A: "准确性", C: "完整性", L: "逻辑性",
  S1: "科学性", S2: "科学性", S3: "科学性",
  A1: "准确性", A2: "准确性", A3: "准确性",
  C1: "完整性", C2: "完整性",
  L1: "逻辑性", L2: "逻辑性", L3: "逻辑性",
};
function dimLabel(code: string) { return DIM_LABELS[code] || code || "批注"; }

const highlightFeedbackId = ref(0);
// 修订历史：批注跟着版本走，而不是跟着「这篇帖子」走。
// 读者只看得到发布过的版本，草稿版本由后端裁掉；作者与教师看全部。
const versionHistory = ref<any[]>([]);
const historyBusy = ref(false);
async function loadVersionHistory(eid: number) {
  versionHistory.value = [];
  if (!eid) return;
  historyBusy.value = true;
  try { versionHistory.value = await api(`/entries/${eid}/versions`); }
  catch { versionHistory.value = []; }
  finally { historyBusy.value = false; }
}
// 更早版本的批注分两组：未解决的要跟进，已解决的只留档（误点还能重开）。
const priorOpen = computed<any[]>(() => (version.value?.priorFeedback || []).filter((f: any) => !f.resolved));
const priorResolved = computed<any[]>(() => (version.value?.priorFeedback || []).filter((f: any) => f.resolved));
// 学生编辑页看的是最新提交版本，上一轮没解决的批注同样要显示，否则改完没处声明。
const draftPrior = computed<any[]>(() => (latest.value?.priorFeedback || []).filter((f: any) => !f.resolved));
const priorBusy = ref(0);
// 学生最近一次处理：ADOPT 表示「我已修改」，只是声明，不等于问题已解决。
// priorFeedback 由后端直接带 student_action；本版批注只有 actions 列表，回退到列表里取最后一条。
const FEEDBACK_ACTION_LABELS: Record<string, string> = { REPLY: "回复", ADOPT: "已采纳", IGNORE: "未采纳", RESOLVE: "已解决", REOPEN: "重新打开" };
function actionLabel(type: string) { return FEEDBACK_ACTION_LABELS[type] || type; }
function studentAction(f: any) {
  if (f.student_action) return f.student_action;
  const acts = (f.actions || []).filter((a: any) => ["ADOPT", "REPLY", "IGNORE"].includes(a.action_type));
  return acts.length ? acts[acts.length - 1].action_type : "";
}
// 「已解决」只有教师能确认：学生的 ADOPT 只是声明，AI 建议的采纳更不参与这个状态。
// 所以徽标把「学生说了什么」和「段落有没有变」都摆出来，教师看一眼就能决定要不要关掉。
function priorState(f: any) {
  const changed = !!f.stale;
  if (f.student_action === "ADOPT")
    return changed ? { text: "学生已改 · 段落有变动", cls: "blue" } : { text: "学生已声明修改", cls: "blue" };
  if (f.student_action === "REPLY") return { text: "学生已回应", cls: "blue" };
  if (f.student_action === "IGNORE") return { text: "学生未采纳", cls: "grey" };
  return changed ? { text: "段落已改动", cls: "grey" } : { text: "未解决", cls: "orange" };
}
async function resolvePrior(f: any, reopen = false) {
  if (priorBusy.value) return;
  priorBusy.value = f.id;
  try {
    await api(`/feedback/${f.id}/actions`, "POST", { action: reopen ? "REOPEN" : "RESOLVE", body: "" });
    version.value = await api(`/submissions/${version.value.id}`);
    notify(reopen ? "已重新打开" : "已标记为已解决");
  } catch (e: any) {
    error.value = e.message || "操作失败，请重试";
  } finally {
    priorBusy.value = 0;
  }
}
// 本版批注 + 上一版遗留未解决的批注一起高亮：学生改过的段落若还留着旧批注，批改时看得见。
// 正文里的高亮统一走这里：本版批注 + 上一版遗留未解决的批注。
// 学生打开自己的稿子时，老师标过的地方应当直接带底色，而不是等他去右侧点一遍。
// 社区读者不在其列：批注是对某一版说的过程意见，不是成品的一部分，后端已按角色裁过数据，
// 这里再挡一层，避免空数组之外的任何情况把高亮漏出来。
const feedbackVisible = computed(() => version.value?.feedbackVisible !== false);
// 知识社区是公开阅读面：教师在这里也只是读者，批注留在批改中心看，避免同一篇在两处长得不一样。
const readerAsVisitor = computed(() => page.value === "reader" && isTeacher.value);
const versionAnnotations = computed(() =>
  feedbackVisible.value && !readerAsVisitor.value
    ? [
        ...(version.value?.feedback || []).filter((f: any) => !f.resolved),
        ...priorOpen.value.filter((f: any) => !f.stale),
      ].map((f: any) => ({
        id: f.id,
        blockId: f.paragraph_id,
        quote: f.quote_text || "",
        dimension: String(f.indicator_code || "C").charAt(0),
      }))
    : [],
);
// 学生编辑页：本版批注 + 上一版遗留未解决的批注都要标在正文上。
// 只取本版的话，学生改完一版就看不到上一版的问题标在哪里了，右侧列表和正文会脱节。
const draftAnnotations = computed(() =>
  [
    ...(latest.value?.feedback || []).filter((f: any) => !f.resolved),
    ...(latest.value?.priorFeedback || []).filter((f: any) => !f.resolved && !f.stale),
  ].map((f: any) => ({
    id: f.id,
    blockId: f.paragraph_id,
    quote: f.quote_text || "",
    dimension: String(f.indicator_code || "C").charAt(0),
  })),
);
async function jumpToFeedback(id: number) {
  highlightFeedbackId.value = id;
  // 面板可能是收起状态，批注列表也可能折叠在 details 里：先展开再定位，否则元素没有布局、滚动无效。
  panelVisible.value = true;
  await nextTick();
  const el = document.getElementById("feedback-" + id);
  if (!el) return;
  let opened = false;
  for (let node = el.parentElement; node; node = node.parentElement) {
    if (node instanceof HTMLDetailsElement && !node.open) { node.open = true; opened = true; }
  }
  if (opened) await nextTick();
  // 等一帧，让展开后的布局完成再滚动。
  await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
  el.scrollIntoView({ behavior: "smooth", block: "center" });
  setTimeout(() => { if (highlightFeedbackId.value === id) highlightFeedbackId.value = 0; }, 2400);
}

const annotation = ref<{ blockId: string; quote: string; label: string; kind: "text" | "figure"; left: number; top: number } | null>(null);
const annotationBody = ref("");
const annotationDim = ref("C");
const annotationBusy = ref(false);
const annotationError = ref("");
// 切换页面时收起批注浮层：否则它会留在创作页面上，看起来像「填了却没保存」。
watch(page, () => { annotation.value = null; annotationError.value = ""; });
function clearAnnotation() { annotation.value = null; annotationBody.value = ""; annotationError.value = ""; }
function onOutsideMouseDown(e: MouseEvent) {
  if (!annotation.value) return;
  const t = e.target as HTMLElement | null;
  if (t?.closest?.(".annotation-popover")) return;
  clearAnnotation();
}
onMounted(() => document.addEventListener("mousedown", onOutsideMouseDown, true));
onBeforeUnmount(() => document.removeEventListener("mousedown", onOutsideMouseDown, true));
function captureSelection() {
  if (page.value !== "grading" || !isTeacher.value) return;
  const sel = window.getSelection();
  if (!sel || sel.isCollapsed) return;
  const text = sel.toString().trim();
  if (!text || text.length > 600) return;
  const range = sel.getRangeAt(0);
  const node = range.startContainer;
  const el = (node.nodeType === 1 ? node : node.parentElement) as HTMLElement | null;
  const block = el?.closest("[data-block-id]") as HTMLElement | null;
  if (!block?.dataset.blockId) return;
  const rect = range.getBoundingClientRect();
  const below = rect.bottom + 10;
  const top = below + 320 > innerHeight ? Math.max(70, rect.top - 330) : below;
  // 浮层不能压到右侧评分栏，否则「添加批注」按钮会被面板盖住，看起来像加了却没保存。
  const col = (document.querySelector(".document-column") || document.querySelector(".document-paper"))?.getBoundingClientRect();
  const limit = col ? Math.min(col.right, innerWidth) : innerWidth;
  annotation.value = {
    blockId: block.dataset.blockId,
    quote: text,
    label: text,
    kind: "text",
    left: Math.max(12, Math.min(rect.left, limit - 352)),
    top,
  };
  annotationError.value = "";
}
// 图片块没有可选中文本，改为在图片右下角提供入口，浮层贴着图片出现。
function annotateFigure(payload: { blockId: string; label: string }) {
  if (page.value !== "grading" || !isTeacher.value) return;
  if (!payload?.blockId) return;
  const el = document.querySelector(`[data-block-id="${payload.blockId}"]`) as HTMLElement | null;
  const rect = el?.getBoundingClientRect();
  const boxW = 320, boxH = 300;
  // 以正文栏右边界为限，避免浮层压住右侧评分面板。
  const col = (document.querySelector(".document-column") as HTMLElement | null)?.getBoundingClientRect();
  const limit = col ? Math.min(col.right, innerWidth) : innerWidth;
  let left = rect ? rect.right + 12 : innerWidth / 2 - boxW / 2;
  let top = rect ? rect.top : 120;
  if (left + boxW > limit - 8) left = Math.max(12, (rect?.left ?? 12) - boxW - 12);
  if (top + boxH > innerHeight - 12) top = Math.max(72, innerHeight - boxH - 12);
  annotation.value = {
    blockId: payload.blockId,
    quote: payload.label && payload.label !== "图片" ? payload.label : "",
    label: payload.label || "图片",
    kind: "figure",
    left,
    top,
  };
  annotationDim.value = "C";
  annotationBody.value = "";
  annotationError.value = "";
}
async function submitAnnotation() {
  if (!annotation.value || !version.value) return;
  const body = annotationBody.value.trim();
  if (!body) { annotationError.value = "请填写批注内容"; return; }
  annotationBusy.value = true; annotationError.value = "";
  try {
    await api(`/submissions/${version.value.id}/feedback`, "POST", {
      paragraphId: annotation.value.blockId,
      indicator: annotationDim.value,
      body,
      quote: annotation.value.quote,
    });
    version.value = await api(`/submissions/${version.value.id}`);
    clearAnnotation();
    window.getSelection()?.removeAllRanges();
    notify("批注已添加，学生可在正文高亮处看到");
  } catch (e: any) {
    annotationError.value = e?.message || "批注保存失败";
  } finally { annotationBusy.value = false; }
}

// 大纲按 H1/H2/H3 生成 1 / 1.1 / 1.1.1 层级编号，避免长文里只剩一串连续序号。
function outlineNumbered(doc: any) {
  const heads = documentHeadings(doc);
  if (!heads.length) return [];
  const base = Math.min(...heads.map((h) => h.level || 1));
  const counters = [0, 0, 0];
  return heads.map((h) => {
    const depth = Math.min(Math.max((h.level || 1) - base, 0), 2);
    counters[depth] += 1;
    for (let i = depth + 1; i < 3; i++) counters[i] = 0;
    const parts = counters.slice(0, depth + 1).map((n) => n || 1);
    // 正文里已经手写了「2.1 xxx」时，大纲不再重复一次编号。
    const text = String(h.text || "").replace(/^\d+(?:\.\d+)*[\s、.．]*/, "") || h.text;
    return { ...h, text, number: parts.join("."), depth };
  });
}
const entryOutline = computed(() => outlineNumbered(entry.value?.content));
// 阅读与批改页的大纲再细一层：标题下列出它覆盖的段落，长文才有可点的粒度。
// 只走顶层块，表格单元格与图注不进大纲。
function outlineDetailed(doc: any) {
  const heads = outlineNumbered(doc);
  const byId = new Map(heads.map((h) => [h.id, h]));
  const out: any[] = [];
  let depth = 0;
  for (const n of doc?.content || []) {
    if (n.type === "heading" && n.attrs?.blockId) {
      const h = byId.get(n.attrs.blockId);
      if (!h) continue;
      out.push({ ...h, kind: "heading" });
      depth = h.depth;
    } else if (n.type === "paragraph" && n.attrs?.blockId) {
      const text = (n.content || []).map((x: any) => x.text || "").join("").trim();
      if (text.length < 12) continue;
      out.push({
        id: n.attrs.blockId,
        text: text.length > 20 ? text.slice(0, 20) + "…" : text,
        number: "",
        depth: Math.min(depth + 1, 2),
        kind: "para",
      });
    }
  }
  return out;
}
const versionOutline = computed(() => outlineDetailed(version.value?.content));

function showModal(kind: string, data: Record<string, any> = {}) {
  modal.value = kind;
  form.value = {
    title: "",
    requirements: "",
    dueAt: "",
    aiFeedbackEnabled: false,
    rubricId: "",
    kind: "POST",
    evidencePolicy: "DISPUTED",
    sourceTitle: "",
    sourceUrl: "",
    excerpt: "",
    claim: "",
    paragraphId:
      (page.value === "editor"
        ? selectedParagraph.value || selectedAnchor.value
        : selectedAnchor.value) ||
      anchorOptions(
        page.value === "editor" ? entry.value?.content : version.value?.content,
      )[0]?.id ||
      "",
    indicator: "C2",
    body: "",
    verdict: "PASS",
    email: "",
    role: "STUDENT",
    ...data,
  };
  if(kind==='assignment')void loadAssignmentRubrics();
  setTimeout(
    () =>
      document
        .querySelector<HTMLInputElement>(
          ".modal input,.modal textarea,.modal select",
        )
        ?.focus(),
    30,
  );
}
async function toggleAssignmentAi(a: any) {
  await run(async () => {
    const updated = await api(`/assignments/${a.id}/ai-policy`, 'PUT', { enabled: !a.ai_feedback_enabled });
    assignments.value = assignments.value.map(row => row.id === a.id ? { ...row, ...updated } : row);
    notify(updated.ai_feedback_enabled ? '已开放 AI 反馈' : '已关闭 AI 反馈');
  });
}
function sidebarJump(id: string) { const target = document.getElementById(id), panel = target?.closest<HTMLElement>('.feedback-sidebar'); if (target && panel) panel.scrollTo({ top: panel.scrollTop + target.getBoundingClientRect().top - panel.getBoundingClientRect().top - (panel.querySelector<HTMLElement>('.feedback-sticky-header')?.offsetHeight || 0) - 16, behavior: 'smooth' }); }
function closeModal() {
  if (!busy.value) modal.value = "";
}
async function modalSubmit() {
  await run(async () => {
    const f = form.value;
    if (modal.value === "assignment") {
      if(assignmentRubricLoading.value||assignmentRubricError.value||!assignmentRubrics.value.some(r=>String(r.id)===f.rubricId))throw new Error("请先读取并选择可用量规版本");
      const created=await api(`/courses/${cid.value}/assignments`, "POST", {
        title: f.title,
        requirements: f.requirements,
        dueAt: new Date(f.dueAt).toISOString(),
        aiFeedbackEnabled: f.aiFeedbackEnabled,
        rubricId:Number(f.rubricId),
        kind: f.kind,
        evidencePolicy: f.evidencePolicy,
      });
      await refreshCourse();
      notify(f.kind === "STEP" ? `步骤型作业已创建：学生用二级标题划分步骤` : `知识帖作业已创建`);
    }
    if (modal.value === "course") {
      // 新建课程会切换当前课程，等同离开当前页面：先把编辑器里未保存的内容落盘。
      if (!(await flushBeforeLeave())) return;
      const c = await api("/courses", "POST", {
        title: f.title,
        description: f.requirements,
      });
      courses.value = await api("/courses");
      cid.value = c.id;
      await refreshCourse();
      // 从「还没有课程」的入口页建课成功后，直接进入新课程首页。
      accessMessage.value = "";
      accessBackup.value = null;
      page.value = "home";
      notify("课程已创建");
    }
    if (modal.value === "member") {
      await api(`/courses/${cid.value}/members`, "POST", {
        email: f.email,
        role: f.role,
      });
      notify("已添加已有账号到课程");
    }
    if (modal.value === "evidence") {
      entry.value.evidence.push({
        paragraphId: f.paragraphId,
        sourceTitle: f.sourceTitle,
        sourceUrl: f.sourceUrl,
        excerpt: f.excerpt,
        claim: f.claim,
      });
      changed();
      notify("证据已加入草稿；提交后可由他人核验");
    }
    if (modal.value === "feedback") {
      await api(`/submissions/${version.value.id}/feedback`, "POST", {
        paragraphId: f.paragraphId,
        indicator: f.indicator,
        body: f.body,
      });
      version.value = await api(`/submissions/${version.value.id}`);
      notify("批注已记录，学生可查看");
    }
    if (modal.value === "action") {
      await api(`/feedback/${f.id}/actions`, "POST", {
        action: f.action,
        body: f.body,
      });
      if (page.value === "editor")
        latest.value = await api(`/submissions/${latest.value.id}`);
      else version.value = await api(`/submissions/${version.value.id}`);
      // 同一条接口承担五种动作，提示要跟着动作走，不能拿学生的文案回给老师。
      const done: Record<string, string> = {
        ADOPT: "已记录你的修改声明",
        REPLY: "回应已记录",
        IGNORE: "已记录不采纳的原因",
        RESOLVE: "已标记为已解决",
        REOPEN: "已重新打开",
      };
      notify(done[f.action] || "已记录");
    }
    if (modal.value === "verify") {
      await api(`/evidence/${f.id}/verifications`, "POST", {
        verdict: f.verdict,
        reason: f.body,
      });
      await refreshCourse();
      if (version.value)
        version.value = await api(`/submissions/${version.value.id}`);
      notify("核验结果已记录");
    }
    if (modal.value === "verdict") {
      if (f.action === "RETURN" && !String(f.body || "").trim())
        throw new Error("打回修订要写清原因，学生才知道改什么");
      await api(`/entries/${version.value.entry_id}/verdict`, "POST", {
        action: f.action,
        reason: f.body || "",
      });
      version.value = await api(`/submissions/${version.value.id}`);
      await refreshCourse();
      notify(
        f.action === "PASS"
          ? "已通过定稿，这篇帖子结束"
          : f.action === "RETURN"
            ? "已打回，学生需要继续修订"
            : "已撤销定稿，学生可以继续提交",
      );
    }
    modal.value = "";
  });
}
async function publishGrade() {
  await run(async () => {
    if(!review.state.loaded || review.state.stale || review.state.backup || review.state.conflict)throw new Error('请先处理评分草稿的加载、恢复或冲突提示。');
    const stepMode = isStepAssignment.value;
    if (stepMode) {
      if (!gradingSteps.value.length) throw new Error("这份提交没有步骤，无法按步骤发分；请让作者用步骤卡作答");
      const missing = stepScores.value.filter((s) => s.score === "" || s.score == null).length;
      if (missing) throw new Error(`还有 ${missing} 步没有给分；步骤分填满后才能发布`);
    } else if (!previewScore.value) {
      throw new Error("请填写完整的四维评分，待核验项不得发布");
    }
    if (!summary.value.trim()) throw new Error("请填写总评与下一步建议");
    if (
      !confirm(
        "确认发布正式评价？学生将立即看到结果；复评会保留历史并撤回该版社区发布。",
      )
    )
      return;
    reviewPublishing.value=true;
    try {
    if(!await review.flush())throw new Error(review.state.error || '请先保存评分草稿');
    await api(`/submissions/${version.value.id}/evaluations`, "POST", {
      items: isStepAssignment.value
        ? []
        : grading.value.map((i) => ({
            code: i.code,
            score: i.status === "SCORED" ? Number(i.score) : null,
            status: i.status,
            reason: i.reason,
          })),
      summary: summary.value,
      gateReason: gateReason.value,
      expectedRevision: review.state.baseRevision,
      stepScores: isStepAssignment.value ? filledStepScores() : [],
    });
    review.published();
    version.value = await api(`/submissions/${version.value.id}`);
    await review.load(version.value.id);
    await refreshCourse();
    notify("正式评价已发布并留存历史记录");
    // 发布后默认回到批改队列，继续处理下一份。
    await go("review");
    } finally { reviewPublishing.value=false; }
  });
}
async function publicationRequest() {await publicationManager.value?.request(version.value.id,version.value.publication[0],version.value.title);}
async function pubAction(id:number,action:string){await publicationManager.value?.open(id,action);}
async function viewVerification(ev: any) {
  await run(async () => {
    const records = await api(`/evidence/${ev.id}/verifications`);
    showModal("records", { records, sourceTitle: ev.source_title });
  });
}
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value || uploadingMedia.value || review.state.dirty || review.state.saving) {
    e.preventDefault();
    e.returnValue = "";
  }
}
function keydown(e: KeyboardEvent) {
  if (e.defaultPrevented || document.querySelector("dialog[open],.notification-overlay")) return;
  if (e.key === "Tab" && (modal.value || commandOpen.value)) {
    const dialog = document.querySelector<HTMLElement>(
      commandOpen.value ? ".command-dialog" : ".modal",
    );
    const controls = [
      ...(dialog?.querySelectorAll<HTMLElement>(
        'button,input,select,textarea,a[href],[tabindex="0"]',
      ) || []),
    ].filter(
      (el) => el.getClientRects().length && !(el as HTMLButtonElement).disabled,
    );
    if (controls.length) {
      const first = controls[0],
        last = controls[controls.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
  }
  if (e.key === "Escape") {
    closeModal();
    menu.value = false;
    commandOpen.value = false;
  }
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k" && me.value) {
    e.preventDefault();
    openCommand();
  }
  if ((e.ctrlKey || e.metaKey) && e.key === "s" && page.value === "editor") {
    e.preventDefault();
    void saveDraft();
  }
}
// 右侧反馈面板：高度按「当前视口 − 面板顶部」收缩，未吸顶时也不会被视口裁掉；
// 面板自身滚动并阻止滚动链，滚轮划过反馈区不会再带动左侧正文。
let sidebarFitFrame = 0, sidebarFitValue = 0, sidebarFitEl: HTMLElement | null = null;
function fitFeedbackSidebar() {
  const el = document.querySelector<HTMLElement>(".feedback-sidebar");
  if (!el) return;
  const stickyTop = 68;
  const top = el.getBoundingClientRect().top;
  const anchor = Math.max(stickyTop, Math.min(top, window.innerHeight - 280));
  const avail = Math.max(280, Math.round(window.innerHeight - anchor - 20));
  if (avail !== sidebarFitValue || el !== sidebarFitEl) {
    sidebarFitValue = avail;
    sidebarFitEl = el;
    el.style.maxHeight = avail + "px";
  }
}
function scheduleSidebarFit() {
  if (sidebarFitFrame) return;
  sidebarFitFrame = requestAnimationFrame(() => {
    sidebarFitFrame = 0;
    fitFeedbackSidebar();
  });
}
watch(
  () => [page.value, panelVisible.value, version.value?.id, entry.value?.id],
  () => nextTick(scheduleSidebarFit),
);
onMounted(async () => {
  window.addEventListener("scroll", scheduleSidebarFit, { passive: true });
  window.addEventListener("resize", scheduleSidebarFit);
  window.addEventListener("ockc-session-expired",sessionExpired);
  deadlineTimer=setInterval(()=>{clockNow.value=Date.now();void refreshAssignmentRules()},30000);
  window.addEventListener("focus",refreshAssignmentRules);
  window.addEventListener("beforeunload", beforeUnload);
  window.addEventListener("keydown", keydown);
  try {
    me.value = await api("/auth/me");
    await initialize();
  } catch (e: any) {
    if (e.status !== 401) error.value = e.message;
  } finally {
    loading.value = false;
  }
});
onBeforeUnmount(() => {
  window.removeEventListener("scroll", scheduleSidebarFit);
  window.removeEventListener("resize", scheduleSidebarFit);
  window.removeEventListener("ockc-session-expired",sessionExpired);
  clearTimeout(saveTimer);clearInterval(deadlineTimer);
  window.removeEventListener("beforeunload", beforeUnload);
  window.removeEventListener("keydown", keydown);
  window.removeEventListener("focus",refreshAssignmentRules);
});
</script>

<template>
  <AccountSettings v-if="accountOpen && me" :key="me.id" @close="accountOpen=false" @updated="profileUpdated" @password-changed="sessionEnded()" />
  <AssignmentManager v-if="managedAssignment && isTeacher" :assignment="managedAssignment" @close="managedAssignment=null" @changed="assignmentManaged" />
  <div v-if="loading" class="boot">
    <BookOpen :size="30" />
    <p>正在连接课程工作空间…</p>
  </div>
  <div v-else-if="!me" class="login-layout">
    <section class="login-story">
      <div class="brand">
        <span class="brand-icon">课</span>
        <div class="brand-copy">
          <strong>课程开放知识社区</strong><span>面向课程的知识共建空间</span>
        </div>
      </div>
      <div class="login-copy">
        <h1>让知识，<br />在每一次修订中生长。</h1>
        <p>
          从一篇知识帖开始，连接可靠的证据、<br />可执行的反馈与持续改进的学习。
        </p>
        <div class="login-principles">
          <span><CheckCircle2 :size="18" />四维评价</span
          ><span><History :size="18" />版本可追溯</span
          ><span><ShieldCheck :size="18" />证据可核验</span>
        </div>
      </div>
    </section>
    <section class="login-form">
      <form @submit.prevent="doLogin">
        <span class="eyebrow">欢迎回来</span>
        <h2>进入你的课程空间</h2>
        <p class="muted">使用课程账号登录，继续未完成的探索。</p>
        <label
          >账号邮箱<input
            v-model="email"
            type="email"
            autocomplete="username"
            required
            placeholder="你的课程账号邮箱" /></label
        ><label
          >密码<input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
            placeholder="输入密码"
        /></label>
        <p v-if="loginError" class="error-note" role="alert">
          {{ loginError }}
        </p>
        <button v-if="accessBackup" type="button" class="btn secondary full" @click="downloadAccessBackup"><Save :size="16"/>下载未同步草稿备份</button>
        <button class="btn primary full" :disabled="busy">
          {{ busy ? "正在登录…" : "登录工作空间" }}<ArrowUpRight :size="18" />
        </button>
      </form>
    </section>
  </div>
  <div v-else class="app-layout">
    <div v-if="menu" class="sidebar-scrim" @click="menu = false"></div>
    <aside class="sidebar" :class="{ open: menu, collapsed: sidebarCollapsed }">
      <a class="brand" href="#" @click.prevent="go('home')"
        ><span class="brand-icon">课</span>
        <div class="brand-copy">
          <strong>课程开放知识社区</strong><span>创作 · 反馈 · 知识共建</span>
        </div></a
      >
      <nav class="primary-nav" aria-label="主导航">
        <button
          v-for="n in nav"
          :key="n.id"
          :aria-label="n.name"
          :title="n.id === 'home' ? '首页' : n.name"
          :class="{
            selected:
              page === n.id ||
              (n.id === 'assignments' && page === 'editor') ||
              (n.id === 'community' && page === 'reader') ||
              (n.id === 'review' && page === 'grading'),
          }"
          @click="go(n.id)"
        >
          <component
            :is="n.id === 'community' ? Layers : n.icon"
            :size="18"
          /><span>{{ n.id === "home" ? "首页" : n.name }}</span
          ><small v-if="n.id === 'review' && pendingReviewCount">{{
            pendingReviewCount
          }}</small>
        </button>
      </nav>
      <div class="workspace-caption">
        <span>{{ isStaff ? "我教的课程" : "我的课程" }}</span>
        <span class="caption-actions">
          <span>{{ courses.length }} 门</span>
          <button
            v-if="canCreateCourse"
            class="course-add"
            type="button"
            title="新建课程（你将成为该课教师）"
            aria-label="新建课程"
            @click="showModal('course')"
          >
            <Plus :size="13" />
          </button>
        </span>
      </div>
      <nav class="course-nav" aria-label="课程列表">
        <button
          v-for="(c, i) in courses"
          :key="c.id"
          :title="c.title"
          :aria-label="'打开课程 ' + c.title"
          :class="{ current: c.id === cid }"
          @click="selectCourse(c.id)"
        >
          <span class="course-dot" :class="'course-tone-' + (i % 3)"></span
          ><span class="course-initial">{{ c.title.slice(0, 1) }}</span
          ><span>{{ c.title }}</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <div class="account">
          <span class="avatar">{{ me.display_name.slice(0, 1) }}</span>
          <div>
            <strong>{{ me.display_name }}</strong
            ><span>{{
              isTeacher
                ? "任课教师"
                : course?.role === "ASSISTANT"
                  ? "课程助教"
                  : course
                    ? "课程学生"
                    : canCreateCourse
                      ? "教师账号"
                      : "学生账号"
            }}</span>
          </div>
          <button class="icon-btn" title="账号设置" aria-label="打开账号设置" @click="openAccount"><Settings :size="17" /></button>
          <button
            class="icon-btn"
            title="退出登录"
            aria-label="退出登录"
            @click="logout"
          >
            <LogOut :size="17" />
          </button>
        </div>
      </div>
    </aside>
    <main class="main" :class="{ expanded: sidebarCollapsed, 'creation-workspace': ['editor', 'reader', 'grading'].includes(page) }">
      <header class="topbar">
        <button
          class="icon-btn nav-toggle"
          :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
          @click="sidebarCollapsed = !sidebarCollapsed"
        >
          <PanelLeft :size="18" /></button
        ><button
          v-if="page !== 'home'"
          class="header-back btn secondary"
          aria-label="返回上一级"
          @click="
            go(
              page === 'editor'
                ? 'assignments'
                : page === 'grading'
                  ? 'review'
                  : page === 'reader'
                    ? 'community'
                    : 'home',
            )
          "
        >
          <ArrowLeft :size="14" />返回</button
        ><span class="crumb"
          ><span v-if="page !== 'home'"
            >{{ course?.title || "课程空间" }} <span class="crumb-sep">/</span></span
          ><strong>{{ page === "home" ? "首页" : navName }}</strong></span
        ><span class="grow"></span
        ><button
          class="command-trigger"
          aria-label="搜索页面、作业或知识条目"
          @click="openCommand"
        >
          <Search :size="16" /><span>搜索页面、作业、条目</span><kbd>⌘K</kbd>
        </button>
        <div class="top-actions">
          <NotificationCenter :key="me.id" :user-id="me.id" :navigate="navigateNotice" />
          <span class="privacy"><ShieldCheck :size="15" />课程内可见</span
          ><span class="role-label">{{
            isTeacher ? "教师" : isStaff ? "助教" : "学生"
          }}</span
          ><button
            class="icon-btn theme-toggle"
            :aria-label="`切换页面外观（当前${theme === 'system' ? '跟随系统' : theme === 'light' ? '浅色' : '深色'}）`"
            :title="
              theme === 'system'
                ? '跟随系统：点击切换浅色'
                : theme === 'light'
                  ? '浅色：点击切换深色'
                  : '深色：点击跟随系统'
            "
            @click="cycleTheme"
          >
            <component
              :is="
                theme === 'system' ? Monitor : theme === 'light' ? Sun : Moon
              "
              :size="18"
            />
          </button>
        </div>
      </header>
      <div v-if="error" class="global-error" role="alert">
        <AlertCircle :size="18" /><span>{{ error }}</span
        ><button aria-label="关闭错误提示" @click="error = ''">
          <X :size="17" />
        </button>
      </div>
      <section v-if="!cid" class="page empty">
        <BookOpen :size="36" />
        <h1>还没有可访问的课程</h1>
        <p v-if="canCreateCourse">
          可以自己开一门课（你将成为该课教师），或请任课教师把你的账号加入课程。
        </p>
        <p v-else>请联系任课教师将你的账号加入课程。</p>
        <div v-if="canCreateCourse" class="button-row">
          <button class="btn primary" @click="showModal('course')">
            <Plus :size="16" />创建我的第一门课程
          </button>
        </div>
      </section>
      <template v-else>
        <HomeDashboard
          v-if="page === 'home'"
          :me="me"
          :course="course"
          :courses="courses"
          :assignments="assignments"
          :queue="queue"
          :queue-summary="reviewQueueSummary"
          :publications="publications"
          :evidences="evidences"
          :staff="isStaff"
          :now="deadlineNow"
          :teacher="isTeacher"
          :home-entry="homeEntry"
          :home-submission="homeSubmission"
          @navigate="go"
          @open-assignment="openAssignment"
          @open-version="openVersion"
          @publish-assignment="showModal('assignment')"
          @select-course="selectCourse"
        />

        <MembersPage v-else-if="page==='members' && isTeacher" :key="cid" :course-id="cid" @changed="refreshAssignmentRules" />
        <section v-else-if="page==='access'" class="page access-changed-page"><div class="card"><ShieldCheck :size="30"/><h1>课程权限已变化</h1><p>{{ accessMessage }}</p><p v-if="accessBackup">刚才编辑的内容已保留在本机，可先下载备份。重新加入课程后，可在原作品中核对恢复。</p><div class="button-row"><button v-if="accessBackup" class="btn secondary" @click="downloadAccessBackup"><Save :size="16"/>下载本机草稿备份</button><button class="btn primary" @click="resumeCourses">重新进入可用课程</button></div></div></section>
        <AssignmentsPage
          v-else-if="page === 'assignments'"
          v-model:filter="assignmentFilter"
          :assignments="assignments"
          :is-staff="isStaff"
          :is-teacher="isTeacher"
          :can-create-course="canCreateCourse"
          :busy="busy"
          :deadline-now="deadlineNow"
          @go="go"
          @show-modal="showModal"
          @new-free-post="newFreePost"
          @toggle-ai="toggleAssignmentAi"
          @manage="managedAssignment = { ...$event }"
          @open-assignment="openAssignment"
        />

        <RubricWorkbench v-else-if="page === 'rubric'" ref="rubricWorkbench" :key="cid+'-'+me.id+'-'+course?.role+'-'+(rubricFocus?'snapshot-'+rubricFocus.code:'catalog')" :course-id="cid" :teacher="isTeacher" :user-id="me.id" :initial-snapshot="rubricFocus" :initial-source="rubricFocusSource" />

        <ProgressPage v-else-if="page === 'progress'" :key="cid+'-'+me.id+'-'+course?.role" :course-id="cid" :course-label="course?.title||''" :staff="isStaff" :user-id="me.id" @open-version="openVersion($event, isStaff)" @open-feedback="progressFeedback" @assignments="go('assignments')" />

        <ReviewWorkbench v-else-if="page === 'review' && isStaff" :key="cid+'-'+me.id+'-'+course?.role" :course-id="cid" :teacher="isTeacher" :course-label="course.title" :filters="reviewFilters" :busy="busy" @filters="reviewFilters=$event" @open="openReviewRow" />

        <CommunityPage
          v-else-if="page === 'community'"
          :key="cid + '-' + me.id"
          :course-id="cid"
          :me-id="me.id"
          :is-teacher="isTeacher"
          :is-staff="isStaff"
          :busy="busy"
          :publications="publications"
          :pending="pending"
          :pending-review-count="pendingReviewCount"
          :my-work-count="myWork.length"
          @open-version="openVersion($event)"
          @pub-action="pubAction"
          @new-free-post="newFreePost"
          @go="go"
          @notify="notify"
        />

        <EvidenceWorkbench v-else-if="page==='evidence'" :key="cid" :course-id="cid" :user-id="me.id" :role="course?.role||''" :assignments="assignments" :focus="evidenceFocus" @changed="refreshCourse" @open-submission="evidenceSubmission" />

        <section v-else-if="page === 'editor' && entry" class="editing-page knowledge-workbench">
          <div class="editor-heading" v-workspace-actions>
            <div>
              <button class="text-btn muted" @click="go('assignments')">
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
                @click="saveDraftAndReturn"
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
                @click="submit"
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
              @click="restoreBackup"
            >
              恢复到编辑器</button
            ><button class="text-btn muted" @click="backup = null">
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
                  v-model="entry.title"
                  maxlength="160"
                  class="document-title"
                  aria-label="知识帖标题"
                  placeholder="为你的解释起个标题"
                  @input="changed"
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
                  v-model="entry.content"
                  @update:model-value="changed"
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
                    @click="
                      entry.evidence.splice(i, 1);
                      changed();
                    "
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
              <AiFeedback ref="aiPanel" :key="entry.id" :entry-id="entry.id" :assignment-enabled="!!currentAssignment?.ai_feedback_enabled" :step-mode="isStepAssignment" :doc="entry.content" @content="applyAiContent" @visibility="aiSidebarVisible = $event" @locate="focusAnchor" @marks="aiMarks = $event" />
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

        <section
          v-else-if="(page === 'reader' || page === 'grading') && version"
          class="editing-page"
        >
          <p v-if="page==='grading'&&nextReviewMessage" role="status" class="review-next-status">{{nextReviewMessage}}</p>

          <div class="editor-heading" v-workspace-actions>
            <div>
              <button
                class="text-btn muted review-return-link"
                @click="go(page === 'grading' ? 'review' : 'community')"
              >
                <ArrowLeft :size="16" />{{
                  page === "grading" ? "返回批改队列" : "返回知识社区"
                }}
              </button>
              <h1>{{ page === "grading" ? (isStepAssignment ? "评审步骤作答" : "评审知识帖") : isStepAssignment ? "阅读步骤作答" : "阅读知识条目" }}</h1>
              <p>
                {{ version.author_name }} · v{{ version.version_no }} ·
                {{ fmt(version.created_at) }} 提交
              </p>
            </div>
            <div class="button-row">
              <button v-if="page==='grading'&&isStaff" class="btn secondary" :disabled="busy||nextReviewBusy||review.state.loading" @click="nextReview">{{nextReviewBusy?'正在核对下一份…':'下一份待评审'}}<ChevronRight :size="16"/></button>
              <button
                class="btn secondary"
                :class="{ pressed: outlineVisible }"
                @click="outlineVisible = !outlineVisible"
              >
                <ListTree :size="16" />文档大纲</button
              ><button
                class="btn secondary"
                :class="{ pressed: panelVisible }"
                @click="panelVisible = !panelVisible"
              >
                <PanelLeftClose :size="16" />{{
                  page === "grading" ? "四维评分" : "质量与版本"
                }}</button
              ><span v-if="page==='reader'" id="publication-entry"></span><template v-if="page === 'reader' && version.author_id === me.id"
                ><button class="btn secondary" @click="openAssignment(assignments.find(a=>a.id===version.assignment_id))"><PenLine :size="16" />继续修订</button><button
                  v-if="
                    !version.publication.length ||
                    ['WITHDRAWN','RETURNED'].includes(version.publication[0].state)
                  "
                  class="btn primary"
                  :disabled="!version.evaluation || busy"
                  @click="publicationRequest"
                >
                  申请课程内发布<Send :size="17" /></button
                ><button
                  v-else
                  class="btn secondary"
                  @click="pubAction(version.publication[0].id, 'WITHDRAW')"
                >
                  {{
                    version.publication[0].state === "PENDING"
                      ? "撤回发布申请"
                      : "撤回社区展示"
                  }}
                </button></template
              ><button
                v-if="page === 'grading' && isTeacher"
                class="btn secondary" :disabled="review.state.saving || reviewPublishing || !review.state.loaded || !!review.state.stale || !!review.state.backup || review.state.conflict" @click="review.save()"><Save :size="16" />保存评分草稿</button><button
                v-if="page === 'grading' && isTeacher"
                class="btn primary"
                :disabled="busy"
                @click="publishGrade"
              >
                <Check :size="18" />发布正式评价
              </button>
            </div>
          </div>
          <div
            class="editor-layout"
            :class="{
              'grading-layout': page === 'grading',
              'has-outline': outlineVisible && !isStepAssignment,
              'without-panel': !panelVisible,
            }"
          >
            <button v-if="!panelVisible" class="panel-reopen" title="展开反馈面板" aria-label="展开反馈面板" @click="panelVisible = true"><MessageSquare :size="16" /><span>反馈</span></button>
            <aside v-if="outlineVisible && !isStepAssignment" class="document-outline">
              <h3><ListTree :size="16" />文档大纲</h3>
              <p>点击定位正文</p>
              <nav aria-label="正文段落">
                <button
                  v-for="a in versionOutline"
                  :key="a.id"
                  :class="{
                    selected: selectedAnchor === a.id,
                    'sub-heading': a.level > 1,
                    'outline-para': a.kind === 'para',
                  }"
                  :style="{ paddingLeft: 12 + a.depth * 16 + 'px' }"
                  @click="focusAnchor(a.id)"
                >
                  <span v-if="a.number">{{ a.number }}</span
                  ><span>{{ a.text }}</span>
                </button>
              </nav>

            </aside>
            <div class="document-column">
              <div class="document-paper" @mouseup="captureSelection">
                <h2 class="reader-title">{{ version.title }}</h2>
                <div class="document-meta">
                  <span class="avatar small">{{
                    version.author_name.slice(0, 1)
                  }}</span
                  ><span>{{ version.author_name }}</span
                  ><span>v{{ version.version_no }}</span>
                </div>
                <details class="assignment-snapshot"><summary>提交时的任务规则{{ version.assignmentSnapshot ? ' · r'+version.assignmentSnapshot.management_revision : ' · 未记录' }}</summary><template v-if="version.assignmentSnapshot"><p><strong>{{ version.assignmentSnapshot.title }}</strong></p><p>{{ version.assignmentSnapshot.requirements }}</p><p>{{ fmt(version.assignmentSnapshot.due_at) }} 截止 · {{ version.assignmentSnapshot.rubric_version }}</p></template><p v-else class="muted">未保存提交时的任务规则。</p></details>
                <StepAnswer
                  v-if="isStepAssignment"
                  :content="version.content"
                  :evidence="version.evidence"
                  :feedback="version.feedback"
                  :prior-feedback="priorOpen"
                  :scores="stepScoresForDisplay"
                  :mode="page === 'grading' ? 'grade' : 'read'"
                  :highlight-id="selectedAnchor"
                  :annotations="versionAnnotations"
                  :can-annotate="isTeacher"
                  @selected="selectedAnchor = $event"
                  @annotation="jumpToFeedback"
                  @annotate="annotateFigure"
                />
                <Editor
                  v-else
                  :highlight-id="selectedAnchor"
                  :annotations="versionAnnotations"
                  :can-annotate="isTeacher"
                  :model-value="version.content"
                  readonly
                  @selected="selectedAnchor = $event"
                  @annotation="jumpToFeedback"
                  @annotate="annotateFigure"
                />
              </div>
              <section v-if="page === 'reader' && versionHistory.length" class="card source-panel revision-history">
                <details :open="versionHistory.length > 1">
                  <summary class="section-title">
                    <h3>修订历史</h3>
                    <span class="muted">{{ versionHistory.length }} 个版本</span>
                  </summary>
                  <article
                    v-for="h in versionHistory"
                    :key="h.id"
                    class="history-row"
                    :class="{ current: h.id === version.id }"
                  >
                    <button
                      class="history-open"
                      :disabled="h.id === version.id || historyBusy"
                      :aria-current="h.id === version.id ? 'true' : undefined"
                      @click="openVersion(h.id)"
                    >
                      <strong>v{{ h.version_no }}</strong>
                      <span class="muted">{{ fmt(h.created_at) }}</span>
                      <span v-if="h.published" class="badge green">已发布</span>
                      <span v-else class="badge">未发布</span>
                      <span v-if="h.q_final != null" class="history-score">{{ number(h.q_final) }} 分</span>
                      <span v-if="h.id === version.id" class="muted">正在阅读</span>
                    </button>
                    <div v-if="h.feedbackVisible && h.feedback.length" class="history-feedback">
                      <p v-for="f in h.feedback" :key="f.id" class="history-note">
                        <span class="indicator-tag">{{ f.indicator_code }}</span>{{ f.body }}
                      </p>
                    </div>
                    <p v-else-if="h.feedbackVisible" class="muted history-note">这一版没有批注。</p>
                    <p v-else class="muted history-note">教师批注未公开。</p>
                  </article>
                </details>
              </section>
              <section class="card source-panel">
                <details class="revision-diff">
                  <summary>
                    {{
                      version.version_no === 1 ? "首个提交版本" : "与上一版相比"
                    }}
                    · {{ version.changes.length }} 处段落文本变化
                  </summary>
                  <div
                    v-for="(c, i) in version.changes"
                    :key="i"
                    class="diff-item"
                  >
                    <strong>{{
                      (
                        {
                          ADDED: "新增段落",
                          DELETED: "删除段落",
                          CHANGED: "修改段落",
                          STRUCTURE: "格式或结构变化",
                        } as any
                      )[c.type]
                    }}</strong>
                    <p v-if="c.before" class="diff-before">− {{ c.before }}</p>
                    <p v-if="c.after" class="diff-after">+ {{ c.after }}</p>
                  </div>
                  <p v-if="!version.changes.length" class="muted">
                    正文段落文本未变化；标题及证据变化请在对应区域核对。
                  </p>
                </details>
              </section>
              <details class="card source-panel source-collapsed">
                <summary class="section-title">
                  <h3>来源与核验</h3>
                  <span class="muted">{{ version.evidence.length }} 条 · 展开查看</span>
                </summary>
                <div
                  v-for="e in version.evidence"
                  :key="e.id"
                  class="reader-source"
                >
                  <strong>{{ e.source_title }}</strong>
                  <button class="text-btn" :disabled="busy" @click="openEvidence(e.id)">进入核验工作台<ArrowUpRight :size="14"/></button>
                  <p>{{ e.claim_text }}</p>
                  <blockquote>{{ e.excerpt }}</blockquote>
                  <div class="evidence-actions">
                    <span
                      class="badge"
                      :class="
                        e.state === 'PASS'
                          ? 'green'
                          : e.state === 'FAIL'
                            ? 'red'
                            : 'orange'
                      "
                      >{{
                        (
                          {
                            PENDING: "待核验",
                            PASS: "已通过",
                            FAIL: "未通过",
                            DISPUTED: "有争议",
                          } as any
                        )[e.state]
                      }}</span
                    ><button class="text-btn" @click="viewVerification(e)">
                      核验记录</button
                    ><button
                      v-if="page === 'grading' && isTeacher"
                      class="text-btn"
                      @click="
                        showModal('verify', {
                          id: e.id,
                          sourceTitle: e.source_title,
                        })
                      "
                    >
                      核验 / 裁定
                    </button>
                  </div>
                </div>
                <p v-if="!version.evidence.length" class="muted">
                  此版本未挂载证据；教师仍需评估需要依据的主张是否有充分来源。
                </p>
              </details>
            </div>
            <div
              v-if="annotation"
              class="annotation-popover"
              :style="{ left: annotation.left + 'px', top: annotation.top + 'px' }"
              role="dialog"
              aria-label="添加批注"
              @mouseup.stop
            >
              <div class="annotation-popover-head">
                <strong><MessageSquare :size="14" />{{ annotation.kind === "figure" ? "图片批注" : "添加批注" }}</strong>
                <button class="annotation-close" aria-label="关闭批注浮层" @click="clearAnnotation"><X :size="15" /></button>
              </div>
              <p class="annotation-quote" :class="{ 'is-figure': annotation.kind === 'figure' }">{{ annotation.kind === "figure" ? annotation.label : annotation.quote }}</p>
              <div class="annotation-dims" role="group" aria-label="批注维度">
                <button
                  v-for="d in DIMENSIONS"
                  :key="d.code"
                  type="button"
                  :class="{ active: annotationDim === d.code }"
                  @click="annotationDim = d.code"
                >{{ d.name }}</button>
              </div>
              <textarea v-model="annotationBody" rows="3" maxlength="2000" :placeholder="annotation.kind === 'figure' ? '说明这张图的问题：读法、标注、与正文是否对得上' : '指出问题并给出改法，一两句即可'"></textarea>
              <p v-if="annotationError" class="annotation-error">{{ annotationError }}</p>
              <div class="annotation-actions">
                <button class="text-btn" @click="clearAnnotation">取消</button>
                <button class="btn primary" :disabled="annotationBusy" @click="submitAnnotation">{{ annotationBusy ? "保存中…" : "添加批注" }}</button>
              </div>
            </div>
            <aside
              v-show="panelVisible"
              class="feedback-sidebar"
              v-if="page === 'grading'"
            >
              <div class="panel-title">
                <h2>{{ isStepAssignment ? "步骤评分" : "四维评分" }}</h2>
                <span class="badge blue">教师确认</span>
                <button class="panel-collapse" title="收起评分面板" aria-label="收起评分面板" @click="panelVisible = false"><ChevronRight :size="17" /></button>
              </div>
              <div v-if="isTeacher" class="review-save-state" :class="{failed:review.state.error}" aria-live="polite"><span>{{ review.state.loading ? '正在加载评分草稿…' : review.state.saving ? '正在保存评分草稿…' : review.state.error ? '评分草稿未保存' : review.state.dirty ? '有未保存的评分修改' : review.state.savedAt ? '评分草稿已保存 · ' + fmt(review.state.savedAt) : '修改后自动保存评分草稿' }}</span><small>仅你可见</small></div>
              <div v-if="review.state.error && isTeacher" class="review-recovery" role="alert"><p>{{ review.state.error }}</p><button class="text-btn" @click="review.state.conflict || !review.state.loaded ? reloadReview() : review.save()">{{ review.state.conflict || !review.state.loaded ? '重新加载并合并' : '重试保存' }}</button></div>
              <div v-if="review.state.stale && isTeacher" class="review-recovery"><strong>正式评价已更新</strong><p>有一份基于旧评价的评分草稿。请先选择恢复并复核，或放弃旧草稿。</p><button class="text-btn" @click="restoreReview(review.state.stale)">恢复旧草稿并复核</button><button class="text-btn muted" @click="review.discardStale()">放弃旧草稿</button></div>
              <div v-if="review.state.backup && isTeacher" class="review-recovery"><strong>发现本机未同步的评分备份</strong><p>可恢复后继续核对；不会直接覆盖正式评价。</p><button class="text-btn" @click="restoreReview(review.state.backup)">恢复本机评分备份</button><button class="text-btn muted" @click="review.discardBackup()">使用服务端草稿</button></div>
              <div v-if="!isStepAssignment" class="grade-summary">
                <span>综合质量 · 四维加权</span>
                <strong
                  >{{ previewScore ? number(previewScore.total) : "—"
                  }}<small>/ 100</small></strong
                >
                <p>
                  {{
                    dimsLeft
                      ? `还有 ${dimsLeft} 个维度未评`
                      : "四维已全部评级"
                  }}
                </p>
              </div>

              <fieldset
                class="grade-form"
                :disabled="
                  !isTeacher ||
                  reviewPublishing ||
                  review.state.loading ||
                  !review.state.loaded ||
                  !!review.state.stale ||
                  !!review.state.backup ||
                  review.state.conflict
                "
              >
                <section v-if="isStepAssignment" class="grade-block step-grade-block">
                  <div class="step-head">
                    <div>
                      <h4>步骤分</h4>
                      <p>{{ scoredStepCount }} / {{ gradingSteps.length }} 步已评</p>
                    </div>
                    <span class="step-sum"
                      ><strong>{{ number(stepTotal) }}</strong
                      ><em>/ {{ number(stepMax) }} 分</em></span
                    >
                  </div>
                  <p v-if="!gradingSteps.length" class="muted">
                    这份提交没有标题，无法切分步骤。可让作者用二级标题标记每一步，或仅用四维评分。
                  </p>
                  <div
                    v-for="(s, i) in gradingSteps"
                    :key="i"
                    class="step-row"
                    :class="{ active: activeStep === i }"
                    @click="selectStep(i)"
                  >
                    <button
                      type="button"
                      class="step-index"
                      :aria-label="`定位到第 ${i + 1} 步`"
                      @click.stop="selectStep(i)"
                    >
                      {{ i + 1 }}
                    </button>
                    <div class="step-main">
                      <strong>{{ s.text }}</strong>
                      <div v-if="stepScores[i]?.tags.length" class="step-tags static">
                        <span v-for="t in stepScores[i].tags" :key="t" class="step-tag on">{{ t }}</span>
                      </div>
                    </div>
                    <input
                      v-if="stepScores[i]"
                      v-model="stepScores[i].score"
                      class="step-score"
                      type="number"
                      min="0"
                      :max="stepScores[i].max"
                      step="0.5"
                      :aria-label="s.text + '得分'"
                      placeholder="—"
                      @click.stop
                    />
                    <span class="step-max">/ {{ stepScores[i]?.max ?? 10 }}</span>
                  </div>

                  <!-- 文字点评只保留「段落批注」一条通道：选中正文即可写，会锚在段落上。
                       步骤这里只留问题类型标签，不再重复一个点评输入框。 -->
                  <div v-if="stepScores[activeStep]" class="step-comment-card">
                    <div class="step-comment-head">
                      <strong>第 {{ activeStep + 1 }} 步 · 问题类型</strong>
                      <span
                        >{{ stepScores[activeStep].score === "" ? "—" : stepScores[activeStep].score }} /
                        {{ stepScores[activeStep].max }} 分</span
                      >
                    </div>
                    <div class="step-tags" role="group" aria-label="问题类型">
                      <button
                        v-for="t in STEP_TAGS"
                        :key="t"
                        type="button"
                        class="step-tag"
                        :class="{ on: stepScores[activeStep].tags.includes(t) }"
                        :aria-pressed="stepScores[activeStep].tags.includes(t)"
                        @click="toggleStepTag(stepScores[activeStep], t)"
                      >
                        {{ t }}
                      </button>
                    </div>
                  </div>
                </section>

                <section v-if="!isStepAssignment" class="grade-block">
                  <div class="grade-block-head">
                    <h4>内容质量</h4>
                  </div>
                  <div v-for="d in dims" :key="d.d" class="grade-dim">
                    <span class="grade-dim-name"
                      >{{ d.name }}<em>{{ d.weight }}%</em></span
                    >
                    <div
                      class="grade-levels"
                      role="group"
                      :aria-label="d.name + '评级'"
                    >
                      <button
                        v-for="l in LEVELS"
                        :key="l.v"
                        type="button"
                        :class="{ on: dimLevel(d.d) === l.v }"
                        :title="l.n + ' · 对应 ' + l.v + ' 分'"
                        @click="setDimLevel(d.d, l.v)"
                      >
                        {{ l.n }}
                      </button>
                    </div>
                  </div>

                  <details class="grade-fine">
                    <summary>逐项微调 · 11 项指标</summary>
                    <div
                      v-for="d in dims"
                      :key="d.d"
                      class="grade-fine-group"
                    >
                      <h5>{{ d.d }} · {{ d.name }}</h5>
                      <div
                        v-for="i in grading.filter((x) => x.code.startsWith(d.d))"
                        :key="i.code"
                        class="grade-fine-row"
                      >
                        <label :for="'score-' + i.code"
                          ><code>{{ i.code }}</code
                          >{{ i.name }}</label
                        >
                        <input
                          :id="'score-' + i.code"
                          v-model="i.score"
                          :disabled="i.status !== 'SCORED'"
                          type="number"
                          min="0"
                          max="100"
                          step="0.01"
                          placeholder="—"
                        />
                        <select
                          v-model="i.status"
                          :aria-label="i.name + '评分状态'"
                        >
                          <option value="SCORED">有效分</option>
                          <option value="NA">N/A</option>
                          <option value="PENDING">待核验</option>
                        </select>
                        <input
                          v-if="i.status === 'NA'"
                          v-model="i.reason"
                          :aria-label="i.name + '不适用理由'"
                          maxlength="500"
                          placeholder="不适用理由"
                        />
                        <details class="grade-anchor">
                          <summary>锚点</summary>
                          <template v-if="gradingCriterion(i.code)?.description">
                            <p v-if="criterionDescription(gradingCriterion(i.code))">{{ criterionDescription(gradingCriterion(i.code)) }}</p>
                            <p
                              v-for="[key, label] in [
                                ['excellent', '优秀'],
                                ['good', '良好'],
                                ['pass', '基本达到'],
                                ['improve', '待改进'],
                              ]"
                              :key="key"
                            >
                              <strong>{{ label }}：</strong
                              >{{ gradingCriterion(i.code)[key] }}
                            </p>
                          </template>
                          <p v-else class="muted">
                            该历史版本未记录单项锚点。
                          </p>
                        </details>
                      </div>
                    </div>
                  </details>
                </section>

                <section class="grade-block">
                  <div class="grade-block-head">
                    <h4>{{ isStepAssignment ? "整体评语" : "总评" }}</h4>
                    <span>学生可见</span>
                  </div>
                  <textarea
                    v-model="summary"
                    rows="5"
                    maxlength="2000"
                    placeholder="写给学生的总体评价与下一步建议"
                    required
                  />
                  <div class="grade-quick">
                    <button
                      v-for="t in QUICK_NOTES"
                      :key="t"
                      type="button"
                      class="grade-chip"
                      @click="addNote(t)"
                    >
                      + {{ t }}
                    </button>
                  </div>
                  <details v-if="!isStepAssignment" class="grade-gate" :open="!!gateReason">
                    <summary>存在严重缺陷 · 综合分封顶 89</summary>
                    <textarea
                      v-model="gateReason"
                      rows="2"
                      maxlength="1000"
                      placeholder="仅填写已证实的严重缺陷"
                    />
                  </details>
                  <p class="grade-tip">
                    <MessageSquare :size="14" /><span
                      >在左侧正文中选中文字即可添加批注；点击高亮可反向定位。</span
                    >
                  </p>
                </section>

                <details v-if="version.feedback.length" class="grade-feedback">
                  <summary>已有批注 {{ version.feedback.length }} 条</summary>
                  <div
                    v-for="f in version.feedback"
                    :key="f.id"
                    :id="'feedback-' + f.id"
                    class="feedback-card"
                    :class="{ 'is-target': highlightFeedbackId === f.id }"
                    @click="focusAnchor(f.paragraph_id, f.original_text)"
                  >
                    <strong
                      >{{ dimLabel(f.indicator_code) }} · {{ f.body }}</strong
                    ><button
                      class="text-btn"
                      @click.stop="focusAnchor(f.paragraph_id, f.original_text)"
                    >
                      定位正文
                    </button>
                    <blockquote v-if="f.quote_text">
                      {{ f.quote_text }}
                    </blockquote>
                    <div
                      v-for="a in f.actions"
                      :key="a.id"
                      class="feedback-replies"
                    >
                      <strong>{{ a.actor_name }}</strong
                      ><span>{{ actionLabel(a.action_type) }} · {{ a.body }}</span>
                    </div>
                    <button
                      v-if="isTeacher"
                      class="text-btn"
                      @click="
                        showModal('action', {
                          id: f.id,
                          action: f.resolved ? 'REOPEN' : 'RESOLVE',
                        })
                      "
                    >
                      {{ f.resolved ? "重新打开问题" : "确认问题已解决" }}
                    </button>
                  </div>
                </details>

                <details v-if="priorOpen.length || priorResolved.length" class="grade-feedback prior-feedback" open>
                  <summary>历史批注 {{ priorOpen.length }} 条待跟进<span v-if="priorResolved.length"> · {{ priorResolved.length }} 条已解决</span></summary>
                  <p class="grade-tip"><MessageSquare :size="14" /><span>来自更早的版本。</span></p>
                  <div
                    v-for="f in priorOpen"
                    :key="'prior-' + f.id"
                    class="feedback-card is-prior"
                    :class="{ 'is-target': highlightFeedbackId === f.id }"
                  >
                    <div class="card-top">
                      <span class="indicator-tag">v{{ f.source_version_no }} 提出</span>
                      <span class="badge" :class="priorState(f).cls">{{ priorState(f).text }}</span>
                    </div>
                    <strong>{{ dimLabel(f.indicator_code) }} · {{ f.body }}</strong>
                    <blockquote v-if="f.quote_text">{{ f.quote_text }}</blockquote>
                    <div class="prior-actions">
                      <button
                        v-if="!f.stale"
                        class="text-btn"
                        @click.stop="focusAnchor(f.paragraph_id, f.original_text)"
                      >
                        定位正文
                      </button>
                      <button
                        class="text-btn resolve"
                        :disabled="priorBusy === f.id"
                        @click.stop="resolvePrior(f)"
                      >
                        标记已解决
                      </button>
                    </div>
                  </div>
                  <details v-if="priorResolved.length" class="prior-resolved">
                    <summary>已解决 {{ priorResolved.length }} 条</summary>
                    <div v-for="f in priorResolved" :key="'done-' + f.id" class="feedback-card is-prior is-done">
                      <div class="card-top">
                        <span class="indicator-tag">v{{ f.source_version_no }} 提出</span>
                        <span class="badge green">已解决</span>
                      </div>
                      <strong>{{ dimLabel(f.indicator_code) }} · {{ f.body }}</strong>
                      <button class="text-btn muted" :disabled="priorBusy === f.id" @click.stop="resolvePrior(f, true)">
                        重新打开
                      </button>
                    </div>
                  </details>
                </details>

                <!-- 终审：决定这篇帖子到底结束没有。作业级「结束作业」是整班一起停，这里是一篇一篇定。 -->
                <section class="grade-block entry-verdict">
                  <div class="grade-block-head">
                    <h4>终审</h4>
                    <span>{{
                      version.entry_state === "PASSED"
                        ? "已通过定稿"
                        : version.entry_state === "REVISION"
                          ? "已打回"
                          : "进行中"
                    }}</span>
                  </div>
                  <p v-if="version.entry_state === 'REVISION' && version.entry_state_reason" class="verdict-reason">
                    <strong>打回原因：</strong>{{ version.entry_state_reason }}
                  </p>
                  <div v-if="version.entry_state !== 'PASSED'" class="verdict-actions">
                    <button class="btn secondary" :disabled="busy" @click="showModal('verdict', { action: 'RETURN', body: '' })">
                      打回修订
                    </button>
                    <button class="btn primary" :disabled="busy" @click="showModal('verdict', { action: 'PASS', body: '' })">
                      <Check :size="16" />通过定稿
                    </button>
                  </div>
                  <button
                    v-else
                    class="text-btn muted"
                    :disabled="busy"
                    @click="showModal('verdict', { action: 'REOPEN', body: '' })"
                  >
                    撤销定稿，允许继续修改
                  </button>
                  <p class="grade-tip"><CheckCircle2 :size="14" /><span>通过后学生不能再提交新版本。</span></p>
                </section>
              </fieldset>
            </aside>
            <aside v-else v-show="panelVisible" class="feedback-sidebar">
              <div class="panel-title">
                <h2>质量与版本</h2>
                <span class="badge">v{{ version.version_no }}</span>
                <button class="panel-collapse" title="收起反馈面板" aria-label="收起反馈面板" @click="panelVisible = false"><ChevronRight :size="17" /></button>
              </div>
              <template v-if="version.evaluation"><QualitySummary v-if="version.evaluation.dimensions && version.evaluation.dimensions.length" :evaluation="version.evaluation" :version="version.version_no" :dimensions="version.rubricSnapshot?.dimensions" @rubric="openRubric" /><div v-if="publishedStepScores.length" class="step-readonly"><div class="step-readonly-head"><h4>步骤分</h4><span>{{ number(publishedStepTotal) }} / {{ number(publishedStepMax) }}</span></div><div v-for="s in publishedStepScores" :key="s.index" class="step-readonly-row" role="button" tabindex="0" :aria-label="'定位到第 ' + (Number(s.index) + 1) + ' 步'" @click="scrollToStep(Number(s.index))" @keydown.enter="scrollToStep(Number(s.index))"><span class="step-index">{{ Number(s.index) + 1 }}</span><div class="step-readonly-main"><strong>{{ s.title || '步骤 ' + (Number(s.index) + 1) }}</strong><div v-if="s.tags && s.tags.length" class="step-tags static"><span v-for="t in s.tags" :key="t" class="step-tag on">{{ t }}</span></div></div><em>{{ number(Number(s.score) || 0) }} / {{ number(Number(s.max) || 10) }}</em></div></div><p class="teacher-summary">{{ version.evaluation.summary }}</p><p class="quality-teacher">{{ version.evaluation.teacher_name }} · {{ fmt(version.evaluation.created_at) }}</p></template>
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
                  @click="focusAnchor(f.paragraph_id, f.original_text)"
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
                    @click="focusAnchor(f.paragraph_id, f.original_text)"
                  >
                    定位正文<ArrowUpRight :size="14" />
                  </button>
                  <p v-else class="muted">这段正文已改动，原批注仅作提醒。</p>
                </article>
              </template>
              </template>

            </aside>
          </div>
        </section>
      </template>

    </main>
  </div>
  <div
    v-if="commandOpen"
    class="modal-scrim command-scrim"
    @click.self="commandOpen = false"
  >
    <section
      class="command-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="搜索页面和课程内容"
    >
      <div class="command-input">
        <Search :size="20" /><input
          v-model="commandQuery"
          placeholder="搜索页面、课程、作业或知识条目"
          aria-label="全局搜索"
          @keydown.enter.prevent="
            commandItems[0] && activateCommand(commandItems[0])
          "
        /><button class="keycap" @click="commandOpen = false">Esc</button>
      </div>
      <div class="command-results">
        <button
          v-for="(item, i) in commandItems"
          :key="i"
          @click="activateCommand(item)"
        >
          <component
            :is="
              item.kind === 'course'
                ? GraduationCap
                : item.kind === 'page'
                  ? LayoutDashboard
                  : item.kind === 'version'
                    ? Library
                    : FileText
            "
            :size="18"
          /><span
            >{{ item.title }}<small>{{ item.sub }}</small></span
          ><CornerDownLeft :size="15" />
        </button>
        <p v-if="!commandItems.length" class="empty-text">
          没有找到相关内容，换个关键词试试。
        </p>
      </div>
      <footer>
        <span>只搜索你有权访问的课程内容</span
        ><span>Enter 打开 · Esc 关闭</span>
      </footer>
    </section>
  </div>
  <div v-if="toast" class="toast" role="status">
    <CheckCircle2 :size="19" />{{ toast }}
  </div>
  <div v-if="modal" class="modal-scrim" @click.self="closeModal">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="
        (
          {
            assignment: '发布课程作业',
            course: '创建课程',
            member: '添加课程成员',
            evidence: '挂载论断证据',
            feedback: '添加段落批注',
            action: '处理教师反馈',
            verify: '核验来源',
            records: '核验历史',
          } as any
        )[modal]
      "
    >
      <header>
        <div>
          <span class="eyebrow">课程工作空间</span>
          <h2>
            {{
              (
                {
                  assignment: "发布课程作业",
                  course: "创建课程",
                  member: "添加课程成员",
                  evidence: "挂载论断证据",
                  feedback: "添加段落批注",
                  action: "处理教师反馈",
                  verify: "核验来源",
                  records: "核验历史",
                } as any
              )[modal]
            }}
          </h2>
        </div>
        <button class="icon-btn" aria-label="关闭弹窗" @click="closeModal">
          <X :size="21" />
        </button>
      </header>
      <form v-if="modal !== 'records'" @submit.prevent="modalSubmit">
        <template v-if="modal === 'assignment' || modal === 'course'"
          ><div
            v-if="modal === 'assignment'"
            class="kind-picker"
            role="radiogroup"
            aria-label="作业类型"
          >
            <button
              type="button"
              class="kind-card"
              :class="{ on: form.kind === 'POST' }"
              role="radio"
              :aria-checked="form.kind === 'POST'"
              @click="form.kind = 'POST'"
            >
              <FileText :size="18" /><strong>知识帖</strong
              ><span>按段落自由写作，教师按四维量规批改</span>
            </button>
            <button
              type="button"
              class="kind-card"
              :class="{ on: form.kind === 'STEP' }"
              role="radio"
              :aria-checked="form.kind === 'STEP'"
              @click="form.kind = 'STEP'"
            >
              <ListOrdered :size="18" /><strong>步骤</strong
              ><span>用二级标题划分步骤，教师逐步给分并标注问题类型</span>
            </button>
          </div>
          <label
            >{{ modal === "course" ? "课程名称" : "作业名称"
            }}<input
              v-model="form.title"
              required
              :maxlength="modal === 'course' ? 120 : 160" /></label
          ><label
            >{{ modal === "course" ? "课程简介" : "成果要求与学习目标"
            }}<textarea
              v-model="form.requirements"
              rows="5"
              required
              :maxlength="modal === 'course' ? 1000 : 4000"
            /></label
          ><template v-if="modal === 'assignment'"
            ><label
              >截止时间（本地时区）<input
                v-model="form.dueAt"
                type="datetime-local"
                required
            /></label>
            <label
              >证据核验策略<select v-model="form.evidencePolicy">
                <option value="DISPUTED">仅争议阻断发分（推荐）</option>
                <option value="REQUIRED">逐条核验后才能发分</option>
                <option value="OFF">不要求核验</option>
              </select></label
            >
            <p class="form-note">
              {{
                form.evidencePolicy === "REQUIRED"
                  ? "每条证据都要有人核验通过或驳回，学生才能发布正式评分。证据多时负担最重。"
                  : form.evidencePolicy === "DISPUTED"
                    ? "待核验证据不阻止发分，只有同伴结论冲突产生的争议必须先裁定。"
                    : "核验记录仍会保留，但不参与发分判断。"
              }}
            </p>
            <label>作业量规版本<select v-model="form.rubricId" required :disabled="assignmentRubricLoading || !!assignmentRubricError" aria-label="作业量规版本"><option value="" disabled>请选择已启用版本</option><option v-for="r in assignmentRubrics" :key="r.id" :value="String(r.id)">{{r.title}} · {{r.code}}</option></select></label><p v-if="assignmentRubricLoading" role="status">正在读取可用量规…</p><p v-if="assignmentRubricError" role="alert">{{assignmentRubricError}} <button type="button" class="text-btn" @click="loadAssignmentRubrics">重试读取量规</button></p>
            <div class="publish-ai-setting"><div><strong><Sparkles :size="15" />AI 修改建议</strong><p>{{ form.aiFeedbackEnabled ? '学生编辑页显示 AI 反馈入口' : '学生编辑页不显示 AI 反馈区块' }}</p></div><button type="button" class="policy-switch" role="switch" :aria-checked="!!form.aiFeedbackEnabled" aria-label="AI 修改建议" @click="form.aiFeedbackEnabled = !form.aiFeedbackEnabled"><span></span></button></div>
            <p class="form-note ai-setting-note">可在发布后按作业随时调整。</p>
            <p class="form-note">
              创建后锁定所选量规版本；更改课程默认不影响本作业。本版截止后不接受新提交。
            </p></template
          ></template
        ><template v-if="modal === 'member'"
          ><label
            >已有账号邮箱<input
              v-model="form.email"
              type="email"
              required
              maxlength="160" /></label
          ><label
            >课程内角色<select v-model="form.role">
              <option value="STUDENT">学生</option>
              <option value="ASSISTANT">助教（只读批改队列，不可发分）</option>
            </select></label
          >
          <p class="form-note">
            只添加已经开通的账号。本阶段暂不含注册与完整账号管理后台。
          </p></template
        ><template v-if="modal === 'evidence' || modal === 'feedback'"
          ><label
            >关联正文段落<select v-model="form.paragraphId" required>
              <option
                v-for="a in anchorOptions(
                  modal === 'evidence' ? entry.content : version.content,
                )"
                :key="a.id"
                :value="a.id"
              >
                {{ a.text }}
              </option>
            </select></label
          ></template
        ><template v-if="modal === 'evidence'"
          ><label
            >来源名称 / 版本 / 页码<input
              v-model="form.sourceTitle"
              required
              maxlength="300"
              placeholder="请填写实际使用的教材、课标或文献" /></label
          ><label
            >来源链接（可选）<input
              v-model="form.sourceUrl"
              type="url"
              maxlength="2000"
              placeholder="https://…" /></label
          ><label
            >原文摘录<textarea
              v-model="form.excerpt"
              required
              maxlength="4000"
              rows="3"
            /></label
          ><label
            >它支持的具体论断<textarea
              v-model="form.claim"
              required
              maxlength="2000"
              rows="2"
            />
          </label>
          <p class="form-note">请引用可回到原文核对的材料。</p></template
        ><template v-if="modal === 'feedback'"
          ><label
            >主责二级指标<select v-model="form.indicator">
              <option v-for="i in allItems" :key="i.code" :value="i.code">
                {{ i.code }} {{ i.name }}
              </option>
            </select></label
          ><label
            >具体问题与修改建议<textarea
              v-model="form.body"
              required
              maxlength="2000"
              rows="5"
            /></label></template
        ><template v-if="modal === 'action'"
          ><p class="muted">
            {{
              (
                {
                  REPLY: "回应反馈，说明你的理解或修订计划。",
                  ADOPT: "声明你已按这条批注改过。",
                  IGNORE: "请解释本轮不采纳的具体原因。",
                  RESOLVE: "确认这条批注已解决。",
                  REOPEN: "重新打开，继续跟进。",
                } as any
              )[form.action]
            }}
          </p>
          <label
            >说明<textarea
              v-model="form.body"
              :required="['REPLY', 'IGNORE'].includes(form.action)"
              rows="4"
              maxlength="2000"
            /></label></template
        ><template v-if="modal === 'verdict'"
          ><p class="muted">
            {{
              form.action === "PASS"
                ? "确认这篇帖子已完成？通过后学生不能再提交新版本；如需继续修改，要在这里撤销定稿。"
                : form.action === "RETURN"
                  ? "打回后学生需要继续修订。写清要改什么，学生端会直接看到这段原因。"
                  : "撤销定稿后，学生可以继续提交新版本。"
            }}
          </p>
          <label v-if="form.action === 'RETURN'"
            >打回原因<textarea
              v-model="form.body"
              required
              rows="4"
              maxlength="2000"
              placeholder="例如：结论只写了「验证了公式」，要改成「在什么条件下、可信到什么程度」"
            /></label></template
        ><template v-if="modal === 'verify'"
          ><p class="muted">{{ form.sourceTitle }}</p>
          <label
            >核验结论<select v-model="form.verdict">
              <option value="PASS">通过：来源与论断支持关系成立</option>
              <option value="FAIL">未通过：来源、引文或支持关系存在问题</option>
            </select></label
          ><label
            >对照材料与判断依据<textarea
              v-model="form.body"
              required
              rows="5"
              maxlength="2000"
              placeholder="请具体说明核对了什么，为什么得到此结论。"
            />
          </label>
          <p class="form-note">
            核验结果将保留操作者与时间；同伴结论不一致时转为有争议，由教师裁定。
          </p></template
        >
        <p v-if="error" class="error-note" role="alert">{{ error }}</p>
        <footer>
          <button type="button" class="btn secondary" @click="closeModal">
            取消</button
          ><button class="btn primary" :disabled="busy">
            {{
              busy
                ? "正在保存…"
                : modal === "assignment"
                  ? "发布作业"
                  : modal === "course"
                    ? "创建课程"
                    : "确认保存"
            }}<Check :size="17" />
          </button>
        </footer>
      </form>
      <div v-else class="verification-records">
        <p>{{ form.sourceTitle }}</p>
        <article v-for="r in form.records" :key="r.id">
          <span class="badge" :class="r.verdict === 'PASS' ? 'green' : 'red'">{{
            r.verdict === "PASS" ? "通过" : "未通过"
          }}</span
          ><strong>{{ r.verifier_name }}</strong>
          <p>{{ r.reason }}</p>
          <span class="muted">{{ fmt(r.created_at) }}</span>
        </article>
        <p v-if="!form.records.length" class="empty-text">尚无核验记录。</p>
      </div>
    </section>
  </div>
  <div v-if="me && cid && ['community','reader'].includes(page)"><PublicationManager ref="publicationManager" :key="cid+'-'+me.id+'-'+page" :course-id="cid" :user-id="me.id" :role="course?.role||''" @changed="publicationChanged" @open-submission="openVersion" /></div>
</template>
