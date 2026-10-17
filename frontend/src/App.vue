<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from "vue";
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
} from "lucide-vue-next";
import HomeDashboard from "./HomeDashboard.vue";
import Editor from "./Editor.vue";
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
  reviewFilter = ref("all");
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
  dirty = ref(false),
  saving = ref(false),
  conflicted = ref(false),
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
  { id: "rubric", name: "课程量规", icon: SlidersHorizontal },
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
const filteredPubs = computed(() =>
  publications.value.filter((p) =>
    (p.title + p.author_name).toLowerCase().includes(query.value.toLowerCase()),
  ),
);
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
  courses.value = await api("/courses");
  cid.value = courses.value[0]?.id || 0;
  if (cid.value) await refreshCourse();
  page.value = "home";
}
async function refreshCourse() {
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
  assignments.value = a;
  publications.value = p;
  evidences.value = e;
  queue.value = q;
  pending.value = n;
  homeEntry.value = null;
  homeSubmission.value = null;
  const current = a[0]?.entry_id ? a[0] : null;
  if (!isStaff.value && current) {
    homeEntry.value = await api(`/entries/${current.entry_id}`);
    const v = homeEntry.value.versions[0];
    if (v) homeSubmission.value = await api(`/submissions/${v.id}`);
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
const filteredAssignments = computed(() =>
  assignments.value.filter(
    (a) =>
      assignmentFilter.value === "all" ||
      (assignmentFilter.value === "submitted"
        ? !!a.version_no
        : assignmentFilter.value === "started"
          ? !!a.entry_id
          : !a.entry_id),
  ),
);
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
  outlineVisible.value = false;
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
  page.value = target;
  menu.value = false;
  query.value = "";
  error.value = "";
  if (cid.value) await run(refreshCourse);
  window.scrollTo(0, 0);
}
async function flushBeforeLeave() {
  if (page.value === "editor" && dirty.value) return await saveDraft();
  return true;
}
async function logout() {
  if (!(await flushBeforeLeave())) return;
  await run(async () => {
    await api("/auth/logout", "POST");
    me.value = null;
    courses.value = [];
    toast.value = "";
    page.value = "home";
  });
}
async function openAssignment(a: any) {
  await run(async () => {
    if (!(await flushBeforeLeave())) return;
    entry.value = await api(`/assignments/${a.id}/entries`, "POST");
    await loadEditorState();
    page.value = "editor";
    menu.value = false;
  });
}
async function loadEditorState() {
  dirty.value = false;
  conflicted.value = false;
  selectedAnchor.value = "";
  savedAt.value = entry.value.updated_at;
  latest.value = entry.value.versions.length
    ? await api(`/submissions/${entry.value.versions[0].id}`)
    : null;
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
  saving.value = true;
  inFlight = (async () => {
    try {
      const saved = await api(`/entries/${entry.value.id}/draft`, "PUT", data);
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
      error.value = e.message;
      if (e.status === 409) conflicted.value = true;
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
    const storageKey = `community-submit-${me.value.id}-${entry.value.id}`;
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
    localStorage.removeItem(storageKey);
    entry.value = await api(`/entries/${entry.value.id}`);
    await loadEditorState();
    await refreshCourse();
    notify(`已提交 v${v.version_no}，等待教师评审`);
  });
}
async function openVersion(sid: number, review = false) {
  if (!(await flushBeforeLeave())) return;
  await run(async () => {
    version.value = await api(`/submissions/${sid}`);
    selectedAnchor.value = "";
    page.value = review ? "grading" : "reader";
    feedbackTab.value = "feedback";
    if (review) {
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
    }
    window.scrollTo(0, 0);
  });
}
function anchorOptions(doc: any) {
  const out: { id: string; text: string }[] = [];
  function walk(n: any) {
    if (n.attrs?.blockId) {
      const texts = (x: any): string =>
        (x.text || "") + (x.content || []).map(texts).join("");
      out.push({
        id: n.attrs.blockId,
        text: texts(n).slice(0, 32) || "空段落",
      });
    }
    for (const c of n.content || []) walk(c);
  }
  if (doc) walk(doc);
  return out;
}
function focusAnchor(id: string, original = "") {
  const nodes = [...document.querySelectorAll<HTMLElement>("[data-block-id]")];
  const el = nodes.find((x) => x.dataset.blockId === id);
  if (el) {
    el.scrollIntoView({ behavior: "smooth", block: "center" });
    selectedAnchor.value = id;
  } else {
    error.value = `原段落已删除或拆分，请对照历史原文：${original || id}`;
  }
}
function showModal(kind: string, data: Record<string, any> = {}) {
  modal.value = kind;
  form.value = {
    title: "",
    requirements: "",
    dueAt: "",
    sourceTitle: "",
    sourceUrl: "",
    excerpt: "",
    claim: "",
    paragraphId:
      selectedAnchor.value ||
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
    if (!previewScore.value)
      throw new Error("请填写完整的四维评分，待核验项不得发布");
    if (!summary.value.trim()) throw new Error("请填写总评与下一步建议");
    if (
      !confirm(
        "确认发布正式评价？学生将立即看到结果；复评会保留历史并撤回该版社区发布。",
      )
    )
      return;
    await api(`/submissions/${version.value.id}/evaluations`, "POST", {
      items: grading.value.map((i) => ({
        code: i.code,
        score: i.status === "SCORED" ? Number(i.score) : null,
        status: i.status,
        reason: i.reason,
      })),
      summary: summary.value,
      gateReason: gateReason.value,
      expectedRevision: version.value.evaluation?.revision_no || 0,
    });
    version.value = await api(`/submissions/${version.value.id}`);
    await refreshCourse();
    notify("正式评价已发布并留存历史记录");
  });
}
async function publicationRequest() {
  await run(async () => {
    if (
      !confirm("将此版本申请发布到本课程知识社区？需教师审核，不会向校外公开。")
    )
      return;
    await api(`/submissions/${version.value.id}/publication`, "POST");
    version.value = await api(`/submissions/${version.value.id}`);
    notify("已提交课程内发布申请");
  });
}
async function pubAction(id: number, action: string) {
  await run(async () => {
    if (
      !confirm(
        action === "APPROVE"
          ? "确认向课程成员发布此指定版本？"
          : "确认撤回此版本的社区展示？",
      )
    )
      return;
    await api(`/publications/${id}/actions`, "POST", { action, body: "" });
    await refreshCourse();
    if (version.value)
      version.value = await api(`/submissions/${version.value.id}`);
    notify(action === "APPROVE" ? "课程内发布成功" : "已撤回社区展示");
  });
}
async function viewVerification(ev: any) {
  await run(async () => {
    const records = await api(`/evidence/${ev.id}/verifications`);
    showModal("records", { records, sourceTitle: ev.source_title });
  });
}
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) {
    e.preventDefault();
    e.returnValue = "";
  }
}
function keydown(e: KeyboardEvent) {
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
  clearTimeout(saveTimer);
  window.removeEventListener("beforeunload", beforeUnload);
  window.removeEventListener("keydown", keydown);
});
</script>

