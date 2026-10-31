<script setup lang="ts">
import { computed } from 'vue';
import { criterionDescription, rubricInstructions } from './rubricText';

const props = defineProps<{ snapshot: any; source?: string }>();
const bands: [string, string, string][] = [
  ['excellent', '优秀', '90–100'],
  ['good', '良好', '75–89'],
  ['pass', '基本', '60–74'],
  ['improve', '待改进', '0–59'],
];
const FALLBACK: Record<string, { name: string; description: string }> = {
  S: { name: '科学性', description: '论断有依据，方法与结论相称。' },
  A: { name: '准确性', description: '事实、数据与表述准确，来源可核验。' },
  C: { name: '完整性', description: '关键环节交代完整，必要要素不缺。' },
  L: { name: '逻辑性', description: '结构清晰，论证与内容关系前后一致。' },
};

const criteria = computed<any[]>(() => (Array.isArray(props.snapshot?.criteria) ? props.snapshot.criteria : []));
const legacy = computed(() => !!props.snapshot?.legacy || props.source === 'BUILTIN_LEGACY');

/** 维度名称与权重以量规快照为准；历史快照没有维度记录时回退到四维等权。 */
const groups = computed(() => {
  const rows = Array.isArray(props.snapshot?.dimensions) ? props.snapshot.dimensions : [];
  return ['S', 'A', 'C', 'L'].map((code) => {
    const row = rows.find((d: any) => d && d.code === code) || {};
    const weight = Number(row.weight);
    const name = row.name && String(row.name).trim() ? String(row.name).trim() : FALLBACK[code].name;
    const description = row.description && String(row.description).trim()
      ? String(row.description).trim()
      : FALLBACK[code].description;
    return {
      code,
      name,
      description,
      weight: Number.isFinite(weight) ? weight : 25,
      items: criteria.value.filter((i: any) => String(i.code || '').startsWith(code)),
    };
  });
});

// 锚点开头常重复写一遍分数段（「90–100：」），表头已经有分数段，显示时去掉。
function bandText(text: unknown) {
  return String(text ?? '').replace(/^\s*\d+\s*[–—~-]\s*\d+\s*[：:]\s*/, '').trim();
}
const hasBands = computed(() => criteria.value.some((i: any) => bands.some((b) => i[b[0]])));
// 某一档所有指标写的都一样时只在顶部写一次，表格里不再重复这一列。
const common = computed(() => {
  const out: Record<string, string> = {};
  if (!hasBands.value || criteria.value.length < 2) return out;
  for (const [key] of bands) {
    const texts = criteria.value.map((i: any) => bandText(i[key]));
    if (texts[0] && texts.every((t) => t === texts[0])) out[key] = texts[0];
  }
  return out;
});
const columns = computed(() => (hasBands.value ? bands.filter((b) => !(b[0] in common.value)) : []));
</script>

