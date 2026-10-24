<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { api } from './api';
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
let alive = true;

const pinned = computed(() => !!props.initialSnapshot);
const dirty = computed(() => !!draft.value && JSON.stringify(draft.value) !== baseline.value);
const editable = computed(() => props.teacher && !pinned.value && selected.value?.state === 'DRAFT');
const anchors: [string, string][] = [
  ['excellent', '优秀 90–100'],
  ['good', '良好 75–89'],
  ['pass', '基本 60–74'],
  ['improve', '待改进 0–59'],
];
const key = (id: number) => `community-rubric-${props.userId}-${props.courseId}-${id}`;

function payload() {
  return {
    title: draft.value.title,
    instructions: draft.value.instructions,
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

function sessionBackup() {
  if (!dirty.value || !selected.value) return null;
  const data = { rubricId: selected.value.id, courseId: props.courseId, ...payload() };
  try {
    localStorage.setItem(key(selected.value.id), JSON.stringify(data));
  } catch {}
  return { data, persisted: true };
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
    if (alive) error.value = e.message || '操作失败，请重试';
  } finally {
    if (alive) busy.value = false;
  }
}

async function fetchCatalog() {
  const data = await api(`/courses/${props.courseId}/rubrics`);
  if (alive) catalog.value = data;
}

function setSelected(data: any) {
  selected.value = data;
  draft.value = JSON.parse(JSON.stringify(data));
  baseline.value = JSON.stringify(draft.value);
}

async function load() {
  await task(async () => {
    await fetchCatalog();
    if (!alive) return;
    if (pinned.value) {
      selected.value = props.initialSnapshot;
      draft.value = null;
      return;
    }
    const items = catalog.value?.items || [];
    const draftItem = props.teacher ? items.find((r: any) => r.state === 'DRAFT') : null;
    const code = draftItem ? draftItem.code : catalog.value.defaultCode;
    const data = await api(`/courses/${props.courseId}/rubrics/${code}`);
    if (alive) setSelected(data);
  });
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

async function startEdit() {
  if (!props.teacher || pinned.value) return;
  if (selected.value?.state === 'DRAFT') {
    setSelected(selected.value);
    editing.value = true;
    return;
  }
  await task(async () => {
    const sourceId = selected.value?.state === 'ENABLED' ? selected.value.id : catalog.value.defaultId;
    const data = await api(`/courses/${props.courseId}/rubrics`, 'POST', { sourceId });
    if (!alive) return;
    setSelected(data);
    editing.value = true;
    await fetchCatalog();
    message.value = '已创建修订草稿，启用后才影响新作业。';
  });
}

async function save() {
  if (!editable.value) return;
  await task(async () => {
    const data = await api(`/courses/${props.courseId}/rubrics/${selected.value.id}`, 'PUT', payload());
    if (!alive) return;
    setSelected(data);
    await fetchCatalog();
    message.value = '草稿已保存。';
  });
}

async function activate() {
  if (!editable.value || !reason.value.trim()) return;
  await task(async () => {
    const data = await api(`/courses/${props.courseId}/rubrics/${selected.value.id}/enable`, 'POST', {
      revision: selected.value.revision,
      catalogRevision: catalog.value.catalogRevision,
      reason: reason.value.trim(),
    });
    if (!alive) return;
    setSelected(data);
    editing.value = false;
    reason.value = '';
    await fetchCatalog();
    message.value = '已启用，新作业默认使用此版本。';
  });
}

function cancel() {
  if (dirty.value && !window.confirm('放弃未保存的修改？')) return;
  editing.value = false;
  setSelected(selected.value);
  message.value = '';
}

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
  alive = false;
  window.removeEventListener('beforeunload', unload);
});
</script>

