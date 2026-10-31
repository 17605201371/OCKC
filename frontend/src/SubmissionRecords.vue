<script setup lang="ts">
// 阅读与批改页正文下方的三块记录：修订历史（仅阅读页）、与上一版的段落变化、来源与核验。
import { ArrowUpRight } from "lucide-vue-next";
import { fmt, number } from "./format";
defineProps<{
  version: any;
  versionHistory: any[];
  historyBusy: boolean;
  busy: boolean;
  isReader: boolean;
  canAdjudicate: boolean;
}>();
const emit = defineEmits<{
  "open-version": [id: number];
  "open-evidence": [id: number];
  "view-verification": [evidence: any];
  modal: [kind: string, data?: Record<string, any>];
}>();
</script>

<template>
  <section v-if="isReader && versionHistory.length" class="card source-panel revision-history">
    <details :open="versionHistory.length > 1">
      <summary class="section-title">
        <h3>修订历史</h3>
        <span class="muted">{{ versionHistory.length }} 个版本</span>
      </summary>
      <article
        v-for="h in versionHistory"
        :key="h.id"
        class="history-row"
        :class="{ current: h.id === version.id }"
      >
        <button
          class="history-open"
          :disabled="h.id === version.id || historyBusy"
          :aria-current="h.id === version.id ? 'true' : undefined"
          @click="emit('open-version', h.id)"
        >
          <strong>v{{ h.version_no }}</strong>
          <span class="muted">{{ fmt(h.created_at) }}</span>
          <span v-if="h.published" class="badge green">已发布</span>
          <span v-else class="badge">未发布</span>
          <span v-if="h.q_final != null" class="history-score">{{ number(h.q_final) }} 分</span>
          <span v-if="h.id === version.id" class="muted">正在阅读</span>
        </button>
        <div v-if="h.feedbackVisible && h.feedback.length" class="history-feedback">
          <p v-for="f in h.feedback" :key="f.id" class="history-note">
            <span class="indicator-tag">{{ f.indicator_code }}</span>{{ f.body }}
          </p>
        </div>
        <p v-else-if="h.feedbackVisible" class="muted history-note">这一版没有批注。</p>
        <p v-else class="muted history-note">教师批注未公开。</p>
      </article>
    </details>
  </section>
  <section class="card source-panel">
    <details class="revision-diff">
      <summary>
        {{
          version.version_no === 1 ? "首个提交版本" : "与上一版相比"
        }}
        · {{ version.changes.length }} 处段落文本变化
      </summary>
      <div
        v-for="(c, i) in version.changes"
        :key="i"
        class="diff-item"
      >
        <strong>{{
          (
            {
              ADDED: "新增段落",
              DELETED: "删除段落",
              CHANGED: "修改段落",
              STRUCTURE: "格式或结构变化",
            } as any
          )[c.type]
        }}</strong>
        <p v-if="c.before" class="diff-before">− {{ c.before }}</p>
        <p v-if="c.after" class="diff-after">+ {{ c.after }}</p>
      </div>
      <p v-if="!version.changes.length" class="muted">
        正文段落文本未变化；标题及证据变化请在对应区域核对。
      </p>
    </details>
  </section>
  <details class="card source-panel source-collapsed">
    <summary class="section-title">
      <h3>来源与核验</h3>
      <span class="muted">{{ version.evidence.length }} 条 · 展开查看</span>
    </summary>
    <div
      v-for="e in version.evidence"
      :key="e.id"
      class="reader-source"
    >
      <strong>{{ e.source_title }}</strong>
      <button class="text-btn" :disabled="busy" @click="emit('open-evidence', e.id)">进入核验工作台<ArrowUpRight :size="14"/></button>
      <p>{{ e.claim_text }}</p>
      <blockquote>{{ e.excerpt }}</blockquote>
      <div class="evidence-actions">
        <span
          class="badge"
          :class="
            e.state === 'PASS'
              ? 'green'
              : e.state === 'FAIL'
                ? 'red'
                : 'orange'
          "
          >{{
            (
              {
                PENDING: "待核验",
                PASS: "已通过",
                FAIL: "未通过",
                DISPUTED: "有争议",
              } as any
            )[e.state]
          }}</span
        ><button class="text-btn" @click="emit('view-verification', e)">
          核验记录</button
        ><button
          v-if="canAdjudicate"
          class="text-btn"
          @click="
            emit('modal', 'verify', {
              id: e.id,
              sourceTitle: e.source_title,
            })
          "
        >
          核验 / 裁定
        </button>
      </div>
    </div>
    <p v-if="!version.evidence.length" class="muted">
      此版本未挂载证据；教师仍需评估需要依据的主张是否有充分来源。
    </p>
  </details>
</template>
