<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue';
import { Clock, ChevronDown, AlertTriangle, RefreshCw } from 'lucide-vue-next';
import { api } from './api';
import { durationLabel } from './dwellTime';

const props = defineProps<{ courseId: number }>();
const emit = defineEmits<{ open: [entryId: number] }>();
const open = ref(true), loading = ref(false), error = ref(''), data = ref<any>(null), assignment = ref('0');
let generation = 0, alive = true;

async function load() {
  const g = ++generation; loading.value = true; error.value = '';
  try {
    const r = await api(`/courses/${props.courseId}/time-stats?assignmentId=${assignment.value}`);
    if (alive && g === generation) data.value = r;
  } catch (e: any) { if (alive && g === generation) error.value = e.message || '用时统计读取失败'; }
  finally { if (alive && g === generation) loading.value = false; }
}
watch(() => props.courseId, () => { assignment.value = '0'; void load(); }, { immediate: true });
onBeforeUnmount(() => { alive = false; generation++; });

const s = computed(() => data.value?.summary || {});
const hasData = computed(() => (s.value.write_seconds || 0) > 0 || (s.value.grade_seconds || 0) > 0);

// 散点：横轴做题用时（对数，分钟），纵轴质量分。
const W = 520, H = 240, PAD = { l: 44, r: 16, t: 14, b: 36 };
const points = computed<any[]>(() => data.value?.points || []);
const maxMin = computed(() => Math.max(10, ...points.value.map((p) => p.write_seconds / 60)));
const xOf = (sec: number) => PAD.l + (Math.log1p(sec / 60) / Math.log1p(maxMin.value)) * (W - PAD.l - PAD.r);
// 纵轴下限跟着数据走（向下取到 10 的倍数，再留 10 分余量），否则分数都挤在顶上一条。
const yMin = computed(() => Math.max(0, Math.min(60, Math.floor((Math.min(100, ...points.value.map((p) => p.score)) - 10) / 10) * 10)));
const yOf = (score: number) => PAD.t + (1 - (Math.max(yMin.value, Math.min(100, score)) - yMin.value) / (100 - yMin.value)) * (H - PAD.t - PAD.b);
const xTicks = computed(() => [1, 5, 15, 30, 60, 120, 240].filter((m) => m <= maxMin.value * 1.05));
const yTicks = computed(() => [yMin.value, 60, 75, 90, 100].filter((v, i, a) => v >= yMin.value && a.indexOf(v) === i));
const correlation = computed(() => {
  const r = data.value?.correlation;
  if (r == null) return points.value.length < 5 ? `已评分且有用时的作品 ${points.value.length} 篇，满 5 篇后计算相关性` : '数据差异太小，无法计算相关性';
  const a = Math.abs(r), level = a < 0.2 ? '几乎无关' : a < 0.4 ? '弱' : a < 0.7 ? '中等' : '强';
  return `相关系数 r = ${r.toFixed(2)}（${level}${a >= 0.2 ? (r > 0 ? '正相关：用时越长得分越高' : '负相关：用时越长得分越低') : ''}）`;
});
const rules = computed(() => data.value?.rules || { short_ratio: 0.25, short_abs_seconds: 600, min_peers: 3 });
</script>

