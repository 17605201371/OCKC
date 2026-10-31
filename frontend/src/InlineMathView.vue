<script setup lang="ts">
import { computed } from 'vue';
import { NodeViewWrapper, nodeViewProps } from '@tiptap/vue-3';
import katex from 'katex';
const props = defineProps(nodeViewProps);
const rendered = computed(() =>
  katex.renderToString(props.node.attrs.latex || '', {
    displayMode: false, throwOnError: false, trust: false,
    strict: 'warn', maxExpand: 100, maxSize: 20, output: 'htmlAndMathml',
  }),
);
</script>
<template>
  <NodeViewWrapper as="span" class="math-inline" :class="{ 'is-selected': selected }" :title="node.attrs.latex" contenteditable="false">
    <span class="math-render" v-html="rendered"></span>
  </NodeViewWrapper>
</template>
