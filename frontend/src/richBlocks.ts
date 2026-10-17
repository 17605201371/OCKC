import { Node, mergeAttributes } from '@tiptap/core';
import { VueNodeViewRenderer } from '@tiptap/vue-3';
import FigureView from './FigureView.vue';
import MathView from './MathView.vue';
export const Figure = Node.create({
  name: 'figure', group: 'block', atom: true, draggable: true,
  addOptions() { return { onEdit: (_node: any, _pos: number) => {}, onEvidence: (_id: string) => {} }; },
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
});
export const Callout = Node.create({
  name: 'callout', group: 'block', content: 'block+', defining: true,
  parseHTML() { return [{ tag: 'aside[data-callout]' }]; },
  renderHTML({ HTMLAttributes }) { return ['aside', mergeAttributes(HTMLAttributes, { 'data-callout': '', class: 'knowledge-callout' }), 0]; },
});
