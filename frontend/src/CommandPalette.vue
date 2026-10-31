<script setup lang="ts">
// 全局搜索（⌘K）：从 App.vue 拆出。打开/关闭和快捷键仍由 App 管理；每次打开都会重新挂载，所以输入框从空开始。
import { ref, computed } from "vue";
import { Search, GraduationCap, LayoutDashboard, Library, FileText, CornerDownLeft } from "lucide-vue-next";
const props = defineProps<{ nav: any[]; courses: any[]; assignments: any[]; publications: any[] }>();
const emit = defineEmits<{ close: []; activate: [item: any] }>();
const commandQuery = ref("");
const commandItems = computed(() => {
  const items: any[] = [
    ...props.nav.map((n: any) => ({
      title: n.id === "home" ? "首页" : n.name,
      sub: "页面",
      kind: "page",
      id: n.id,
    })),
    ...props.courses.map((c: any) => ({
      title: c.title,
      sub: "课程",
      kind: "course",
      id: c.id,
    })),
    ...props.assignments.map((a: any) => ({
      title: a.title,
      sub: "课程作业",
      kind: "assignment",
      data: a,
    })),
    ...props.publications.map((p: any) => ({
      title: p.title,
      sub: "知识条目 · " + p.author_name,
      kind: "version",
      id: p.submission_id,
    })),
  ];
  return items
    .filter(
      (i) =>
        !commandQuery.value ||
        (i.title + i.sub)
          .toLowerCase()
          .includes(commandQuery.value.toLowerCase()),
    )
    .slice(0, 16);
});
</script>

<template>
<div class="modal-scrim command-scrim" @click.self="emit('close')">
  <section
    class="command-dialog"
    role="dialog"
    aria-modal="true"
    aria-label="搜索页面和课程内容"
  >
    <div class="command-input">
      <Search :size="20" /><input
        v-model="commandQuery"
        placeholder="搜索页面、课程、作业或知识条目"
        aria-label="全局搜索"
        @keydown.enter.prevent="
          commandItems[0] && emit('activate', commandItems[0])
        "
      /><button class="keycap" @click="emit('close')">Esc</button>
    </div>
    <div class="command-results">
      <button
        v-for="(item, i) in commandItems"
        :key="i"
        @click="emit('activate', item)"
      >
        <component
          :is="
            item.kind === 'course'
              ? GraduationCap
              : item.kind === 'page'
                ? LayoutDashboard
                : item.kind === 'version'
                  ? Library
                  : FileText
          "
          :size="18"
        /><span
          >{{ item.title }}<small>{{ item.sub }}</small></span
        ><CornerDownLeft :size="15" />
      </button>
      <p v-if="!commandItems.length" class="empty-text">
        没有找到相关内容，换个关键词试试。
      </p>
    </div>
    <footer>
      <span>只搜索你有权访问的课程内容</span
      ><span>Enter 打开 · Esc 关闭</span>
    </footer>
  </section>
</div>
</template>
