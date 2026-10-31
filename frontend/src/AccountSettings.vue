<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { X, UserRound, ShieldCheck, RefreshCw } from 'lucide-vue-next';
import { api } from './api';
const emit=defineEmits(['close','updated','password-changed']);
const dialog=ref<HTMLDialogElement>(),profile=ref<any>(null),name=ref(''),loading=ref(true),saving=ref(false),error=ref(''),notice=ref(''),conflict=ref(false);
const current=ref(''),next=ref(''),confirmation=ref('');
let alive=true,request=0,prior:HTMLElement|null=null,overflow='';
const bytes=computed(()=>new TextEncoder().encode(next.value).length);
function clearSecrets(){current.value='';next.value='';confirmation.value='';}
async function load(preserve=false){const seq=++request;loading.value=true;error.value='';try{const data=await api('/account');if(!alive||seq!==request)return;profile.value=data;if(!preserve)name.value=data.display_name;conflict.value=false;if(preserve)notice.value='已刷新资料并保留输入，请核对后再次保存。';}catch(e:any){if(alive&&seq===request)error.value=e.message;}finally{if(alive&&seq===request)loading.value=false;}}
async function saveName(){if(saving.value||!profile.value)return;saving.value=true;error.value='';notice.value='';try{const data=await api('/account/profile','PUT',{displayName:name.value,revision:profile.value.profile_revision});if(!alive)return;profile.value=data;name.value=data.display_name;conflict.value=false;notice.value='显示名称已保存，课程身份和历史作品不变。';emit('updated',data);}catch(e:any){if(alive){error.value=e.message;conflict.value=e.status===409;}}finally{if(alive)saving.value=false;}}
async function changePassword(){if(saving.value)return;error.value='';notice.value='';if(!next.value.trim()||[...next.value].length<12||bytes.value>72){error.value='新密码至少 12 个字符，且不能过长。';return;}if(next.value!==confirmation.value){error.value='两次输入的新密码不一致。';return;}saving.value=true;try{await api('/account/password','PUT',{currentPassword:current.value,newPassword:next.value,confirmation:confirmation.value});if(alive)emit('password-changed');}catch(e:any){if(alive)error.value=e.message;}finally{clearSecrets();if(alive)saving.value=false;}}
function close(){if(!saving.value)emit('close');}
function keys(e:KeyboardEvent){if(e.key==='Escape'){e.stopImmediatePropagation();}if((e.ctrlKey||e.metaKey)&&['k','s'].includes(e.key.toLowerCase())){e.preventDefault();e.stopImmediatePropagation();}}
onMounted(async()=>{prior=document.activeElement as HTMLElement;overflow=document.body.style.overflow;document.body.style.overflow='hidden';window.addEventListener('keydown',keys,true);await nextTick();if(!alive)return;dialog.value?.showModal();void load();});
onBeforeUnmount(()=>{alive=false;request++;clearSecrets();dialog.value?.close();document.body.style.overflow=overflow;window.removeEventListener('keydown',keys,true);if(prior?.isConnected)prior.focus();});
</script>
<template>
<Teleport to="body"><dialog ref="dialog" class="account-settings" aria-labelledby="account-heading" aria-modal="true" @cancel.prevent="close()" @click.self="close()">
<header><div><span class="eyebrow">个人资料与账号安全</span><h2 id="account-heading">账号设置</h2></div><button class="icon-btn" aria-label="关闭账号设置" :disabled="saving" @click="close"><X :size="20"/></button></header>
<div class="account-settings-content" :aria-busy="loading||saving">
<p v-if="loading" role="status">正在读取账号资料…</p><p v-if="error" class="error-note" role="alert">{{error}}</p><p v-if="notice" class="account-success" role="status">{{notice}}</p>
<button v-if="!profile&&!loading" class="btn secondary" @click="load()"><RefreshCw :size="16"/>重新读取账号</button>
<template v-if="profile">
<section><h3><UserRound :size="18"/>个人资料</h3>
<form @submit.prevent="saveName"><fieldset :disabled="saving||loading"><label>登录邮箱<input :value="profile.email" readonly type="email" autocomplete="username"/></label><label>显示名称<input v-model="name" required maxlength="80" autocomplete="nickname" aria-describedby="account-name-help"/></label><p id="account-name-help" class="muted">1–40 个字符</p>
<div class="account-actions"><button class="btn primary" :disabled="!name.trim()">保存显示名称</button><button v-if="conflict" type="button" class="btn secondary" @click="load(true)">刷新资料，保留输入</button></div></fieldset></form></section>
<section><h3><ShieldCheck :size="18"/>修改密码</h3><p class="muted account-scope">修改后需重新登录。</p><p v-if="profile.password_changed_at" class="muted">上次修改：{{new Date(profile.password_changed_at).toLocaleString('zh-CN')}}</p>
<form @submit.prevent="changePassword"><fieldset :disabled="saving||loading"><label>当前密码<input v-model="current" required type="password" autocomplete="current-password" maxlength="72"/></label><label>新密码<input v-model="next" required type="password" autocomplete="new-password" minlength="12" maxlength="72" aria-describedby="account-password-help"/></label><p id="account-password-help" class="muted">{{ bytes>72 ? '密码过长，请缩短' : '至少 12 个字符' }}</p><label>确认新密码<input v-model="confirmation" required type="password" autocomplete="new-password" minlength="12" maxlength="72"/></label><button class="btn primary" :disabled="bytes>72">修改密码并重新登录</button></fieldset></form></section>
</template><p v-if="saving" class="muted" role="status">正在保存，请稍候再关闭…</p>
</div>
</dialog></Teleport>
</template>
