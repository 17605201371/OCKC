<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { Bell, X, CheckCheck, RefreshCw, ArrowUpRight, Inbox } from 'lucide-vue-next';
import { api } from './api';
const props=defineProps<{userId:number;navigate:(notice:any)=>Promise<void>}>();
const open=ref(false), unreadOnly=ref(false), rows=ref<any[]>([]), unread=ref(0), loading=ref(false), error=ref(''), hasMore=ref(false), cursor=ref<number|null>(null), acting=ref(false);
const trigger=ref<HTMLButtonElement>(),dialog=ref<HTMLElement>();let timer:ReturnType<typeof setInterval>,generation=0;
const kindName:Record<string,string>={MEMBERSHIP_CHANGE:'课程身份',ASSIGNMENT_CHANGE:'作业变更',SUBMISSION:'待评审',EVALUATION:'正式评价',FEEDBACK:'教师批注',FEEDBACK_REPLY:'反馈回应',EVIDENCE:'证据核验',EVIDENCE_DISPUTED:'争议裁定',PUBLICATION_REQUEST:'发布审核',PUBLICATION_RESULT:'发布结果'};
function time(value:string){return new Date(value).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false});}
async function load(more=false){const gen=++generation;loading.value=true;error.value='';try{const q=new URLSearchParams({unread:String(unreadOnly.value)});if(more&&cursor.value)q.set('before',String(cursor.value));const data=await api('/notifications?'+q);if(gen!==generation)return;rows.value=more?[...rows.value,...data.items]:data.items;unread.value=data.unreadCount;hasMore.value=data.hasMore;cursor.value=data.nextCursor;}catch(e:any){if(gen===generation)error.value=e.message;}finally{if(gen===generation)loading.value=false;}}
async function refreshCount(){try{const data=await api('/notifications?unread=true');unread.value=data.unreadCount;if(open.value&&!acting.value)await load();}catch{/* Existing page remains usable during a connection failure. */}}
async function show(){rows.value=[];cursor.value=null;hasMore.value=false;open.value=true;await load();await nextTick();dialog.value?.querySelector<HTMLButtonElement>('button')?.focus();}
function close(){if(acting.value)return;open.value=false;nextTick(()=>trigger.value?.focus());}
async function markAll(){acting.value=true;error.value='';try{await api('/notifications/read-all','PUT');await load();}catch(e:any){error.value=e.message;}finally{acting.value=false;}}
async function read(n:any){try{await api(`/notifications/${n.id}/read`,'PUT');await load();}catch(e:any){error.value=e.message;}}
async function visit(n:any){acting.value=true;error.value='';try{await props.navigate(n);await api(`/notifications/${n.id}/read`,'PUT');open.value=false;await refreshCount();}catch(e:any){error.value=e.message||'暂时无法打开这条消息';}finally{acting.value=false;}}
function keys(e:KeyboardEvent){if(!open.value)return;if(e.key==='Escape'){e.preventDefault();e.stopImmediatePropagation();close();}if(e.key==='Tab'){const els=[...(dialog.value?.querySelectorAll<HTMLElement>('button:not([disabled]),input:not([disabled]),a[href]')||[])];if(!els.length)return;const first=els[0],last=els[els.length-1];if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}}
watch(unreadOnly,()=>{if(open.value)load();});
onMounted(()=>{refreshCount();timer=setInterval(refreshCount,30000);window.addEventListener('focus',refreshCount);window.addEventListener('keydown',keys,true);});
onBeforeUnmount(()=>{generation++;clearInterval(timer);window.removeEventListener('focus',refreshCount);window.removeEventListener('keydown',keys,true);});
</script>
<template>
  <button ref="trigger" class="icon-btn notification-trigger" aria-label="打开消息中心" :aria-expanded="open" @click="show"><Bell :size="18" /><span v-if="unread" class="notification-dot">{{ unread>99?'99+':unread }}</span></button>
  <Teleport to="body"><div v-if="open" class="notification-overlay" @click.self="close"><section ref="dialog" class="notification-drawer" role="dialog" aria-modal="true" aria-labelledby="notification-heading">
    <header><div><span class="eyebrow">工作流提醒</span><h2 id="notification-heading">消息中心 <span>{{ unread }} 条未读</span></h2></div><button class="icon-btn" aria-label="关闭消息中心" :disabled="acting" @click="close"><X :size="20" /></button></header>
    <div class="notification-toolbar"><div class="list-tabs"><button :class="{active:!unreadOnly}" @click="unreadOnly=false">全部</button><button :class="{active:unreadOnly}" @click="unreadOnly=true">未读</button></div><button class="text-btn" :disabled="acting||!unread" @click="markAll"><CheckCheck :size="14" />全部已读</button><button class="icon-btn" aria-label="刷新消息" :disabled="loading||acting" @click="load()"><RefreshCw :size="15" /></button></div>
    <p v-if="error" class="notice-error" role="alert">{{ error }}</p>
    <div class="notification-items" :aria-busy="loading"><article v-for="n in rows" :key="n.id" class="notification-item" :class="{unread:!n.read_at}"><div class="notification-item-meta"><span>{{ kindName[n.kind]||'课程提醒' }}</span><time>{{ time(n.created_at) }}</time></div><button class="notification-open" :disabled="acting||loading" @click="visit(n)"><strong>{{ n.title }}</strong><ArrowUpRight :size="15" /></button><p>{{ n.course_title }}</p><button v-if="!n.read_at" class="text-btn mark-read" :disabled="acting" @click="read(n)">标为已读</button></article>
      <div v-if="!rows.length&&!loading" class="notification-empty"><Inbox :size="30" /><h3>{{ unreadOnly?'没有未读消息':'还没有消息' }}</h3><p>新的提交、评价、批注和审核结果会在这里提醒。</p></div>
      <button v-if="hasMore" class="btn secondary full" :disabled="loading||acting" @click="load(true)">{{ loading?'正在加载…':'加载更早消息' }}</button><p v-else-if="loading" class="empty-text">正在读取消息…</p>
    </div><footer><Bell :size="13" />仅展示你有权限接收的课程事件；每 30 秒更新。</footer>
  </section></div></Teleport>
</template>
