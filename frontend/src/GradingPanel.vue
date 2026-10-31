<script setup lang="ts">
// 批改页右侧的评分面板。评分表单的数据（逐项分、总评、步骤分、评分草稿）都由 App 持有：
// 草稿自动保存、离开前保存和发布正式评价都要读它们。这里直接改表单行里的分数，
// 与原来模板里的 v-model 一样；总评、封顶理由和当前步骤通过 v-model 回写。
import { Check, CheckCircle2, ChevronRight, MessageSquare } from "lucide-vue-next";
import { fmt, number } from "./format";
import { criterionDescription } from "./rubricText";
import { dimLabel, actionLabel } from "./feedbackLabels";
import { STEP_TAGS, STEP_DEFAULT_MAX, scrollToStep } from "./stepGrading";
import { computed } from "vue";

const props = defineProps<{
  version: any;
  grading: any[];
  stepScores: Array<{ index: number; title: string; score: any; max: number; tags: string[]; comment: string }>;
  gradingSteps: Array<{ text: string; level: number }>;
  dims: Array<{ d: string; name: string; weight: number }>;
  previewScore: { dims: Record<string, number>; raw: number; total: number } | null;
  review: any;
  isTeacher: boolean;
  isStepAssignment: boolean;
  busy: boolean;
  reviewPublishing: boolean;
  highlightFeedbackId: number;
  priorOpen: any[];
  priorResolved: any[];
  priorBusy: number;
}>();
const panelVisible = defineModel<boolean>("panelVisible", { required: true });
const summary = defineModel<string>("summary", { required: true });
const gateReason = defineModel<string>("gateReason", { required: true });
// 当前步骤由 App 持有：切换到下一份提交时保留原来的位置，与拆分前一致。
const activeStep = defineModel<number>("activeStep", { required: true });
const emit = defineEmits<{
  "focus-anchor": [id: string, original: string];
  modal: [kind: string, data?: Record<string, any>];
  "resolve-prior": [feedback: any, reopen: boolean];
  "reload-review": [];
  "restore-review": [data: any];
  notify: [text: string];
}>();
function focusAnchor(id: string, original = "") { emit("focus-anchor", id, original); }
function showModal(kind: string, data?: Record<string, any>) { emit("modal", kind, data); }

function gradingCriterion(code:string){return props.version?.rubricSnapshot?.criteria?.find((i:any)=>i.code===code);}

const stepTotal = computed(() => props.stepScores.reduce((sum, s) => sum + (Number(s.score) || 0), 0));
const stepMax = computed(() => props.stepScores.reduce((sum, s) => sum + (Number(s.max) || STEP_DEFAULT_MAX), 0));
const scoredStepCount = computed(() => props.stepScores.filter((s) => s.score !== '' && s.score != null).length);
// 右侧只列步骤与分数；标签和点评写在下方卡片里，点哪一步就编辑哪一步。
function selectStep(index: number) {
  activeStep.value = index;
  scrollToStep(index);
}
function toggleStepTag(row: any, tag: string) {
  if (!row) return;
  const at = row.tags.indexOf(tag);
  if (at >= 0) row.tags.splice(at, 1);
  else if (row.tags.length < 4) row.tags.push(tag);
  else emit('notify', '每步最多标注 4 个问题类型');
}

