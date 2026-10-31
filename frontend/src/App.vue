<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, defineAsyncComponent, nextTick } from "vue";
import { useTheme } from "./theme";
import { uid } from "./uid";
import EvidenceWorkbench from "./EvidenceWorkbench.vue";
import PublicationManager from "./PublicationManager.vue";
import ReviewWorkbench from "./ReviewWorkbench.vue";
import {defaultReviewQuery,reviewParams,reviewAdvanceBlocked,type ReviewQuery} from "./reviewQuery";
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
  LayoutDashboard,
  ClipboardList,
  Library,
  SlidersHorizontal,
  PanelLeftClose,
  LogOut,
  Settings,
  ArrowLeft,
  Plus,
  Check,
  ChevronRight,
  MessageSquare,
  ShieldCheck,
  Search,
  Send,
  Save,
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
  TrendingUp,
  X,
} from "lucide-vue-next";
import HomeDashboard from "./HomeDashboard.vue";
import CommunityPage from "./CommunityPage.vue";
import AssignmentsPage from "./AssignmentsPage.vue";
import LoginPage from "./LoginPage.vue";
import CommandPalette from "./CommandPalette.vue";
import { fmt, number } from "./format";
import { anchorOptions, outlineNumbered } from "./docOutline";
import { dimLabel, actionLabel, studentAction } from "./feedbackLabels";
import { vWorkspaceActions } from "./workspaceActions";
const Editor = defineAsyncComponent(() => import("./Editor.vue"));
import ProgressPage from "./ProgressPage.vue";
import MembersPage from "./MembersPage.vue";
const accessBackup=ref<any>(null),accessMessage=ref(''),backupDownloadRequired=ref(false);
import StepAnswer from "./StepAnswer.vue";
import EditorPage from "./EditorPage.vue";
import GradingPanel from "./GradingPanel.vue";
import SubmissionRecords from "./SubmissionRecords.vue";
import AnnotationPopover from "./AnnotationPopover.vue";
import ReaderFeedbackPanel from "./ReaderFeedbackPanel.vue";
import WorkspaceModal from "./WorkspaceModal.vue";
import { STEP_DEFAULT_MAX, scrollToStep } from "./stepGrading";
import NotificationCenter from "./NotificationCenter.vue";
import AccountSettings from "./AccountSettings.vue";
import RubricWorkbench from "./RubricWorkbench.vue";
const rubricWorkbench=ref<InstanceType<typeof RubricWorkbench>>();
const rubricFocus=ref<any>(null),rubricFocusSource=ref('');
const assignmentRubrics=ref<any[]>([]),assignmentRubricError=ref(''),assignmentRubricLoading=ref(false);
let assignmentRubricGeneration=0;
async function loadAssignmentRubrics(){const g=++assignmentRubricGeneration,activeCid=cid.value,uid=me.value?.id;assignmentRubricLoading.value=true;assignmentRubricError.value='';assignmentRubrics.value=[];try{const data=await api(`/courses/${activeCid}/rubrics`);if(g!==assignmentRubricGeneration||activeCid!==cid.value||uid!==me.value?.id||modal.value!=='assignment')return;assignmentRubrics.value=data.items.filter((r:any)=>r.state==='ENABLED');form.value.rubricId=String(data.defaultId);}catch(e:any){if(g===assignmentRubricGeneration)assignmentRubricError.value=e.message;}finally{if(g===assignmentRubricGeneration)assignmentRubricLoading.value=false;}}
async function openRubric(){const fromVersion=['reader','grading'].includes(page.value),snapshot=fromVersion?version.value?.rubricSnapshot:entry.value?.rubric,source=fromVersion?version.value?.rubricSnapshotSource:'';if(!await flushBeforeLeave())return;rubricFocus.value=snapshot?JSON.parse(JSON.stringify(snapshot)):null;rubricFocusSource.value=source||'';page.value='rubric';}

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
// 标签与默认满分在 stepGrading.ts。
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
// 右侧只列步骤与分数；标签和点评写在下方卡片里，点哪一步就编辑哪一步。
const activeStep = ref(0);
// 已发布的步骤分（学生与教师都看这一份，来自 evaluation.stepScores）。
const publishedStepScores = computed<any[]>(() => version.value?.evaluation?.stepScores || []);
// 批改时卡片徽标跟随当前填写，阅读时看已发布的步骤分。
const stepScoresForDisplay = computed(() => (page.value === "grading" ? stepScores.value : publishedStepScores.value));
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
function openCommand() {
  commandOpen.value = true;
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
const priorBusy = ref(0);
// 「已解决」只有教师能确认：学生的 ADOPT 只是声明，AI 建议的采纳更不参与这个状态。
// 所以徽标把「学生说了什么」和「段落有没有变」都摆出来，教师看一眼就能决定要不要关掉。
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
  <LoginPage
    v-else-if="!me"
    v-model:email="email"
    v-model:password="password"
    :busy="busy"
    :error="loginError"
    :has-backup="!!accessBackup"
    @submit="doLogin"
    @download-backup="downloadAccessBackup"
  />
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
          @publication-records="publicationManager?.list()"
          @new-free-post="newFreePost"
          @go="go"
          @notify="notify"
        />

        <EvidenceWorkbench v-else-if="page==='evidence'" :key="cid" :course-id="cid" :user-id="me.id" :role="course?.role||''" :assignments="assignments" :focus="evidenceFocus" @changed="refreshCourse" @open-submission="evidenceSubmission" />

        <EditorPage
          v-else-if="page === 'editor' && entry"
          v-model:panel-visible="panelVisible"
          v-model:outline-visible="outlineVisible"
          v-model:feedback-tab="feedbackTab"
          v-model:selected-paragraph="selectedParagraph"
          v-model:uploading-media="uploadingMedia"
          :entry="entry"
          :latest="latest"
          :me="me"
          :current-assignment="currentAssignment"
          :deadline-now="deadlineNow"
          :saving="saving"
          :dirty="dirty"
          :saved-at="savedAt"
          :busy="busy"
          :conflicted="conflicted"
          :submit-limit="submitLimit"
          :submitted-count="submittedCount"
          :submit-exhausted="submitExhausted"
          :rule-refresh-error="ruleRefreshError"
          :backup="backup"
          :is-step-assignment="isStepAssignment"
          :selected-anchor="selectedAnchor"
          :highlight-feedback-id="highlightFeedbackId"
          @back="go('assignments')"
          @save="saveDraftAndReturn"
          @submit="submit"
          @changed="changed"
          @title="entry.title = $event"
          @content="setEntryContent"
          @ai-content="applyAiContent"
          @remove-evidence="entry.evidence.splice($event, 1); changed()"
          @restore-backup="restoreBackup"
          @dismiss-backup="backup = null"
          @focus-anchor="focusAnchor"
          @modal="showModal"
          @open-version="openVersion"
          @jump-feedback="jumpToFeedback"
          @rubric="openRubric"
        />

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
              <SubmissionRecords
                :version="version"
                :version-history="versionHistory"
                :history-busy="historyBusy"
                :busy="busy"
                :is-reader="page === 'reader'"
                :can-adjudicate="page === 'grading' && isTeacher"
                @open-version="openVersion"
                @open-evidence="openEvidence"
                @view-verification="viewVerification"
                @modal="showModal"
              />
            </div>
            <AnnotationPopover
              v-if="annotation"
              v-model:dim="annotationDim"
              v-model:body="annotationBody"
              :annotation="annotation"
              :error="annotationError"
              :busy="annotationBusy"
              @close="clearAnnotation"
              @submit="submitAnnotation"
            />
            <GradingPanel
              v-if="page === 'grading'"
              v-model:panel-visible="panelVisible"
              v-model:summary="summary"
              v-model:gate-reason="gateReason"
              v-model:active-step="activeStep"
              :version="version"
              :grading="grading"
              :step-scores="stepScores"
              :grading-steps="gradingSteps"
              :dims="dims"
              :preview-score="previewScore"
              :review="review"
              :is-teacher="isTeacher"
              :is-step-assignment="isStepAssignment"
              :busy="busy"
              :review-publishing="reviewPublishing"
              :highlight-feedback-id="highlightFeedbackId"
              :prior-open="priorOpen"
              :prior-resolved="priorResolved"
              :prior-busy="priorBusy"
              @focus-anchor="focusAnchor"
              @modal="showModal"
              @resolve-prior="resolvePrior"
              @reload-review="reloadReview"
              @restore-review="restoreReview"
              @notify="notify"
            />
            <ReaderFeedbackPanel
              v-else
              v-model:panel-visible="panelVisible"
              :version="version"
              :feedback-visible="feedbackVisible"
              :reader-as-visitor="readerAsVisitor"
              :prior-open="priorOpen"
              :published-step-scores="publishedStepScores"
              :published-step-total="publishedStepTotal"
              :published-step-max="publishedStepMax"
              @focus-anchor="focusAnchor"
              @rubric="openRubric"
            />
          </div>
        </section>
      </template>

    </main>
  </div>
  <CommandPalette
    v-if="commandOpen"
    :nav="nav"
    :courses="courses"
    :assignments="assignments"
    :publications="publications"
    @close="commandOpen = false"
    @activate="activateCommand"
  />
  <div v-if="toast" class="toast" role="status">
    <CheckCircle2 :size="19" />{{ toast }}
  </div>
  <WorkspaceModal
    v-if="modal"
    :modal="modal"
    :form="form"
    :busy="busy"
    :error="error"
    :entry="entry"
    :version="version"
    :all-items="allItems"
    :assignment-rubrics="assignmentRubrics"
    :assignment-rubric-loading="assignmentRubricLoading"
    :assignment-rubric-error="assignmentRubricError"
    @close="closeModal"
    @submit="modalSubmit"
    @load-rubrics="loadAssignmentRubrics"
  />
  <div v-if="me && cid && ['community','reader'].includes(page)"><PublicationManager ref="publicationManager" :key="cid+'-'+me.id+'-'+page" :course-id="cid" :user-id="me.id" :role="course?.role||''" @changed="publicationChanged" @open-submission="openVersion" /></div>
</template>
