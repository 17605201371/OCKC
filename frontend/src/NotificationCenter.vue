<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { Bell, X, CheckCheck, RefreshCw, ArrowUpRight, Inbox } from 'lucide-vue-next';
import { api } from './api';
const props=defineProps<{userId:number;navigate:(notice:any)=>Promise<void>}>();
const open=ref(false), unreadOnly=ref(false), kind=ref(''), rows=ref<any[]>([]), unread=ref(0), scopeUnread=ref(0), loading=ref(false), error=ref(''), hasMore=ref(false), cursor=ref<number|null>(null), acting=ref(false), updates=ref(false), disconnected=ref(false);
const trigger=ref<HTMLButtonElement>(), modal=ref<HTMLDialogElement>(), items=ref<HTMLElement>();
let timer:ReturnType<typeof setInterval>, session=0, request=0, summaryRequest=0, disposed=false, summaryBusy=false, head=0, baselineUnread=0, accessStamp='', bodyOverflow='';
let retryMode:'first'|'more'|number|'all'|null=null;
const kindName:Record<string,string>={MEMBERSHIP_CHANGE:'课程身份',ASSIGNMENT_CHANGE:'作业变更',SUBMISSION:'待评审',EVALUATION:'正式评价',FEEDBACK:'教师批注',FEEDBACK_REPLY:'反馈回应',EVIDENCE:'证据核验',EVIDENCE_DISPUTED:'争议裁定',PUBLICATION_REQUEST:'发布审核',PUBLICATION_RESULT:'发布结果',ENTRY_VERDICT:'终审结果'};
const current=(token:number)=>!disposed&&session===token;
function query(){const q=new URLSearchParams();if(kind.value)q.set('kind',kind.value);return q;}
function time(value:string){return new Date(value).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false});}
function applySummary(data:any,rebase=false){unread.value=data.unreadCount;scopeUnread.value=data.scopeUnreadCount;disconnected.value=false;if(rebase){accessStamp=data.accessStamp;head=data.latestId;baselineUnread=data.scopeUnreadCount;updates.value=false;}else if(open.value&&(data.latestId!==head||data.scopeUnreadCount!==baselineUnread))updates.value=true;}
async function refreshCount(rebase=false){
  if(disposed||loading.value||summaryBusy||(!rebase&&(acting.value||document.hidden)))return;
  const token=session,seq=++summaryRequest;summaryBusy=true;
  try{const data=await api('/notifications/summary?'+query());if(current(token)&&seq===summaryRequest){if(open.value&&accessStamp&&accessStamp!==data.accessStamp){reset();void load();}else applySummary(data,rebase);}}
  catch{if(current(token)&&seq===summaryRequest)disconnected.value=true;}
  finally{if(seq===summaryRequest)summaryBusy=false;}
}
function reset(){request++;summaryRequest++;summaryBusy=false;rows.value=[];cursor.value=null;hasMore.value=false;accessStamp='';head=0;baselineUnread=0;updates.value=false;error.value='';retryMode=null;}
async function load(more=false){
  if(acting.value||(more&&(!hasMore.value||loading.value)))return;
  const token=session,seq=++request;summaryRequest++;summaryBusy=false;loading.value=true;error.value='';retryMode=null;
  if(!more){rows.value=[];cursor.value=null;hasMore.value=false;updates.value=false;}
  const q=query();q.set('unread',String(unreadOnly.value));if(more&&cursor.value)q.set('before',String(cursor.value));
  try{const data=await api('/notifications?'+q);if(!current(token)||seq!==request)return;
    if(more&&accessStamp&&accessStamp!==data.accessStamp){reset();void load();return;}
    if(more){const known=new Set(rows.value.map(n=>n.id));rows.value.push(...data.items.filter((n:any)=>!known.has(n.id)));applySummary(data);}
    else{rows.value=data.items;applySummary(data,true);if(items.value)items.value.scrollTop=0;}
    hasMore.value=data.hasMore;cursor.value=data.nextCursor;
  }catch(e:any){if(current(token)&&seq===request){error.value=e.message;retryMode=more?'more':'first';}}
  finally{if(current(token)&&seq===request)loading.value=false;}
}
async function show(){if(open.value)return;const token=++session;reset();acting.value=false;open.value=true;bodyOverflow=document.body.style.overflow;document.body.style.overflow='hidden';await nextTick();if(!open.value||!current(token))return;modal.value?.showModal();void load();}
function close(restore=true){session++;request++;summaryRequest++;summaryBusy=false;modal.value?.close();open.value=false;loading.value=false;acting.value=false;document.body.style.overflow=bodyOverflow;if(restore)nextTick(()=>{if(!disposed&&!open.value)trigger.value?.focus();});}
async function markRead(id:number|'all'){
  if(acting.value||loading.value)return;const token=session;acting.value=true;summaryRequest++;summaryBusy=false;error.value='';retryMode=null;
  try{await api(id==='all'?'/notifications/read-all?'+query():`/notifications/${id}/read`,'PUT');if(!current(token))return;
    if(unreadOnly.value)rows.value=rows.value.filter(n=>id!=='all'&&n.id!==id);
    else rows.value=rows.value.map(n=>id==='all'||n.id===id?{...n,read_at:n.read_at||new Date().toISOString()}:n);
    // Account only for this acknowledged write; new arrivals still require explicit refresh.
    baselineUnread=id==='all'?0:Math.max(0,baselineUnread-1);
  }catch(e:any){if(current(token)){error.value=e.message;retryMode=id;}}
  finally{if(current(token)){acting.value=false;await refreshCount();}}
}
async function visit(n:any){
  if(acting.value||loading.value)return;const token=session;acting.value=true;summaryRequest++;summaryBusy=false;error.value='';retryMode=null;
  try{await props.navigate(n);if(!current(token))return;await api(`/notifications/${n.id}/read`,'PUT');if(!current(token))return;close(false);void refreshCount();}
  catch(e:any){if(current(token))error.value=e.message||'暂时无法打开这条消息';}
  finally{if(current(token))acting.value=false;}
}
function retry(){if(retryMode==='first'||retryMode==='more')void load(retryMode==='more');else if(retryMode!==null)void markRead(retryMode);}
function keys(event:KeyboardEvent){if(!open.value)return;if(event.key==='Escape')event.stopImmediatePropagation();if(event.key==='Tab'){event.stopImmediatePropagation();const controls=Array.from(modal.value?.querySelectorAll<HTMLElement>('button:not(:disabled), select:not(:disabled), a[href]')||[]).filter(el=>el.getClientRects().length);const first=controls[0],last=controls.at(-1);if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}}if((event.ctrlKey||event.metaKey)&&['k','s'].includes(event.key.toLowerCase())){event.preventDefault();event.stopImmediatePropagation();}}
const background=()=>{void refreshCount();};
watch([unreadOnly,kind],()=>{if(open.value){reset();void load();}});
onMounted(()=>{window.addEventListener('keydown',keys,true);background();timer=setInterval(background,30000);window.addEventListener('focus',background);window.addEventListener('online',background);document.addEventListener('visibilitychange',background);});
onBeforeUnmount(()=>{window.removeEventListener('keydown',keys,true);disposed=true;session++;request++;summaryRequest++;clearInterval(timer);if(open.value){modal.value?.close();document.body.style.overflow=bodyOverflow;}window.removeEventListener('focus',background);window.removeEventListener('online',background);document.removeEventListener('visibilitychange',background);});
</script>
<template>
  <button ref="trigger" class="icon-btn notification-trigger" aria-label="打开消息中心" :aria-expanded="open" @click="show"><Bell :size="18"/><span v-if="unread" class="notification-dot">{{ unread>99?'99+':unread }}</span></button>
  <Teleport to="body"><dialog v-if="open" ref="modal" class="notification-overlay" aria-modal="true" aria-labelledby="notification-heading" @cancel.prevent="close()" @click.self="close()"><section class="notification-drawer">
    <header><div><span class="eyebrow">工作流提醒</span><h2 id="notification-heading">消息中心 <span>{{ unread }} 条未读</span></h2></div><button class="icon-btn" aria-label="关闭消息中心" @click="close()"><X :size="20"/></button></header>
    <div class="notification-toolbar"><div class="list-tabs"><button :class="{active:!unreadOnly}" :aria-pressed="!unreadOnly" :disabled="acting" @click="unreadOnly=false">全部</button><button :class="{active:unreadOnly}" :aria-pressed="unreadOnly" :disabled="acting" @click="unreadOnly=true">未读</button></div><button class="text-btn" :disabled="acting||loading||!(kind?scopeUnread:unread)" @click="markRead('all')"><CheckCheck :size="14"/>{{ kind?'本类已读':'全部已读' }}</button><button class="icon-btn" aria-label="刷新消息" :disabled="loading||acting" @click="load()"><RefreshCw :size="15"/></button></div>
    <label class="notification-filter">消息分类<select v-model="kind" aria-label="筛选消息分类" :disabled="acting"><option value="">全部分类</option><option v-for="(label,key) in kindName" :key="key" :value="key">{{ label }}</option></select><span v-if="kind">本类 {{ scopeUnread }} 条未读</span></label>
    <div v-if="updates" class="notification-updates" role="status"><span>消息有更新，当前阅读位置已保留。</span><button class="text-btn" :disabled="loading||acting" @click="load()">查看最新消息</button></div>
    <div v-if="error" class="notice-error" role="alert"><span>{{ error }}</span><button v-if="retryMode!==null" class="text-btn" :disabled="loading||acting" @click="retry">{{ typeof retryMode==='number'||retryMode==='all'?'重试操作':'重试读取' }}</button></div>
    <div ref="items" class="notification-items" :aria-busy="loading"><article v-for="n in rows" :key="n.id" :data-notification-id="n.id" class="notification-item" :class="{unread:!n.read_at}"><div class="notification-item-meta"><span>{{ kindName[n.kind]||'课程提醒' }}</span><time :datetime="n.created_at">{{ time(n.created_at) }}</time></div><button class="notification-open" :disabled="acting||loading" @click="visit(n)"><strong>{{ n.title }}</strong><ArrowUpRight :size="15"/></button><p>{{ n.course_title }}</p><button v-if="!n.read_at" class="text-btn mark-read" :disabled="acting||loading" @click="markRead(n.id)">标为已读</button></article>
      <div v-if="!rows.length&&!loading&&!error&&!hasMore" class="notification-empty"><Inbox :size="30"/><h3>{{ kind?'没有匹配的消息':unreadOnly?'没有未读消息':'还没有消息' }}</h3><p>{{ kind?'可切换其他分类或查看全部消息。':'新的提交、评价、批注和审核结果会在这里提醒。' }}</p></div>
      <button v-if="hasMore" class="btn secondary full" :disabled="loading||acting" @click="load(true)">{{ loading?'正在加载…':'加载更早消息' }}</button><p v-else-if="loading" class="empty-text" role="status">正在读取消息…</p>
    </div><footer><Bell :size="13"/><span>{{ disconnected?'连接暂不可用，已有列表保留。可手动刷新。':'仅展示当前有权接收的课程事件；未读总数涵盖所有分类。每 30 秒检查更新，不打断阅读。' }}</span></footer>
  </section></dialog></Teleport>
</template>
