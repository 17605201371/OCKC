# 编辑器右侧：质量与反馈

## 顺序与风格

- 综合质量环：教师发布的最近提交版本质量分，未评分不显示假分数。
- 四维指标：科学性、准确性、完整性、逻辑性，均占 25%；点击可展开四维 11 项评分与不适用理由。
- 教师批注：总评、锚定段落/图片的批注、原文、回应与修订记录。
- AI 反馈：仅教师为本作业开放时显示，与教师批注、正式评分分离。
- 版本记录默认折叠；顶部可快速定位评分、教师批注和 AI 反馈。

视觉沿用现有 CSS 语义变量：白底/深色纸面、轻边框、小圆角、统一紫色强调。去掉独立的渐变色总分卡与多色指标条。保留版本归属和评分来源，不将草稿质量冒充正式成绩。

## 教师开关

发布作业表单新增「AI 修改建议」，默认关闭。教师可在课程作业卡片上随时切换既有作业的策略；助教与学生不可操作。开关只影响目标作业，不修改正文、冻结提交、教师评价或其他作业。

- `POST /api/courses/{cid}/assignments` 可选 `aiFeedbackEnabled`，省略或 null 为 false。
- `PUT /api/assignments/{aid}/ai-policy`，JSON `{ "enabled": true | false }`。仅本课任课教师；需要 session 与 CSRF；状态落库并审计。
- Flyway V3 为既有作业添加 `ai_feedback_enabled BOOLEAN NOT NULL DEFAULT FALSE`。
- `GET /api/entries/{eid}/ai-feedback` 仅作者与本课教师可访问。课程同学即使能阅读公开条目，也不能读取他人私有 AI 状态/反馈。默认只读已保存的建议；带 `?generate=true` 时调用模型生成新一轮并覆盖旧建议。
- `POST /api/ai-suggestions/{id}/state`，JSON `{ "state": "OPEN" | "ADOPTED" | "DISMISSED" }`，作者与本课教师可用。
- 关闭返回 `DISABLED` 与空列表。学生右侧不显示 AI 分区和导航项。编辑器每 15 秒、重新获得窗口焦点或手动刷新时重新读取策略；权限/网络异常按关闭状态处理，避免继续展示过期反馈。

## 当前真实边界

**已接入 OpenAI 兼容的模型服务**（`POST {AI_API_BASE}/chat/completions`），配置项：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_API_KEY` | 空 | 为空时返回 `NOT_CONFIGURED`，UI 显示「AI 服务尚未配置」，不调用外部服务 |
| `AI_API_BASE` | `https://api.stepfun.com/step_plan/v1` | 模型服务地址 |
| `AI_MODEL` | `step-3.7-flash` | 模型名 |

发送给模型的数据：

- 只在作者或任课教师点击「生成 AI 建议」/「重新生成建议」时发送，打开页面不会触发。
- 内容是标题和带段落标识的草稿正文，截断到约 6000 字；步骤作业按步骤分组发送。图片与证据原文不发送。
- 模型返回的 JSON 无法解析时，用同一份输入自动重试一次，所以一次生成最多调用两次接口。每次 `max_tokens` 为 16000，原因见 [教学工作流](WORKFLOW.md)。

建议的处理：

- 每轮最多保存：知识帖 4 条，步骤作业 5 条（每步最多 2 条）。提示词里的条数和服务端截断用同一组常量 `POST_AI_LIMIT` / `STEP_AI_LIMIT`。新一轮覆盖旧一轮。`paragraphId` 不是本文真实段落时，建议不能定位或替换正文，只能手动采纳；`quote` 不是该段原文时去掉引用和替换文本。
- 采纳、忽略和撤销替换都写入建议状态（`ADOPTED` / `DISMISSED` / `OPEN`）并记审计；每次生成记 `AI_FEEDBACK_GENERATE` 审计。
- 没有 AI 伪分数。正式四维质量分仍仅由教师发布。

## 验证

- `AiPolicyIntegrationTest`：10 项，默认关闭、持久化、授权、CSRF、空参数、同学隔离、无虚构反馈、发布时选择、作业隔离与不改正式评分。
- `scripts/sidebar-e2e.mjs`：本地独立数据库；关闭/开启/重新关闭、刷新持久化、学生已打开页面同步、指标明细、教师批注正文可见、版本记录、发布表单、1280/1440、深色与脚本错误。
- 原图文编辑与评审 `scripts/studio-e2e.mjs` 持续回归。
