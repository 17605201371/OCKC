# 课程开放知识社区（OCKC）

面向课程的知识帖创作、四维评价、反馈修订与证据核验平台。

**当前版本：v0.1.0 / 第一阶段可运行开发版，不是已完成的生产系统。**

本仓库不是静态原型：Vue 页面调用 Spring Boot API，正文、提交、评价、核验与审计实际存入数据库。示例课程和分数由明确的演示数据初始化产生，不代表真实教学记录。AI 服务未接入，界面不会模拟 AI 评分。

**本期范围：仅 PC 浏览器，建议视口宽度 1280px 及以上；移动端不在本期开发与验收范围。**

视觉基线已结合用户 HTML 原型与三栏知识创作参考更新：浅灰蓝工作区、紫色操作、任务进度首页及连贯的图文创作画布。详见 [界面更新与边界](docs/UI-REDESIGN.md)。

写作已升级为三栏知识工作台：图片上传/图注/来源、表格、LaTeX 公式、提示块、Markdown 快捷输入与导出、目录/证据及真实质量反馈。能力与边界见 [写作编辑器](docs/WRITING-EDITOR.md)。

发布审核已补齐退回理由、作者重申、撤回历史、旧窗口冲突保护与复评自动下架；社区与阅读页可查看本人/教师发布记录。详见 [发布审核与记录](docs/PUBLICATIONS.md)。

本地页面易用性增量：长文操作栏、短视口弹窗、评分填写进度与待填写项定位、折叠高级筛选/顶部翻页、独立图片工具栏及报告时间展示。详见 [页面优化与验证边界](docs/PAGE-USABILITY.md)。此增量已按授权提交至 `feat/validated-pc-baseline` 功能分支；保持草稿 PR，未合并 main。另有经用户授权的临时演示预览，不是长期正式部署。

## 1. 已实现

- 真实站内消息中心：提交/评价/批注/核验/发布审核提醒，未读管理与权限校验。
- 教师私有评分草稿：自动保存、跨页面续评、乐观锁、本机备份恢复及正式评价版本隔离。
- 教师作业管理：编辑任务、调整截止时间、结束/重新开放、冲突保护与变更记录；新提交保存当时任务规则快照。详见 [作业管理](docs/ASSIGNMENT-MANAGEMENT.md)。
- 作业截止状态：服务端校时、即将截止/已截止筛选；已截止仍可保存已有草稿，不接受新提交。
- Vue 3 + TypeScript + TipTap：学生工作台、课程作业、正文创作、教师评审、四维量规、知识社区、证据核验。
- 学习轨迹/教学进展：按权限展示真实版本、最近已评分质量、四维指标与反馈处理；社区图文封面与排序。
- 安全图片上传：作者/课程权限、重编码、稳定媒体 URL、与指定发布版本绑定的只读访问。
- Spring Boot 3 + Spring Security：BCrypt 密码、服务端会话、CSRF、课程成员与作者权限检查。
- H2 文件数据库用于本地开发；Flyway 数据迁移；提供 MySQL 8 Docker Compose 配置。
- 富文本正文 JSON、稳定段落标识、自动保存、乐观锁冲突提示、本地备份恢复。
- 每次提交生成不可变正文与证据副本，保存内容 SHA-256；提交幂等、截止时间校验。
- 四维 11 项计分，维度内平均、四维等权；N/A 要求理由；待核验不允许正式发分；教师确认严重缺陷时封顶 89。
- 教师发布/复评、段落批注、学生回应/采纳/忽略、教师确认问题解决，旧评价和操作记录保留。
- 证据核验工作台：来源/论断检索、状态与作业筛选、分页、引用段落对照、核验历史、争议裁定与评审定位；作者禁止自核，评分发布后冻结。详见 [核验工作台](docs/EVIDENCE-WORKBENCH.md)。
- 基于段落标识的前后版本文本差异；格式变化单独提示，不冒充逐字符富文本 diff。
- 作者申请指定版本在课程社区发布，教师审核；新草稿不替换已发布版本；作者/教师可撤回。
- 教师创建课程/作业；课程成员搜索、已有账号添加、学生/助教身份调整、移出及操作记录；移出保留作品与评价。账号开通与管理后台尚未实现。详见 [成员管理](docs/MEMBERS.md)。