// 打分：先按维度评级（4 档），再按需逐项微调
const LEVELS = [
  { n: "优", v: 95 },
  { n: "良", v: 82 },
  { n: "合格", v: 67 },
  { n: "待改进", v: 50 },
];
const QUICK_NOTES = ["证据链完整", "因果衔接还需补一句", "注意限定语的范围", "图注要写清机制"];
function addNote(t: string) {
  const base = summary.value.trim().replace(/[；;。]\s*$/, "");
  summary.value = base ? `${base}；${t}` : t;
}
function dimLevel(code: string) {
  const items = props.grading.filter(
    (i) =>
      i.code.startsWith(code) &&
      i.status === "SCORED" &&
      i.score !== null &&
      i.score !== "" &&
      Number.isFinite(Number(i.score)),
  );
  if (!items.length) return null;
  const avg = items.reduce((n, i) => n + Number(i.score), 0) / items.length;
  return avg >= 90 ? 95 : avg >= 75 ? 82 : avg >= 60 ? 67 : 50;
}
function setDimLevel(code: string, v: number) {
  for (const i of props.grading) {
    if (!i.code.startsWith(code)) continue;
    if (i.status === "NA") continue; // 已说明的不适用项保持不变
    i.status = "SCORED";
    i.score = v;
  }
}
function dimDone(code: string) {
  const items = props.grading.filter((i) => i.code.startsWith(code));
  return (
    items.length > 0 &&
    items.every((i) =>
      i.status === "SCORED"
        ? i.score !== null && i.score !== "" && Number.isFinite(Number(i.score))
        : i.status === "NA"
          ? !!String(i.reason || "").trim()
          : false,
    )
  );
}
const dimsLeft = computed(
  () => props.dims.filter((d: any) => !dimDone(d.d)).length,
);

function priorState(f: any) {
  const changed = !!f.stale;
  if (f.student_action === "ADOPT")
    return changed ? { text: "学生已改 · 段落有变动", cls: "blue" } : { text: "学生已声明修改", cls: "blue" };
  if (f.student_action === "REPLY") return { text: "学生已回应", cls: "blue" };
  if (f.student_action === "IGNORE") return { text: "学生未采纳", cls: "grey" };
  return changed ? { text: "段落已改动", cls: "grey" } : { text: "未解决", cls: "orange" };
}
</script>

