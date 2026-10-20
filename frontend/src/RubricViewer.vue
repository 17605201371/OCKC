<script setup lang="ts">
defineProps<{snapshot:any;source?:string}>();
const bands=[['excellent','90–100 · 优秀'],['good','75–89 · 良好'],['pass','60–74 · 基本达到'],['improve','0–59 · 待改进']];
</script>
<template><div v-if="snapshot" class="rubric-viewer">
 <p><strong>{{ snapshot.title }}</strong> <span class="muted">{{ snapshot.code }}</span></p>
 <p class="notice" v-if="snapshot.legacy || source==='BUILTIN_LEGACY'">此历史提交早于量规快照，仅显示当时固定指标与规则；不追溯新增锚点。</p>
 <p>{{ snapshot.instructions }}</p><p class="muted">科学性、准确性、完整性、逻辑性各占 25%；11 个指标代码及计算规则保持固定。锚点辅助判断，不自动代替教师评分。</p>
 <details v-for="i in snapshot.criteria" :key="i.code" class="rubric-criterion"><summary>{{ i.code }} · {{ i.name }}</summary><template v-if="i.description"><p>{{ i.description }}</p><dl><template v-for="b in bands" :key="b[0]"><dt>{{ b[1] }}</dt><dd>{{ i[b[0]] }}</dd></template></dl></template><p v-else class="muted">该历史版本未记录单项锚点。</p></details>
</div></template>
<style scoped>
.rubric-viewer{line-height:1.8;overflow-wrap:anywhere}.rubric-criterion{border:1px solid var(--border);border-radius:8px;padding:12px;margin:10px 0}.rubric-criterion summary{cursor:pointer;font-weight:600}.rubric-criterion dl{display:grid;grid-template-columns:minmax(100px,140px) 1fr;gap:8px;margin:12px 0}.rubric-criterion dd{margin:0}.rubric-criterion dt{color:var(--muted)}
</style>