## 2. 技术与目录

```text
frontend/                 Vue 3、TypeScript、Vite、TipTap
backend/                  Spring Boot 3、JDBC、Security、Flyway
  src/main/resources/db/  版本化数据库迁移
  src/test/               计分与 API 集成测试
scripts/                  本地启动与浏览器联调脚本
docs/                     设计决策、接口与验证记录
Dockerfile                前后端多阶段构建
compose.yml               本地 MySQL 集成配置
```

应用为模块化单体的第一步骨架；当前按控制器、业务服务、存储、计分、安全分层，领域模块将在后续迭代继续拆分。没有提前引入微服务、RabbitMQ、Redis、对象存储或向量数据库。

## 3. 本地启动（推荐首次验收）

环境：**JDK 21、Node.js 22.12+、npm、联网下载 Maven/npm 依赖**。Maven Wrapper 已包含，不需要全局安装 Maven。

macOS / Linux（先 `git clone https://github.com/17605201371/OCKC.git`，再 `cd OCKC`）：

```bash
# 请先在当前终端设置你自己选择的本地演示密码，至少 12 位。
# 不要将密码写入 Git、截图或 issue；四个演示账号共用这个本地密码。
read -s -p 'Local demo password (12+ chars): ' DEMO_PASSWORD; echo
export DEMO_PASSWORD
bash scripts/run-local.sh
```

启动后在**运行此命令的电脑**访问 `http://127.0.0.1:8080`。此地址不是已部署的公网网站。

首次启动会创建以下演示账号（均使用你设置的 `DEMO_PASSWORD`）：

| 邮箱 | 用途 |
| --- | --- |
| `teacher@example.test` | 任课教师，发布作业、批改、裁定和审核 |
| `student@example.test` | 学生，已有 v1 教师评价与待修订反馈 |
| `peer@example.test` | 同班学生，另有待批改提交 |
| `outsider@example.test` | 不同课程学生，用于跨课程权限测试 |

- 数据位于 `backend/data/`，重启后保留；不要提交这些文件。
- `DEMO_PASSWORD` 只在空数据库首次初始化时使用；改变变量**不会重置已有账号密码**。
- 默认仅监听本机。请勿把演示账号服务直接暴露到公网。
- 演示来源摘录仅用于流程演示；教学使用前应替换成获授权原件。
- 本地备份存在当前浏览器的 localStorage，退出账号不自动删除；共享电脑使用后请清理站点数据。

手动启动 / Windows：

```text
1. 在 frontend/ 中执行 npm ci、npm run build。
2. 将 frontend/dist/ 中的内容复制到 backend/src/main/resources/static/。
3. 在 backend/ 中执行 bash mvnw -B verify（Windows 使用 mvnw.cmd -B verify）。
4. 在终端设置 DEMO_SEED=true、DEMO_PASSWORD（至少12位）。
5. 在 backend/ 中执行 java -jar target/course-community-0.1.0.jar。
```

分开开发时：后端在 8080 运行；`cd frontend && npm run dev` 的 Vite 代理将 `/api` 转发到后端。注意生产构建 CSP 不允许 Vite HMR 的跨源/WebSocket，开发联调建议先使用同源打包版；若专门调整开发 CSP，不得将放宽设置用于生产。

## 4. MySQL / Docker

```bash
cp .env.example .env
# 在本地编辑 .env，设置数据库密码、root 密码及演示密码，不提交 .env。
docker compose up --build
```

此 Compose 默认只将应用映射到本机端口，数据库不暴露宿主端口。**当前环境已验证 H2；MySQL/容器配置待在具备 Docker 的机器上实际验收，不宣称已通过。**

