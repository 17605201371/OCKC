<script setup lang="ts">
// 知识社区：从 App.vue 拆出。数据（本课程已发布条目、待审核条目）仍由 App 统一加载后传入；
// 本组件只负责筛选、排序、范围切换和展示。切到「我的课程」「全站公开」时自己按范围读取，不改写 App 的数据。
import { ref, computed, watch, onBeforeUnmount } from "vue";
import { PenLine, Plus, Search, MessageSquare, Library } from "lucide-vue-next";
import { api } from "./api";

const props = defineProps<{
  courseId: number;
  meId: number;
  isTeacher: boolean;
  isStaff: boolean;
  busy: boolean;
  publications: any[];
  pending: any[];
  pendingReviewCount: number;
  myWorkCount: number;
}>();
const emit = defineEmits<{
  "open-version": [submissionId: number];
  "pub-action": [publicationId: number, action: string];
  "new-free-post": [];
  go: [target: string];
  notify: [message: string];
}>();

const query = ref("");
const publicationSort = ref("new");
const commTab = ref("all"),
  commStatus = ref("all");
// 社区可见范围：本课程 / 我参与的所有课程 / 全站公开（仅作者授权公开的帖子）。
const commScope = ref("COURSE");
const commScopes = [
  { id: "COURSE", name: "本课程" },
  { id: "MINE", name: "我的课程" },
  { id: "PUBLIC", name: "全站公开" },
];
const scoped = ref<any[] | null>(null);
let generation = 0, alive = true;
watch(commScope, async (scope) => {
  commTab.value = "all";
  const g = ++generation, course = props.courseId;
  if (scope === "COURSE") { scoped.value = null; return; }
  try {
    const rows = await api(`/courses/${course}/publications?scope=${scope}`);
    if (alive && g === generation && course === props.courseId) scoped.value = rows;
  } catch (e: any) {
    if (alive && g === generation) emit("notify", e.message || "无法切换社区范围");
  }
});
onBeforeUnmount(() => { alive = false; generation++; });

const shown = computed<any[]>(() => (commScope.value === "COURSE" || !scoped.value ? props.publications : scoped.value));
const commSource = computed(() => [
  ...shown.value.map((p: any) => ({ ...p, _pending: false })),
  ...(props.isTeacher ? props.pending.map((p: any) => ({ ...p, _pending: true })) : []),
]);
const commMine = (p: any) => p.author_id === props.meId;
const commTopFlag = (p: any) => !p._pending && p.q_final != null && Number(p.q_final) >= 85;
const commTabs = computed(() => [
  { id: "all", name: "全部" },
  props.isTeacher ? { id: "rev", name: "待评议" } : { id: "mine", name: "我参与的" },
  { id: "top", name: "优质" },
]);
const commStatusChips = computed(() => [
  { id: "all", name: "全部状态" },
  ...(props.isTeacher ? [{ id: "rev", name: "待评议" }] : []),
  { id: "ver", name: "已核验" },
  { id: "top", name: "优质" },
]);
const commCounts = computed<Record<string, number>>(() => ({
  all: commSource.value.length,
  mine: commSource.value.filter(commMine).length,
  rev: commSource.value.filter((p: any) => p._pending).length,
  top: commSource.value.filter(commTopFlag).length,
}));
const commRows = computed(() => {
  const kw = query.value.trim().toLowerCase();
  const list = commSource.value.filter((p: any) => {
    if (commTab.value === "mine" && !commMine(p)) return false;
    if (commTab.value === "rev" && !p._pending) return false;
    if (commTab.value === "top" && !commTopFlag(p)) return false;
    if (commStatus.value === "rev" && !p._pending) return false;
    if (commStatus.value === "ver" && p._pending) return false;
    if (commStatus.value === "top" && !commTopFlag(p)) return false;
    if (kw && !`${p.title} ${p.author_name} ${p.summary || ""}`.toLowerCase().includes(kw)) return false;
    return true;
  });
  const stamp = (p: any) => String(p.published_at || p.requested_at || "");
  const sorters: Record<string, (a: any, b: any) => number> = {
    new: (a, b) => stamp(b).localeCompare(stamp(a)),
    q: (a, b) => Number(b.q_final ?? -1) - Number(a.q_final ?? -1),
    disc: (a, b) => Number(b.discussion_count || 0) - Number(a.discussion_count || 0),
  };
  return list.sort(sorters[publicationSort.value] || sorters.new);
});
const commTop = computed(() =>
  shown.value
    .filter((p: any) => p.q_final != null)
    .slice()
    .sort((a: any, b: any) => Number(b.q_final) - Number(a.q_final))
    .slice(0, 4),
);
const commPeople = computed(() => {
  const map = new Map<string, number>();
  for (const p of shown.value) map.set(p.author_name, (map.get(p.author_name) || 0) + 1);
  return [...map.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([name, n]) => ({ name, n }));
});
const commTodo = computed(() => {
  const rows = props.isTeacher
    ? [
        { text: `${props.pending.length} 个条目待评议`, sub: "学生已申请发布", tag: "待评议", tone: "orange", act: "rev" },
        { text: `${props.pendingReviewCount} 份提交待批改`, sub: "进入批改中心", tag: "待评", tone: "blue", act: "review" },
      ]
    : [
        { text: `${props.myWorkCount} 篇作品在修订`, sub: "继续写作或提交", tag: "作品", tone: "blue", act: "assignments" },
        { text: `${commCounts.value.top} 篇优质条目`, sub: "按质量分排序", tag: "优质", tone: "green", act: "top" },
      ];
  return rows.filter((r) => !r.text.startsWith("0 "));
});
function commAct(kind: string) {
  if (kind === "rev") {
    commTab.value = "rev";
    commStatus.value = "all";
  } else if (kind === "top") commTab.value = "top";
  else if (kind === "review") emit("go", "review");
  else if (kind === "assignments") emit("go", "assignments");
}
function commStatusOf(p: any) {
  if (p._pending) return { n: "待评议", c: "orange" };
  if (commTopFlag(p)) return { n: "优质", c: "green" };
  return { n: "已核验", c: "blue" };
}
function commBars(p: any) {
  const d = p.dims || {};
  return ["S", "A", "C", "L"].map((k) => {
    const v = d[k] == null ? 0 : Math.round(Number(d[k]));
    return { k, v, h: Math.max(3, Math.round(v / 5)) };
  });
}
function shortDate(v: string) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return `${d.getMonth() + 1}-${String(d.getDate()).padStart(2, "0")}`;
}
const number = (n: any) => (n == null ? "—" : Number(n).toFixed(1));
const openVersion = (sid: number) => emit("open-version", sid);
</script>

