<script setup lang="ts">
import { closeHistory } from '@tiptap/pm/history';
import { NodeViewWrapper, nodeViewProps } from '@tiptap/vue-3';
import { ImagePlus, BookOpen, PencilLine, Trash2 } from 'lucide-vue-next';
const props = defineProps(nodeViewProps);
function removeImage() { props.editor.view.dispatch(closeHistory(props.editor.state.tr)); props.deleteNode(); props.editor.view.dispatch(closeHistory(props.editor.state.tr)); }
</script>
<template>
  <NodeViewWrapper as="figure" class="knowledge-figure" :class="{ 'is-selected': selected }" :data-block-id="node.attrs.blockId">
    <div class="figure-image" contenteditable="false">
      <img :src="node.attrs.src" :alt="node.attrs.alt || '知识帖插图'" loading="lazy" draggable="false" />
      <div v-if="editor.isEditable" class="figure-tools">
        <button type="button" @click="extension.options.onEdit(node, getPos())"><ImagePlus :size="14" />换图</button>
        <button type="button" @click="extension.options.onEdit(node, getPos())"><PencilLine :size="14" />编辑图注</button>
        <button type="button" @click="extension.options.onEvidence(node.attrs.blockId)"><BookOpen :size="14" />挂证据</button>
        <button type="button" aria-label="删除图片" title="删除图片（可撤销）" @click="removeImage()"><Trash2 :size="14" /></button>
      </div>
    </div>
    <figcaption contenteditable="false">
      <span class="figure-label">图</span>
      <div><strong>{{ node.attrs.caption || node.attrs.alt || '未添加图注' }}</strong><p v-if="node.attrs.source">来源：{{ node.attrs.source }}</p></div>
    </figcaption>
  </NodeViewWrapper>
</template>
