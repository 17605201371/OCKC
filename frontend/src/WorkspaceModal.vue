<script setup lang="ts">
// 工作空间里的通用表单弹窗：发布作业、建课、加成员、挂证据、批注、处理反馈、终审、核验和核验记录。
// 表单内容 form 由 App 持有并在提交时按 modal 类型调用接口；这里直接编辑 form 的字段，与原模板一致。
import { Check, FileText, ListOrdered, Sparkles, X } from "lucide-vue-next";
import { fmt } from "./format";
import { anchorOptions } from "./docOutline";
defineProps<{
  modal: string;
  form: Record<string, any>;
  busy: boolean;
  error: string;
  entry: any;
  version: any;
  allItems: Array<{ code: string; name: string }>;
  assignmentRubrics: any[];
  assignmentRubricLoading: boolean;
  assignmentRubricError: string;
}>();
const emit = defineEmits<{ close: []; submit: []; "load-rubrics": [] }>();
</script>

<template>
<div class="modal-scrim" @click.self="emit('close')">
  <section
    class="modal"
    role="dialog"
    aria-modal="true"
    :aria-label="
      (
        {
          assignment: '发布课程作业',
          course: '创建课程',
          member: '添加课程成员',
          evidence: '挂载论断证据',
          feedback: '添加段落批注',
          action: '处理教师反馈',
          verify: '核验来源',
          records: '核验历史',
        } as any
      )[modal]
    "
  >
    <header>
      <div>
        <span class="eyebrow">课程工作空间</span>
        <h2>
          {{
            (
              {
                assignment: "发布课程作业",
                course: "创建课程",
                member: "添加课程成员",
                evidence: "挂载论断证据",
                feedback: "添加段落批注",
                action: "处理教师反馈",
                verify: "核验来源",
                records: "核验历史",
              } as any
            )[modal]
          }}
        </h2>
      </div>
      <button class="icon-btn" aria-label="关闭弹窗" @click="emit('close')">
        <X :size="21" />
      </button>
    </header>
    <form v-if="modal !== 'records'" @submit.prevent="emit('submit')">
      <template v-if="modal === 'assignment' || modal === 'course'"
        ><div
          v-if="modal === 'assignment'"
          class="kind-picker"
          role="radiogroup"
          aria-label="作业类型"
        >
          <button
            type="button"
            class="kind-card"
            :class="{ on: form.kind === 'POST' }"
            role="radio"
            :aria-checked="form.kind === 'POST'"
            @click="form.kind = 'POST'"
          >
            <FileText :size="18" /><strong>知识帖</strong
            ><span>按段落自由写作，教师按四维量规批改</span>
          </button>
          <button
            type="button"
            class="kind-card"
            :class="{ on: form.kind === 'STEP' }"
            role="radio"
            :aria-checked="form.kind === 'STEP'"
            @click="form.kind = 'STEP'"
          >
            <ListOrdered :size="18" /><strong>步骤</strong
            ><span>用二级标题划分步骤，教师逐步给分并标注问题类型</span>
          </button>
        </div>
        <label
          >{{ modal === "course" ? "课程名称" : "作业名称"
          }}<input
            v-model="form.title"
            required
            :maxlength="modal === 'course' ? 120 : 160" /></label
        ><label
          >{{ modal === "course" ? "课程简介" : "成果要求与学习目标"
          }}<textarea
            v-model="form.requirements"
            rows="5"
            required
            :maxlength="modal === 'course' ? 1000 : 4000"
          /></label
        ><template v-if="modal === 'assignment'"
          ><label
            >截止时间（本地时区）<input
              v-model="form.dueAt"
              type="datetime-local"
              required
          /></label>
          <label
            >证据核验策略<select v-model="form.evidencePolicy">
              <option value="DISPUTED">仅争议阻断发分（推荐）</option>
              <option value="REQUIRED">逐条核验后才能发分</option>
              <option value="OFF">不要求核验</option>
            </select></label
          >
          <p class="form-note">
            {{
              form.evidencePolicy === "REQUIRED"
                ? "每条证据都要有人核验通过或驳回，学生才能发布正式评分。证据多时负担最重。"
                : form.evidencePolicy === "DISPUTED"
                  ? "待核验证据不阻止发分，只有同伴结论冲突产生的争议必须先裁定。"
                  : "核验记录仍会保留，但不参与发分判断。"
            }}
          </p>
          <label>作业量规版本<select v-model="form.rubricId" required :disabled="assignmentRubricLoading || !!assignmentRubricError" aria-label="作业量规版本"><option value="" disabled>请选择已启用版本</option><option v-for="r in assignmentRubrics" :key="r.id" :value="String(r.id)">{{r.title}} · {{r.code}}</option></select></label><p v-if="assignmentRubricLoading" role="status">正在读取可用量规…</p><p v-if="assignmentRubricError" role="alert">{{assignmentRubricError}} <button type="button" class="text-btn" @click="emit('load-rubrics')">重试读取量规</button></p>
          <div class="publish-ai-setting"><div><strong><Sparkles :size="15" />AI 修改建议</strong><p>{{ form.aiFeedbackEnabled ? '学生编辑页显示 AI 反馈入口' : '学生编辑页不显示 AI 反馈区块' }}</p></div><button type="button" class="policy-switch" role="switch" :aria-checked="!!form.aiFeedbackEnabled" aria-label="AI 修改建议" @click="form.aiFeedbackEnabled = !form.aiFeedbackEnabled"><span></span></button></div>
          <p class="form-note ai-setting-note">可在发布后按作业随时调整。</p>
          <p class="form-note">
            创建后锁定所选量规版本；更改课程默认不影响本作业。本版截止后不接受新提交。
          </p></template
        ></template
      ><template v-if="modal === 'member'"
        ><label
          >已有账号邮箱<input
            v-model="form.email"
            type="email"
            required
            maxlength="160" /></label
        ><label
          >课程内角色<select v-model="form.role">
            <option value="STUDENT">学生</option>
            <option value="ASSISTANT">助教（只读批改队列，不可发分）</option>
          </select></label
        >
        <p class="form-note">
          只添加已经开通的账号。本阶段暂不含注册与完整账号管理后台。
        </p></template
      ><template v-if="modal === 'evidence' || modal === 'feedback'"
        ><label
          >关联正文段落<select v-model="form.paragraphId" required>
            <option
              v-for="a in anchorOptions(
                modal === 'evidence' ? entry.content : version.content,
              )"
              :key="a.id"
              :value="a.id"
            >
              {{ a.text }}
            </option>
          </select></label
        ></template
      ><template v-if="modal === 'evidence'"
        ><label
          >来源名称 / 版本 / 页码<input
            v-model="form.sourceTitle"
            required
            maxlength="300"
            placeholder="请填写实际使用的教材、课标或文献" /></label
        ><label
          >来源链接（可选）<input
            v-model="form.sourceUrl"
            type="url"
            maxlength="2000"
            placeholder="https://…" /></label
        ><label
          >原文摘录<textarea
            v-model="form.excerpt"
            required
            maxlength="4000"
            rows="3"
          /></label
        ><label
          >它支持的具体论断<textarea
            v-model="form.claim"
            required
            maxlength="2000"
            rows="2"
          />
        </label>
        <p class="form-note">请引用可回到原文核对的材料。</p></template
      ><template v-if="modal === 'feedback'"
        ><label
          >主责二级指标<select v-model="form.indicator">
            <option v-for="i in allItems" :key="i.code" :value="i.code">
              {{ i.code }} {{ i.name }}
            </option>
          </select></label
        ><label
          >具体问题与修改建议<textarea
            v-model="form.body"
            required
            maxlength="2000"
            rows="5"
          /></label></template
      ><template v-if="modal === 'action'"
        ><p class="muted">
          {{
            (
              {
                REPLY: "回应反馈，说明你的理解或修订计划。",
                ADOPT: "声明你已按这条批注改过。",
                IGNORE: "请解释本轮不采纳的具体原因。",
                RESOLVE: "确认这条批注已解决。",
                REOPEN: "重新打开，继续跟进。",
              } as any
            )[form.action]
          }}
        </p>
        <label
          >说明<textarea
            v-model="form.body"
            :required="['REPLY', 'IGNORE'].includes(form.action)"
            rows="4"
            maxlength="2000"
          /></label></template
      ><template v-if="modal === 'verdict'"
        ><p class="muted">
          {{
            form.action === "PASS"
              ? "确认这篇帖子已完成？通过后学生不能再提交新版本；如需继续修改，要在这里撤销定稿。"
              : form.action === "RETURN"
                ? "打回后学生需要继续修订。写清要改什么，学生端会直接看到这段原因。"
                : "撤销定稿后，学生可以继续提交新版本。"
          }}
        </p>
        <label v-if="form.action === 'RETURN'"
          >打回原因<textarea
            v-model="form.body"
            required
            rows="4"
            maxlength="2000"
            placeholder="例如：结论只写了「验证了公式」，要改成「在什么条件下、可信到什么程度」"
          /></label></template
      ><template v-if="modal === 'verify'"
        ><p class="muted">{{ form.sourceTitle }}</p>
        <label
          >核验结论<select v-model="form.verdict">
            <option value="PASS">通过：来源与论断支持关系成立</option>
            <option value="FAIL">未通过：来源、引文或支持关系存在问题</option>
          </select></label
        ><label
          >对照材料与判断依据<textarea
            v-model="form.body"
            required
            rows="5"
            maxlength="2000"
            placeholder="请具体说明核对了什么，为什么得到此结论。"
          />
        </label>
        <p class="form-note">
          核验结果将保留操作者与时间；同伴结论不一致时转为有争议，由教师裁定。
        </p></template
      >
      <p v-if="error" class="error-note" role="alert">{{ error }}</p>
      <footer>
        <button type="button" class="btn secondary" @click="emit('close')">
          取消</button
        ><button class="btn primary" :disabled="busy">
          {{
            busy
              ? "正在保存…"
              : modal === "assignment"
                ? "发布作业"
                : modal === "course"
                  ? "创建课程"
                  : "确认保存"
          }}<Check :size="17" />
        </button>
      </footer>
    </form>
    <div v-else class="verification-records">
      <p>{{ form.sourceTitle }}</p>
      <article v-for="r in form.records" :key="r.id">
        <span class="badge" :class="r.verdict === 'PASS' ? 'green' : 'red'">{{
          r.verdict === "PASS" ? "通过" : "未通过"
        }}</span
        ><strong>{{ r.verifier_name }}</strong>
        <p>{{ r.reason }}</p>
        <span class="muted">{{ fmt(r.created_at) }}</span>
      </article>
      <p v-if="!form.records.length" class="empty-text">尚无核验记录。</p>
    </div>
  </section>
</div>
</template>
