<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { api, ApiFailure } from './api';
import { fmt } from './format';
import RubricViewer from './RubricViewer.vue';

const props = defineProps<{
  courseId: number;
  teacher: boolean;
  userId: number;
  initialSnapshot?: any;
  initialSource?: string;
}>();

const catalog = ref<any>(null);
const selected = ref<any>(null);
const draft = ref<any>(null);
const baseline = ref('');
const error = ref('');
const message = ref('');
const busy = ref(false);
const reason = ref('');
const editing = ref(false);
const confirming = ref(false);
const savedAt = ref('');
const openItems = ref<Record<string, boolean>>({});
const bulk = ref<Record<string, any>>({});
const usage = ref<Record<string, number>>({});
// 保存冲突（其他窗口先保存了同一份草稿）和本机未同步备份。
const conflict = ref(false);
const recovery = ref<any>(null);
let alive = true;

const pinned = computed(() => !!props.initialSnapshot);
const dirty = computed(() => !!draft.value && JSON.stringify(draft.value) !== baseline.value);
const editable = computed(() => props.teacher && !pinned.value && selected.value?.state === 'DRAFT');
// 新建修订时先只在页面里复制一份，第一次保存才在服务器上建草稿，取消就不会留下空草稿。
const unsaved = computed(() => editing.value && selected.value?.state === 'DRAFT' && selected.value?.id == null);
// 库里可能残留多份从没保存过的旧草稿；只有最新那份可以编辑，列表里也只列它。
const draftItem = computed(() => (catalog.value?.items || []).find((r: any) => r.state === 'DRAFT') || null);
const versions = computed(() => (catalog.value?.items || []).filter((r: any) => r.state !== 'DRAFT' || r.id === draftItem.value?.id));
const live = computed(() => (catalog.value?.items || []).find((r: any) => r.id === catalog.value?.defaultId) || null);
const viewingLive = computed(() => !!selected.value && selected.value.code === catalog.value?.defaultCode);
const anchors: [string, string][] = [
  ['excellent', '优秀 90–100'],
  ['good', '良好 75–89'],
  ['pass', '基本 60–74'],
  ['improve', '待改进 0–59'],
];
const key = (id: number | null | undefined) => `community-rubric-${props.userId}-${props.courseId}-${id ?? 'new'}`;

function day(d: string) {
  return d ? new Date(d).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }) : '';
}
/** 版本的可读名：内置版本 / v3 草稿 · 10/31 修改 / v2 已启用 · 10/26 启用。 */
function versionLabel(r: any) {
  if (!r) return '';
  if (Number(r.id) === 0) return '内置版本';
  return r.state === 'DRAFT'
    ? `v${r.version_no} 草稿${r.updated_at ? ' · ' + day(r.updated_at) + ' 修改' : ''}`
    : `v${r.version_no} 已启用${r.enabled_at ? ' · ' + day(r.enabled_at) + ' 启用' : ''}`;
}
function optionLabel(r: any) {
  return `${versionLabel(r)} — ${r.title}`;
}
const selectedRow = computed(() => (catalog.value?.items || []).find((r: any) => r.code === selected.value?.code) || selected.value);
const editLabel = computed(() => (unsaved.value ? `基于${versionLabel(sourceRow.value)}的新修订 · 尚未保存` : versionLabel(selectedRow.value)));
const sourceRow = ref<any>(null);
const draftBackup = computed(() => (draftItem.value ? readBackup(draftItem.value.id) : null));

