<script setup lang="ts">
import {
  ref,
  computed,
  watch,
  nextTick,
  onMounted,
  onBeforeUnmount,
} from "vue";
import { useEditor, EditorContent } from "@tiptap/vue-3";
import { TableKit } from '@tiptap/extension-table';
import katex from 'katex';
import 'katex/dist/katex.min.css';
import { Figure, MathBlock, Callout } from './richBlocks';
import { uploadImage } from './api';
import StarterKit from "@tiptap/starter-kit";
import { Extension } from "@tiptap/core";
import { closeHistory } from "@tiptap/pm/history";
import { Plugin, PluginKey } from "@tiptap/pm/state";
import { Decoration, DecorationSet } from "@tiptap/pm/view";
import {
  Bold,
  Italic,
  Strikethrough,
  Code2,
  Heading1,
  Heading2,
  Heading3,
  List,
  ListOrdered,
  Quote,
  Minus,
  Type,
  Undo2,
  Redo2,
  Link2,
  Unlink,
  BookOpen,
  Plus,
  Download,
  Keyboard,
  Check,
  X,
  ImagePlus,
  Table2,
  Sigma,
  Lightbulb,
  Underline,
  Trash2,
} from "lucide-vue-next";
import { toMarkdown } from "./markdown";
const props = defineProps<{
  modelValue: any;
  readonly?: boolean;
  highlightId?: string;
  documentTitle?: string;
  entryId?: number;
  evidenceCount?: number;
}>();
const emit = defineEmits(["update:modelValue", "selected", "evidence", "uploading"]);
const mediaDialog = ref<HTMLDialogElement>(), mathDialog = ref<HTMLDialogElement>();
const imageFile = ref<HTMLInputElement>();
const media = ref({ src: '', alt: '', caption: '', source: '' });
const mediaError = ref(''), mediaBusy = ref(false), mathLatex = ref(''), mathError = ref('');
let mediaPosition: number | null = null, mathPosition: number | null = null;
const mathPreview = computed(() => {
  try { return katex.renderToString(mathLatex.value || '\\text{在下方输入公式}', { displayMode: true, throwOnError: true, trust: false, maxExpand: 100, maxSize: 20 }); }
  catch { return ''; }
});
function showImage(node?: any, pos?: number) {
  mediaPosition = typeof pos === 'number' ? pos : null;
  media.value = node ? { ...node.attrs } : { src: '', alt: '', caption: '', source: '' };
  mediaError.value = ''; bubble.value = false;
  savedSelection = { from: editor.value!.state.selection.from, to: editor.value!.state.selection.to };
  nextTick(() => mediaDialog.value?.showModal());
}
async function selectImage(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0];
  if (!file) return;
  await loadImage(file); input.value = '';
}
async function loadImage(file: File) {
  if (!props.entryId) { mediaError.value = '请先打开一份作业草稿'; return; }
  if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 5 * 1024 * 1024) { mediaError.value = '请选择不超过 5 MB 的 PNG 或 JPEG 图片'; return; }
  mediaBusy.value = true; mediaError.value = ''; emit('uploading', true);
  try { const result = await uploadImage(props.entryId, file); media.value.src = result.src; if (!media.value.alt) media.value.alt = file.name.replace(/\.[^.]+$/, '').slice(0, 300); }
  catch (e: any) { mediaError.value = e.message || '上传失败，请重试'; }
  finally { mediaBusy.value = false; emit('uploading', false); }
}
function insertImage() {
  if (!media.value.src) { mediaError.value = '请先上传一张图片'; return; }
  const e = editor.value; if (!e) return;
  e.view.dispatch(closeHistory(e.state.tr));
  if (mediaPosition !== null) e.chain().focus().setNodeSelection(mediaPosition).updateAttributes('figure', media.value).run();
  else e.chain().focus().setTextSelection(savedSelection).insertContent([{ type: 'figure', attrs: { ...media.value, blockId: crypto.randomUUID() } }, { type: 'paragraph', attrs: { blockId: crypto.randomUUID() } }]).run();
  e.view.dispatch(closeHistory(e.state.tr));
  mediaDialog.value?.close();
}
function showMath(node?: any, pos?: number) {
  mathPosition = typeof pos === 'number' ? pos : null; mathLatex.value = node?.attrs?.latex || ''; mathError.value = ''; bubble.value = false;
  savedSelection = { from: editor.value!.state.selection.from, to: editor.value!.state.selection.to };
  nextTick(() => mathDialog.value?.showModal());
}
function insertMath() {
  if (!mathLatex.value.trim() || !mathPreview.value) { mathError.value = '请填写有效的 LaTeX 公式'; return; }
  const e = editor.value; if (!e) return;
  e.view.dispatch(closeHistory(e.state.tr));
  if (mathPosition !== null) e.chain().focus().setNodeSelection(mathPosition).updateAttributes('mathBlock', { latex: mathLatex.value }).run();
  else e.chain().focus().setTextSelection(savedSelection).insertContent([{ type: 'mathBlock', attrs: { latex: mathLatex.value, blockId: crypto.randomUUID() } }, { type: 'paragraph', attrs: { blockId: crypto.randomUUID() } }]).run();
  e.view.dispatch(closeHistory(e.state.tr));
  mathDialog.value?.close();
}
function evidenceFor(id: string) { emit('selected', id); emit('evidence'); }
function tableAction(action: string) {
  const c = editor.value?.chain().focus(); if (!c) return;
  if (action === 'row') c.addRowAfter().run(); else if (action === 'column') c.addColumnAfter().run();
  else if (action === 'deleteRow') c.deleteRow().run(); else if (action === 'deleteColumn') c.deleteColumn().run(); else c.deleteTable().run();
}
const slash = ref<{ from: number; to: number; query: string } | null>(null);
const slashIndex = ref(0),
  bubble = ref(false),
  revision = ref(0);