<template>
  <section class="page rubric-workbench">
    <header class="page-heading">
      <div>
        <h1>课程量规</h1>
        <p>四维等权，11 项指标固定。</p>
      </div>
      <div class="rw-head-actions">
        <select
          v-if="catalog && catalog.items.length > 1 && !pinned"
          class="rw-version"
          aria-label="查看量规版本"
          :value="selected?.code"
          :disabled="busy || editing"
          @change="onVersionChange"
        >
          <option v-for="r in catalog.items" :key="r.code" :value="r.code">
            {{ r.title }} · {{ r.state === 'DRAFT' ? '草稿' : '已启用' }}
          </option>
        </select>
        <button
          v-if="props.teacher && !pinned && !editing"
          class="btn primary"
          :disabled="busy"
          @click="startEdit"
        >
          {{ selected?.state === 'DRAFT' ? '继续编辑' : '修改量规' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="notice danger" role="alert">{{ error }}</p>
    <p v-if="message" class="notice" role="status">{{ message }}</p>
    <p v-if="!catalog && !error">正在读取量规…</p>

    <template v-if="catalog">
      <div v-if="!editing" class="rw-current">
        <div class="rw-current-head">
          <span class="comm-tag" :class="selected?.state === 'DRAFT' ? 'orange' : 'green'">
            {{ selected?.state === 'DRAFT' ? '草稿 · 未启用' : '已启用' }}
          </span>
          <strong>{{ selected?.title }}</strong>
          <span class="comm-meta-line">{{ selected?.code }}</span>
          <span v-if="selected?.id === catalog.defaultId" class="comm-tag blue">新作业默认</span>
          <span class="grow"></span>
          <span v-if="pinned" class="comm-meta-line">当前提交绑定的量规快照</span>
        </div>
        <RubricViewer :snapshot="selected" :source="pinned ? initialSource : undefined" />
      </div>

      <form v-else class="rw-form" @submit.prevent="save">
        <fieldset :disabled="busy">
          <label>量规标题<input v-model="draft.title" maxlength="120" required /></label>
          <label>总体说明<textarea v-model="draft.instructions" maxlength="2000" rows="3" required /></label>

          <div class="rw-items">
            <section v-for="i in draft.criteria" :key="i.code" class="rw-item">
              <div class="rw-item-head"><b>{{ i.code }}</b><span>{{ i.name }}</span></div>
              <label class="rw-item-desc">指标说明<input v-model="i.description" maxlength="1000" required /></label>
              <div class="rw-anchors">
                <label v-for="a in anchors" :key="a[0]">
                  {{ a[1] }}<input v-model="i[a[0]]" maxlength="1000" required />
                </label>
              </div>
            </section>
          </div>

          <div class="rw-foot">
            <label class="rw-reason">
              变更说明
              <input v-model="reason" maxlength="1000" placeholder="写入启用记录，仅任课教师可见" />
            </label>
            <button class="btn primary" type="submit" :disabled="!dirty">保存草稿</button>
            <button
              type="button"
              class="btn secondary"
              :disabled="busy || dirty || !reason.trim()"
              @click="activate"
            >
              启用为新作业默认
            </button>
            <button type="button" class="text-btn" :disabled="busy" @click="cancel">取消</button>
          </div>
        </fieldset>
      </form>
    </template>
  </section>
</template>

<style scoped>
.rubric-workbench { max-width: 1040px; }
.rw-head-actions { display: flex; align-items: center; gap: 10px; }
.rw-version { width: auto; max-width: 260px; padding: 7px 10px; font-size: 13px; }
.rw-current { border: 1px solid var(--border); border-radius: 12px; background: var(--paper); padding: 18px 22px 22px; }
.rw-current-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding-bottom: 14px; margin-bottom: 6px; border-bottom: 1px solid var(--line-soft); }
.rw-form label { display: block; margin: 14px 0; font-size: 13px; color: var(--muted); }
.rw-form input, .rw-form textarea { display: block; width: 100%; margin-top: 6px; box-sizing: border-box; }
.rw-form fieldset { border: 0; padding: 0; min-width: 0; }
.rw-items { display: grid; gap: 12px; margin-top: 18px; }
.rw-item { border: 1px solid var(--border); border-radius: 10px; padding: 14px 16px; }
.rw-item-head { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.rw-item-head b { font-size: 13px; color: var(--blue-text); background: var(--blue-soft); border-radius: 4px; padding: 1px 6px; }
.rw-item-head span { font-size: 14px; font-weight: 600; color: var(--text); }
.rw-item label { margin: 0; }
.rw-anchors { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 14px; margin-top: 10px; }
.rw-foot { display: flex; align-items: flex-end; gap: 12px; flex-wrap: wrap; margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--border); }
.rw-reason { flex: 1 1 240px; margin: 0 !important; }
.rw-form .danger { color: var(--red); }
.rubric-workbench p { overflow-wrap: anywhere; }
@media (max-width: 720px) {
  .rw-anchors { grid-template-columns: minmax(0, 1fr); }
}
</style>