<template>
<section class="page community-page">
  <div class="page-heading">
    <div>
      <h1>知识社区</h1>
      <p>同学们共同撰写、评议与核验的知识条目。</p>
    </div>
    <div class="button-row">
      <button
        v-if="!isStaff"
        class="btn primary"
        :disabled="busy"
        @click="emit('new-free-post')"
      >
        写知识贴 <PenLine :size="15" />
      </button>
      <button class="btn secondary" @click="emit('go', 'assignments')">
        去作业 <Plus :size="15" />
      </button>
    </div>
  </div>

  <div class="comm-scopes" role="radiogroup" aria-label="可见范围">
    <button
      v-for="s in commScopes"
      :key="s.id"
      class="comm-scope"
      :class="{ on: commScope === s.id }"
      role="radio"
      :aria-checked="commScope === s.id"
      @click="commScope = s.id"
    >
      {{ s.name }}
    </button>
  </div>

  <div class="comm-tabs" role="tablist" aria-label="社区范围">
    <button
      v-for="t in commTabs"
      :key="t.id"
      class="comm-tab"
      :class="{ on: commTab === t.id }"
      role="tab"
      :aria-selected="commTab === t.id"
      @click="commTab = t.id"
    >
      {{ t.name }}<span class="c">{{ commCounts[t.id] || 0 }}</span>
    </button>
  </div>

  <section v-if="isTeacher && pending.length" class="comm-approve">
    <strong>{{ pending.length }} 个条目等待发布审核</strong>
    <div v-for="p in pending" :key="p.id" class="comm-approve-row">
      <span class="comm-approve-title">{{ p.title }}</span>
      <span class="comm-meta-line">{{ p.author_name }} · v{{ p.version_no }}</span>
      <span class="grow"></span>
      <button class="text-btn" @click="openVersion(p.submission_id)">查看</button>
      <button class="btn secondary" @click="emit('pub-action', p.id, 'APPROVE')">同意发布</button>
      <button class="text-btn" @click="emit('pub-action', p.id, 'RETURN')">退回</button>
    </div>
  </section>

  <div class="comm-layout">
    <div class="comm-main">
      <div class="comm-filters">
        <label class="search-box">
          <Search :size="17" />
          <input v-model="query" placeholder="搜索条目" aria-label="搜索社区条目" />
        </label>
        <div class="comm-chips">
          <button
            v-for="s in commStatusChips"
            :key="s.id"
            class="comm-chip"
            :class="{ on: commStatus === s.id }"
            @click="commStatus = s.id"
          >
            {{ s.name }}
          </button>
        </div>
        <span class="grow"></span>
        <select v-model="publicationSort" class="comm-sort" aria-label="社区排序">
          <option value="new">最近更新</option>
          <option value="q">质量最高</option>
          <option value="disc">讨论最多</option>
        </select>
      </div>

      <div class="comm-list">
        <article
          v-for="p in commRows"
          :key="p.id"
          class="comm-row"
          role="button"
          tabindex="0"
          @click="openVersion(p.submission_id)"
          @keydown.enter="openVersion(p.submission_id)"
        >
          <div class="comm-cover" :class="'cover-tone-' + (Number(p.id) % 5)" aria-hidden="true">{{ (p.title || '知').slice(0, 1) }}</div>
          <div class="comm-row-main">
            <div class="comm-tags">
              <span class="comm-tag" :class="commStatusOf(p).c">{{ commStatusOf(p).n }}</span>
              <span v-if="commMine(p)" class="comm-tag blue">我参与</span>
              <span class="comm-meta-line">v{{ p.version_no }} · {{ p.author_name }}</span>
            </div>
            <h3>{{ p.title }}</h3>
            <p class="comm-abs">{{ p.summary }}</p>
            <div class="comm-meta">
              <span class="avatar small">{{ p.author_name.slice(0, 1) }}</span>
              <span>{{ p.author_name }}</span>
              <span><MessageSquare :size="13" />{{ p.discussion_count || 0 }}</span>
              <span>更新于 {{ shortDate(p.published_at || p.requested_at) }}</span>
            </div>
          </div>
          <div class="comm-score">
            <template v-if="p.q_final != null">
              <b>{{ number(p.q_final) }}</b><span>质量分</span>
              <div class="comm-bars">
                <i
                  v-for="b in commBars(p)"
                  :key="b.k"
                  :class="{ low: b.v < 60 }"
                  :style="{ height: b.h + 'px' }"
                  :title="b.k + ' ' + b.v"
                />
              </div>
            </template>
            <template v-else><b class="none">—</b><span>未评分</span></template>
          </div>
        </article>
        <div v-if="!commRows.length" class="empty">
          <Library :size="30" />
          <h2>没有符合条件的条目</h2>
          <p>换个关键词，或清除筛选条件。</p>
          <button
            class="btn secondary"
            @click="query = ''; commStatus = 'all'; commTab = 'all'"
          >
            清除筛选
          </button>
        </div>
      </div>
      <p class="comm-foot">{{ commRows.length }} 条</p>
    </div>

    <aside class="comm-rail">
      <section class="comm-card">
        <div class="comm-card-head"><h3>待我处理</h3><span>{{ commTodo.length }} 项</span></div>
        <button v-for="t in commTodo" :key="t.text" class="comm-li" @click="commAct(t.act)">
          <div class="comm-li-main">
            <strong>{{ t.text }}</strong><small>{{ t.sub }}</small>
          </div>
          <span class="comm-tag" :class="t.tone">{{ t.tag }}</span>
        </button>
        <p v-if="!commTodo.length" class="comm-empty">暂无待办。</p>
      </section>
      <section class="comm-card">
        <div class="comm-card-head"><h3>高质量条目</h3><span>按质量分</span></div>
        <button v-for="(e, i) in commTop" :key="e.id" class="comm-rank" @click="openVersion(e.submission_id)">
          <span class="i">{{ i + 1 }}</span>
          <div class="comm-li-main"><strong>{{ e.title }}</strong><small>{{ e.author_name }} · v{{ e.version_no }}</small></div>
          <span class="v">{{ number(e.q_final) }}</span>
        </button>
        <p v-if="!commTop.length" class="comm-empty">还没有已评分条目。</p>
      </section>
      <section class="comm-card">
        <div class="comm-card-head"><h3>活跃贡献者</h3><span>本课程</span></div>
        <div v-for="c in commPeople" :key="c.name" class="comm-li static">
          <span class="avatar small">{{ c.name.slice(0, 1) }}</span>
          <div class="comm-li-main"><strong>{{ c.name }}</strong></div>
          <span class="num">参与 {{ c.n }} 条</span>
        </div>
        <p v-if="!commPeople.length" class="comm-empty">暂无贡献记录。</p>
      </section>
    </aside>
  </div>
</section>
</template>