const slashPosition = ref({ left: "0px", top: "0px" }),
  bubblePosition = ref({ left: "0px", top: "0px" });
const helpDialog = ref<HTMLDialogElement>(),
  linkDialog = ref<HTMLDialogElement>();
const linkValue = ref(""),
  linkError = ref("");
let savedSelection = { from: 0, to: 0 };
let dismissedSlash = "";
const commands = [
  { id: 'image', label: '图片与图注', detail: '上传示意图，补充图注和来源', hint: '图片', icon: ImagePlus, terms: 'image photo tupian 图片' },
  { id: 'table', label: '表格', detail: '对照概念、过程与数据', hint: '3 × 3', icon: Table2, terms: 'table biaoge 表格' },
  { id: 'math', label: '公式', detail: '用 LaTeX 排版公式与反应式', hint: 'LaTeX', icon: Sigma, terms: 'math formula gongshi 公式' },
  { id: 'callout', label: '提示块', detail: '突出关键结论或常见误区', hint: '提示', icon: Lightbulb, terms: 'callout note tishi 提示' },
  {
    id: "paragraph",
    label: "正文",
    detail: "回到普通段落",
    hint: "文本",
    icon: Type,
    terms: "text paragraph zhengwen",
  },
  {
    id: "h1",
    label: "一级标题",
    detail: "文章的主要章节",
    hint: "#",
    icon: Heading1,
    terms: "heading h1 biaoti 标题",
  },
  {
    id: "h2",
    label: "二级标题",
    detail: "展开一个具体论点",
    hint: "##",
    icon: Heading2,
    terms: "heading h2 biaoti 标题",
  },
  {
    id: "h3",
    label: "三级标题",
    detail: "补充细节与说明",
    hint: "###",
    icon: Heading3,
    terms: "heading h3 biaoti 标题",
  },
  {
    id: "bullet",
    label: "无序列表",
    detail: "列出并列的观点",
    hint: "- 空格",
    icon: List,
    terms: "list bullet liebiao 列表",
  },
  {
    id: "ordered",
    label: "有序列表",
    detail: "按步骤组织解释",
    hint: "1. 空格",
    icon: ListOrdered,
    terms: "list ordered liebiao 列表",
  },
  {
    id: "quote",
    label: "引用",
    detail: "区分原文与自己的论述",
    hint: "> 空格",
    icon: Quote,
    terms: "quote yinyong",
  },
  {
    id: "code",
    label: "代码块",
    detail: "等宽排版，保留缩进",
    hint: "``` 空格",
    icon: Code2,
    terms: "code daima",
  },
  {
    id: "rule",
    label: "分隔线",
    detail: "为内容划分段落",
    hint: "---",
    icon: Minus,
    terms: "divider rule fengexian",
  },
];
const filteredCommands = computed(() => {
  const q = slash.value?.query.trim().toLowerCase() || "";
  return commands.filter((c) => !q || `${c.label} ${c.terms}`.includes(q));
});
const characterCount = computed(() => {
  revision.value;
  return editor.value?.state.doc.textContent.replace(/\s/g, "").length || 0;
});
const readingMinutes = computed(() =>
  Math.max(1, Math.ceil(characterCount.value / 350)),
);
function activeBlockId() {
  const selection = editor.value?.state.selection;
  if ((selection as any)?.node?.attrs?.blockId) return (selection as any).node.attrs.blockId;
  const p = selection?.$from;
  if (!p) return "";
  for (let d = p.depth; d >= 0; d--)
    if (p.node(d).attrs.blockId) return p.node(d).attrs.blockId;
  return "";
}
function positionPopup(pos: number, width: number, height: number) {
  const e = editor.value;
  if (!e) return { left: "16px", top: "80px" };
  const c = e.view.coordsAtPos(pos);
  const top =
    c.bottom + height + 16 > innerHeight
      ? Math.max(64, c.top - height - 10)
      : c.bottom + 10;
  return {
    left: `${Math.max(16, Math.min(c.left, innerWidth - width - 16))}px`,
    top: `${top}px`,
  };
}
function syncUi() {
  const e = editor.value;
  if (!e || props.readonly) return;
  revision.value++;
  const { selection } = e.state;
  const id = activeBlockId();
  if (id) emit("selected", id);
  const parent = selection.$from.parent;
  const before = parent.textBetween(0, selection.$from.parentOffset, "", "");
  const match =
    selection.empty &&
    ["paragraph", "heading"].includes(parent.type.name) &&
    !e.isActive("codeBlock")
      ? /^\/([^/\n]{0,30})$/.exec(before)
      : null;
  const key = `${selection.$from.start()}:${before}`;
  if (!match) dismissedSlash = "";
  if (match && dismissedSlash !== key && e.isFocused) {
    const old = slash.value?.query;
    slash.value = {
      from: selection.$from.start(),
      to: selection.from,
      query: match[1],
    };
    if (old !== match[1]) slashIndex.value = 0;
    slashPosition.value = positionPopup(
      selection.from,
      320,
      Math.min(420, Math.max(180, filteredCommands.value.length * 58 + 104)),
    );
  } else slash.value = null;
  bubble.value =
    !selection.empty &&
    e.isFocused &&
    !e.isActive("codeBlock") &&
    !linkDialog.value?.open;
  if (bubble.value) {
    const a = e.view.coordsAtPos(selection.from),
      b = e.view.coordsAtPos(selection.to);
    bubblePosition.value = {
      left: `${Math.max(16, Math.min((a.left + b.left) / 2 - 164, innerWidth - 344))}px`,
      top: `${Math.max(64, a.top - 58)}px`,
    };
  }
}
function closeSlash() {
  const e = editor.value;
  if (e && slash.value)
    dismissedSlash = `${e.state.selection.$from.start()}:${e.state.selection.$from.parent.textBetween(0, e.state.selection.$from.parentOffset, "", "")}`;
  slash.value = null;
}
function insertCommand(id: string) {
  const e = editor.value;
  if (!e) return;
  let chain = e.chain().focus();
  if (slash.value)
    chain = chain.deleteRange({ from: slash.value.from, to: slash.value.to });
  slash.value = null;
  if (['image', 'math'].includes(id)) {
    chain.run(); if (id === 'image') showImage(); else showMath(); return;
  }
  if (id === 'table') { chain.insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run(); return; }
  if (id === 'callout') { chain.insertContent({ type: 'callout', content: [{ type: 'paragraph', attrs: { blockId: crypto.randomUUID() }, content: [{ type: 'text', text: '关键结论：' }] }] }).run(); return; }
  if (id === "paragraph") chain.setParagraph().run();
  else if (id.startsWith("h"))
    chain.setHeading({ level: Number(id.slice(1)) as 1 | 2 | 3 }).run();
  else if (id === "bullet") chain.toggleBulletList().run();
  else if (id === "ordered") chain.toggleOrderedList().run();
  else if (id === "quote") chain.toggleBlockquote().run();
  else if (id === "code") chain.setCodeBlock().run();
  else if (id === "rule") chain.setHorizontalRule().run();
}
function openInsert() {
  const e = editor.value;
  if (!e) return;
  e.chain().focus().run();
  if (e.state.selection.$from.parent.textContent)
    e.chain().insertContent({ type: "paragraph" }).run();
  dismissedSlash = "";
  e.chain().insertContent("/").run();
  syncUi();
}
async function showHelp() {
  closeSlash();
  bubble.value = false;
  await nextTick();
  helpDialog.value?.showModal();
}
async function showLink() {
  const e = editor.value;
  if (!e) return;
  savedSelection = { from: e.state.selection.from, to: e.state.selection.to };
  linkValue.value = e.getAttributes("link").href || "";
  linkError.value = "";
  bubble.value = false;
  await nextTick();
  linkDialog.value?.showModal();
}
function setLink() {
  const raw = linkValue.value.trim();
  let url: URL;
  try {
    url = new URL(raw);
    if (
      !["https:", "http:"].includes(url.protocol) ||
      !url.hostname ||
      /\s/.test(raw)
    )
      throw Error();
  } catch {
    linkError.value = "请输入完整的 http:// 或 https:// 链接。";
    return;
  }
  editor.value
    ?.chain()
    .focus()
    .setTextSelection(savedSelection)
    .extendMarkRange("link")
    .setLink({ href: url.href })
    .run();
  linkDialog.value?.close();
}
function removeLink() {
  editor.value
    ?.chain()
    .focus()
    .setTextSelection(savedSelection)
    .extendMarkRange("link")
    .unsetLink()
    .run();
  linkDialog.value?.close();
}
function attachEvidence() {
  const id = activeBlockId();
  if (id) {
    emit("selected", id);
    bubble.value = false;
    emit("evidence");
  }
}
function exportMarkdown() {
  const title = props.documentTitle || "知识帖";
  const body =
    "# " +
    title.replace(/\n/g, " ") +
    "\n\n" +
    toMarkdown(editor.value?.getJSON() || props.modelValue);
  const url = URL.createObjectURL(
    new Blob([body], { type: "text/markdown;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = title.replace(/[\\/:*?"<>|]/g, "_").slice(0, 80) + ".md";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
const Ids = Extension.create({
  name: "blockIds",
  addGlobalAttributes() {
    return [
      {
        types: ["paragraph", "heading", "codeBlock", "figure", "mathBlock"],
        attributes: {
          blockId: {
            default: null,
            parseHTML: (e) => e.getAttribute("data-block-id"),
            renderHTML: (a) =>
              a.blockId ? { "data-block-id": a.blockId } : {},
          },
        },
      },
    ];
  },
  addProseMirrorPlugins() {
    return [
      new Plugin({
        appendTransaction: (_trs, _old, state) => {
          const tr = state.tr;
          let changed = false;
          const seen = new Set<string>();
          state.doc.descendants((node, pos) => {
            if (
              ["paragraph", "heading", "codeBlock", "figure", "mathBlock"].includes(node.type.name)
            ) {
              let id = node.attrs.blockId;
              if (!id || seen.has(id)) {
                id = crypto.randomUUID();
                tr.setNodeMarkup(pos, undefined, {
                  ...node.attrs,
                  blockId: id,
                });
                changed = true;
              }
              seen.add(id);
            }
          });
          return changed ? tr : null;
        },
      }),
    ];
  },
});
const highlightKey = new PluginKey("feedbackHighlight");
const Highlight = Extension.create({
  name: "feedbackHighlight",
  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: highlightKey,
        state: {
          init: () => "",
          apply: (tr, old) => tr.getMeta(highlightKey) ?? old,
        },
        props: {
          decorations: (state) => {
            const id = highlightKey.getState(state);
            const nodes: Decoration[] = [];
            if (
              !props.readonly &&
              state.doc.childCount === 1 &&
              state.doc.firstChild?.type.name === "paragraph" &&
              !state.doc.firstChild.content.size
            ) {
              nodes.push(
                Decoration.node(0, state.doc.firstChild.nodeSize, {
                  class: "is-empty",
                  "data-placeholder": "写下你的解释，或输入 / 插入内容…",
                }),
              );
            }
            if (id)
              state.doc.descendants((node, pos) => {
                if (node.attrs.blockId === id)
                  nodes.push(
                    Decoration.node(pos, pos + node.nodeSize, {
                      class: "anchor-focus",
                    }),
                  );
              });
            return DecorationSet.create(state.doc, nodes);
          },
        },
      }),
    ];
  },
});
const editor = useEditor({
  content: props.modelValue,
  editable: !props.readonly,
  extensions: [
    StarterKit.configure({
      link: {
        openOnClick: !!props.readonly,
        autolink: false,
        linkOnPaste: false,
        protocols: ["http", "https"],
        HTMLAttributes: {
          rel: "noopener noreferrer nofollow",
          target: "_blank",
        },
      },
      heading: { levels: [1, 2, 3] },
    }),
    TableKit.configure({ table: { resizable: false, HTMLAttributes: { class: 'knowledge-table' } } }),
    Figure.configure({ onEdit: showImage, onEvidence: evidenceFor }),
    MathBlock.configure({ onEdit: showMath }), Callout,
    Ids,
    Highlight,
  ],
  editorProps: {
    attributes: {
      class: "prose-editor",
      "aria-label": props.readonly ? "作品正文" : "知识帖正文",
      "data-placeholder": "写下你的解释，或输入 / 插入内容…",
    },
    handlePaste: (_view, event) => {
      const file = [...(event.clipboardData?.files || [])].find(f => f.type.startsWith('image/'));
      if (!props.readonly && file) { event.preventDefault(); showImage(); void loadImage(file); return true; }
      return false;
    },
    handleDrop: (_view, event) => {
      const file = [...(event.dataTransfer?.files || [])].find(f => f.type.startsWith('image/'));
      if (!props.readonly && file) { event.preventDefault(); const pos = editor.value?.view.posAtCoords({ left: event.clientX, top: event.clientY }); if (pos) editor.value?.commands.setTextSelection(pos.pos); showImage(); void loadImage(file); return true; }
      return false;
    },
    handleKeyDown: (_view, event) => {
      if (event.isComposing) return false;
      if (slash.value) {
        if (event.key === "Escape") {
          closeSlash();
          return true;
        }
        if (["ArrowDown", "ArrowUp"].includes(event.key)) {
          const n = filteredCommands.value.length;
          slashIndex.value = n
            ? (slashIndex.value + (event.key === "ArrowDown" ? 1 : -1) + n) % n
            : 0;
          nextTick(() =>
            document
              .querySelector(".slash-item.selected")
              ?.scrollIntoView({ block: "nearest" }),
          );
          return true;
        }
        if (event.key === "Enter" && filteredCommands.value.length) {
          insertCommand(
            filteredCommands.value[slashIndex.value]?.id ||
              filteredCommands.value[0].id,
          );
          return true;
        }
      }
      if (
        (event.metaKey || event.ctrlKey) &&
        event.key.toLowerCase() === "k" &&
        !props.readonly
      ) {
        void showLink();
        return true;
      }
      return false;
    },
  },
  onCreate: () => nextTick(syncUi),
  onUpdate: ({ editor: e }) => {
    emit("update:modelValue", e.getJSON());
    syncUi();
  },
  onSelectionUpdate: () => syncUi(),
  onFocus: () => syncUi(),
  onBlur: () =>
    setTimeout(() => {
      if (
        !editor.value?.isFocused &&
        !document.activeElement?.closest(".writer-bubble, .writer-slash")
      ) {
        bubble.value = false;
        slash.value = null;
      }
    }, 120),
  onTransaction: () => {
    revision.value++;
  },
});
watch(
  () => props.modelValue,
  (v) => {
    if (
      editor.value &&
      JSON.stringify(v) !== JSON.stringify(editor.value.getJSON())
    )
      editor.value.commands.setContent(v, { emitUpdate: false });
  },
);
watch(
  () => props.readonly,
  (v) => editor.value?.setEditable(!v),
);
watch(
  () => props.highlightId,
  (id) => {
    if (editor.value)
      editor.value.view.dispatch(
        editor.value.state.tr.setMeta(highlightKey, id || ""),
      );
  },
);
function onViewportChange() {
  if (slash.value || bubble.value) syncUi();
}
onMounted(() => {
  window.addEventListener("resize", onViewportChange);
  window.addEventListener("scroll", onViewportChange, true);
});
onBeforeUnmount(() => {
  window.removeEventListener("resize", onViewportChange);
  window.removeEventListener("scroll", onViewportChange, true);
  editor.value?.destroy();
});
</script>
<template>
  <div class="editor-shell" :class="{ 'writer-editable': !readonly }">
    <div
      v-if="!readonly && editor"
      class="writer-commandbar"
      aria-label="写作工具"
    >
      <div class="writer-format-tools">
        <button type="button" aria-label="正文加粗" title="加粗" :class="{ active: editor.isActive('bold') }" @mousedown.prevent @click="editor.chain().focus().toggleBold().run()"><Bold :size="17" /></button>
        <button type="button" aria-label="正文斜体" title="斜体" :class="{ active: editor.isActive('italic') }" @mousedown.prevent @click="editor.chain().focus().toggleItalic().run()"><Italic :size="17" /></button>
        <button type="button" aria-label="正文下划线" title="下划线" :class="{ active: editor.isActive('underline') }" @mousedown.prevent @click="editor.chain().focus().toggleUnderline().run()"><Underline :size="17" /></button>
        <span class="writer-separator"></span>
        <button type="button" aria-label="设置一级标题" title="一级标题" :class="{ active: editor.isActive('heading', {level: 1}) }" @mousedown.prevent @click="editor.chain().focus().toggleHeading({level: 1}).run()"><Heading1 :size="19" /></button>
        <button type="button" aria-label="设置二级标题" title="二级标题" :class="{ active: editor.isActive('heading', {level: 2}) }" @mousedown.prevent @click="editor.chain().focus().toggleHeading({level: 2}).run()"><Heading2 :size="19" /></button>
        <span class="writer-separator"></span>
        <button type="button" aria-label="插入无序列表" title="无序列表" @mousedown.prevent @click="editor.chain().focus().toggleBulletList().run()"><List :size="19" /></button>
        <button type="button" aria-label="插入图片" title="图片与图注" @mousedown.prevent @click="showImage()"><ImagePlus :size="19" /></button>
        <button type="button" aria-label="插入表格" title="插入表格" @mousedown.prevent @click="insertCommand('table')"><Table2 :size="18" /></button>
        <button type="button" aria-label="插入公式" title="公式" @mousedown.prevent @click="showMath()"><Sigma :size="19" /></button>
        <button type="button" aria-label="插入提示块" title="提示块" @mousedown.prevent @click="insertCommand('callout')"><Lightbulb :size="18" /></button>
        <button type="button" class="toolbar-evidence" @mousedown.prevent @click="attachEvidence"><BookOpen :size="16" /><span>挂证据<span v-if="evidenceCount"> · {{ evidenceCount }}</span></span></button>
      </div>
      <div class="writer-mode">
        <span class="writer-mode-dot"></span>即时渲染
        <span class="writer-key">Markdown</span>
      </div>
      <div class="writer-actions">
        <button
          type="button"
          class="writer-tool"
          aria-label="插入内容"
          title="插入内容"
          @mousedown.prevent
          @click="openInsert"
        >
          <Plus :size="17" /><span>插入</span>
        </button>
        <button
          type="button"
          class="writer-icon"
          aria-label="撤销"
          title="撤销 Ctrl / ⌘ Z"
          :disabled="!editor.can().undo()"
          @mousedown.prevent
          @click="editor.chain().focus().undo().run()"
        >
          <Undo2 :size="17" />
        </button>
        <button
          type="button"
          class="writer-icon"
          aria-label="重做"
          title="重做 Ctrl / ⌘ Shift Z"
          :disabled="!editor.can().redo()"
          @mousedown.prevent
          @click="editor.chain().focus().redo().run()"
        >
          <Redo2 :size="17" />
        </button>
        <span class="writer-separator"></span>
        <button
          type="button"
          class="writer-icon"
          aria-label="导出 Markdown"
          title="导出正文 Markdown（不含教学记录）"
          @click="exportMarkdown"
        >
          <Download :size="17" />
        </button>
        <button
          type="button"
          class="writer-icon"
          aria-label="Markdown 写作帮助"
          title="Markdown 写作帮助"
          @click="showHelp"
        >
          <Keyboard :size="18" />
        </button>
      </div>
    </div>
    <div v-if="!readonly && editor?.isActive('table')" class="table-context" aria-label="表格操作">
      <span>表格</span><button @mousedown.prevent @click="tableAction('row')">下方加行</button><button @mousedown.prevent @click="tableAction('column')">右侧加列</button><button @mousedown.prevent @click="tableAction('deleteRow')">删除行</button><button @mousedown.prevent @click="tableAction('deleteColumn')">删除列</button><button @mousedown.prevent @click="tableAction('delete')"><Trash2 :size="13" />删除表格</button>
    </div>
    <EditorContent :editor="editor" />
    <div v-if="!readonly" class="writer-status">
      <span
        ><kbd>/</kbd> 插入内容
        <span class="writer-status-separator">·</span>
        选中文字即可设置格式</span
      ><span
        >{{ characterCount.toLocaleString() }} 字符
        <span class="writer-status-separator">·</span> 约
        {{ readingMinutes }} 分钟阅读</span
      >
    </div>
    <Teleport to="body">
      <dialog ref="mediaDialog" class="writer-dialog media-dialog" @cancel="mediaBusy && $event.preventDefault()">
        <div class="writer-dialog-head"><div><span class="eyebrow">图文表达</span><h2>图片与图注</h2></div><button class="icon-btn" aria-label="关闭图片设置" :disabled="mediaBusy" @click="mediaDialog?.close()"><X :size="19" /></button></div>
        <div class="writer-dialog-content">
          <input ref="imageFile" type="file" accept="image/png,image/jpeg" hidden aria-label="选择图片文件" @change="selectImage" />
          <button class="image-upload-zone" type="button" :disabled="mediaBusy" @click="imageFile?.click()">
            <img v-if="media.src" :src="media.src" alt="待插入图片预览" /><ImagePlus v-else :size="30" />
            <strong>{{ mediaBusy ? '正在上传…' : media.src ? '点击替换图片' : '选择一张知识示意图' }}</strong><span>PNG / JPEG · 最大 5 MB · 也可粘贴或拖入正文</span>
          </button>
          <label>图片说明（替代文本）<input v-model="media.alt" maxlength="300" placeholder="简要描述图片内容，便于理解和无障碍阅读" /></label>
          <label>图注<input v-model="media.caption" maxlength="1000" placeholder="例如：图 1　类囊体薄膜上的电子传递与 ATP 合成" /></label>
          <label>图片来源<input v-model="media.source" maxlength="1000" placeholder="教材版本及页码、原始网址，或注明自绘" /></label>
          <p class="small-note">仅上传有权使用的图片。来源说明不等于证据已通过核验。</p><p v-if="mediaError" role="alert" class="writer-field-error">{{ mediaError }}</p>
        </div>
        <div class="writer-dialog-foot"><button class="btn secondary" :disabled="mediaBusy" @click="mediaDialog?.close()">取消</button><button class="btn primary" :disabled="mediaBusy" @click="insertImage">{{ mediaPosition !== null ? '更新图片' : '插入图片' }}</button></div>
      </dialog>
      <dialog ref="mathDialog" class="writer-dialog math-dialog">
        <div class="writer-dialog-head"><h2>插入学科公式</h2><button class="icon-btn" aria-label="关闭公式设置" @click="mathDialog?.close()"><X :size="19" /></button></div>
        <div class="writer-dialog-content"><div class="formula-preview" v-if="mathPreview" v-html="mathPreview"></div><p v-else class="formula-preview muted">输入有效公式后显示排版预览</p>
          <label>LaTeX 公式<textarea v-model="mathLatex" rows="4" maxlength="2000" placeholder="例如：ATP \rightarrow ADP + P_i" /></label>
          <div class="formula-examples"><span>快速示例</span><button @click="mathLatex = 'ATP \\rightarrow ADP + P_i'">ATP 转化</button><button @click="mathLatex = 'E = mc^2'">能量</button><button @click="mathLatex = '\\frac{a}{b}'">分式</button></div>
          <p class="small-note">使用 KaTeX 支持的 LaTeX 语法；不执行脚本或加载外部资源。</p><p v-if="mathError" role="alert" class="writer-field-error">{{ mathError }}</p>
        </div><div class="writer-dialog-foot"><button class="btn secondary" @click="mathDialog?.close()">取消</button><button class="btn primary" @click="insertMath">确认公式</button></div>
      </dialog>
      <div
        v-if="slash && !readonly"
        class="writer-slash"
        :style="slashPosition"
        role="dialog"
        aria-label="插入内容菜单"
        @mousedown.prevent
      >
        <div class="writer-menu-heading">插入内容 <kbd>Esc 关闭</kbd></div>
        <div class="writer-menu-list" role="listbox" aria-label="内容块类型">
          <button
            v-for="(item, i) in filteredCommands"
            :key="item.id"
            class="slash-item"
            :class="{ selected: i === slashIndex }"
            role="option"
            :aria-selected="i === slashIndex"
            @mouseenter="slashIndex = i"
            @click="insertCommand(item.id)"
          >
            <span class="slash-icon"
              ><component :is="item.icon" :size="19" /></span
            ><span class="slash-copy"
              ><strong>{{ item.label }}</strong
              ><span>{{ item.detail }}</span></span
            ><kbd>{{ item.hint }}</kbd>
          </button>
          <p v-if="!filteredCommands.length" class="writer-no-results">
            没有匹配的内容块。按 Esc 继续输入。
          </p>
        </div>
        <div class="writer-menu-foot">↑ ↓ 选择 <span>Enter 插入</span></div>
      </div>
      <div
        v-if="bubble && editor && !readonly"
        class="writer-bubble"
        :style="bubblePosition"
        role="toolbar"
        aria-label="选中文字格式"
        @mousedown.prevent
      >
        <button
          aria-label="加粗"
          title="加粗"
          :class="{ active: editor.isActive('bold') }"
          @click="editor.chain().focus().toggleBold().run()"
        >
          <Bold :size="17" />
        </button>
        <button
          aria-label="斜体"
          title="斜体"
          :class="{ active: editor.isActive('italic') }"
          @click="editor.chain().focus().toggleItalic().run()"
        >
          <Italic :size="17" />
        </button>
        <button
          aria-label="删除线"
          title="删除线"
          :class="{ active: editor.isActive('strike') }"
          @click="editor.chain().focus().toggleStrike().run()"
        >
          <Strikethrough :size="17" />
        </button>
        <button
          aria-label="行内代码"
          title="行内代码"
          :class="{ active: editor.isActive('code') }"
          @click="editor.chain().focus().toggleCode().run()"
        >
          <Code2 :size="17" />
        </button>
        <span class="writer-separator"></span>
        <button
          aria-label="设置链接"
          title="链接 Ctrl / ⌘ K"
          :class="{ active: editor.isActive('link') }"
          @click="showLink"
        >
          <Link2 :size="17" />
        </button>
        <button
          class="bubble-evidence"
          aria-label="为选中段落挂载证据"
          title="为选中段落挂载证据"
          :disabled="!activeBlockId()"
          @click="attachEvidence"
        >
          <BookOpen :size="16" />证据
        </button>
      </div>
      <dialog
        ref="linkDialog"
        class="writer-dialog"
        aria-labelledby="writer-link-title"
        @close="editor?.commands.focus()"
      >
        <form @submit.prevent="setLink">
          <div class="writer-dialog-head">
            <h2 id="writer-link-title">设置链接</h2>
            <button
              type="button"
              class="writer-icon"
              aria-label="关闭链接设置"
              @click="linkDialog?.close()"
            >
              <X :size="18" />
            </button>
          </div>
          <div class="writer-dialog-content">
            <label for="writer-link-url">链接地址</label
            ><input
              id="writer-link-url"
              v-model="linkValue"
              type="url"
              placeholder="https://…"
              autocomplete="off"
              autofocus
              required
            />
            <p class="writer-link-note">
              链接用于阅读跳转，不代表证据已经核验。
            </p>
            <p v-if="linkError" role="alert" class="writer-link-error">
              {{ linkError }}
            </p>
          </div>
          <div class="writer-dialog-foot">
            <button type="button" class="btn" @click="removeLink">
              <Unlink :size="16" />移除链接</button
            ><button class="btn primary" type="submit">
              <Check :size="16" />应用链接
            </button>
          </div>
        </form>
      </dialog>
      <dialog
        ref="helpDialog"
        class="writer-dialog writer-help"
        aria-labelledby="writer-help-title"
      >
        <div class="writer-dialog-head">
          <div>
            <span class="writer-help-eyebrow">专注内容，轻松排版</span>
            <h2 id="writer-help-title">像 Markdown 一样写作</h2>
          </div>
          <button
            type="button"
            class="writer-icon"
            aria-label="关闭写作帮助"
            @click="helpDialog?.close()"
          >
            <X :size="18" />
          </button>
        </div>
        <div class="writer-dialog-content">
          <p class="writer-help-intro">
            在段落开头输入以下符号并按空格，即时转换为排版，无需切换预览。
          </p>
          <div class="writer-shortcuts">
            <div>
              <span>一级 / 二级 / 三级标题</span><code># / ## / ###</code>
            </div>
            <div><span>无序列表 / 有序列表</span><code>- / 1.</code></div>
            <div><span>引用 / 代码块</span><code>&gt; / ```</code></div>
            <div><span>分隔线（输入后按空格）</span><code>---</code></div>
            <div>
              <span>加粗 / 斜体 / 行内代码</span
              ><code>**文本** / *文本* / `代码`</code>
            </div>
            <div>
              <span>打开内容菜单 / 设置链接</span><code>/ / Ctrl 或 ⌘ K</code>
            </div>
          </div>
          <p class="writer-help-note">
            这是结构化富文本，不是 Markdown
            源码模式。支持上述快捷输入；暂不支持整篇 Markdown
            导入。支持图片、图注、表格、公式和提示块。导出的 .md
            仅含标题与正文；图片为需登录的站内链接，不包含图片文件、证据附件、批注、评分或版本记录。
          </p>
        </div>
        <div class="writer-dialog-foot">
          <button
            type="button"
            class="btn primary"
            @click="helpDialog?.close()"
          >
            开始写作
          </button>
        </div>
      </dialog>
    </Teleport>
  </div>
</template>
