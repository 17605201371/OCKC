<script setup lang="ts">
import { computed } from 'vue';
import { criterionDescription, rubricInstructions } from './rubricText';

const props = defineProps<{ snapshot: any; source?: string }>();
const bands: [string, string][] = [
  ['excellent', '优秀 90–100'],
  ['good', '良好 75–89'],
  ['pass', '基本 60–74'],
  ['improve', '待改进 0–59'],
];
const FALLBACK: Record<string, { name: string; description: string }> = {
  S: { name: '科学性', description: '论断有依据，方法与结论相称。' },
  A: { name: '准确性', description: '事实、数据与表述准确，来源可核验。' },
  C: { name: '完整性', description: '关键环节交代完整，必要要素不缺。' },
  L: { name: '逻辑性', description: '结构清晰，论证与内容关系前后一致。' },
};

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
    return { code, name, description, weight: Number.isFinite(weight) ? weight : 25 };
  });
});

function inGroup(criteria: any[], d: string) {
  return (criteria || []).filter((i: any) => String(i.code || '').startsWith(d));
}
</script>

<template>
  <div v-if="snapshot" class="rv-viewer">
    <p v-if="snapshot.legacy || source === 'BUILTIN_LEGACY'" class="rv-legacy">
      历史提交未保存锚点，仅显示当时固定的指标代码。
    </p>
    <p v-if="rubricInstructions(snapshot.instructions)" class="rv-instructions">{{ rubricInstructions(snapshot.instructions) }}</p>

    <section v-for="g in groups" :key="g.code" class="rv-group">
      <div class="rv-group-head">
        <span class="rv-dim">{{ g.code }}</span>
        <strong>{{ g.name }}</strong>
        <span class="rv-weight">{{ g.weight }}%</span>
      </div>
      <p class="rv-group-desc">{{ g.description }}</p>
      <article v-for="i in inGroup(snapshot.criteria, g.code)" :key="i.code" class="rv-item">
        <div class="rv-item-head">
          <b>{{ i.code }}</b><span>{{ i.name }}</span>
        </div>
        <p v-if="criterionDescription(i)" class="rv-item-desc">{{ criterionDescription(i) }}</p>
        <div v-if="i.excellent || i.good || i.pass || i.improve" class="rv-bands">
          <div v-for="b in bands" :key="b[0]">
            <small>{{ b[1] }}</small>
            <p>{{ i[b[0]] }}</p>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.rv-viewer { line-height: 1.7; overflow-wrap: anywhere; }
.rv-legacy { font-size: 13px; color: var(--muted); background: var(--surface); border-radius: 8px; padding: 10px 12px; }
.rv-instructions { font-size: 14px; color: var(--muted); margin: 0 0 18px; }
.rv-group { margin-top: 18px; }
.rv-group-head { display: flex; align-items: center; gap: 8px; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
.rv-group-head strong { font-size: 14px; }
.rv-dim { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 5px; background: var(--blue-soft); color: var(--blue-text); font-size: 12px; font-weight: 700; }
.rv-weight { margin-left: auto; font-size: 12px; color: var(--subtle); }
.rv-group-desc { font-size: 12.5px; color: var(--subtle); margin: 6px 0 0; }
.rv-item { padding: 12px 0; border-bottom: 1px solid var(--line-soft); }
.rv-item:last-child { border-bottom: 0; }
.rv-item-head { display: flex; align-items: center; gap: 8px; }
.rv-item-head b { font-size: 12px; color: var(--blue-text); background: var(--blue-soft); border-radius: 4px; padding: 1px 6px; }
.rv-item-head span { font-size: 14px; font-weight: 600; }
.rv-item-desc { font-size: 13px; color: var(--muted); margin: 4px 0 0; }
.rv-bands { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px 16px; margin-top: 10px; }
.rv-bands small { display: block; font-size: 11px; color: var(--subtle); margin-bottom: 2px; }
.rv-bands p { font-size: 13px; margin: 0; line-height: 1.6; }
@media (max-width: 900px) {
  .rv-bands { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