准备接入真实用户时，先完成账号管理、HTTPS、单独账号密码、限流审查、备份恢复、依赖安全扫描、真实负载压测与数据治理审批；关闭 `DEMO_SEED` 不会删除已存在的演示账号。

## 5. 测试

```bash
cd frontend && npm ci && npm run build
cd ../backend && bash mvnw -B test
```

浏览器联调脚本 `scripts/ui-smoke.mjs` 针对独立演示数据库运行，会写入修订和评审测试数据；不能对真实课程运行。需先启动应用，设置环境变量 `E2E_PASSWORD` 为初始化时的演示密码，并安装浏览器：

```bash
cd frontend
npm exec playwright install chromium
cd ..
E2E_PASSWORD="$DEMO_PASSWORD" node scripts/ui-smoke.mjs
```

可用 `CHROMIUM_PATH` 指定本地 Chromium。脚本应在新初始化的演示数据上运行，输出到忽略的 `.qa/`，测试截图和日志不作为仓库运行依赖。详见 `docs/VALIDATION.md`。

## 6. 计分规则

一级维度：科学性 S、准确性 A、完整性 C、逻辑性 L。

二级指标：S1–S3、A1–A3、C1–C2、L1–L3，共 11 项。

- `Dk = 本维度有效指标之和 / 有效指标数`。
- `Qraw = (S + A + C + L) / 4`，不是 11 项直接平均。
- 有已证实严重缺陷时 `Qfinal = min(Qraw, 89)`；保留原分和门槛理由。
- N/A 不计零分，需要理由；维度全空或仍有待核验时拒绝正式发布。
- 正式评分只允许课程教师发布；助教只读队列及参与证据核验。
- 四维等权和 11 项指标固定；本地已增加量规说明/学科判分锚点编辑、不可变启用版本与作业绑定，详见 [量规版本](docs/RUBRIC-VERSIONS.md)。尚未部署。

## 7. 当前未实现 / 后续计划

- AI 模型网关、异步任务队列、模型评估与成本治理。
- 对象存储、媒体回收与生产配额、授权教材自动比对与完整所见即所得公式编辑（现已支持 PNG/JPEG、LaTeX，以及六类可视化公式结构拼装）。
- 完整移动端适配与全流程验收（账号、量规、报告等专项已覆盖 390px，尚不代表全部流程完成移动端认证）。
- 实时多人编辑、小组分工与协商、分步习题与题库。
- 自定义权重/指标、跨课程量规共享、专家校准判分锚例、班级深度分析、异步大批量导出、XLSX 和服务器 PDF（已支持基础学习轨迹/教学进展及 CSV、作品报告 JSON/可打印 HTML）。
- 账号注册/邀请/重置密码、完整管理后台、SSO、生产分布式会话。
- 生产备份/异地容灾、生产告警、合规审查和 200 会话压力测试（现已提供 Linux 本地 H2 停机备份/核验/不覆盖恢复工具，不等同于生产运维方案）。

请将本版本作为可运行的开发基线，不要直接对真实学生投入生产。后续按设计计划分阶段推进，而非把 16 周建设内容视为本次已全部完成。

## 8. CI 模板

本地源码含 `.github/workflows/ci.yml`，用于前端构建、序列化/截止策略/主题/报告单元检查及后端验证。`docs/ci-template.yml` 保留配置参考。本轮未向远程推送工作流，也未运行或宣称 GitHub Actions 在线通过；在线启用仍需维护者授权。

## 9. PC 在线演示部署