/** 四维：代码固定，名称 / 说明 / 权重可编辑。 */
const dimensions = computed<any[]>(() => {
  const rows = draft.value?.dimensions;
  return Array.isArray(rows) ? rows : [];
});
const weightSum = computed(() => dimensions.value.reduce((n, d) => n + (Number(d.weight) || 0), 0));
const weightOk = computed(() => weightSum.value === 100);
const allOpen = computed(() =>
  !!draft.value && draft.value.criteria.every((i: any) => openItems.value[i.code]),
);
const state = computed(() =>
  busy.value ? '正在保存…' : dirty.value ? '有未保存的修改' : savedAt.value ? `已保存 · ${fmt(savedAt.value)}` : unsaved.value ? '未修改 · 保存后才生成草稿' : '未修改',
);
function itemsOf(code: string) {
  return (draft.value?.criteria || []).filter((i: any) => String(i.code).startsWith(code));
}
function dimOpen(code: string) {
  const items = itemsOf(code);
  return items.length > 0 && items.every((i: any) => openItems.value[i.code]);
}
function toggleItem(code: string) {
  openItems.value = { ...openItems.value, [code]: !openItems.value[code] };
}
function toggleDim(code: string) {
  const next = !dimOpen(code);
  const map = { ...openItems.value };
  for (const i of itemsOf(code)) map[i.code] = next;
  openItems.value = map;
}
function toggleAll() {
  const next = !allOpen.value;
  const map: Record<string, boolean> = {};
  for (const i of draft.value.criteria) map[i.code] = next;
  openItems.value = map;
}
function preview(i: any) {
  const text = String(i.description || '').trim();
  return text.length > 42 ? text.slice(0, 42) + '…' : text;
}
// 统一设置某个维度的四档描述：先带出各项相同的写法，填好后一次写进本维度所有指标。
function toggleBulk(code: string) {
  if (bulk.value[code]) {
    const { [code]: _, ...rest } = bulk.value;
    bulk.value = rest;
    return;
  }
  const items = itemsOf(code);
  const row: Record<string, string> = {};
  for (const [k] of anchors) row[k] = items.every((i: any) => i[k] === items[0]?.[k]) ? items[0]?.[k] || '' : '';
  bulk.value = { ...bulk.value, [code]: row };
}
function applyBulk(code: string) {
  const row = bulk.value[code];
  if (!row) return;
  for (const i of itemsOf(code)) for (const [k] of anchors) if (String(row[k] || '').trim()) i[k] = row[k].trim();
  const { [code]: _, ...rest } = bulk.value;
  bulk.value = rest;
  message.value = `已把四档描述写入 ${code} 维度的 ${itemsOf(code).length} 项指标，保存后生效。`;
}

function payload() {
  return {
    title: draft.value.title,
    instructions: draft.value.instructions,
    dimensions: dimensions.value.map((d: any) => ({
      code: d.code,
      name: d.name,
      description: d.description,
      weight: Number(d.weight) || 0,
    })),
    criteria: draft.value.criteria.map((i: any) => ({
      code: i.code,
      description: i.description,
      excellent: i.excellent,
      good: i.good,
      pass: i.pass,
      improve: i.improve,
    })),
    revision: selected.value.revision,
  };
}

