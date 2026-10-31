<script setup lang="ts">
// 课程作业 / 我的作业：从 App.vue 拆出。作业数据和筛选状态仍在 App（离开再回来要保留筛选），
// 这里只负责展示和把操作交回 App。
import { computed } from "vue";
import { Users, Plus, PenLine, FileText, Clock, Sparkles, SlidersHorizontal, ArrowUpRight, ClipboardList } from "lucide-vue-next";
import { assignmentState, dueLabel } from "./deadlines";
import { fmt } from "./format";

const props = defineProps<{
  assignments: any[];
  isStaff: boolean;
  isTeacher: boolean;
  canCreateCourse: boolean;
  busy: boolean;
  deadlineNow: number;
}>();
const assignmentFilter = defineModel<string>("filter", { required: true });
const emit = defineEmits<{
  go: [target: string];
  "show-modal": [kind: string];
  "new-free-post": [];
  "toggle-ai": [assignment: any];
  manage: [assignment: any];
  "open-assignment": [assignment: any];
}>();

const filteredAssignments = computed(() => props.assignments.filter(a => {
  if(assignmentFilter.value==='dueSoon')return assignmentState(a,props.deadlineNow)==='DUE_SOON';
  if(assignmentFilter.value==='closed')return assignmentState(a,props.deadlineNow)==='CLOSED';
  return assignmentFilter.value==='all'||(assignmentFilter.value==='submitted'?!!a.version_no:assignmentFilter.value==='started'?!!a.entry_id:!a.entry_id);
}));
</script>

<template>
<section class="page assignments-page">
  <div class="page-heading">
    <div>
      <span class="eyebrow">课程与学习任务</span>
      <h1>{{ isStaff ? "课程作业" : "我的作业" }}</h1>
      <p>从任务要求出发，明确这次创作需要完成什么。</p>
    </div>
    <div v-if="isTeacher" class="button-row">
      <button class="btn secondary" @click="emit('go', 'members')">
        <Users :size="17" />成员管理</button
      ><button class="btn primary" @click="emit('show-modal', 'assignment')">
        <Plus :size="17" />发布作业
      </button>
    </div>
    <div v-else-if="!isStaff" class="button-row">
      <button class="btn secondary" :disabled="busy" @click="emit('new-free-post')">
        <PenLine :size="16" />写知识贴
      </button>
    </div>
  </div>
  <div class="list-tabs" role="group" aria-label="作业筛选">
    <button
      v-for="[key, label] in [
        ['all', '全部作业'],
        ['started', '已开始'],
        ['submitted', '已提交'],
        ['unstarted', '未开始'],
        ['dueSoon', '即将截止'],
        ['closed', '已截止'],
      ]"
      :key="key"
      :class="{ active: assignmentFilter === key }"
      @click="assignmentFilter = key"
    >
      {{ label
      }}<span v-if="key === 'all'">{{ assignments.length }}</span>
    </button>
  </div>
  <div class="assignment-grid">
    <article
      v-for="a in filteredAssignments"
      :key="a.id"
      class="assignment-card"
      :data-assignment-id="a.id"
    >
      <div class="card-top">
        <span class="file-icon"><FileText :size="22" /></span
        ><span class="badge blue">{{ a.free_form ? '自由创作' : a.kind === 'STEP' ? '步骤' : '知识帖' }}</span>
        <span v-if="!isStaff && a.entry_state === 'PASSED'" class="badge green">已通过定稿</span>
        <span v-else-if="!isStaff && a.entry_state === 'REVISION'" class="badge orange">已打回 · 待修订</span>
      </div>
      <h2>{{ a.title }}</h2>
      <p>{{ a.requirements }}</p>
      <div class="meta-line">
        <template v-if="a.free_form"
          ><PenLine :size="16" />自由创作 · 不受截止时间限制</template
        >
        <template v-else
          ><Clock :size="16" />{{ fmt(a.due_at) }} 截止
          <span class="deadline-badge" :class="assignmentState(a,deadlineNow).toLowerCase()">{{ a.manually_closed ? '已结束' : dueLabel(assignmentState(a,deadlineNow)) }}</span></template
        >
      </div>
      <div class="assignment-ai-policy">
        <div><span><Sparkles :size="14" />AI 修改建议</span><small>{{ a.ai_feedback_enabled ? '已开放' : '未开放' }}</small></div>
        <button v-if="isTeacher" class="policy-switch" role="switch" :aria-checked="!!a.ai_feedback_enabled" :aria-label="`AI 修改建议：${a.title}`" :disabled="busy" @click="emit('toggle-ai', a)"><span></span></button>
        <span v-else class="small-label">{{ a.ai_feedback_enabled ? '已开启' : '未开启' }}</span>
      </div>
      <div class="assignment-foot"><button v-if="isTeacher" class="btn secondary manage-assignment-btn" @click="emit('manage', a)"><SlidersHorizontal :size="14"/>管理作业</button>
        <button
          class="text-btn"
          :disabled="busy || (!isStaff && !a.entry_id && assignmentState(a,deadlineNow)==='CLOSED')"
          @click="isStaff ? emit('go', 'review') : emit('open-assignment', a)"
        >
          {{
            isStaff ? "查看提交" : a.entry_state === 'PASSED' ? '查看定稿' : assignmentState(a,deadlineNow)==='CLOSED' ? (a.entry_id ? '查看与整理' : '已截止') : a.entry_id ? "继续编辑" : "开始作答"
          }}<ArrowUpRight :size="16" />
        </button>
      </div>
    </article>
    <div v-if="!filteredAssignments.length" class="empty card">
      <ClipboardList :size="32" />
      <h2>还没有发布作业</h2>
      <p>教师发布任务后，将显示在这里。</p>
    </div>
  </div>
  <button
    v-if="canCreateCourse"
    class="text-btn more-space"
    @click="emit('show-modal', 'course')"
  >
    创建另一门课程<Plus :size="15" />
  </button>
</section>
</template>