<template>
  <aside v-show="panelVisible" class="feedback-sidebar">
    <div class="panel-title">
      <h2>{{ isStepAssignment ? "步骤评分" : "四维评分" }}</h2>
      <span class="badge blue">教师确认</span>
      <button class="panel-collapse" title="收起评分面板" aria-label="收起评分面板" @click="panelVisible = false"><ChevronRight :size="17" /></button>
    </div>
    <div v-if="isTeacher" class="review-save-state" :class="{failed:review.state.error}" aria-live="polite"><span>{{ review.state.loading ? '正在加载评分草稿…' : review.state.saving ? '正在保存评分草稿…' : review.state.error ? '评分草稿未保存' : review.state.dirty ? '有未保存的评分修改' : review.state.savedAt ? '评分草稿已保存 · ' + fmt(review.state.savedAt) : '修改后自动保存评分草稿' }}</span><small>仅你可见</small></div>
    <div v-if="review.state.error && isTeacher" class="review-recovery" role="alert"><p>{{ review.state.error }}</p><button class="text-btn" @click="review.state.conflict || !review.state.loaded ? emit('reload-review') : review.save()">{{ review.state.conflict || !review.state.loaded ? '重新加载并合并' : '重试保存' }}</button></div>
    <div v-if="review.state.stale && isTeacher" class="review-recovery"><strong>正式评价已更新</strong><p>有一份基于旧评价的评分草稿。请先选择恢复并复核，或放弃旧草稿。</p><button class="text-btn" @click="emit('restore-review', review.state.stale)">恢复旧草稿并复核</button><button class="text-btn muted" @click="review.discardStale()">放弃旧草稿</button></div>
    <div v-if="review.state.backup && isTeacher" class="review-recovery"><strong>发现本机未同步的评分备份</strong><p>可恢复后继续核对；不会直接覆盖正式评价。</p><button class="text-btn" @click="emit('restore-review', review.state.backup)">恢复本机评分备份</button><button class="text-btn muted" @click="review.discardBackup()">使用服务端草稿</button></div>
    <div v-if="!isStepAssignment" class="grade-summary">
      <span>综合质量 · 四维加权</span>
      <strong
        >{{ previewScore ? number(previewScore.total) : "—"
        }}<small>/ 100</small></strong
      >
      <p>
        {{
          dimsLeft
            ? `还有 ${dimsLeft} 个维度未评`
            : "四维已全部评级"
        }}
      </p>
    </div>

    <fieldset
      class="grade-form"
      :disabled="
        !isTeacher ||
        reviewPublishing ||
        review.state.loading ||
        !review.state.loaded ||
        !!review.state.stale ||
        !!review.state.backup ||
        review.state.conflict
      "
    >
      <section v-if="isStepAssignment" class="grade-block step-grade-block">
        <div class="step-head">
          <div>
            <h4>步骤分</h4>
            <p>{{ scoredStepCount }} / {{ gradingSteps.length }} 步已评</p>
          </div>
          <span class="step-sum"
            ><strong>{{ number(stepTotal) }}</strong
            ><em>/ {{ number(stepMax) }} 分</em></span
          >
        </div>
        <p v-if="!gradingSteps.length" class="muted">
          这份提交没有标题，无法切分步骤。可让作者用二级标题标记每一步，或仅用四维评分。
        </p>
        <div
          v-for="(s, i) in gradingSteps"
          :key="i"
          class="step-row"
          :class="{ active: activeStep === i }"
          @click="selectStep(i)"
        >
          <button
            type="button"
            class="step-index"
            :aria-label="`定位到第 ${i + 1} 步`"
            @click.stop="selectStep(i)"
          >
            {{ i + 1 }}
          </button>
          <div class="step-main">
            <strong>{{ s.text }}</strong>
            <div v-if="stepScores[i]?.tags.length" class="step-tags static">
              <span v-for="t in stepScores[i].tags" :key="t" class="step-tag on">{{ t }}</span>
            </div>
          </div>
          <input
            v-if="stepScores[i]"
            v-model="stepScores[i].score"
            class="step-score"
            type="number"
            min="0"
            :max="stepScores[i].max"
            step="0.5"
            :aria-label="s.text + '得分'"
            placeholder="—"
            @click.stop
          />
          <span class="step-max">/ {{ stepScores[i]?.max ?? 10 }}</span>
        </div>

        <!-- 文字点评只保留「段落批注」一条通道：选中正文即可写，会锚在段落上。
             步骤这里只留问题类型标签，不再重复一个点评输入框。 -->
        <div v-if="stepScores[activeStep]" class="step-comment-card">
          <div class="step-comment-head">
            <strong>第 {{ activeStep + 1 }} 步 · 问题类型</strong>
            <span
              >{{ stepScores[activeStep].score === "" ? "—" : stepScores[activeStep].score }} /
              {{ stepScores[activeStep].max }} 分</span
            >
          </div>
          <div class="step-tags" role="group" aria-label="问题类型">
            <button
              v-for="t in STEP_TAGS"
              :key="t"
              type="button"
              class="step-tag"
              :class="{ on: stepScores[activeStep].tags.includes(t) }"
              :aria-pressed="stepScores[activeStep].tags.includes(t)"
              @click="toggleStepTag(stepScores[activeStep], t)"
            >
              {{ t }}
            </button>
          </div>
        </div>
      </section>

      <section v-if="!isStepAssignment" class="grade-block">
        <div class="grade-block-head">
          <h4>内容质量</h4>
        </div>
        <div v-for="d in dims" :key="d.d" class="grade-dim">
          <span class="grade-dim-name"
            >{{ d.name }}<em>{{ d.weight }}%</em></span
          >
          <div
            class="grade-levels"
            role="group"
            :aria-label="d.name + '评级'"
          >
            <button
              v-for="l in LEVELS"
              :key="l.v"
              type="button"
              :class="{ on: dimLevel(d.d) === l.v }"
              :title="l.n + ' · 对应 ' + l.v + ' 分'"
              @click="setDimLevel(d.d, l.v)"
            >
              {{ l.n }}
            </button>
          </div>
        </div>

        <details class="grade-fine">
          <summary>逐项微调 · 11 项指标</summary>
          <div
            v-for="d in dims"
            :key="d.d"
            class="grade-fine-group"
          >
            <h5>{{ d.d }} · {{ d.name }}</h5>
            <div
              v-for="i in grading.filter((x) => x.code.startsWith(d.d))"
              :key="i.code"
              class="grade-fine-row"
            >
              <label :for="'score-' + i.code"
                ><code>{{ i.code }}</code
                >{{ i.name }}</label
              >
              <input
                :id="'score-' + i.code"
                v-model="i.score"
                :disabled="i.status !== 'SCORED'"
                type="number"
                min="0"
                max="100"
                step="0.01"
                placeholder="—"
              />
              <select
                v-model="i.status"
                :aria-label="i.name + '评分状态'"
              >
                <option value="SCORED">有效分</option>
                <option value="NA">N/A</option>
                <option value="PENDING">待核验</option>
              </select>
              <input
                v-if="i.status === 'NA'"
                v-model="i.reason"
                :aria-label="i.name + '不适用理由'"
                maxlength="500"
                placeholder="不适用理由"
              />
              <details class="grade-anchor">
                <summary>锚点</summary>
                <template v-if="gradingCriterion(i.code)?.description">
                  <p v-if="criterionDescription(gradingCriterion(i.code))">{{ criterionDescription(gradingCriterion(i.code)) }}</p>
                  <p
                    v-for="[key, label] in [
                      ['excellent', '优秀'],
                      ['good', '良好'],
                      ['pass', '基本达到'],
                      ['improve', '待改进'],
                    ]"
                    :key="key"
                  >
                    <strong>{{ label }}：</strong
                    >{{ gradingCriterion(i.code)[key] }}
                  </p>
                </template>
                <p v-else class="muted">
                  该历史版本未记录单项锚点。
                </p>
              </details>
            </div>
          </div>
        </details>
      </section>

      <section class="grade-block">
        <div class="grade-block-head">
          <h4>{{ isStepAssignment ? "整体评语" : "总评" }}</h4>
          <span>学生可见</span>
        </div>
        <textarea
          v-model="summary"
          rows="5"
          maxlength="2000"
          placeholder="写给学生的总体评价与下一步建议"
          required
        />
        <div class="grade-quick">
          <button
            v-for="t in QUICK_NOTES"
            :key="t"
            type="button"
            class="grade-chip"
            @click="addNote(t)"
          >
            + {{ t }}
          </button>
        </div>
        <details v-if="!isStepAssignment" class="grade-gate" :open="!!gateReason">
          <summary>存在严重缺陷 · 综合分封顶 89</summary>
          <textarea
            v-model="gateReason"
            rows="2"
            maxlength="1000"
            placeholder="仅填写已证实的严重缺陷"
          />
        </details>
        <p class="grade-tip">
          <MessageSquare :size="14" /><span
            >在左侧正文中选中文字即可添加批注；点击高亮可反向定位。</span
          >
        </p>
      </section>

      <details v-if="version.feedback.length" class="grade-feedback">
        <summary>已有批注 {{ version.feedback.length }} 条</summary>
        <div
          v-for="f in version.feedback"
          :key="f.id"
          :id="'feedback-' + f.id"
          class="feedback-card"
          :class="{ 'is-target': highlightFeedbackId === f.id }"
          @click="focusAnchor(f.paragraph_id, f.original_text)"
        >
          <strong
            >{{ dimLabel(f.indicator_code) }} · {{ f.body }}</strong
          ><button
            class="text-btn"
            @click.stop="focusAnchor(f.paragraph_id, f.original_text)"
          >
            定位正文
          </button>
          <blockquote v-if="f.quote_text">
            {{ f.quote_text }}
          </blockquote>
          <div
            v-for="a in f.actions"
            :key="a.id"
            class="feedback-replies"
          >
            <strong>{{ a.actor_name }}</strong
            ><span>{{ actionLabel(a.action_type) }} · {{ a.body }}</span>
          </div>
          <button
            v-if="isTeacher"
            class="text-btn"
            @click="
              showModal('action', {
                id: f.id,
                action: f.resolved ? 'REOPEN' : 'RESOLVE',
              })
            "
          >
            {{ f.resolved ? "重新打开问题" : "确认问题已解决" }}
          </button>
        </div>
      </details>

      <details v-if="priorOpen.length || priorResolved.length" class="grade-feedback prior-feedback" open>
        <summary>历史批注 {{ priorOpen.length }} 条待跟进<span v-if="priorResolved.length"> · {{ priorResolved.length }} 条已解决</span></summary>
        <p class="grade-tip"><MessageSquare :size="14" /><span>来自更早的版本。</span></p>
        <div
          v-for="f in priorOpen"
          :key="'prior-' + f.id"
          class="feedback-card is-prior"
          :class="{ 'is-target': highlightFeedbackId === f.id }"
        >
          <div class="card-top">
            <span class="indicator-tag">v{{ f.source_version_no }} 提出</span>
            <span class="badge" :class="priorState(f).cls">{{ priorState(f).text }}</span>
          </div>
          <strong>{{ dimLabel(f.indicator_code) }} · {{ f.body }}</strong>
          <blockquote v-if="f.quote_text">{{ f.quote_text }}</blockquote>
          <div class="prior-actions">
            <button
              v-if="!f.stale"
              class="text-btn"
              @click.stop="focusAnchor(f.paragraph_id, f.original_text)"
            >
              定位正文
            </button>
            <button
              class="text-btn resolve"
              :disabled="priorBusy === f.id"
              @click.stop="emit('resolve-prior', f, false)"
            >
              标记已解决
            </button>
          </div>
        </div>
        <details v-if="priorResolved.length" class="prior-resolved">
          <summary>已解决 {{ priorResolved.length }} 条</summary>
          <div v-for="f in priorResolved" :key="'done-' + f.id" class="feedback-card is-prior is-done">
            <div class="card-top">
              <span class="indicator-tag">v{{ f.source_version_no }} 提出</span>
              <span class="badge green">已解决</span>
            </div>
            <strong>{{ dimLabel(f.indicator_code) }} · {{ f.body }}</strong>
            <button class="text-btn muted" :disabled="priorBusy === f.id" @click.stop="emit('resolve-prior', f, true)">
              重新打开
            </button>
          </div>
        </details>
      </details>

      <!-- 终审：决定这篇帖子到底结束没有。作业级「结束作业」是整班一起停，这里是一篇一篇定。 -->
      <section class="grade-block entry-verdict">
        <div class="grade-block-head">
          <h4>终审</h4>
          <span>{{
            version.entry_state === "PASSED"
              ? "已通过定稿"
              : version.entry_state === "REVISION"
                ? "已打回"
                : "进行中"
          }}</span>
        </div>
        <p v-if="version.entry_state === 'REVISION' && version.entry_state_reason" class="verdict-reason">
          <strong>打回原因：</strong>{{ version.entry_state_reason }}
        </p>
        <div v-if="version.entry_state !== 'PASSED'" class="verdict-actions">
          <button class="btn secondary" :disabled="busy" @click="showModal('verdict', { action: 'RETURN', body: '' })">
            打回修订
          </button>
          <button class="btn primary" :disabled="busy" @click="showModal('verdict', { action: 'PASS', body: '' })">
            <Check :size="16" />通过定稿
          </button>
        </div>
        <button
          v-else
          class="text-btn muted"
          :disabled="busy"
          @click="showModal('verdict', { action: 'REOPEN', body: '' })"
        >
          撤销定稿，允许继续修改
        </button>
        <p class="grade-tip"><CheckCircle2 :size="14" /><span>通过后学生不能再提交新版本。</span></p>
      </section>
    </fieldset>
  </aside>
</template>
