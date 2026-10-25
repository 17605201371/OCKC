<script setup lang="ts">
import { assignmentState, dueLabel } from "./deadlines";
import { computed } from "vue";
import {
  ArrowUpRight,
  Check,
  ChevronRight,
  ClipboardList,
  FileText,
  GraduationCap,
  MessageSquare,
  PenLine,
  Plus,
  ShieldCheck,
  SlidersHorizontal,
  Clock,
  Library,
} from "lucide-vue-next";
const props = defineProps<{
  me: any;
  course: any;
  courses: any[];
  assignments: any[];
  now?: number;
  queue: any[];
  queueSummary?:any;
  publications: any[];
  evidences: any[];
  staff: boolean;
  teacher: boolean;
  homeEntry: any;
  homeSubmission: any;
}>();
const emit = defineEmits([
  "navigate",
  "open-assignment",
  "open-version",
  "publish-assignment",
  "select-course",
]);
const pendingCount=computed(()=>props.queueSummary?.pending_count??pending.value.length);
const submissionCount=computed(()=>props.queueSummary?.submission_count??props.queue.length);
const pending = computed(() => props.queue.filter((x) => x.q_final == null));
const evidencePending = computed(() =>
  props.evidences.filter((x) => ["PENDING", "DISPUTED"].includes(x.state)),
);
const featured = computed(() =>
  props.staff ? pending.value[0] || props.queue[0] : props.assignments[0],
);
const quality = computed(() =>
  props.staff
    ? featured.value?.q_final
    : props.homeSubmission?.evaluation?.q_final,
);
// 步骤型作业按步骤分换算百分制，没有四维；标签跟着评分口径走，别再说「四维加权」。
const gradeBasis = computed(() =>
  (props.homeSubmission?.evaluation?.stepScores?.length || 0) > 0 ? "步骤分换算" : "四维加权",
);
const feedback = computed(
  () => props.homeSubmission?.feedback?.filter((x: any) => !x.resolved) || [],
);
const todoRows = computed(() => {
  const rows: any[] = [];
  if (props.staff) {
    for (const q of pending.value.slice(0, 3))
      rows.push({
        title: q.title,
        sub: `${q.author_name} · v${q.version_no} · 等待教师评价`,
        label: "开始批改",
        kind: "review",
        data: q,
        icon: PenLine,
      });
  } else {
    for (const f of feedback.value.slice(0, 3))
      rows.push({
        title: f.body,
        sub: `${f.indicator_code} · ${f.teacher_name || "教师"}反馈`,
        label: "查看批注",
        kind: "edit",
        data: props.assignments[0],
        icon: MessageSquare,
      });
    if (
      !rows.length &&
      props.homeSubmission &&
      !props.homeSubmission.evaluation
    )
      rows.push({
        title: `v${props.homeSubmission.version_no} 已提交，等待教师评审`,
        sub: "下一步：收到反馈后继续修订",
        label: "查看提交",
        kind: "version",
        data: props.homeSubmission,
        icon: Clock,
      });
  }
  if (evidencePending.value.length)
    rows.push({
      title: `${evidencePending.value.length} 条证据等待核验或裁定`,
      sub: "对照来源、引文与论断之间的支持关系",
      label: "查看证据",
      kind: "evidence",
      icon: ShieldCheck,
    });
  return rows;
});
const phases = computed(() => {
  if (props.staff)
    return [
      { name: "学生提交", done: submissionCount.value > 0 },
      {
        name: "教师评审",
        current: pendingCount.value > 0,
        done: submissionCount.value > 0 && !pendingCount.value,
      },
      { name: "反馈修订", done: false },
      { name: "课程共享", done: props.publications.length > 0 },
    ];
  const v = props.homeSubmission,
    e = props.homeEntry;
  return [
    { name: "初稿创作", done: !!e },
    { name: "已提交", done: !!v },
    { name: "教师反馈", done: !!v?.evaluation, current: !!v && !v.evaluation },
    {
      name: "修订与核验",
      done: !!v?.evaluation && v.version_no > 1 && !feedback.value.length,
      current: !!feedback.value.length,
    },
  ];
});
function date(d: string) {
  return d
    ? new Date(d).toLocaleString("zh-CN", {
        month: "numeric",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      })
    : "—";
}
function dimensionValue(key: string): number | null {
  if (props.staff) return null;
  const value = props.homeSubmission?.evaluation?.dimensions?.[key];
  if (value == null || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) && n >= 0 && n <= 100 ? n : null;
}
function score(n: any) {
  return n == null ? "—" : Number(n).toFixed(1);
}
function openFeatured() {
  if (props.staff) {
    if (featured.value) emit("open-version", featured.value.id, true);
    else emit("navigate", "review");
  } else if (featured.value) emit("open-assignment", featured.value);
}
function action(row: any) {
  if (row.kind === "review") emit("open-version", row.data.id, true);
  else if (row.kind === "edit") emit("open-assignment", row.data);
  else if (row.kind === "version") emit("open-version", row.data.id, false);
  else emit("navigate", "evidence");
}
const today = new Date().toLocaleDateString("zh-CN", {
  month: "long",
  day: "numeric",
  weekday: "long",
});
</script>
<template>
  <section class="page home-page">
    <div class="page-heading home-greeting">
      <div>
        <span class="page-kicker">{{
          staff ? "你的教学工作台" : "你的学习工作台"
        }}</span>
        <h1>你好，{{ me.display_name }}</h1>
        <p>
          {{ today }} ·
          {{
            staff
              ? `${pendingCount} 份提交等待评审`
              : `${todoRows.length} 项学习进度值得关注`
          }}
        </p>
      </div>
      <button
        class="btn secondary"
        @click="
          teacher ? emit('publish-assignment') : emit('navigate', 'assignments')
        "
      >
        <component :is="teacher ? Plus : ClipboardList" :size="16" />{{
          teacher ? "发布作业" : "全部作业"
        }}
      </button>
    </div>
    <section v-if="featured" class="workspace-hero">
      <div class="workspace-hero-main">
        <div class="hero-context">
          <span class="course-dot"></span><span>{{ course.title }}</span
          ><span>·</span><span>{{ staff ? "当前待办" : "知识帖任务" }}</span
          ><span v-if="!staff" class="hero-due"
            >· {{ date(featured.due_at) }} 截止 <span class="deadline-badge" :class="assignmentState(featured,now).toLowerCase()">{{ featured.manually_closed?'已结束':dueLabel(assignmentState(featured,now)) }}</span></span
          >
        </div>
        <h2>
          {{ staff ? featured.title : homeEntry?.title || featured.title }}
        </h2>
        <div class="hero-meta">
          <span v-if="staff"
            >{{ featured.author_name }} · v{{ featured.version_no }}</span
          ><span v-else
            >{{
              homeSubmission
                ? `最近提交 v${homeSubmission.version_no}`
                : homeEntry
                  ? "草稿已创建"
                  : "尚未开始"
            }}<span v-if="homeEntry">
              · 草稿 r{{ homeEntry.revision }}</span
            ></span
          ><span>四维评价 · 版本可追溯</span>
        </div>
        <div class="next-action">
          <span>下一步</span>
          <h3>
            {{
              staff
                ? "先理解这一次变化，再给出关键反馈"
                : feedback.length
                  ? "根据教师批注，完善这一版解释"
                  : homeSubmission && !homeSubmission.evaluation
                    ? "等待教师反馈，或查看你的提交记录"
                    : "围绕任务要求，写下有依据的解释"
            }}
          </h3>
          <p>
            {{
              staff
                ? "评审基于冻结版本，发布后学生即可查看。"
                : feedback[0]?.body || featured.requirements
            }}
          </p>
        </div>
        <div class="hero-actions">
          <button class="btn primary" :disabled="!staff && !featured.entry_id && assignmentState(featured,now)==='CLOSED'" @click="openFeatured">
            <component :is="staff ? PenLine : PenLine" :size="16" />{{
              staff ? "开始批改" : assignmentState(featured,now)==='CLOSED' && !featured.entry_id ? "作业已截止" : homeEntry ? "继续创作" : "开始创作"
            }}</button
          ><button
            class="btn secondary"
            @click="emit('navigate', staff ? 'review' : 'assignments')"
          >
            {{ staff ? "查看批改队列" : "任务要求" }}</button
          ><button class="text-btn" @click="emit('navigate', 'community')">
            查看知识条目<ArrowUpRight :size="15" />
          </button>
        </div>
      </div>
      <aside class="hero-summary">
        <span class="muted">任务进度</span>
        <ol class="phase-grid">
          <li
            v-for="(phase, i) in phases"
            :key="phase.name"
            :class="{ done: phase.done, current: phase.current }"
          >
            <span class="phase-index"
              ><Check v-if="phase.done" :size="13" /><template v-else>{{
                i + 1
              }}</template></span
            >{{ phase.name }}
          </li>
        </ol>
        <div class="hero-quality">
          <span class="muted"
            >{{ staff ? "此提交质量分" : "最近提交质量分" }} · {{ gradeBasis }}</span
          >
          <div>
            <strong>{{ score(quality) }}</strong
            ><span>/100</span>
          </div>
          <button
            class="text-btn"
            @click="
              staff
                ? openFeatured()
                : homeSubmission
                  ? emit('open-version', homeSubmission.id, false)
                  : emit('navigate', 'rubric')
            "
          >
            {{ quality == null ? "查看评价规则" : "查看评分明细"
            }}<ChevronRight :size="14" />
          </button>
        </div>
      </aside>
    </section>
    <section v-else class="card empty hero-empty">
      <ClipboardList :size="30" />
      <h2>{{ staff ? "从第一个课程任务开始" : "等待老师发布新任务" }}</h2>
      <p>任务、提交与反馈会在这里汇聚。</p>
      <button
        v-if="teacher"
        class="btn primary"
        @click="emit('publish-assignment')"
      >
        <Plus :size="16" />发布作业
      </button>
    </section>
    <div class="dashboard-columns">
      <div class="dashboard-main stack">
        <section class="dashboard-card">
          <header class="card-header">
            <h2>需要你处理</h2>
            <span class="muted">{{ todoRows.length }} 项</span>
          </header>
          <div v-if="todoRows.length" class="todo-list">
            <div v-for="(row, i) in todoRows" :key="i" class="todo-row">
              <span class="todo-icon"
                ><component :is="row.icon" :size="17"
              /></span>
              <div>
                <strong>{{ row.title }}</strong>
                <p>{{ row.sub }}</p>
              </div>
              <button class="btn secondary compact" @click="action(row)">
                {{ row.label }}
              </button>
            </div>
          </div>
          <div v-else class="quiet-empty">
            <Check :size="20" />
            <div>
              <strong>当前没有待处理事项</strong>
              <p>继续创作，也可以看看课程里的好解释。</p>
            </div>
          </div>
        </section>
        <section class="dashboard-card">
          <header class="card-header">
            <h2>{{ staff ? "评审队列摘录" : "近期作业" }}</h2>
            <button
              class="text-btn"
              @click="emit('navigate', staff ? 'review' : 'assignments')"
            >
              查看全部<ChevronRight :size="14" />
            </button>
          </header>
          <p v-if="staff" class="muted" style="padding:0 20px 12px">待评优先，仅显示部分记录；完整筛选和历史版本请进入批改中心。</p>
          <template v-if="staff"
            ><button
              v-for="q in queue.slice(0, 4)"
              :key="q.id"
              class="work-row"
              @click="emit('open-version', q.id, true)"
            >
              <span class="work-thumb"><FileText :size="23" /></span>
              <div>
                <strong>{{ q.title }}</strong>
                <p>
                  {{ q.author_name }} · v{{ q.version_no }} ·
                  {{ date(q.created_at) }}
                </p>
              </div>
              <span
                class="badge"
                :class="q.q_final == null ? 'orange' : 'green'"
                >{{
                  q.q_final == null ? "待评审" : `${score(q.q_final)} 分`
                }}</span
              ><ChevronRight :size="16" />
            </button>
            <p v-if="!queue.length" class="empty-text">
              暂无学生提交。
            </p></template
          ><template v-else
            ><button
              v-for="a in assignments.slice(0, 4)"
              :key="a.id"
              class="work-row"
              :disabled="!a.entry_id && assignmentState(a,now)==='CLOSED'"
              @click="emit('open-assignment', a)"
            >
              <span class="work-thumb"><FileText :size="23" /></span>
              <div>
                <strong>{{ a.title }}</strong>
                <p>{{ course.title }} · {{ date(a.due_at) }} 截止 <span class="deadline-badge" :class="assignmentState(a,now).toLowerCase()">{{ a.manually_closed?'已结束':dueLabel(assignmentState(a,now)) }}</span></p>
              </div>
              <span
                class="badge"
                :class="a.version_no ? 'green' : a.entry_id ? 'blue' : ''"
                >{{
                  a.version_no
                    ? `已提交 v${a.version_no}`
                    : a.entry_id
                      ? "创作中"
                      : "未开始"
                }}</span
              ><ChevronRight :size="16" />
            </button>
            <p v-if="!assignments.length" class="empty-text">
              暂无课程作业。
            </p></template
          >
        </section>
      </div>
      <aside class="dashboard-aside stack">
        <section class="dashboard-card">
          <header class="card-header">
            <h2>{{ staff ? "我教的课程" : "我的课程" }}</h2>
            <span class="muted">{{ courses.length }} 门</span>
          </header>
          <div class="course-links">
            <button
              v-for="(c, i) in courses"
              :key="c.id"
              @click="emit('select-course', c.id)"
            >
              <span class="course-dot" :class="'course-tone-' + (i % 3)"></span
              ><span>{{ c.title }}</span
              ><ChevronRight :size="15" />
            </button>
          </div>
          <div class="overview-counts">
            <div>
              <strong>{{
                staff
                  ? pendingCount
                  : assignments.filter((a) => a.entry_id).length
              }}</strong
              ><span>{{ staff ? "待评审提交" : "我的作品" }}</span>
            </div>
            <div>
              <strong>{{ publications.length }}</strong
              ><span>社区知识条目</span>
            </div>
          </div>
        </section>
        <section class="dashboard-card">
          <header class="card-header">
            <h2>四维质量</h2>
            <button
              class="icon-btn"
              aria-label="了解课程评价规则"
              @click="emit('navigate', 'rubric')"
            >
              <SlidersHorizontal :size="17" />
            </button>
          </header>
          <div class="home-dimensions" aria-label="四维质量分数">
            <div v-for="[key, label] in [['S', '科学性'], ['A', '准确性'], ['C', '完整性'], ['L', '逻辑性']]" :key="key" class="home-dimension-row">
              <span class="home-dimension-label"><i>{{key}}</i>{{label}}</span>
              <div class="home-dimension-track" :class="{'is-unscored':dimensionValue(key)===null}" :role="dimensionValue(key)!==null?'progressbar':'img'" :aria-label="label+(dimensionValue(key)===null?'暂无正式评分':'质量分')" :aria-valuemin="dimensionValue(key)!==null?0:undefined" :aria-valuemax="dimensionValue(key)!==null?100:undefined" :aria-valuenow="dimensionValue(key)??undefined" :aria-valuetext="dimensionValue(key)===null?undefined:score(dimensionValue(key))+' / 100'">
                <span v-if="dimensionValue(key)!==null" class="home-dimension-fill" :style="{width:dimensionValue(key)+'%'}"></span>
              </div>
              <strong>{{score(dimensionValue(key))}}</strong>
            </div>
          </div>
          <p class="home-dimension-note">{{staff?'11 项指标，四个维度按量规权重加权；此处不显示班级均分。':homeSubmission?.evaluation?'当前作品正式评价 · 各维度按量规权重加权':'尚无正式评分，未评分不计零分。'}}</p>
          <button class="card-link" @click="emit('navigate', 'rubric')">
            查看课程量规<ArrowUpRight :size="15" />
          </button>
        </section>
      </aside>
    </div>
  </section>
</template>