function readBackup(id: number | null | undefined) {
  try {
    const raw = localStorage.getItem(key(id));
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}
function clearBackup(id: number | null | undefined) {
  try { localStorage.removeItem(key(id)); } catch {}
}
const content = (d: any) => JSON.stringify({ title: d?.title, instructions: d?.instructions, dimensions: d?.dimensions, criteria: d?.criteria });
// 进入编辑时，如果本机有和服务器不一样的未同步修改，就提示恢复。
function checkRecovery() {
  const saved = readBackup(selected.value?.id);
  if (!saved) { recovery.value = null; return; }
  const local = { ...draft.value, ...saved, dimensions: saved.dimensions, criteria: (draft.value?.criteria || []).map((i: any) => ({ ...i, ...(saved.criteria || []).find((x: any) => x.code === i.code) })) };
  recovery.value = content(local) === content(draft.value) ? null : saved;
  if (!recovery.value) clearBackup(selected.value?.id);
}
function restoreRecovery() {
  const saved = recovery.value;
  if (!saved || !draft.value) return;
  draft.value.title = saved.title;
  draft.value.instructions = saved.instructions;
  if (Array.isArray(saved.dimensions)) draft.value.dimensions = draft.value.dimensions.map((d: any) => ({ ...d, ...saved.dimensions.find((x: any) => x.code === d.code) }));
  if (Array.isArray(saved.criteria)) for (const i of draft.value.criteria) Object.assign(i, saved.criteria.find((x: any) => x.code === i.code) || {});
  recovery.value = null;
  message.value = '已恢复本机的修改，核对后保存。';
}
function discardRecovery() {
  clearBackup(selected.value?.id);
  recovery.value = null;
}

function sessionBackup() {
  if (!dirty.value || !selected.value) return null;
  const data = { rubricId: selected.value.id, courseId: props.courseId, savedAt: new Date().toISOString(), baseRevision: selected.value.revision, ...payload() };
  // 本机存储写不进去时要如实返回 persisted:false，App 才会在登录失效时要求先下载备份。
  let persisted = false;
  try {
    localStorage.setItem(key(selected.value.id), JSON.stringify(data));
    persisted = localStorage.getItem(key(selected.value.id)) === JSON.stringify(data);
  } catch {}
  return { data, persisted };
}

function canLeave() {
  if (busy.value) {
    error.value = '正在保存量规，请稍候。';
    return false;
  }
  if (editing.value && dirty.value) {
    sessionBackup();
    error.value = '量规有未保存的修改，请先保存或取消。';
    return false;
  }
  return true;
}

defineExpose({ canLeave, sessionBackup });

async function task(fn: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  message.value = '';
  try {
    await fn();
  } catch (e: any) {
    if (!alive) return;
    error.value = e.message || '操作失败，请重试';
    if (e instanceof ApiFailure && e.status === 409 && editing.value) conflict.value = true;
  } finally {
    if (alive) busy.value = false;
  }
}

async function fetchCatalog() {
  const data = await api(`/courses/${props.courseId}/rubrics`);
  if (alive) catalog.value = data;
}

// 「几份作业在用」只是提示，读取失败不影响量规本身。
async function fetchUsage() {
  if (!props.teacher) return;
  try {
    const rows = await api(`/courses/${props.courseId}/assignments`);
    const map: Record<string, number> = {};
    for (const a of rows || []) if (a.rubric_version) map[a.rubric_version] = (map[a.rubric_version] || 0) + 1;
    if (alive) usage.value = map;
  } catch {}
}

function setSelected(data: any) {
  selected.value = data;
  draft.value = JSON.parse(JSON.stringify(data));
  baseline.value = JSON.stringify(draft.value);
}

// 打开页面时显示正在生效的版本；未启用的草稿只在旁边提示，不默认展示。
async function load() {
  await task(async () => {
    await fetchCatalog();
    if (!alive) return;
    if (pinned.value) {
      selected.value = props.initialSnapshot;
      draft.value = null;
      return;
    }
    const data = await api(`/courses/${props.courseId}/rubrics/${catalog.value.defaultCode}`);
    if (alive) setSelected(data);
  });
  void fetchUsage();
}

async function choose(code: string) {
  if (!code || !canLeave()) return;
  await task(async () => {
    const data = await api(`/courses/${props.courseId}/rubrics/${code}`);
    if (alive) {
      setSelected(data);
      editing.value = false;
    }
  });
}

function onVersionChange(event: Event) {
  const input = event.target as HTMLSelectElement;
  const code = input.value;
  input.value = selected.value?.code || '';
  void choose(code);
}

/** 唯一的编辑入口：有草稿就打开草稿，没有就以当前查看的已启用版本为底新建一份。 */
async function startEdit() {
  if (!props.teacher || pinned.value) return;
  await task(async () => {
    if (draftItem.value) {
      const data = selected.value?.code === draftItem.value.code
        ? selected.value
        : await api(`/courses/${props.courseId}/rubrics/${draftItem.value.code}`);
      if (!alive) return;
      setSelected(data);
      editing.value = true;
      savedAt.value = '';
      conflict.value = false;
      return;
    }
    const source = selected.value?.state === 'ENABLED'
      ? selected.value
      : await api(`/courses/${props.courseId}/rubrics/${catalog.value.defaultCode}`);
    if (!alive) return;
    sourceRow.value = (catalog.value?.items || []).find((r: any) => r.code === source.code) || source;
    setSelected({ ...JSON.parse(JSON.stringify(source)), id: null, code: null, state: 'DRAFT', revision: 0, sourceId: Number(source.id) || 0 });
    editing.value = true;
    savedAt.value = '';
    message.value = '这是一份修订副本，第一次保存时才会生成草稿；取消不会留下任何记录。';
  });
  if (editing.value) checkRecovery();
}

function weightError() {
  error.value = `四个维度的权重合计必须为 100%，当前为 ${weightSum.value}%。`;
}

async function putDraft() {
  const previousId = selected.value.id;
  if (previousId == null) {
    const created = await api(`/courses/${props.courseId}/rubrics`, 'POST', { sourceId: selected.value.sourceId ?? catalog.value.defaultId });
    if (!alive) return;
    selected.value = { ...selected.value, id: created.id, code: created.code, revision: created.revision, version_no: created.version_no };
  }
  const data = await api(`/courses/${props.courseId}/rubrics/${selected.value.id}`, 'PUT', payload());
  if (!alive) return;
  setSelected(data);
  clearBackup(previousId);
  clearBackup(data.id);
  conflict.value = false;
  recovery.value = null;
  savedAt.value = new Date().toISOString();
}

async function save() {
  if (!editable.value || (!dirty.value && !unsaved.value)) return;
  if (!weightOk.value) return weightError();
  await task(async () => {
    await putDraft();
    if (!alive) return;
    await fetchCatalog();
    message.value = '草稿已保存。';
  });
}

function askEnable() {
  if (!editable.value) return;
  if (!weightOk.value) return weightError();
  error.value = '';
  confirming.value = true;
}

// 启用前如有未保存的修改，先保存再启用，老师不用分两步点。
async function activate() {
  if (!editable.value || !reason.value.trim()) return;
  if (!weightOk.value) return weightError();
  await task(async () => {
    if (dirty.value || unsaved.value) await putDraft();
    if (!alive) return;
    const data = await api(`/courses/${props.courseId}/rubrics/${selected.value.id}/enable`, 'POST', {
      revision: selected.value.revision,
      catalogRevision: catalog.value.catalogRevision,
      reason: reason.value.trim(),
    });
    if (!alive) return;
    setSelected(data);
    clearBackup(data.id);
    editing.value = false;
    confirming.value = false;
    reason.value = '';
    await fetchCatalog();
    message.value = '已启用，新作业默认使用此版本。';
  });
  void fetchUsage();
}

// 其他窗口先保存了这份草稿：载入服务器上的最新版本号，保留当前输入，再保存就以当前输入为准。
async function keepMine() {
  await task(async () => {
    const latest = await api(`/courses/${props.courseId}/rubrics/${selected.value.code}`);
    if (!alive) return;
    if (latest.state !== 'DRAFT') {
      error.value = '这份草稿已在其他窗口启用，不能再修改。你的输入仍在页面上，可先复制，再取消并新建修订版本。';
      return;
    }
    const mine = draft.value;
    selected.value = latest;
    baseline.value = JSON.stringify(JSON.parse(JSON.stringify(latest)));
    draft.value = mine;
    conflict.value = false;
    message.value = '已载入最新版本，你的输入保留；保存后会覆盖其他窗口的修改。';
  });
}
async function takeTheirs() {
  await task(async () => {
    const latest = await api(`/courses/${props.courseId}/rubrics/${selected.value.code}`);
    if (!alive) return;
    setSelected(latest);
    clearBackup(latest.id);
    conflict.value = false;
    if (latest.state !== 'DRAFT') editing.value = false;
    message.value = '已改为显示其他窗口保存的版本。';
  });
}

function cancel() {
  if (dirty.value && !window.confirm('放弃未保存的修改？')) return;
  clearBackup(selected.value?.id);
  editing.value = false;
  confirming.value = false;
  conflict.value = false;
  recovery.value = null;
  bulk.value = {};
  // 新修订还没保存过：回到原来查看的版本。
  if (selected.value?.id == null && sourceRow.value) void choose(sourceRow.value.code);
  else setSelected(selected.value);
  message.value = '';
}

// 编辑时持续在本机留一份未同步备份：浏览器崩溃或误关页面后，下次进来能恢复。
let backupTimer: ReturnType<typeof setTimeout> | undefined;
watch(draft, () => {
  clearTimeout(backupTimer);
  backupTimer = setTimeout(() => {
    if (!editing.value || !selected.value) return;
    if (dirty.value) sessionBackup();
    else clearBackup(selected.value.id);
  }, 600);
}, { deep: true });

function unload(e: BeforeUnloadEvent) {
  if (dirty.value) {
    sessionBackup();
    e.preventDefault();
    e.returnValue = '';
  }
}

onMounted(() => {
  window.addEventListener('beforeunload', unload);
  void load();
});
onBeforeUnmount(() => {
  clearTimeout(backupTimer);
  alive = false;
  window.removeEventListener('beforeunload', unload);
});
</script>

<template>
  <section class="page rubric-workbench">
    <template v-if="!editing">
      <header class="page-heading">
        <div>
          <h1>课程量规</h1>
          <p v-if="pinned">正在查看这份提交绑定的量规快照，不随课程默认量规变化。</p>
          <p v-else-if="live" class="rw-live">
            当前生效：<strong>{{ live.title }}</strong> · {{ versionLabel(live) }}<template v-if="teacher"> · {{ usage[live.code] || 0 }} 份作业在用</template>
          </p>
          <p v-else>四维按权重加权，11 项二级指标固定。</p>
        </div>
        <div v-if="catalog && !pinned" class="rw-head-actions">
          <label v-if="versions.length > 1" class="rw-version-label">
            查看版本
            <select class="rw-version" aria-label="查看量规版本" :value="selected?.code" :disabled="busy" @change="onVersionChange">
              <option v-for="r in versions" :key="r.code" :value="r.code">{{ optionLabel(r) }}</option>
            </select>
          </label>
          <button v-if="teacher" class="btn primary rw-edit" :disabled="busy" @click="startEdit">
            {{ draftItem ? '继续编辑草稿' : '新建修订版本' }}
          </button>
        </div>
      </header>

      <p v-if="error" class="notice danger" role="alert">
        {{ error }}
        <button v-if="!catalog" type="button" class="text-btn" :disabled="busy" @click="load">重新读取</button>
      </p>
      <p v-if="message" class="notice" role="status">{{ message }}</p>
      <p v-if="!catalog && !error">正在读取量规…</p>
      <p v-if="teacher && !pinned && draftItem && selected?.code !== draftItem.code" class="rw-draft-note">
        有一份未启用的草稿（{{ versionLabel(draftItem) }}），启用前不影响任何作业。<template v-if="draftBackup">本机还有上次没保存的修改，进入编辑时可以恢复。</template>
        <button type="button" class="text-btn" :disabled="busy" @click="startEdit">继续编辑</button>
      </p>

      <div v-if="catalog && selected" class="rw-current">
        <div class="rw-current-head">
          <strong>{{ selected.title }}</strong>
          <span class="comm-meta-line">{{ pinned ? '提交时的量规快照' : versionLabel(selectedRow) }}</span>
          <span v-if="pinned" class="comm-tag">当前提交绑定的量规快照</span>
          <span v-else-if="viewingLive" class="comm-tag blue">当前生效</span>
          <span v-else-if="selected.state === 'DRAFT'" class="comm-tag orange">草稿 · 未启用</span>
          <span v-else class="comm-tag">已启用 · 非当前默认</span>
          <span class="grow"></span>
          <span v-if="teacher && !pinned && !viewingLive && usage[selected.code]" class="comm-meta-line">{{ usage[selected.code] }} 份作业在用</span>
        </div>
        <RubricViewer :snapshot="selected" :source="pinned ? initialSource : undefined" />
      </div>
    </template>

    <template v-else>
      <header class="page-heading">
        <div>
          <h1>{{ unsaved ? '新建修订版本' : '编辑量规草稿' }}</h1>
          <p>{{ editLabel }} · 启用后只影响新发布的作业，已有作业和已交的作业不变。</p>
        </div>
      </header>

      <div class="rw-sticky">
        <div class="rw-toolbar" role="toolbar" aria-label="草稿操作">
          <span class="rw-state" :class="{ dirty }">{{ state }}</span>
          <span v-if="!weightOk" class="rw-warn">权重合计 {{ weightSum }}%，需为 100%</span>
          <span class="grow"></span>
          <button type="button" class="text-btn" :disabled="busy" @click="cancel">取消</button>
          <button type="button" class="btn secondary" :disabled="busy || !dirty || !weightOk" :title="!dirty ? '还没有修改' : ''" @click="save">保存草稿</button>
          <button type="button" class="btn primary" :disabled="busy || !weightOk || confirming" @click="askEnable">启用为新作业默认</button>
        </div>
        <div v-if="confirming" class="rw-confirm" role="group" aria-label="启用量规">
          <p>启用后，新发布的作业默认使用这一版；已有作业和已交的作业不变。<template v-if="dirty">未保存的修改会先保存。</template></p>
          <label>启用说明（必填，仅任课教师可见）<textarea v-model="reason" rows="2" maxlength="1000" placeholder="例如：补充了教材页码要求" /></label>
          <div class="rw-confirm-actions">
            <button type="button" class="btn primary" :disabled="busy || !reason.trim()" @click="activate">确认启用</button>
            <button type="button" class="text-btn" :disabled="busy" @click="confirming = false">返回编辑</button>
          </div>
        </div>
      </div>

      <div v-if="recovery" class="rw-recovery" role="alert">
        <p><strong>发现本机未同步的修改</strong>{{ recovery.savedAt ? '（' + fmt(recovery.savedAt) + '）' : '' }}。上次离开或掉线时没有保存到服务器。</p>
        <div class="rw-recovery-actions">
          <button type="button" class="btn secondary" :disabled="busy" @click="restoreRecovery">恢复到编辑器</button>
          <button type="button" class="text-btn" :disabled="busy" @click="discardRecovery">丢弃本机修改</button>
        </div>
      </div>
      <p v-if="error" class="notice danger" role="alert">{{ error }}</p>
      <div v-if="conflict" class="rw-recovery">
        <p>你可以保留自己的输入并以它为准再保存，或者放弃自己的输入，查看其他窗口保存的版本。</p>
        <div class="rw-recovery-actions">
          <button type="button" class="btn secondary" :disabled="busy" @click="keepMine">载入最新版本并保留我的输入</button>
          <button type="button" class="text-btn" :disabled="busy" @click="takeTheirs">放弃我的输入，看对方的版本</button>
        </div>
      </div>
      <p v-if="message" class="notice" role="status">{{ message }}</p>

      <form class="rw-form" @submit.prevent="save">
        <fieldset :disabled="busy">
          <section class="rw-block">
            <h2 class="rw-step"><i>1</i>基本信息</h2>
            <div class="rw-basic">
              <label>量规标题<input v-model="draft.title" maxlength="120" required /></label>
              <label>总体说明<textarea v-model="draft.instructions" maxlength="2000" rows="3" required /></label>
            </div>
          </section>

          <section class="rw-block">
            <h2 class="rw-step">
              <i>2</i>四维设置
              <span class="rw-sum" :class="{ bad: !weightOk }">
                权重合计 {{ weightSum }}%
                <em v-if="!weightOk">需为 100%</em>
              </span>
            </h2>
            <p class="rw-hint">维度代码 S / A / C / L 固定，可改名称、说明与权重；权重决定该维在总分中的占比。</p>
            <div class="rw-dims">
              <article v-for="d in dimensions" :key="d.code" class="rw-dim-card">
                <div class="rw-dim-top">
                  <span class="rw-dim-code">{{ d.code }}</span>
                  <input v-model="d.name" class="rw-dim-name" maxlength="40" :aria-label="d.code + ' 维度名称'" placeholder="维度名称" required />
                  <span class="rw-dim-weight">
                    <input v-model.number="d.weight" type="number" min="0" max="100" step="1" :aria-label="d.code + ' 维度权重'" required />%
                  </span>
                </div>
                <input v-model="d.description" class="rw-dim-desc" maxlength="500" :aria-label="d.code + ' 维度说明'" placeholder="这个维度考察什么" required />
                <p class="rw-dim-count">{{ itemsOf(d.code).length }} 项指标</p>
              </article>
            </div>
          </section>

          <section class="rw-block">
            <h2 class="rw-step">
              <i>3</i>指标锚点
              <button type="button" class="text-btn rw-toggle-all" @click="toggleAll">{{ allOpen ? '全部收起' : '全部展开' }}</button>
            </h2>
            <p class="rw-hint">每项指标有一句说明和四档描述。同一维度的四档写法相同时，可用「统一四档描述」一次写好。</p>

            <div v-for="d in dimensions" :key="d.code" class="rw-dim-group">
              <div class="rw-dim-group-head">
                <span class="rw-dim-code">{{ d.code }}</span>
                <strong>{{ d.name }}</strong>
                <span class="rw-dim-group-weight">{{ d.weight }}%</span>
                <span class="grow"></span>
                <button type="button" class="text-btn" :aria-expanded="!!bulk[d.code]" @click="toggleBulk(d.code)">统一四档描述</button>
                <button type="button" class="text-btn" @click="toggleDim(d.code)">{{ dimOpen(d.code) ? '收起本维度' : '展开本维度' }}</button>
              </div>
              <div v-if="bulk[d.code]" class="rw-bulk">
                <p>填写后点「写入」，会覆盖 {{ d.code }} 维度 {{ itemsOf(d.code).length }} 项指标的对应档位；留空的档位不改。</p>
                <div class="rw-anchors">
                  <label v-for="a in anchors" :key="a[0]">{{ a[1] }}<input v-model="bulk[d.code][a[0]]" maxlength="1000" :aria-label="d.code + ' 统一' + a[1]" /></label>
                </div>
                <div class="rw-bulk-actions">
                  <button type="button" class="btn secondary" @click="applyBulk(d.code)">写入 {{ itemsOf(d.code).map((i: any) => i.code).join(' / ') }}</button>
                  <button type="button" class="text-btn" @click="toggleBulk(d.code)">取消</button>
                </div>
              </div>
              <article v-for="i in itemsOf(d.code)" :key="i.code" class="rw-item">
                <button type="button" class="rw-item-head" :aria-expanded="!!openItems[i.code]" @click="toggleItem(i.code)">
                  <span class="rw-caret" :class="{ open: !!openItems[i.code] }">›</span>
                  <b>{{ i.code }}</b>
                  <span class="rw-item-title">{{ i.name }}</span>
                  <span class="rw-item-preview">{{ openItems[i.code] ? '收起' : preview(i) || '展开编辑' }}</span>
                </button>
                <div v-if="openItems[i.code]" class="rw-item-body">
                  <label class="rw-item-desc">指标说明<input v-model="i.description" maxlength="1000" required /></label>
                  <div class="rw-anchors">
                    <label v-for="a in anchors" :key="a[0]">{{ a[1] }}<input v-model="i[a[0]]" maxlength="1000" required /></label>
                  </div>
                </div>
              </article>
            </div>
          </section>
        </fieldset>
      </form>
    </template>
  </section>
</template>

<style scoped>
.rubric-workbench { max-width: 1040px; }
.rw-live strong { font-weight: 600; color: var(--text); }
.rw-head-actions { display: flex; align-items: flex-end; gap: 10px; }
.rw-version-label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--muted); }
.rw-version { width: auto; max-width: 340px; padding: 7px 10px; font-size: 13px; }
.rw-edit { white-space: nowrap; }
.rw-draft-note { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin: 0 0 14px; padding: 10px 14px; font-size: 13px; color: var(--muted); border: 1px dashed var(--border); border-radius: 10px; background: var(--paper); }
.rw-current { border: 1px solid var(--border); border-radius: 12px; background: var(--paper); padding: 18px 22px 22px; }
.rw-current-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding-bottom: 14px; margin-bottom: 14px; border-bottom: 1px solid var(--line-soft); }
.rw-current-head strong { font-size: 15px; }

