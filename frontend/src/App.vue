<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, defineAsyncComponent, nextTick } from "vue";
import EvidenceWorkbench from "./EvidenceWorkbench.vue";
import PublicationManager from "./PublicationManager.vue";
const publicationManager=ref<InstanceType<typeof PublicationManager>>();
async function publicationChanged(){const sid=version.value?.id,activeCourse=cid.value,activeUser=me.value?.id,epoch=accessEpoch;await refreshCourse();if(epoch!==accessEpoch||activeCourse!==cid.value||activeUser!==me.value?.id)return;if(sid&&version.value?.id===sid){const v=await api(`/submissions/${sid}`);if(epoch===accessEpoch&&activeCourse===cid.value&&activeUser===me.value?.id&&version.value?.id===sid)version.value=v;}if(epoch===accessEpoch&&activeUser===me.value?.id)notify("课程内发布处理已保存");}
const evidenceFocus=ref<{id:number;nonce:number}>();
async function openEvidence(id:number){if(!await flushBeforeLeave())return;evidenceFocus.value={id,nonce:Date.now()};page.value="evidence";}
async function evidenceSubmission(d:any){if(await openVersion(d.id,d.review)){await nextTick();await focusAnchor(d.paragraphId);}}

import {
  BookOpen,
  LayoutDashboard,
  ClipboardList,
  Library,
  SlidersHorizontal,
  PanelLeftClose,
  LogOut,
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
  Menu,
  X,
  Send,
  Save,
  Link as LinkIcon,
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
const Editor = defineAsyncComponent(() => import("./Editor.vue"));
import ProgressPage from "./ProgressPage.vue";
import MembersPage from "./MembersPage.vue";
const accessBackup=ref<any>(null),accessMessage=ref('');
import QualitySummary from "./QualitySummary.vue";
import AiFeedback from "./AiFeedback.vue";
import NotificationCenter from "./NotificationCenter.vue";
import AssignmentManager from "./AssignmentManager.vue";
const managedAssignment=ref<any>(null);
const ruleRefreshError=ref('');
import { useReviewDraft } from "./useReviewDraft";
import { assignmentState, dueLabel } from "./deadlines";
const clockNow = ref(Date.now()), serverOffset = ref(0);
let deadlineTimer: ReturnType<typeof setInterval>;
const deadlineNow = computed(() => clockNow.value + serverOffset.value);
const aiSidebarVisible = ref(false);
import { api, login, ApiFailure } from "./api";
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
  assignmentFilter = ref("all"),
  reviewFilter = ref("all"),
  publicationSort = ref("recent");
const page = ref("home"),
  loading = ref(true),
  busy = ref(false),
  error = ref(""),
  toast = ref(""),
  menu = ref(false),
  query = ref("");
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
const grading = ref<any[]>([]),
  summary = ref(""),
  gateReason = ref("");
const course = computed(() => courses.value.find((c) => c.id === cid.value));
const isTeacher = computed(() => course.value?.role === "TEACHER");
const isStaff = computed(() =>
  ["TEACHER", "ASSISTANT"].includes(course.value?.role),
);
function reviewSnapshot() { return { items: grading.value.map(i => ({code:i.code,score:i.score === '' || i.score == null ? null : Number(i.score),status:i.status,reason:i.reason || ''})), summary:summary.value, gateReason:gateReason.value }; }
function applyReviewData(data:any) { grading.value = allItems.map(i => ({...i,score:data.items?.find((x:any)=>x.code===i.code)?.score ?? null,status:data.items?.find((x:any)=>x.code===i.code)?.status || 'SCORED',reason:data.items?.find((x:any)=>x.code===i.code)?.reason || ''})); summary.value=data.summary || '';gateReason.value=data.gateReason || ''; }
const review = useReviewDraft(reviewSnapshot, applyReviewData, () => page.value === 'grading' && isTeacher.value, () => me.value?.id);
const reviewPublishing=ref(false);
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
  } else if(n.target_type==='publication'&&isTeacher.value&&pending.value.some(p=>p.submission_id===n.target_id)) {page.value='community';await nextTick();document.querySelector('.approval-card')?.scrollIntoView({behavior:'smooth'});}
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
const pendingQueue = computed(() =>
  queue.value.filter((q) => q.q_final === null),
);
const myWork = computed(() => assignments.value.filter((a) => a.entry_id));
const filteredPubs = computed(() => {
  const rows = publications.value.filter(p => (p.title + p.author_name).toLowerCase().includes(query.value.toLowerCase()));
  return publicationSort.value === 'quality' ? rows.sort((a, b) => Number(b.q_final) - Number(a.q_final)) : rows;
});
const previewScore = computed(() => {
  const dims: Record<string, number> = {};
  for (const d of rubric) {
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
    dims[d.d] = v.reduce((n, i) => n + Number(i.score), 0) / v.length;
  }
  const raw = Object.values(dims).reduce((a, b) => a + b, 0) / 4;
  return {
    dims,
    raw,
    total: gateReason.value.trim() ? Math.min(raw, 89) : raw,
  };
});
function fmt(d: string) {
  if (!d) return "—";
  return new Date(d).toLocaleString("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}
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
  loginError.value = "";
  busy.value = true;
  try {
    me.value = await login(email.value, password.value);
    password.value = "";
    await initialize();
  } catch (e: any) {
    loginError.value = e.message;
  } finally {
    busy.value = false;
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
  if(page.value==='editor'&&entry.value){const copy={title:entry.value.title,content:entry.value.content,evidence:entry.value.evidence,revision:entry.value.revision};accessBackup.value=copy;try{localStorage.setItem(`community-draft-${me.value.id}-${entry.value.id}`,JSON.stringify(copy));}catch{}}
  clearTimeout(saveTimer);dirty.value=false;entry.value=null;latest.value=null;version.value=null;homeEntry.value=null;homeSubmission.value=null;assignments.value=[];queue.value=[];evidences.value=[];publications.value=[];pending.value=[];managedAssignment.value=null;menu.value=false;modal.value='';commandOpen.value=false;accessMessage.value=message;page.value='access';
}
function downloadAccessBackup(){if(!accessBackup.value)return;const url=URL.createObjectURL(new Blob([JSON.stringify(accessBackup.value,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='knowledge-draft-backup.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
async function resumeCourses(){await run(async()=>{courses.value=await api('/courses');if(!courses.value.length){accessMessage.value='当前没有可访问的课程，请联系任课教师。';return;}accessBackup.value=null;cid.value=courses.value[0].id;await refreshCourse();page.value='home';});}
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
      ? api(`/courses/${cid.value}/review-queue`)
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
  queue.value = q;
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
const filteredAssignments = computed(() => assignments.value.filter(a => {
  if(assignmentFilter.value==='dueSoon')return assignmentState(a,deadlineNow.value)==='DUE_SOON';
  if(assignmentFilter.value==='closed')return assignmentState(a,deadlineNow.value)==='CLOSED';
  return assignmentFilter.value==='all'||(assignmentFilter.value==='submitted'?!!a.version_no:assignmentFilter.value==='started'?!!a.entry_id:!a.entry_id);
}));
const filteredQueue = computed(() =>
  queue.value.filter(
    (q) =>
      (reviewFilter.value === "all" ||
        (reviewFilter.value === "pending"
          ? q.q_final == null
          : q.q_final != null)) &&
      (!query.value || (q.title + q.author_name).includes(query.value)),
  ),
);
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
const theme = ref("system");
try {
  theme.value = localStorage.getItem("ockc-theme") || "system";
} catch {}
function applyTheme() {
  document.documentElement.dataset.theme = theme.value;
  try {
    localStorage.setItem("ockc-theme", theme.value);
  } catch {}
}
function cycleTheme() {
  theme.value =
    theme.value === "system"
      ? "light"
      : theme.value === "light"
        ? "dark"
        : "system";
  applyTheme();
  notify(
    ({ system: "跟随系统外观", light: "浅色外观", dark: "深色外观" } as any)[
      theme.value
    ],
  );
}
applyTheme();
watch(page, () => {
  sidebarCollapsed.value = ["editor", "grading", "reader"].includes(page.value);
  panelVisible.value = true;
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
  page.value = target;
  menu.value = false;
  query.value = "";
  error.value = "";
  if (cid.value) await run(refreshCourse);
  window.scrollTo(0, 0);
}
async function flushBeforeLeave() {
  if (page.value === "grading" && isTeacher.value && !await review.flush()) { error.value = review.state.error || "评分草稿尚未保存，请先处理保存失败或冲突。"; return false; }
  if (uploadingMedia.value) { notify("图片上传中，请稍候再离开"); return false; }
  if (page.value === "editor" && dirty.value) return await saveDraft();
  return true;
}
async function logout() {
  if (!(await flushBeforeLeave())) return;
  await run(async () => {
    await api("/auth/logout", "POST");
    me.value = null;
    accessBackup.value=null;accessMessage.value='';
    courses.value = [];
    toast.value = "";
    page.value = "home";
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
      operation = { revision: entry.value.revision, key: crypto.randomUUID() };
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
  });
}
async function openVersion(sid: number, asReview = false) {
  if (!(await flushBeforeLeave())) return;
  let succeeded=false;
  await run(async () => {
    version.value = await api(`/submissions/${sid}`);
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

function showModal(kind: string, data: Record<string, any> = {}) {
  modal.value = kind;
  form.value = {
    title: "",
    requirements: "",
    dueAt: "",
    aiFeedbackEnabled: false,
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
    notify(updated.ai_feedback_enabled ? '已开放 AI 反馈入口；模型服务尚未配置' : '已关闭 AI 反馈，学生编辑页不再显示该区块');
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
      await api(`/courses/${cid.value}/assignments`, "POST", {
        title: f.title,
        requirements: f.requirements,
        dueAt: new Date(f.dueAt).toISOString(),
        aiFeedbackEnabled: f.aiFeedbackEnabled,
      });
      await refreshCourse();
      notify("作业已发布，量规锁定为 four-dim-v1");
    }
    if (modal.value === "course") {
      const c = await api("/courses", "POST", {
        title: f.title,
        description: f.requirements,
      });
      courses.value = await api("/courses");
      cid.value = c.id;
      await refreshCourse();
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
      notify("反馈处理已记录；采纳不自动代表问题已解决");
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
    modal.value = "";
  });
}
async function publishGrade() {
  await run(async () => {
    if(!review.state.loaded || review.state.stale || review.state.backup || review.state.conflict)throw new Error('请先处理评分草稿的加载、恢复或冲突提示。');
    if (!previewScore.value)
      throw new Error("请填写完整的四维评分，待核验项不得发布");
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
      items: grading.value.map((i) => ({
        code: i.code,
        score: i.status === "SCORED" ? Number(i.score) : null,
        status: i.status,
        reason: i.reason,
      })),
      summary: summary.value,
      gateReason: gateReason.value,
      expectedRevision: review.state.baseRevision,
    });
    review.published();
    version.value = await api(`/submissions/${version.value.id}`);
    await review.load(version.value.id);
    await refreshCourse();
    notify("正式评价已发布并留存历史记录");
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
onMounted(async () => {
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
  clearTimeout(saveTimer);clearInterval(deadlineTimer);
  window.removeEventListener("beforeunload", beforeUnload);
  window.removeEventListener("keydown", keydown);
  window.removeEventListener("focus",refreshAssignmentRules);
});
</script>

<template>
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
        <span class="eyebrow">课程开放知识社区</span>
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
      <p class="login-foot">面向课程的创作、评价与知识共建空间</p>
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
        <button class="btn primary full" :disabled="busy">
          {{ busy ? "正在登录…" : "登录工作空间" }}<ArrowUpRight :size="18" />
        </button>
        <p class="form-note">
          第一阶段开发版 · 账号由部署者配置<br />演示账号见项目
          README，密码由启动环境设置。
        </p>
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
          ><small v-if="n.id === 'review' && pendingQueue.length">{{
            pendingQueue.length
          }}</small>
        </button>
      </nav>
      <div class="workspace-caption">
        <span>{{ isStaff ? "我教的课程" : "我的课程" }}</span
        ><span>{{ courses.length }} 门</span>
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
        <div class="local-note">
          <span class="dot"></span>课程内协同学习 · 开发版
        </div>
        <div class="account">
          <span class="avatar">{{ me.display_name.slice(0, 1) }}</span>
          <div>
            <strong>{{ me.display_name }}</strong
            ><span>{{
              isTeacher
                ? "任课教师"
                : course?.role === "ASSISTANT"
                  ? "课程助教"
                  : "课程学生"
            }}</span>
          </div>
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
            aria-label="切换页面外观"
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
        <p>请联系任课教师将你的账号加入课程。</p>
      </section>
      <template v-else>
        <HomeDashboard
          v-if="page === 'home'"
          :me="me"
          :course="course"
          :courses="courses"
          :assignments="assignments"
          :queue="queue"
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
        <section v-else-if="page === 'assignments'" class="page">
          <div class="page-heading">
            <div>
              <span class="eyebrow">课程与学习任务</span>
              <h1>{{ isStaff ? "课程作业" : "我的作业" }}</h1>
              <p>从任务要求出发，明确这次创作需要完成什么。</p>
            </div>
            <div v-if="isTeacher" class="button-row">
              <button class="btn secondary" @click="go('members')">
                <Users :size="17" />成员管理</button
              ><button class="btn primary" @click="showModal('assignment')">
                <Plus :size="17" />发布作业
              </button>
            </div>
          </div>
          <div class="list-tabs" role="group" aria-label="作业筛选">
            <button
              v-for="[key, label] in [
                ['all', '全部作业'],
                ['started', '已开始'],
                ['submitted', '已提交'],
                ['unstarted', '未开始'],
                ['dueSoon', '即将截止'],
                ['closed', '已截止'],
              ]"
              :key="key"
              :class="{ active: assignmentFilter === key }"
              @click="assignmentFilter = key"
            >
              {{ label
              }}<span v-if="key === 'all'">{{ assignments.length }}</span>
            </button>
          </div>
          <div class="assignment-grid">
            <article
              v-for="a in filteredAssignments"
              :key="a.id"
              class="assignment-card"
              :data-assignment-id="a.id"
            >
              <div class="card-top">
                <span class="file-icon"><FileText :size="22" /></span
                ><span class="badge blue">知识帖</span>
              </div>
              <h2>{{ a.title }}</h2>
              <p>{{ a.requirements }}</p>
              <div class="meta-line">
                <Clock :size="16" />{{ fmt(a.due_at) }} 截止
                <span class="deadline-badge" :class="assignmentState(a,deadlineNow).toLowerCase()">{{ a.manually_closed ? '已结束' : dueLabel(assignmentState(a,deadlineNow)) }}</span>
              </div>
              <div class="assignment-ai-policy">
                <div><span><Sparkles :size="14" />AI 修改建议</span><small>{{ a.ai_feedback_enabled ? '已开放入口 · 服务待配置' : '未开放 · 学生端不显示' }}</small></div>
                <button v-if="isTeacher" class="policy-switch" role="switch" :aria-checked="!!a.ai_feedback_enabled" :aria-label="`AI 修改建议：${a.title}`" :disabled="busy" @click="toggleAssignmentAi(a)"><span></span></button>
                <span v-else class="small-label">{{ a.ai_feedback_enabled ? '已开启' : '未开启' }}</span>
              </div>
              <div class="assignment-foot"><button v-if="isTeacher" class="btn secondary manage-assignment-btn" @click="managedAssignment={...a}"><SlidersHorizontal :size="14"/>管理作业</button>
                <span class="muted">{{ a.rubric_version }}</span
                ><button
                  class="text-btn"
                  :disabled="busy || (!isStaff && !a.entry_id && assignmentState(a,deadlineNow)==='CLOSED')"
                  @click="isStaff ? go('review') : openAssignment(a)"
                >
                  {{
                    isStaff ? "查看提交" : assignmentState(a,deadlineNow)==='CLOSED' ? (a.entry_id ? '查看与整理' : '已截止') : a.entry_id ? "继续编辑" : "开始作答"
                  }}<ArrowUpRight :size="16" />
                </button>
              </div>
            </article>
            <div v-if="!filteredAssignments.length" class="empty card">
              <ClipboardList :size="32" />
              <h2>还没有发布作业</h2>
              <p>教师发布任务后，将显示在这里。</p>
            </div>
          </div>
          <button
            v-if="isTeacher"
            class="text-btn more-space"
            @click="showModal('course')"
          >
            创建另一门课程<Plus :size="15" />
          </button>
        </section>

        <section v-else-if="page === 'rubric'" class="page">
          <div class="page-heading">
            <div>
              <span class="eyebrow">课程评价标准</span>
              <h1>四维评价，共同的尺度。</h1>
              <p>每一次评分和反馈，都回到同一套可追溯的课程量规。</p>
            </div>
            <span class="badge green"
              ><CheckCircle2 :size="15" />four-dim-v1</span
            >
          </div>
          <div class="rubric-explainer">
            <SlidersHorizontal :size="24" />
            <div>
              <strong>维度内取有效指标平均，四维等权计算综合分。</strong>
              <p>
                N/A 不计零分；待核验不发布正式
                Q。严重缺陷由教师确认后，综合分最高为 89。
              </p>
            </div>
          </div>
          <div class="rubric-grid">
            <article v-for="d in rubric" :key="d.d" class="rubric-card">
              <div class="rubric-card-title">
                <span>{{ d.d }}</span>
                <div>
                  <h2>{{ d.name }}</h2>
                  <p>{{ d.desc }}</p>
                </div>
                <strong>25<small>%</small></strong>
              </div>
              <div
                v-for="[code, name] in d.items"
                :key="code"
                class="rubric-item"
              >
                <code>{{ code }}</code
                ><strong>{{ name }}</strong
                ><span>0–100</span>
              </div>
            </article>
          </div>
          <div class="card rubric-bands">
            <h3>四个评价区间</h3>
            <div>
              <span><strong>90–100</strong> 优</span
              ><span><strong>75–89</strong> 良</span
              ><span><strong>60–74</strong> 合格</span
              ><span><strong>0–59</strong> 待改进</span>
            </div>
            <p>
              分数由教师按二级指标确认，AI
              不参与最终成绩。本开发版固定量规，学科判分锚例与量规发布功能尚待后续实现。
            </p>
          </div>
        </section>

        <ProgressPage v-else-if="page === 'progress'" :course-id="cid" :staff="isStaff" @open-version="openVersion($event, isStaff)" @assignments="go('assignments')" />

        <section v-else-if="page === 'review'" class="page">
          <div class="page-heading">
            <div>
              <span class="eyebrow">教师评审工作区</span>
              <h1>批改中心</h1>
              <p>
                {{ pendingQueue.length }}
                份提交等待评审。先理解变化，再给出关键反馈。
              </p>
            </div>
            <span class="small-label">{{ course.title }}</span>
          </div>
          <div class="review-toolbar">
            <div class="list-tabs" role="group" aria-label="评审筛选">
              <button
                v-for="[key, label] in [
                  ['all', '全部提交'],
                  ['pending', '待评审'],
                  ['graded', '已评价'],
                ]"
                :key="key"
                :class="{ active: reviewFilter === key }"
                @click="reviewFilter = key"
              >
                {{ label
                }}<span v-if="key === 'pending'">{{
                  pendingQueue.length
                }}</span>
              </button>
            </div>
            <label class="search-box"
              ><Search :size="16" /><input
                v-model="query"
                aria-label="搜索评审提交"
                placeholder="搜索作品或学生"
            /></label>
          </div>
          <div class="list-card">
            <div class="queue-column-head">
              <span>作品 / 作者</span><span>评价状态</span><span>提交时间</span>
            </div>
            <button
              v-for="q in filteredQueue"
              :key="q.id"
              class="review-row"
              :disabled="busy" :aria-busy="busy"
              @click="openVersion(q.id, true)"
            >
              <span class="avatar">{{ q.author_name.slice(0, 1) }}</span>
              <div>
                <strong>{{ q.title }}</strong>
                <p>
                  {{ q.author_name }} · {{ q.assignment_title }} · v{{
                    q.version_no
                  }}
                </p>
              </div>
              <span
                class="badge"
                :class="q.q_final === null ? 'orange' : 'green'"
                >{{
                  q.q_final === null ? (q.review_draft_updated_at ? "待评审 · 有草稿" : "待评审") : `${number(q.q_final)} 分`
                }}</span
              ><span class="row-time">{{ fmt(q.created_at) }}</span
              ><ChevronRight :size="18" />
            </button>
            <div v-if="!filteredQueue.length" class="empty">
              <CheckCircle2 :size="32" />
              <h2>
                {{
                  query.trim() || reviewFilter !== "all"
                    ? "没有匹配的提交"
                    : "暂时没有提交"
                }}
              </h2>
              <p>
                {{
                  query.trim() || reviewFilter !== "all"
                    ? "试试其他关键词，或切换到全部提交。"
                    : "学生提交评审后将在此显示。"
                }}
              </p>
            </div>
          </div>
        </section>

        <section v-else-if="page === 'community'" class="page">
          <div class="page-heading">
            <div>
              <span class="eyebrow">课程知识共建</span>
              <h1>让好的解释，被更多人看见。</h1>
              <p>这里的每一篇知识帖，都保留作者、来源、版本和教师评价。</p>
            </div>
            <span class="badge blue"
              >{{ publications.length }} 篇课程内条目</span
            >
          </div>
          <div id="publication-entry" class="publication-entry"></div>
          <section v-if="isTeacher && pending.length" class="approval-card">
            <div class="section-title">
              <h2>等待你的发布审核</h2>
              <span class="badge orange">{{ pending.length }}</span>
            </div>
            <div v-for="p in pending" :key="p.id" class="approval-row">
              <div>
                <strong>{{ p.title }}</strong>
                <p>
                  {{ p.author_name }} · v{{ p.version_no }} · 仅申请发布到本课程
                </p>
              </div>
              <button
                class="btn secondary"
                @click="openVersion(p.submission_id)"
              >
                查看</button
              ><button class="btn primary" @click="pubAction(p.id, 'APPROVE')">
                同意发布</button
              ><button class="text-btn" @click="pubAction(p.id, 'RETURN')">
                退回
              </button>
            </div>
          </section>
          <div class="filter-row">
            <label class="search-box"
              ><Search :size="18" /><input
                v-model="query"
                placeholder="搜索标题或作者"
                aria-label="搜索社区条目" /></label
            ><label class="community-sort">排序<select v-model="publicationSort" aria-label="社区排序"><option value="recent">最近发布</option><option value="quality">质量分优先</option></select></label><span class="muted">仅展示已获批的指定版本</span>
          </div>
          <div class="community-grid">
            <article
              v-for="p in filteredPubs"
              :key="p.id"
              class="knowledge-card"
            >
              <button v-if="p.cover" class="knowledge-cover" aria-label="打开图文知识帖" @click="openVersion(p.submission_id)"><img :src="p.cover" :alt="p.title + '的插图'" loading="lazy" /></button>
              <div class="card-top">
                <span class="category-label">课程知识帖</span
                ><span class="quality-number"
                  >{{ number(p.q_final) }}<small>质量分</small></span
                >
              </div>
              <button
                class="title-button"
                @click="openVersion(p.submission_id)"
              >
                <h2>{{ p.title }}</h2>
              </button>
              <p>{{ p.summary }}</p>
              <div class="knowledge-foot">
                <span class="avatar small">{{ p.author_name.slice(0, 1) }}</span
                ><span>{{ p.author_name }}</span
                ><span class="grow"></span><span>v{{ p.version_no }}</span
                ><button
                  class="icon-btn"
                  aria-label="阅读知识帖"
                  @click="openVersion(p.submission_id)"
                >
                  <ArrowUpRight :size="18" />
                </button>
              </div>
            </article>
            <div v-if="!filteredPubs.length" class="empty card">
              <Library :size="32" />
              <h2>{{ query ? "没有找到匹配条目" : "课程社区还没有条目" }}</h2>
              <p>作品需要作者申请、教师审核后才会展示。</p>
            </div>
          </div>
        </section>

        <EvidenceWorkbench v-else-if="page==='evidence'" :key="cid" :course-id="cid" :user-id="me.id" :role="course?.role||''" :assignments="assignments" :focus="evidenceFocus" @changed="refreshCourse" @open-submission="evidenceSubmission" />

        <section v-else-if="page === 'editor' && entry" class="editing-page knowledge-workbench">
          <div class="editor-heading">
            <div>
              <button class="text-btn muted" @click="go('assignments')">
                <ArrowLeft :size="16" />返回作业
              </button>
              <span class="workspace-kicker">KNOWLEDGE STUDIO</span><h1>创作与修订</h1>
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
                @click="saveDraft"
              >
                <Save :size="17" />保存草稿</button
              ><button
                class="btn primary"
                :disabled="saving || busy || conflicted || uploadingMedia || (currentAssignment && assignmentState(currentAssignment,deadlineNow)==='CLOSED')"
                @click="submit"
              >
                <Send :size="17" />{{ currentAssignment && assignmentState(currentAssignment,deadlineNow)==='CLOSED' ? (currentAssignment?.manually_closed?'作业已结束':'作业已截止') : '提交评审' }}
              </button>
            </div>
          </div>
          <p v-if="ruleRefreshError" class="notice-error" role="alert">{{ ruleRefreshError }}</p>
          <div v-if="currentAssignment && assignmentState(currentAssignment,deadlineNow)!=='OPEN'" class="deadline-banner" :class="assignmentState(currentAssignment,deadlineNow).toLowerCase()"><Clock :size="16" /><span>{{ assignmentState(currentAssignment,deadlineNow)==='CLOSED' ? (currentAssignment.manually_closed?'教师已结束本作业。已有草稿仍可整理和保存，但不能提交新版本。':'本作业已截止。已有草稿仍可整理和保存，但不能提交新版本。') : '本作业将在 24 小时内截止，请及时保存并提交评审。' }}</span></div>
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
              'has-outline': outlineVisible,
              'without-panel': !panelVisible,
            }"
          >
            <aside v-if="outlineVisible" class="document-outline studio-outline">
              <div class="outline-section-label"><ListTree :size="16" /><h3>写作大纲</h3><span>{{ documentHeadings(entry.content).length }}</span></div>
              <nav aria-label="文章章节">
                <button v-for="(a, i) in documentHeadings(entry.content)" :key="a.id" :class="{ selected: activeOutlineId === a.id, 'sub-heading': a.level > 1 }" @click="focusAnchor(a.id)"><span>{{ i + 1 }}</span><span>{{ a.text }}</span></button>
              </nav>
              <p v-if="!documentHeadings(entry.content).length" class="outline-empty">用 H1 / H2 组织文章，章节将自动出现在这里。</p>
              <section class="outline-evidence"><div class="outline-section-label"><BookOpen :size="16" /><h3>挂载证据</h3><span>{{ entry.evidence.length }}</span></div>
                <button v-for="(e, i) in entry.evidence" :key="i" class="outline-source" @click="focusAnchor(e.paragraphId)"><span>E{{ i + 1 }}</span><span>{{ e.sourceTitle }}</span></button>
                <p v-if="!entry.evidence.length" class="outline-empty">让每一个关键论断，都有可靠的来源。</p>
                <button class="outline-add" @click="showModal('evidence')"><Plus :size="15" />挂载新证据</button>
              </section>
              <section class="outline-assignment"><span class="eyebrow">当前任务</span><strong>{{ currentAssignment?.title }}</strong><p><Clock :size="13" />{{ fmt(currentAssignment?.due_at) }} 截止</p><details><summary>查看任务要求</summary><p>{{ currentAssignment?.requirements }}</p></details></section>
              <div class="outline-foot"><ShieldCheck :size="16" /><span>草稿持续保存，提交后生成独立版本。段落与图片均可关联证据。</span></div>
            </aside>
            <div class="document-column">
              <div class="document-paper">
                <span class="eyebrow document-label"><span class="draft-dot"></span>知识帖草稿 <span class="grow"></span><span>仅你与任课教师可见</span></span
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
                <Editor
                  :highlight-id="selectedAnchor"
                  :document-title="entry.title"
                  :entry-id="entry.id" :evidence-count="entry.evidence.length" @uploading="uploadingMedia = $event"
                  @evidence="showModal('evidence')"
                  v-model="entry.content"
                  @update:model-value="changed"
                  @selected="selectedParagraph = $event"
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
              </div>
              <nav class="feedback-jump-nav" aria-label="反馈分区导航"><button @click="sidebarJump('sidebar-quality')">质量评分</button><button @click="sidebarJump('sidebar-teacher')">教师批注<span>{{ latest?.feedback?.length || 0 }}</span></button><button v-if="aiSidebarVisible" @click="sidebarJump('sidebar-ai')">AI 反馈</button></nav>
              </div>
              <div id="sidebar-quality"><QualitySummary :evaluation="latest?.evaluation" :version="latest?.version_no" draft @rubric="go('rubric')" /></div>
              <section id="sidebar-teacher" class="sidebar-section teacher-feedback-section" aria-label="教师批注">
                <header class="sidebar-section-heading"><h3><MessageSquare :size="15" />教师批注</h3><span class="small-label">{{ latest?.feedback?.length || 0 }} 条</span></header>
              <p v-if="latest?.evaluation?.summary" class="teacher-summary">
                  {{ latest.evaluation.summary }}
                </p>
                <article
                  v-for="f in latest?.feedback || []"
                  :key="f.id"
                  class="feedback-card"
                >
                  <div class="card-top">
                    <span class="indicator-tag">{{ f.indicator_code }}</span
                    ><span
                      class="badge"
                      :class="f.resolved ? 'green' : 'orange'"
                      >{{ f.resolved ? "已解决" : "待修订" }}</span
                    >
                  </div>
                  <p class="feedback-version-meta">{{ latest?.evaluation?.teacher_name || '任课教师' }} · 提交 v{{ latest?.version_no }}</p>
                  <h3 class="feedback-highlight">{{ f.body }}</h3>
                  <button
                    class="text-btn"
                    @click="focusAnchor(f.paragraph_id, f.original_text)"
                  >
                    定位正文<ArrowUpRight :size="14" />
                  </button>
                  <blockquote>{{ f.original_text }}</blockquote>
                  <div
                    class="feedback-replies"
                    v-for="a in f.actions"
                    :key="a.id"
                  >
                    <strong>{{ a.actor_name }}</strong
                    ><span>{{ a.action_type }} · {{ a.body }}</span>
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
                      class="text-btn"
                      @click="
                        showModal('action', { id: f.id, action: 'ADOPT' })
                      "
                    >
                      标记采纳</button
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
                <div v-if="!latest?.feedback?.length" class="sidebar-empty"><MessageSquare :size="21" /><p>{{ latest ? '还没有教师批注' : '提交后，在这里查看教师批注' }}</p><span>批注会定位到对应段落或图片。</span></div>
              </section>
              <AiFeedback :key="entry.id" :entry-id="entry.id" :assignment-enabled="!!currentAssignment?.ai_feedback_enabled" @visibility="aiSidebarVisible = $event" />
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
                  提交评审后生成不可变版本。
                </p>
              </div>
              </details>
              <p class="sidebar-trust-note"><ShieldCheck :size="13" /><span>教师批注与 AI 建议分开呈现。正式质量分仅由任课教师确认。</span></p>
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
          <div class="editor-heading">
            <div>
              <button
                class="text-btn muted"
                @click="go(page === 'grading' ? 'review' : 'community')"
              >
                <ArrowLeft :size="16" />{{
                  page === "grading" ? "返回批改队列" : "返回知识社区"
                }}
              </button>
              <h1>{{ page === "grading" ? "评审知识帖" : "阅读知识条目" }}</h1>
              <p>
                {{ version.author_name }} · v{{ version.version_no }} ·
                {{ fmt(version.created_at) }} 提交
              </p>
            </div>
            <div class="button-row">
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
              'has-outline': outlineVisible,
              'without-panel': !panelVisible,
            }"
          >
            <aside v-if="outlineVisible" class="document-outline">
              <h3><ListTree :size="16" />文档大纲</h3>
              <p>点击章节，定位正文</p>
              <nav aria-label="正文段落">
                <button
                  v-for="(a, i) in documentHeadings(version.content)"
                  :key="a.id"
                  :class="{ selected: selectedAnchor === a.id }"
                  @click="focusAnchor(a.id)"
                >
                  <span>{{ i + 1 }}</span
                  ><span>{{ a.text }}</span>
                </button>
              </nav>
              <div class="outline-foot">
                <ShieldCheck :size="16" /><span
                  >段落标识保持稳定，反馈不会随顺序变化而错位。</span
                >
              </div>
            </aside>
            <div class="document-column">
              <div class="document-paper">
                <span class="eyebrow"
                  >{{ version.rubric_version }} · 不可变提交版本</span
                >
                <h2 class="reader-title">{{ version.title }}</h2>
                <div class="document-meta">
                  <span class="avatar small">{{
                    version.author_name.slice(0, 1)
                  }}</span
                  ><span>{{ version.author_name }}</span
                  ><span>v{{ version.version_no }}</span>
                </div>
                <details class="assignment-snapshot"><summary>提交时的任务规则{{ version.assignmentSnapshot ? ' · r'+version.assignmentSnapshot.management_revision : ' · 未记录' }}</summary><template v-if="version.assignmentSnapshot"><p><strong>{{ version.assignmentSnapshot.title }}</strong></p><p>{{ version.assignmentSnapshot.requirements }}</p><p>{{ fmt(version.assignmentSnapshot.due_at) }} 截止 · {{ version.assignmentSnapshot.rubric_version }}</p><p class="muted">本规则快照随该提交保存，后续调整不改写本记录。</p></template><p v-else class="muted">此历史提交早于任务快照功能，未保存当时的规则；不使用当前规则冒充历史记录。</p></details>
                <Editor
                  :highlight-id="selectedAnchor"
                  :model-value="version.content"
                  readonly
                  @selected="selectedAnchor = $event"
                />
              </div>
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
              <section class="card source-panel">
                <div class="section-title">
                  <h3>来源与核验</h3>
                  <span class="muted">{{ version.evidence.length }} 条</span>
                </div>
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
              </section>
            </div>
            <aside
              v-show="panelVisible"
              class="feedback-sidebar"
              v-if="page === 'grading'"
            >
              <div class="panel-title">
                <h2>四维评分</h2>
                <span class="badge blue">教师确认</span>
              </div>
              <div v-if="isTeacher" class="review-save-state" :class="{failed:review.state.error}" aria-live="polite"><span>{{ review.state.loading ? '正在加载评分草稿…' : review.state.saving ? '正在保存评分草稿…' : review.state.error ? '评分草稿未保存' : review.state.dirty ? '有未保存的评分修改' : review.state.savedAt ? '评分草稿已保存 · ' + fmt(review.state.savedAt) : '修改后自动保存评分草稿' }}</span><small>仅你可见；发布后学生才会看到评分和总评。</small></div>
              <div v-if="review.state.error && isTeacher" class="review-recovery" role="alert"><p>{{ review.state.error }}</p><button class="text-btn" @click="review.state.conflict || !review.state.loaded ? reloadReview() : review.save()">{{ review.state.conflict || !review.state.loaded ? '重新加载并合并' : '重试保存' }}</button></div>
              <div v-if="review.state.stale && isTeacher" class="review-recovery"><strong>正式评价已更新</strong><p>有一份基于旧评价的评分草稿。请先选择恢复并复核，或放弃旧草稿。</p><button class="text-btn" @click="restoreReview(review.state.stale)">恢复旧草稿并复核</button><button class="text-btn muted" @click="review.discardStale()">放弃旧草稿</button></div>
              <div v-if="review.state.backup && isTeacher" class="review-recovery"><strong>发现本机未同步的评分备份</strong><p>可恢复后继续核对；不会直接覆盖正式评价。</p><button class="text-btn" @click="restoreReview(review.state.backup)">恢复本机评分备份</button><button class="text-btn muted" @click="review.discardBackup()">使用服务端草稿</button></div>
              <div class="grading-preview">
                <span>综合质量 · 四维等权</span
                ><strong
                  >{{ previewScore ? number(previewScore.total) : "—"
                  }}<small>/ 100</small></strong
                >
                <p>
                  {{
                    previewScore
                      ? "仅为待发布计算值"
                      : "请逐项评分，不适用项需填写理由"
                  }}
                </p>
              </div>
              <fieldset class="review-form" :disabled="!isTeacher || reviewPublishing || review.state.loading || !review.state.loaded || !!review.state.stale || !!review.state.backup || review.state.conflict">
              <details
                v-for="d in rubric"
                :key="d.d"
                class="grading-dimension"
                :open="d.d === 'S'"
              >
                <summary>
                  <span
                    ><i>{{ d.d }}</i
                    >{{ d.name }}</span
                  ><span
                    >{{ dimensionScore(d.d) }}<ChevronRight :size="15"
                  /></span>
                </summary>
                <div
                  v-for="i in grading.filter((i) => i.code.startsWith(d.d))"
                  :key="i.code"
                  class="grading-item"
                >
                  <label :for="'score-' + i.code"
                    ><code>{{ i.code }}</code
                    >{{ i.name }}</label
                  >
                  <div class="grading-inputs">
                    <input
                      :id="'score-' + i.code"
                      v-model="i.score"
                      :disabled="i.status !== 'SCORED'"
                      type="number"
                      min="0"
                      max="100"
                      step="0.01"
                      placeholder="分数"
                    /><select
                      v-model="i.status"
                      :aria-label="i.name + '评分状态'"
                    >
                      <option value="SCORED">有效分</option>
                      <option value="NA">N/A</option>
                      <option value="PENDING">待核验</option>
                    </select>
                  </div>
                  <input
                    v-if="i.status === 'NA'"
                    v-model="i.reason"
                    :aria-label="i.name + '不适用理由'"
                    maxlength="500"
                    placeholder="填写不适用理由"
                  />
                </div>
              </details>
              <div class="grading-notes">
                <label
                  >关键质量门槛<textarea
                    v-model="gateReason"
                    rows="2"
                    maxlength="1000"
                    placeholder="仅已证实的严重缺陷填写；将 Q 封顶 89"
                  /></label
                ><label
                  >总评与下一步建议<textarea
                    v-model="summary"
                    rows="4"
                    maxlength="2000"
                    placeholder="最重要的 1–3 个改进方向"
                    required
                  /></label
                ><button
                  class="btn secondary full"
                  @click="showModal('feedback')"
                >
                  <MessageSquare :size="17" />添加段落批注
                </button>
                <p class="small-note">
                  复评保留旧记录。该版已有证据必须先完成核验或教师裁定。
                </p>
                <div
                  v-for="f in version.feedback"
                  :key="f.id"
                  class="feedback-card"
                >
                  <strong>{{ f.indicator_code }} · {{ f.body }}</strong
                  ><button
                    class="text-btn"
                    @click="focusAnchor(f.paragraph_id, f.original_text)"
                  >
                    定位正文
                  </button>
                  <div
                    v-for="a in f.actions"
                    :key="a.id"
                    class="feedback-replies"
                  >
                    <strong>{{ a.actor_name }}</strong
                    ><span>{{ a.action_type }} · {{ a.body }}</span>
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
              </div>
              </fieldset>
            </aside>
            <aside v-else v-show="panelVisible" class="feedback-sidebar">
              <div class="panel-title">
                <h2>质量与版本</h2>
                <span class="badge">v{{ version.version_no }}</span>
              </div>
              <template v-if="version.evaluation"><QualitySummary :evaluation="version.evaluation" :version="version.version_no" @rubric="go('rubric')" /><p class="teacher-summary">{{ version.evaluation.summary }}</p><p class="quality-teacher">{{ version.evaluation.teacher_name }} · {{ fmt(version.evaluation.created_at) }}</p></template>
              <div v-else class="empty">
                <Clock :size="26" />
                <h3>等待教师评审</h3>
              </div>
              <h3 class="sub-panel-title">教师反馈</h3>
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
              <div class="ai-boundary">
                <History :size="18" /><span
                  >本页展示冻结的 v{{
                    version.version_no
                  }}。后续修订不会自动替换此版本。</span
                >
              </div>
            </aside>
          </div>
        </section>
      </template>
      <footer class="app-footer">
        <span>课程开放知识社区</span
        ><span>创作有依据 · 反馈可行动 · 成长可追溯</span>
      </footer>
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
          ><label
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
            <div class="publish-ai-setting"><div><strong><Sparkles :size="15" />AI 修改建议</strong><p>{{ form.aiFeedbackEnabled ? '学生编辑页显示 AI 反馈入口' : '学生编辑页不显示 AI 反馈区块' }}</p></div><button type="button" class="policy-switch" role="switch" :aria-checked="!!form.aiFeedbackEnabled" aria-label="AI 修改建议" @click="form.aiFeedbackEnabled = !form.aiFeedbackEnabled"><span></span></button></div>
            <p class="form-note ai-setting-note">可在发布后按作业随时调整。当前模型服务尚未配置；开启入口不会生成建议，也不会发送学生正文。</p>
            <p class="form-note">
              发布后锁定四维 11 项量规 four-dim-v1。本版截止后不接受新提交。
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
          <p class="form-note">
            来源不会被系统自动判定为真实。请仅引用获得授权、可回到原文核对的材料。
          </p></template
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
                  ADOPT: "仅记录采纳意向，请在正文中完成修改。",
                  IGNORE: "请解释本轮不采纳的具体原因。",
                  RESOLVE: "教师确认该问题已解决，原批注和回应仍会保留。",
                  REOPEN: "重新打开问题，继续跟进修订。",
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
