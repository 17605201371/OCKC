<script setup lang="ts">
// 批改页选中正文或点图片后弹出的批注浮层。选区与位置由 App 计算，保存也在 App。
import { MessageSquare, X } from "lucide-vue-next";
defineProps<{
  annotation: { kind: string; left: number; top: number; label?: string; quote?: string };
  error: string;
  busy: boolean;
}>();
const dim = defineModel<string>("dim", { required: true });
const body = defineModel<string>("body", { required: true });
const emit = defineEmits<{ close: []; submit: [] }>();
const DIMENSIONS = [
  { code: "S", name: "科学性" },
  { code: "A", name: "准确性" },
  { code: "C", name: "完整性" },
  { code: "L", name: "逻辑性" },
];
</script>

<template>
  <div
    class="annotation-popover"
    :style="{ left: annotation.left + 'px', top: annotation.top + 'px' }"
    role="dialog"
    aria-label="添加批注"
    @mouseup.stop
  >
    <div class="annotation-popover-head">
      <strong><MessageSquare :size="14" />{{ annotation.kind === "figure" ? "图片批注" : "添加批注" }}</strong>
      <button class="annotation-close" aria-label="关闭批注浮层" @click="emit('close')"><X :size="15" /></button>
    </div>
    <p class="annotation-quote" :class="{ 'is-figure': annotation.kind === 'figure' }">{{ annotation.kind === "figure" ? annotation.label : annotation.quote }}</p>
    <div class="annotation-dims" role="group" aria-label="批注维度">
      <button
        v-for="d in DIMENSIONS"
        :key="d.code"
        type="button"
        :class="{ active: dim === d.code }"
        @click="dim = d.code"
      >{{ d.name }}</button>
    </div>
    <textarea v-model="body" rows="3" maxlength="2000" :placeholder="annotation.kind === 'figure' ? '说明这张图的问题：读法、标注、与正文是否对得上' : '指出问题并给出改法，一两句即可'"></textarea>
    <p v-if="error" class="annotation-error">{{ error }}</p>
    <div class="annotation-actions">
      <button class="text-btn" @click="emit('close')">取消</button>
      <button class="btn primary" :disabled="busy" @click="emit('submit')">{{ busy ? "保存中…" : "添加批注" }}</button>
    </div>
  </div>
</template>