/* 编辑工具条贴在顶栏下方，不压在正文上。 */
.rw-sticky { position: sticky; top: 56px; z-index: 4; margin: 0 0 6px; background: var(--workspace, var(--bg)); padding: 8px 0; }
.rw-toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 10px 14px; border: 1px solid var(--border); border-radius: 12px; background: var(--paper); }
.rw-state { font-size: 13px; color: var(--muted); }
.rw-state.dirty { color: var(--text); font-weight: 600; }
.rw-warn { font-size: 12.5px; color: var(--red); }
.rw-confirm { margin-top: 8px; padding: 14px 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--paper); }
.rw-confirm p { margin: 0 0 10px; font-size: 13px; color: var(--muted); }
.rw-confirm label { display: block; font-size: 13px; color: var(--muted); }
.rw-confirm textarea { display: block; width: 100%; margin-top: 6px; box-sizing: border-box; }
.rw-confirm-actions { display: flex; align-items: center; gap: 12px; margin-top: 10px; }
.rw-recovery { margin: 0 0 12px; padding: 12px 16px; border: 1px solid var(--border); border-left: 3px solid var(--blue); border-radius: 10px; background: var(--paper); }
.rw-recovery p { margin: 0; font-size: 13px; color: var(--muted); }
.rw-recovery strong { color: var(--text); }
.rw-recovery-actions { display: flex; align-items: center; gap: 12px; margin-top: 10px; }