<template>
  <div v-if="snapshot" class="rv-viewer">
    <p v-if="legacy" class="rv-legacy">历史提交未保存锚点，仅显示当时固定的指标代码。</p>
    <p v-if="rubricInstructions(snapshot.instructions)" class="rv-instructions">{{ rubricInstructions(snapshot.instructions) }}</p>

    <section v-if="Object.keys(common).length" class="rv-common" aria-label="各项通用的档位描述">
      <h3>{{ columns.length ? '以下档位各项指标相同' : '四档描述（11 项指标相同）' }}</h3>
      <dl>
        <template v-for="b in bands" :key="b[0]">
          <div v-if="common[b[0]]">
            <dt>{{ b[1] }}<small>{{ b[2] }}</small></dt>
            <dd>{{ common[b[0]] }}</dd>
          </div>
        </template>
      </dl>
    </section>

    <section v-for="g in groups" :key="g.code" class="rv-group">
      <header class="rv-group-head">
        <span class="rv-dim">{{ g.code }}</span>
        <strong>{{ g.name }}</strong>
        <span class="rv-weight">{{ g.weight }}%</span>
        <span class="rv-group-desc">{{ g.description }}</span>
      </header>
      <div v-if="columns.length" class="rv-table-wrap">
        <table class="rv-table rv-bands">
          <thead>
            <tr>
              <th scope="col" class="rv-col-item">指标</th>
              <th v-for="b in columns" :key="b[0]" scope="col">{{ b[1] }}<small>{{ b[2] }}</small></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="i in g.items" :key="i.code">
              <th scope="row" class="rv-col-item">
                <span class="rv-code">{{ i.code }}</span>{{ i.name }}
                <p v-if="criterionDescription(i)" class="rv-item-desc">{{ criterionDescription(i) }}</p>
              </th>
              <td v-for="b in columns" :key="b[0]">{{ bandText(i[b[0]]) || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <ul v-else class="rv-plain">
        <li v-for="i in g.items" :key="i.code">
          <span class="rv-code">{{ i.code }}</span>{{ i.name }}
          <p v-if="criterionDescription(i)" class="rv-item-desc">{{ criterionDescription(i) }}</p>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.rv-viewer { line-height: 1.65; overflow-wrap: anywhere; }
.rv-legacy { font-size: 13px; color: var(--muted); background: var(--surface); border-radius: 8px; padding: 10px 12px; margin: 0 0 14px; }
.rv-instructions { font-size: 13.5px; color: var(--muted); margin: 0 0 16px; }

.rv-common { border: 1px solid var(--border); border-radius: 10px; padding: 12px 16px; margin-bottom: 18px; background: var(--surface); }
.rv-common h3 { font-size: 13px; font-weight: 600; margin: 0 0 8px; color: var(--muted); }
.rv-common dl { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px 18px; margin: 0; }
.rv-common dt { font-size: 12.5px; font-weight: 600; }
.rv-common dt small, .rv-table th small { margin-left: 6px; font-weight: 400; color: var(--subtle); }
.rv-common dd { margin: 2px 0 0; font-size: 13px; color: var(--text); }

.rv-group { margin-top: 20px; }
.rv-group:first-of-type { margin-top: 4px; }
.rv-group-head { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4px 8px; padding-bottom: 8px; }
.rv-group-head strong { font-size: 14.5px; }
.rv-dim, .rv-code { display: inline-flex; align-items: center; justify-content: center; border-radius: 5px; background: var(--blue-soft); color: var(--blue-text); font-size: 12px; font-weight: 700; }
.rv-dim { width: 20px; height: 20px; align-self: center; }
.rv-code { padding: 0 6px; height: 20px; margin-right: 8px; }
.rv-weight { font-size: 12.5px; font-weight: 600; color: var(--blue-text); }
.rv-group-desc { flex-basis: 100%; font-size: 12.5px; color: var(--subtle); }

.rv-table-wrap { overflow-x: auto; border: 1px solid var(--border); border-radius: 10px; }
.rv-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.rv-table th, .rv-table td { padding: 10px 12px; text-align: left; vertical-align: top; border-top: 1px solid var(--line-soft); }
.rv-table thead th { border-top: 0; background: var(--surface); font-size: 12.5px; font-weight: 600; color: var(--muted); white-space: nowrap; }
.rv-table tbody th { font-weight: 600; font-size: 13.5px; }
.rv-col-item { width: 26%; min-width: 150px; }
.rv-table td { min-width: 140px; color: var(--text); }
.rv-item-desc { margin: 4px 0 0; font-size: 12.5px; font-weight: 400; color: var(--muted); }

.rv-plain { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.rv-plain li { border: 1px solid var(--border); border-radius: 9px; padding: 9px 12px; font-size: 13.5px; font-weight: 600; background: var(--paper); }
@media (max-width: 900px) { .rv-plain { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 560px) { .rv-plain { grid-template-columns: minmax(0, 1fr); } }
</style>
