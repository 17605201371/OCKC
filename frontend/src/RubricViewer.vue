<script setup lang="ts">
defineProps<{ snapshot: any; source?: string }>();
const bands: [string, string][] = [
  ['excellent', '优秀 90–100'],
  ['good', '良好 75–89'],
  ['pass', '基本 60–74'],
  ['improve', '待改进 0–59'],
];
const groups: [string, string][] = [
  ['S', '科学性'],
  ['A', '准确性'],
  ['C', '完整性'],
  ['L', '逻辑性'],
];
function inGroup(criteria: any[], d: string) {
  return (criteria || []).filter((i: any) => String(i.code || '').startsWith(d));
}
</script>

<template>
  <div v-if="snapshot" class="rv-viewer">
    <p v-if="snapshot.legacy || source === 'BUILTIN_LEGACY'" class="rv-legacy">
      历史提交未保存锚点，仅显示当时固定的指标代码。
    </p>
    <p v-if="snapshot.instructions" class="rv-instructions">{{ snapshot.instructions }}</p>

    <section v-for="g in groups" :key="g[0]" class="rv-group">
      <div class="rv-group-head">
        <span class="rv-dim">{{ g[0] }}</span>
        <strong>{{ g[1] }}</strong>
        <span class="rv-weight">25%</span>
      </div>
      <article v-for="i in inGroup(snapshot.criteria, g[0])" :key="i.code" class="rv-item">
        <div class="rv-item-head">
          <b>{{ i.code }}</b><span>{{ i.name }}</span>
        </div>
        <p v-if="i.description" class="rv-item-desc">{{ i.description }}</p>
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