<template>
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
    <main class="main" :class="{ expanded: sidebarCollapsed }">
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
            >课程空间 <span class="crumb-sep">/</span></span
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
          :teacher="isTeacher"
          :home-entry="homeEntry"
          :home-submission="homeSubmission"
          @navigate="go"
          @open-assignment="openAssignment"
          @open-version="openVersion"
          @publish-assignment="showModal('assignment')"
          @select-course="selectCourse"
        />

        <section v-else-if="page === 'assignments'" class="page">
          <div class="page-heading">
            <div>
              <span class="eyebrow">课程与学习任务</span>
              <h1>{{ isStaff ? "课程作业" : "我的作业" }}</h1>
              <p>从任务要求出发，明确这次创作需要完成什么。</p>
            </div>
            <div v-if="isTeacher" class="button-row">
              <button class="btn secondary" @click="showModal('member')">
                <Users :size="17" />添加成员</button
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
            >
              <div class="card-top">
                <span class="file-icon"><FileText :size="22" /></span
                ><span class="badge blue">知识帖</span>
              </div>
              <h2>{{ a.title }}</h2>
              <p>{{ a.requirements }}</p>
              <div class="meta-line">
                <Clock :size="16" />{{ fmt(a.due_at) }} 截止
              </div>
              <div class="assignment-foot">
                <span class="muted">{{ a.rubric_version }}</span
                ><button
                  class="text-btn"
                  @click="isStaff ? go('review') : openAssignment(a)"
                >
                  {{
                    isStaff ? "查看提交" : a.entry_id ? "继续编辑" : "开始作答"
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
                  q.q_final === null ? "待评审" : `${number(q.q_final)} 分`
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
              ><button class="text-btn" @click="pubAction(p.id, 'WITHDRAW')">
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
            ><span class="muted">仅展示已获批的指定版本</span>
          </div>
          <div class="community-grid">
            <article
              v-for="p in filteredPubs"
              :key="p.id"
              class="knowledge-card"
            >
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

        <section v-else-if="page === 'evidence'" class="page">
          <div class="page-heading">
            <div>
              <span class="eyebrow">来源与论断</span>
              <h1>证据核验</h1>
              <p>核对原始来源、引用内容与论断之间的支持关系。</p>
            </div>
            <span class="small-label">作者不可自核验</span>
          </div>
          <div class="evidence-list">
            <article v-for="e in evidences" :key="e.id" class="evidence-card">
              <div class="card-top">
                <span class="source-label"
                  ><LinkIcon :size="18" />{{ e.source_title }}</span
                ><span
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
                        DISPUTED: "有争议，待教师裁定",
                      } as any
                    )[e.state]
                  }}</span
                >
              </div>
              <h3>{{ e.claim_text }}</h3>
              <blockquote>{{ e.excerpt }}</blockquote>
              <p class="muted">
                {{ e.author_name }} · {{ e.title }} · v{{ e.version_no }}
              </p>
              <a
                v-if="e.source_url"
                :href="e.source_url"
                target="_blank"
                rel="noopener noreferrer"
                class="text-btn"
                >打开来源<ArrowUpRight :size="15"
              /></a>
              <div class="evidence-actions">
                <button class="btn secondary" @click="viewVerification(e)">
                  <History :size="16" />查看核验记录</button
                ><button
                  v-if="e.author_id !== me.id"
                  class="btn secondary"
                  @click="
                    showModal('verify', {
                      id: e.id,
                      sourceTitle: e.source_title,
                    })
                  "
                >
                  <ShieldCheck :size="17" />{{
                    isTeacher ? "核验 / 裁定" : "核对这条证据"
                  }}</button
                ><span v-else class="muted">你的证据，等待其他成员核验</span>
              </div>
            </article>
            <div v-if="!evidences.length" class="empty card">
              <ShieldCheck :size="32" />
              <h2>暂无证据核验任务</h2>
              <p>学生提交带来源的知识帖后，将在这里生成核验记录。</p>
            </div>
          </div>
        </section>

        <section v-else-if="page === 'editor' && entry" class="editing-page">
          <div class="editor-heading">
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
            <div class="button-row">
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
                :disabled="saving || busy"
                @click="saveDraft"
              >
                <Save :size="17" />保存草稿</button
              ><button
                class="btn primary"
                :disabled="saving || busy || conflicted"
                @click="submit"
              >
                <Send :size="17" />提交评审
              </button>
            </div>
          </div>
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
            <aside v-if="outlineVisible" class="document-outline">
              <h3>
                <ListTree :size="16" />{{
                  page === "editor" ? "写作大纲" : "文档大纲"
                }}
              </h3>
              <p>点击段落，定位正文</p>
              <nav aria-label="正文段落">
                <button
                  v-for="(a, i) in anchorOptions(entry.content)"
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
                <span class="eyebrow">知识帖 · 草稿</span
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
                  v-model="entry.content"
                  @update:model-value="changed"
                  @selected="selectedAnchor = $event"
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
                    <p>{{ e.claim }}</p>
                    <span class="muted"
                      >{{ e.paragraphId }} · 提交后待核验</span
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
              <div class="panel-title">
                <h2>评分与反馈</h2>
                <span class="badge">{{
                  latest ? `最近提交 v${latest.version_no}` : "尚未提交"
                }}</span>
              </div>
              <div v-if="latest?.evaluation" class="score-overview">
                <span>教师确认质量分</span>
                <div class="big-score">
                  {{ number(latest.evaluation.q_final) }}<small>/ 100</small>
                </div>
                <div class="dimension-grid">
                  <div v-for="d in rubric" :key="d.d">
                    <span>{{ d.name }}</span
                    ><strong>{{
                      number(latest.evaluation.dimensions[d.d])
                    }}</strong>
                  </div>
                </div>
                <p class="small-note">
                  四维等权 · 分数对应已提交版本，不是当前草稿
                </p>
              </div>
              <div v-else class="no-score">
                <Clock :size="24" />
                <h3>{{ latest ? "等待教师评审" : "写下你的第一版" }}</h3>
                <p>提交后，教师反馈和四维评价会出现在这里。</p>
              </div>
              <div class="tabs">
                <button
                  :class="{ active: feedbackTab === 'feedback' }"
                  @click="feedbackTab = 'feedback'"
                >
                  教师反馈
                  <span>{{ latest?.feedback?.length || 0 }}</span></button
                ><button
                  :class="{ active: feedbackTab === 'versions' }"
                  @click="feedbackTab = 'versions'"
                >
                  版本记录
                </button>
              </div>
              <template v-if="feedbackTab === 'feedback'"
                ><p v-if="latest?.evaluation?.summary" class="teacher-summary">
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
                  <h3>{{ f.body }}</h3>
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
                <p v-if="!latest?.feedback?.length" class="empty-text">
                  还没有教师批注。
                </p></template
              >
              <div v-else class="version-list">
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
              <div class="ai-boundary">
                <ShieldCheck :size="17" /><span
                  >AI 预评未接入。本版所有正式评分由教师发布。</span
                >
              </div>
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
              ><template v-if="page === 'reader' && version.author_id === me.id"
                ><button
                  v-if="
                    !version.publication.length ||
                    version.publication[0].state === 'WITHDRAWN'
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
              <p>点击段落，定位正文</p>
              <nav aria-label="正文段落">
                <button
                  v-for="(a, i) in anchorOptions(version.content)"
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
            </aside>
            <aside v-else v-show="panelVisible" class="feedback-sidebar">
              <div class="panel-title">
                <h2>质量与版本</h2>
                <span class="badge">v{{ version.version_no }}</span>
              </div>
              <div v-if="version.evaluation" class="score-overview">
                <span>教师确认质量分</span>
                <div class="big-score">
                  {{ number(version.evaluation.q_final) }}<small>/ 100</small>
                </div>
                <div class="dimension-grid">
                  <div v-for="d in rubric" :key="d.d">
                    <span>{{ d.name }}</span
                    ><strong>{{
                      number(version.evaluation.dimensions[d.d])
                    }}</strong>
                  </div>
                </div>
                <p class="small-note">
                  {{ version.evaluation.teacher_name }} ·
                  {{ fmt(version.evaluation.created_at) }}
                </p>
                <p v-if="version.evaluation.gate_reason" class="error-note">
                  质量门槛：{{ version.evaluation.gate_reason }}
                </p>
                <p class="teacher-summary">{{ version.evaluation.summary }}</p>
              </div>
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
</template>