.rw-form fieldset { border: 0; padding: 0; min-width: 0; }
.rw-block { margin-top: 22px; }
.rw-block:first-child { margin-top: 4px; }
.rw-step { display: flex; align-items: center; gap: 8px; margin: 0 0 4px; font-size: 15px; }
.rw-step i { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 50%; background: var(--blue-soft); color: var(--blue-text); font-size: 12px; font-style: normal; font-weight: 700; }
.rw-sum { margin-left: auto; font-size: 12px; font-weight: 600; color: var(--muted); background: var(--surface); border-radius: 999px; padding: 3px 10px; }
.rw-sum.bad { color: var(--red); background: var(--red-soft, #fdecec); }
.rw-sum em { font-style: normal; margin-left: 6px; }
.rw-hint { margin: 6px 0 12px; font-size: 12.5px; color: var(--muted); line-height: 1.6; }
.rw-toggle-all { margin-left: auto; font-size: 12.5px; }

.rw-basic label { display: block; margin: 12px 0; font-size: 13px; color: var(--muted); }
.rw-basic input, .rw-basic textarea { display: block; width: 100%; margin-top: 6px; box-sizing: border-box; }

.rw-dims { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.rw-dim-card { border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; background: var(--paper); }
.rw-dim-top { display: flex; align-items: center; gap: 8px; }
.rw-dim-code { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; height: 22px; padding: 0 5px; border-radius: 5px; background: var(--blue-soft); color: var(--blue-text); font-size: 12px; font-weight: 700; }
.rw-dim-name { flex: 1 1 auto; min-width: 0; font-weight: 600; }
.rw-dim-weight { display: inline-flex; align-items: center; gap: 2px; font-size: 12px; color: var(--muted); white-space: nowrap; }
.rw-dim-weight input { width: 58px; text-align: right; }
.rw-dim-desc { display: block; width: 100%; margin-top: 8px; box-sizing: border-box; font-size: 13px; }
.rw-dim-count { margin: 8px 0 0; font-size: 11.5px; color: var(--subtle); }

.rw-dim-group { margin-top: 16px; }
.rw-dim-group-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
.rw-dim-group-head strong { font-size: 14px; }
.rw-dim-group-head .text-btn { font-size: 12.5px; }
.rw-dim-group-weight { font-size: 12px; color: var(--blue-text); font-weight: 600; }
.rw-bulk { margin-top: 8px; padding: 12px 14px; border: 1px solid var(--border); border-radius: 10px; background: var(--surface); }
.rw-bulk p { margin: 0 0 4px; font-size: 12.5px; color: var(--muted); }
.rw-bulk label { display: block; margin-top: 8px; font-size: 13px; color: var(--muted); }
.rw-bulk input { display: block; width: 100%; margin-top: 6px; box-sizing: border-box; }
.rw-bulk-actions { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.rw-item { border: 1px solid var(--border); border-radius: 10px; margin-top: 8px; overflow: hidden; background: var(--paper); }
.rw-item-head { display: flex; align-items: center; gap: 8px; width: 100%; padding: 10px 14px; background: none; border: 0; text-align: left; cursor: pointer; font: inherit; }
.rw-item-head:hover { background: var(--surface); }
.rw-caret { display: inline-block; width: 12px; color: var(--subtle); transition: transform .15s ease; }
.rw-caret.open { transform: rotate(90deg); }
.rw-item-head b { font-size: 12px; color: var(--blue-text); background: var(--blue-soft); border-radius: 4px; padding: 1px 6px; }
.rw-item-title { font-size: 14px; font-weight: 600; color: var(--text); white-space: nowrap; }
.rw-item-preview { margin-left: auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; color: var(--subtle); }
.rw-item-body { padding: 0 14px 14px; }
.rw-item-body label { display: block; margin: 10px 0 0; font-size: 13px; color: var(--muted); }
.rw-item-body input { display: block; width: 100%; margin-top: 6px; box-sizing: border-box; }
.rw-anchors { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 14px; margin-top: 4px; }
.rubric-workbench p { overflow-wrap: anywhere; }
@media (max-width: 860px) {
  .rw-dims { grid-template-columns: minmax(0, 1fr); }
  .rw-anchors { grid-template-columns: minmax(0, 1fr); }
  .rw-head-actions { flex-wrap: wrap; }
  .rw-version { max-width: 100%; }
  .rw-item-preview { display: none; }
}
@media (max-width: 720px) {
  .rw-sticky { top: 54px; }
}
</style>
