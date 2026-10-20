<script setup lang="ts">
import {ref,computed,onMounted,onBeforeUnmount,watch,nextTick} from 'vue';
import {api,ApiFailure} from './api';
import RubricViewer from './RubricViewer.vue';
const props=defineProps<{courseId:number;teacher:boolean;userId:number;initialSnapshot?:any;initialSource?:string}>();
const catalog=ref<any>(null),selected=ref<any>(null),draft=ref<any>(null),baseline=ref(''),error=ref(''),message=ref(''),busy=ref(false),reason=ref(''),history=ref<any[]|null>(null),recovery=ref<any>(null),conflict=ref(false),pinned=ref(!!props.initialSnapshot);let generation=0,alive=true;
const dirty=computed(()=>!!draft.value&&JSON.stringify(draft.value)!==baseline.value);
const editable=computed(()=>props.teacher&&!pinned.value&&selected.value?.state==='DRAFT');
const fields=[['description','指标说明'],['excellent','90–100 · 优秀锚点'],['good','75–89 · 良好锚点'],['pass','60–74 · 基本达到锚点'],['improve','0–59 · 待改进锚点']];
const key=(id:number)=>`community-rubric-${props.userId}-${props.courseId}-${id}`;
function payload(){return {title:draft.value.title,instructions:draft.value.instructions,criteria:draft.value.criteria.map((i:any)=>({code:i.code,description:i.description,excellent:i.excellent,good:i.good,pass:i.pass,improve:i.improve})),revision:selected.value.revision};}
function sessionBackup(){if(!dirty.value)return null;const data={rubricId:selected.value.id,courseId:props.courseId,...payload()};let persisted=false;try{localStorage.setItem(key(selected.value.id),JSON.stringify(data));persisted=localStorage.getItem(key(selected.value.id))===JSON.stringify(data);}catch{}return {data,persisted};}
watch(draft,()=>{if(dirty.value)sessionBackup();},{deep:true});
watch(error,async value=>{if(value){await nextTick();document.querySelector('.rubric-workbench [role=alert]')?.scrollIntoView({block:'center'});}});
function clearBackup(){try{localStorage.removeItem(key(selected.value.id));}catch{}}
function canLeave(ignoreReason=false){if(busy.value){error.value='正在处理量规，请稍候。';return false;}if(dirty.value){sessionBackup();error.value='量规有未保存输入，请先保存或明确放弃本次输入，再离开。';return false;}if(!ignoreReason&&reason.value.trim()){if(!window.confirm('放弃尚未提交的启用 / 切换理由？'))return false;reason.value='';}return true;}
defineExpose({canLeave,sessionBackup});
async function task(fn:()=>Promise<void>){if(busy.value)return;const g=++generation;busy.value=true;error.value='';message.value='';try{await fn();}catch(e:any){if(alive&&g===generation){error.value=e.message||'操作失败，请重试';if(e instanceof ApiFailure&&e.status===409)conflict.value=true;}}finally{if(alive&&g===generation)busy.value=false;}}
async function fetchCatalog(){const data=await api(`/courses/${props.courseId}/rubrics`);if(alive)catalog.value=data;}
function setSelected(data:any,checkBackup=true){selected.value=data;draft.value=JSON.parse(JSON.stringify(data));baseline.value=JSON.stringify(draft.value);reason.value='';conflict.value=false;recovery.value=null;if(checkBackup&&data.state==='DRAFT'&&props.teacher)try{const saved=JSON.parse(localStorage.getItem(key(data.id))||'null');if(saved&&JSON.stringify({...saved,revision:0})!==JSON.stringify({...payload(),rubricId:data.id,courseId:props.courseId,revision:0}))recovery.value=saved;}catch{}}
function changeSelection(event:Event){const input=event.target as HTMLSelectElement,code=input.value;input.value=pinned.value?'snapshot':selected.value?.code||'';void choose(code);}
async function choose(code:string){if(!canLeave())return;await task(async()=>{const data=await api(`/courses/${props.courseId}/rubrics/${code}`);if(alive){pinned.value=false;setSelected(data);}});}
async function load(){await task(async()=>{await fetchCatalog();if(!alive)return;if(pinned.value){selected.value=props.initialSnapshot;draft.value=null;}else{const data=await api(`/courses/${props.courseId}/rubrics/${catalog.value.defaultCode}`);if(alive)setSelected(data);}});}
async function create(){if(!canLeave())return;await task(async()=>{const sourceId=selected.value?.state==='ENABLED'?selected.value.id:catalog.value.defaultId;const data=await api(`/courses/${props.courseId}/rubrics`,'POST',{sourceId});if(alive){setSelected(data);await fetchCatalog();message.value='修订草稿已创建；尚未启用，不影响任何作业。';}});}
async function save(){if(!editable.value)return;await task(async()=>{const data=await api(`/courses/${props.courseId}/rubrics/${selected.value.id}`,'PUT',payload());if(alive){clearBackup();setSelected(data,false);await fetchCatalog();message.value='量规草稿已保存；未改变已绑定作业。';}});}
async function refreshConflict(){await task(async()=>{const data=await api(`/courses/${props.courseId}/rubrics/${selected.value.code}`);await fetchCatalog();if(!alive)return;selected.value=data;conflict.value=false;if(data.state!=='DRAFT'){sessionBackup();error.value='其他窗口已启用此版本，当前输入已备份；不能覆盖。请先放弃本次输入，再从已启用版本复制新草稿。';}else{message.value='已读取服务器最新修订号，当前输入仍保留。请展开服务器内容核对后再保存。';} });}
function discard(){if(busy.value||!selected.value)return;if(!window.confirm('放弃本窗口未保存输入及对应本机备份？'))return;clearBackup();setSelected(selected.value,false);message.value='已恢复服务器版本。';}
function restore(){if(!recovery.value||!editable.value)return;const r=recovery.value;for(const field of ['title','instructions'])draft.value[field]=r[field];draft.value.criteria=r.criteria.map((i:any)=>({...i,name:selected.value.criteria.find((v:any)=>v.code===i.code)?.name}));recovery.value=null;message.value='本机输入已恢复；尚未保存到服务器，请核对后保存。';}
async function activate(){if(!canLeave(true))return;if(!reason.value.trim()){error.value='请填写启用理由。';return;}await task(async()=>{const data=await api(`/courses/${props.courseId}/rubrics/${selected.value.id}/enable`,'POST',{revision:selected.value.revision,catalogRevision:catalog.value.catalogRevision,reason:reason.value});if(alive){clearBackup();setSelected(data,false);await fetchCatalog();history.value=null;message.value='版本已启用并设为新作业默认；既有作业和提交不变。';}});}
async function setDefault(){if(!canLeave(true))return;if(!reason.value.trim()){error.value='请填写切换默认量规的理由。';return;}await task(async()=>{const data=await api(`/courses/${props.courseId}/rubrics/default`,'PUT',{rubricId:selected.value.id,catalogRevision:catalog.value.catalogRevision,reason:reason.value});if(alive){catalog.value=data;history.value=null;reason.value='';message.value='新作业默认量规已切换；既有绑定不变。';}});}
async function loadHistory(){await task(async()=>{const data=await api(`/courses/${props.courseId}/rubrics/history`);if(alive)history.value=data;});}
function unload(e:BeforeUnloadEvent){if(dirty.value){sessionBackup();e.preventDefault();e.returnValue='';}}
onMounted(()=>{window.addEventListener('beforeunload',unload);load();});onBeforeUnmount(()=>{sessionBackup();alive=false;generation++;window.removeEventListener('beforeunload',unload);});
</script>
<template><section class="page rubric-workbench">
 <header class="page-heading"><div><p class="eyebrow">课程标准 · 明确版本</p><h1>课程量规</h1><p class="muted">四维等权与 11 项指标固定；可配置说明与评分锚点。已启用版本不可改写。</p></div><button class="btn secondary" :disabled="busy" @click="canLeave()&&load()">刷新版本列表</button></header>
 <p v-if="error" class="notice danger" role="alert">{{error}}</p><p v-if="message" class="notice" role="status">{{message}}</p>
 <p v-if="!catalog&&!error" role="status">正在读取量规…</p>
 <template v-if="catalog"><p class="notice">新作业默认：{{catalog.defaultCode}}。切换默认不修改已有作业；提交时另存量规快照。</p>
 <label>量规版本<select aria-label="量规版本" :value="pinned?'snapshot':selected?.code" :disabled="busy" @change="changeSelection"><option v-if="pinned" value="snapshot" disabled>当前提交 / 作业绑定的量规快照</option><option v-for="r in catalog.items" :key="r.code" :value="r.code">{{r.title}} · {{r.code}} · {{r.state==='DRAFT'?'教师草稿':'已启用'}}{{r.id===catalog.defaultId?' · 默认':''}}</option></select></label>
 <p v-if="pinned" class="notice">正在查看绑定快照，不会以当前课程默认量规替换历史标准。</p>
 <template v-if="selected"><div class="rubric-actions" v-if="teacher&&!pinned"><button class="btn secondary" :disabled="busy" @click="create">复制为新修订草稿</button><button class="btn secondary" :disabled="busy" @click="loadHistory">启用与默认切换记录</button><span class="muted">{{selected.state==='DRAFT'?'教师私有草稿 · r'+selected.revision:'已启用 · 只读'}}</span></div>
 <div v-if="recovery" class="notice"><p>发现此量规的本机未同步输入。不会自动覆盖服务器。</p><button class="btn secondary" :disabled="busy" @click="restore">恢复本机输入供核对</button><button class="text-btn" @click="clearBackup();recovery=null">放弃本机备份</button></div>
 <div v-if="dirty" class="rubric-actions"><span>有未保存输入</span><button class="text-btn" :disabled="busy" @click="discard">放弃本次输入</button></div>
 <div v-if="conflict"><button class="btn secondary" :disabled="busy" @click="refreshConflict">刷新并保留当前输入</button></div>
 <details v-if="dirty"><summary>服务器最近读取内容（用于冲突核对）</summary><RubricViewer :snapshot="selected"/></details>
 <form v-if="editable" @submit.prevent="save"><fieldset :disabled="busy"><label>量规标题<input v-model="draft.title" maxlength="120" required /></label><label>总体说明<textarea v-model="draft.instructions" maxlength="2000" required rows="4"/></label>
 <details v-for="i in draft.criteria" :key="i.code" class="rubric-edit-item"><summary>{{i.code}} · {{i.name}}</summary><label v-for="f in fields" :key="f[0]">{{f[1]}}<textarea v-model="i[f[0]]" maxlength="1000" required rows="3"/></label></details>
 <button class="btn primary" type="submit" :disabled="!dirty">保存量规草稿</button></fieldset></form>
 <RubricViewer v-else :snapshot="selected" :source="pinned?initialSource:undefined"/>
 <div v-if="teacher&&!pinned" class="rubric-enable"><label>启用 / 切换理由<textarea v-model="reason" maxlength="1000" :disabled="busy" placeholder="仅任课教师可见；说明本次标准调整" rows="3"/></label><button v-if="selected.state==='DRAFT'" class="btn primary" :disabled="busy||dirty" @click="activate">启用版本并设为新作业默认</button><button v-else-if="selected.id!==catalog.defaultId" class="btn secondary" :disabled="busy" @click="setDefault">设为新作业默认量规</button><p class="muted">启用后版本冻结；需要调整时复制为新的修订草稿。此操作不发布作品或部署系统。</p></div>
 </template><section v-if="history"><h2>启用与默认切换记录</h2><p class="muted">仅任课教师可见，最近 100 条；不包含普通草稿保存审计。</p><p v-if="!history.length">暂无记录。</p><article class="rubric-history" v-for="h in history" :key="h.id"><strong>{{h.action_type==='ENABLE'?'启用版本':'切换默认'}} · {{h.before_code}} → {{h.after_code}}</strong><p>{{h.reason}}</p><small>{{h.actor_name}} · {{h.created_at}}</small></article></section>
 </template></section></template>
<style scoped>
.rubric-workbench{max-width:1100px}.rubric-workbench label{display:block;margin:16px 0}.rubric-workbench input,.rubric-workbench textarea,.rubric-workbench select{display:block;width:100%;margin-top:8px;box-sizing:border-box}.rubric-workbench fieldset{border:0;padding:0;min-width:0}.rubric-edit-item,.rubric-history,.rubric-enable{padding:16px;border:1px solid var(--border);border-radius:10px;margin:16px 0}.rubric-edit-item summary{cursor:pointer;font-weight:600}.rubric-actions{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin:16px 0}.rubric-workbench .danger{color:var(--danger,#d43838)}.rubric-workbench p{overflow-wrap:anywhere}
</style>