可在自己的浏览器打开 [Render 部署入口](https://render.com/deploy?repo=https://github.com/17605201371/OCKC)，按 `docs/RENDER-DEMO.md` 创建 Free 演示服务。此链接是部署配置入口，不是已上线的网址。需要登录 Render、设置演示密码、确认免费计划并等待构建；数据可能在重启/休眠后丢失。当前仅补齐配置，尚未完成云端构建验收。

## 作业级 AI 策略与反馈右栏

实现说明与安全边界见 [docs/FEEDBACK-SIDEBAR.md](docs/FEEDBACK-SIDEBAR.md)。当前仅完成入口策略和状态展示，尚未接入模型，不会发送学生正文。

## 教学工作流增量

消息提醒、教师评分草稿和截止策略见 [docs/WORKFLOW.md](docs/WORKFLOW.md)。旧数据不补造历史消息，评分草稿不等于正式评价。

## 本地待发布增量：个人账号设置

账号资料、安全改密、旧会话失效及草稿保护见 [docs/ACCOUNT-SETTINGS.md](docs/ACCOUNT-SETTINGS.md)。本轮只做本地开发与隔离验收，没有发布、部署或更新在线数据库。注册邀请、密码找回、邮箱变更和 SSO/MFA 尚未开放。

轨迹、主题和消息说明分别见 [docs/PROGRESS-WORKBENCH.md](docs/PROGRESS-WORKBENCH.md)、[docs/THEME-AND-MEDIA.md](docs/THEME-AND-MEDIA.md)、[docs/NOTIFICATION-WORKBENCH.md](docs/NOTIFICATION-WORKBENCH.md)。源码连续性处理记录见 [docs/LOCAL-RECOVERY.md](docs/LOCAL-RECOVERY.md)。


## 本地待发布增量：课程量规版本

教师修订草稿、版本冻结、新作业默认选择、提交快照与历史兼容见 [量规版本](docs/RUBRIC-VERSIONS.md)。只配置说明与判分锚点，不改变四维等权计算；未发布、未更新在线版本。


## 本地待发布增量：学习报告导出

学生可下载本人作品的提交、当前正式评价及反馈记录；教师/助教可按既有课程权限导出全部匹配页的进展 CSV。作品报告支持 JSON 与独立可打印 HTML，不是全文备份，也不自动发布或分享。统计范围、空分与零分、复评修订和未解决反馈分别标注；超限明确拒绝，不截断。详见 [学习报告导出](docs/PROGRESS-REPORTS.md)。本轮未推送、部署或更新在线数据库。


## 本地待发布增量：批改工作台

新增服务端分页、多维筛选、真实范围统计、会话内位置保留及受评分草稿保存保护的“下一份待评审”；首页改用限量摘录与全课准确总数，助教继续只读。详见 [批改工作台](docs/REVIEW-WORKBENCH.md)。不批量发布、不领取锁定任务；仍未发布或更新在线版本。


## 当前版本一次性收尾（本地，未发布）

四项收尾与七类后续范围见 [完成状态](docs/COMPLETION-STATUS.md)，统一验收结果见 [最终本地验收](docs/VALIDATION-FINAL-LOCAL.md)。

```bash
python3 scripts/local_preflight.py --verify-source
bash scripts/verify-local.sh --install
node scripts/qa-local.mjs --isolated-local --jobs 3
```

配置安全检查、Linux H2 停机备份/恢复、敏感文件保护与运行边界见 [本地运行维护](docs/LOCAL-OPERATIONS.md)。上述命令不部署、不更新在线数据库。登录期间在工作区加载完成前不展示可操作导航，失败可重试，避免首次导航被初始化流程覆盖。


## 本地增量：可视化公式

增加六类结构拼装、六个科学公式模板、即时预览、输入安全限制和正文插入位置保护。切换输入方式不会自动覆盖原输入；编辑已有公式保留稳定 ID，选中文字后插入不删除原文。详见 [可视化公式](docs/VISUAL-FORMULAS.md)。只做符号排版，不做求解或化学配平；本轮未部署上线。

本轮公式最终回归结果见 [公式增量验收](docs/VALIDATION-FORMULAS-LOCAL.md)；该增量已按用户明确授权提交至原草稿 PR #1，未合并、未部署；提交状态与工作流权限限制见 [GitHub 交付说明](docs/GITHUB-HANDOFF.md)。
