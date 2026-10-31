<script setup lang="ts">
// 登录页：从 App.vue 拆出。邮箱和密码仍放在 App（登录失效后要保留邮箱、清空密码）。
import { CheckCircle2, History, ShieldCheck, Save, ArrowUpRight } from "lucide-vue-next";
defineProps<{ busy: boolean; error: string; hasBackup: boolean }>();
const email = defineModel<string>("email", { required: true });
const password = defineModel<string>("password", { required: true });
const emit = defineEmits<{ submit: []; "download-backup": [] }>();
</script>

<template>
<div class="login-layout">
  <section class="login-story">
    <div class="brand">
      <span class="brand-icon">课</span>
      <div class="brand-copy">
        <strong>课程开放知识社区</strong><span>面向课程的知识共建空间</span>
      </div>
    </div>
    <div class="login-copy">
      <h1>让知识，<br />在每一次修订中生长。</h1>
      <p>
        从一篇知识帖开始，连接可靠的证据、<br />可执行的反馈与持续改进的学习。
      </p>
      <div class="login-principles">
        <span><CheckCircle2 :size="18" />四维评价</span
        ><span><History :size="18" />版本可追溯</span
        ><span><ShieldCheck :size="18" />证据可核验</span>
      </div>
    </div>
  </section>
  <section class="login-form">
    <form @submit.prevent="emit('submit')">
      <span class="eyebrow">欢迎回来</span>
      <h2>进入你的课程空间</h2>
      <p class="muted">使用课程账号登录，继续未完成的探索。</p>
      <label
        >账号邮箱<input
          v-model="email"
          type="email"
          autocomplete="username"
          required
          placeholder="你的课程账号邮箱" /></label
      ><label
        >密码<input
          v-model="password"
          type="password"
          autocomplete="current-password"
          required
          placeholder="输入密码"
      /></label>
      <p v-if="error" class="error-note" role="alert">
        {{ error }}
      </p>
      <button v-if="hasBackup" type="button" class="btn secondary full" @click="emit('download-backup')"><Save :size="16"/>下载未同步草稿备份</button>
      <button class="btn primary full" :disabled="busy">
        {{ busy ? "正在登录…" : "登录工作空间" }}<ArrowUpRight :size="18" />
      </button>
    </form>
  </section>
</div>
</template>