<template>
  <section class="time-stats card" aria-labelledby="time-stats-title">
    <header class="time-stats-head">
      <button class="time-stats-toggle" :aria-expanded="open" @click="open = !open">
        <span class="stat-icon-inline"><Clock :size="16" /></span>
        <h2 id="time-stats-title">用时统计</h2>
        <ChevronDown :size="16" :class="{ flipped: !open }" />
      </button>
      <span class="grow"></span>
      <template v-if="open">
        <select v-model="assignment" aria-label="按作业查看用时" @change="load">
          <option value="0">全部作业</option>
          <option v-for="a in data?.assignment_options || []" :key="a.id" :value="String(a.id)">{{ a.title }}</option>
        </select>
        <button class="btn secondary compact" :disabled="loading" @click="load"><RefreshCw :size="14" />刷新</button>
      </template>
    </header>

    <div v-if="open" class="time-stats-body" :aria-busy="loading">
      <p v-if="error" class="notice-error" role="alert">{{ error }}</p>
      <p v-else-if="loading && !data" class="empty-text" role="status">正在读取用时…</p>
      <template v-else-if="data">
        <div class="time-kpis">
          <div><span>有用时记录的作品</span><strong>{{ s.timed_entries }}<small> / {{ s.entries }} 篇</small></strong></div>
          <div><span>平均做题用时</span><strong>{{ durationLabel(s.avg_write_seconds) }}</strong></div>
          <div><span>每个版本平均批改用时</span><strong>{{ durationLabel(s.avg_grade_seconds_per_version) }}</strong></div>
          <div><span>累计批改用时</span><strong>{{ durationLabel(s.grade_seconds) }}</strong></div>
        </div>
        <p class="time-note">只计页面在前台、且 2 分钟内有操作的时间；功能上线前的作品没有记录，不计入平均。</p>

        <p v-if="!hasData" class="empty-text">还没有用时记录。学生写作、教师批改时会自动开始记录。</p>
        <template v-else>
          <div class="time-grid">
            <div class="time-block">
              <h3>用时与得分</h3>
              <svg :viewBox="`0 0 ${W} ${H}`" class="time-scatter" role="img" :aria-label="correlation">
                <g class="axis">
                  <line v-for="y in yTicks" :key="'y' + y" :x1="PAD.l" :x2="W - PAD.r" :y1="yOf(y)" :y2="yOf(y)" />
                  <text v-for="y in yTicks" :key="'yt' + y" :x="PAD.l - 8" :y="yOf(y) + 4" text-anchor="end">{{ y }}</text>
                  <text v-for="m in xTicks" :key="'xt' + m" :x="xOf(m * 60)" :y="H - PAD.b + 18" text-anchor="middle">{{ m >= 60 ? m / 60 + ' 小时' : m + ' 分' }}</text>
                </g>
                <circle v-for="p in points" :key="p.entry_id" class="dot" :class="{ high: p.score >= 90, low: p.score < 75 }"
                  :cx="xOf(p.write_seconds)" :cy="yOf(p.score)" r="6" tabindex="0" @click="emit('open', p.entry_id)" @keydown.enter="emit('open', p.entry_id)">
                  <title>{{ p.author_name }} · {{ p.title }}：{{ durationLabel(p.write_seconds) }}，{{ p.score.toFixed(1) }} 分</title>
                </circle>
              </svg>
              <p class="time-note">{{ correlation }}。相关不代表因果，样本少时只作参考。</p>
            </div>

            <div class="time-block">
              <h3><AlertTriangle :size="15" />用时明显偏短</h3>
              <p class="time-note">已提交，且做题用时不到同作业中位数的 {{ Math.round(rules.short_ratio * 100) }}%、也不到 {{ rules.short_abs_seconds / 60 }} 分钟（同作业至少 {{ rules.min_peers }} 篇有记录时才判断）。可能是在别处写好后粘贴进来，值得留意，不等于有问题。</p>
              <ul v-if="data.short_entries.length" class="time-list">
                <li v-for="e in data.short_entries" :key="e.id">
                  <button class="text-btn" @click="emit('open', e.id)">{{ e.author_name }} · {{ e.title }}</button>
                  <span>{{ durationLabel(e.write_seconds) }}<small>（中位数 {{ durationLabel(e.median_write_seconds) }}）</small></span>
                </li>
              </ul>
              <p v-else class="empty-text">没有用时明显偏短的作品。</p>
            </div>
          </div>

          <h3 class="time-table-title">按作业</h3>
          <div class="time-table-wrap">
            <table class="time-table">
              <thead><tr><th>作业</th><th>有记录 / 作品</th><th>做题用时中位数</th><th>平均做题用时</th><th>每版平均批改用时</th><th>平均分</th></tr></thead>
              <tbody>
                <tr v-for="a in data.assignments" :key="a.assignment_id">
                  <td>{{ a.assignment_title }}</td><td>{{ a.timed_entries }} / {{ a.entries }}</td>
                  <td>{{ durationLabel(a.median_write_seconds) }}</td><td>{{ durationLabel(a.avg_write_seconds) }}</td>
                  <td>{{ durationLabel(a.avg_grade_seconds_per_version) }}</td><td>{{ a.avg_score == null ? '—' : a.avg_score.toFixed(1) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <h3 class="time-table-title">按学生</h3>
          <div class="time-table-wrap">
            <table class="time-table">
              <thead><tr><th>学生</th><th>有记录 / 作品</th><th>累计做题用时</th><th>平均分</th></tr></thead>
              <tbody>
                <tr v-for="u in data.students" :key="u.author_id">
                  <td>{{ u.author_name }}</td><td>{{ u.timed_entries }} / {{ u.entries }}</td>
                  <td>{{ durationLabel(u.write_seconds) }}</td><td>{{ u.avg_score == null ? '—' : u.avg_score.toFixed(1) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </template>
    </div>
  </section>
</template>
