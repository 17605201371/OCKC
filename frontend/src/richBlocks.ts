import { Node, mergeAttributes, nodeInputRule } from '@tiptap/core';
import { VueNodeViewRenderer } from '@tiptap/vue-3';
import { shallowRef } from 'vue';
import FigureView from './FigureView.vue';
import MathView from './MathView.vue';
import InlineMathView from './InlineMathView.vue';

// 图块批注徽标需要知道「这张图有几条批注」。用模块级 shallowRef 共享，
// 比通过 extension.options 传 ref 更可靠（options 在 configure 时会被合并）。
export const annotationStore = shallowRef<Array<{ id: number; blockId: string; quote: string }>>([]);

export const Figure = Node.create({
  name: 'figure', group: 'block', atom: true, draggable: true,
  addOptions() { return { onEdit: (_node: any, _pos: number) => {}, onEvidence: (_id: string) => {}, onAnnotate: (_node: any) => {}, annotateEnabled: false }; },
  addAttributes() { return { src: { default: '' }, alt: { default: '' }, caption: { default: '' }, source: { default: '' } }; },
  parseHTML() { return [{ tag: 'figure[data-knowledge-image]' }]; },
  renderHTML({ HTMLAttributes }) { return ['figure', mergeAttributes(HTMLAttributes, { 'data-knowledge-image': '' }), ['img', { src: HTMLAttributes.src, alt: HTMLAttributes.alt }], ['figcaption', {}, HTMLAttributes.caption || '']]; },
  addNodeView() { return VueNodeViewRenderer(FigureView); },
});
export const MathBlock = Node.create({
  name: 'mathBlock', group: 'block', atom: true,
  addOptions() { return { onEdit: (_node: any, _pos: number) => {} }; },
  addAttributes() { return { latex: { default: '' } }; },
  parseHTML() { return [{ tag: 'div[data-math-block]' }]; },
  renderHTML({ HTMLAttributes }) { return ['div', mergeAttributes(HTMLAttributes, { 'data-math-block': '' }), HTMLAttributes.latex]; },
  addNodeView() { return VueNodeViewRenderer(MathView); },
  // 输入 $$...$$ 后立即转成独立公式块，省掉对话框。
  addInputRules() {
    return [
      nodeInputRule({
        find: /^\$\$([^$\n]{1,2000})\$\$$/,
        type: this.type,
        getAttributes: (match) => ({ latex: String(match[1]).trim() }),
      }),
    ];
  },
});
export const InlineMath = Node.create({
  name: 'inlineMath', group: 'inline', inline: true, atom: true, selectable: true,
  addOptions() { return { onEdit: (_node: any, _pos: number) => {} }; },
  addAttributes() { return { latex: { default: '' } }; },
  parseHTML() { return [{ tag: 'span[data-inline-math]' }]; },
  renderHTML({ HTMLAttributes }) { return ['span', mergeAttributes(HTMLAttributes, { 'data-inline-math': '' }), HTMLAttributes.latex]; },
  addNodeView() { return VueNodeViewRenderer(InlineMathView); },
  // 输入 $...$ 就地转成行内公式；要求两侧不贴空格，避免把价格写法误判成公式。
  addInputRules() {
    return [
      nodeInputRule({
        find: /(?<!\$)\$(?!\s)([^$\n]{1,300}?)(?<!\s)\$$/,
        type: this.type,
        getAttributes: (match) => ({ latex: String(match[1]).trim() }),
      }),
    ];
  },
});
export const Callout = Node.create({
  name: 'callout', group: 'block', content: 'block+', defining: true,
  parseHTML() { return [{ tag: 'aside[data-callout]' }]; },
  renderHTML({ HTMLAttributes }) { return ['aside', mergeAttributes(HTMLAttributes, { 'data-callout': '', class: 'knowledge-callout' }), 0]; },
});
