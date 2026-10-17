<script setup lang="ts">
import { computed } from 'vue';
import { NodeViewWrapper, nodeViewProps } from '@tiptap/vue-3';
import katex from 'katex';
const props = defineProps(nodeViewProps);
const rendered = computed(() => katex.renderToString(props.node.attrs.latex || '', { displayMode: true, throwOnError: false, trust: false, strict: 'warn', maxExpand: 100, maxSize: 20, output: 'htmlAndMathml' }));
</script>
<template>
  <NodeViewWrapper class="knowledge-math" :class="{ 'is-selected': selected }" :data-block-id="node.attrs.blockId" contenteditable="false">
    <div class="math-render" v-html="rendered"></div>
    <button v-if="editor.isEditable" type="button" @click="extension.options.onEdit(node, getPos())">编辑公式</button>
  </NodeViewWrapper>
</template>
