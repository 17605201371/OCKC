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
import StarterKit from "@tiptap/starter-kit";
import { Extension } from "@tiptap/core";
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
} from "lucide-vue-next";
import { toMarkdown } from "./markdown";
const props = defineProps<{
  modelValue: any;
  readonly?: boolean;
  highlightId?: string;
  documentTitle?: string;
}>();
const emit = defineEmits(["update:modelValue", "selected", "evidence"]);
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
  const p = editor.value?.state.selection.$from;
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
        types: ["paragraph", "heading", "codeBlock"],
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
              ["paragraph", "heading", "codeBlock"].includes(node.type.name)
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
    Ids,
    Highlight,
  ],
  editorProps: {
    attributes: {
      class: "prose-editor",
      "aria-label": props.readonly ? "作品正文" : "知识帖正文",
      "data-placeholder": "写下你的解释，或输入 / 插入内容…",
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
            导入、表格和图片上传。导出的 .md
            仅含标题与正文，不包含证据附件、批注、评分或版本记录。
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
