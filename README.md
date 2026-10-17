# 课程开放知识社区（OCKC）

面向课程的知识帖创作、四维评价、反馈修订与证据核验平台。

**当前版本：v0.1.0 / 第一阶段可运行开发版，不是已完成的生产系统。**

本仓库不是静态原型：Vue 页面调用 Spring Boot API，正文、提交、评价、核验与审计实际存入数据库。示例课程和分数由明确的演示数据初始化产生，不代表真实教学记录。AI 服务未接入，界面不会模拟 AI 评分。

**本期范围：仅 PC 浏览器，建议视口宽度 1280px 及以上；移动端不在本期开发与验收范围。**

视觉基线已按用户提供的 HTML 原型重做：暖灰侧栏、紧凑顶栏、靛紫操作色、任务进度首页及文档式编辑。详见 [界面更新与边界](docs/UI-REDESIGN.md)。

写作体验已升级为 Markdown 风格即时排版：斜杠菜单、选中文字浮动工具、链接与证据、正文 Markdown 导出。能力与边界见 [写作编辑器](docs/WRITING-EDITOR.md)。

## 1. 已实现

- Vue 3 + TypeScript + TipTap：学生工作台、课程作业、正文创作、教师评审、四维量规、知识社区、证据核验。
- Spring Boot 3 + Spring Security：BCrypt 密码、服务端会话、CSRF、课程成员与作者权限检查。
- H2 文件数据库用于本地开发；Flyway 数据迁移；提供 MySQL 8 Docker Compose 配置。
- 富文本正文 JSON、稳定段落标识、自动保存、乐观锁冲突提示、本地备份恢复。
- 每次提交生成不可变正文与证据副本，保存内容 SHA-256；提交幂等、截止时间校验。
- 四维 11 项计分，维度内平均、四维等权；N/A 要求理由；待核验不允许正式发分；教师确认严重缺陷时封顶 89。
- 教师发布/复评、段落批注、学生回应/采纳/忽略、教师确认问题解决，旧评价和操作记录保留。
- 证据独立核验、作者禁止自核、同伴结论冲突转争议、教师裁定；评分发布后核验记录冻结。
- 基于段落标识的前后版本文本差异；格式变化单独提示，不冒充逐字符富文本 diff。
- 作者申请指定版本在课程社区发布，教师审核；新草稿不替换已发布版本；作者/教师可撤回。
- 教师创建课程/作业、将已有账号加入课程。账号开通与管理后台尚未实现。

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
- 本阶段固定 `four-dim-v1`，未实现量规的在线编辑与发布。

## 7. 当前未实现 / 后续计划

- AI 模型网关、异步任务队列、模型评估与成本治理。
- 图片附件上传、对象存储、授权教材自动比对、公式增强编辑。
- 移动端适配与移动端验收（本期仅 PC）。
- 实时多人编辑、小组分工与协商、分步习题与题库。
- 量规版本编辑、学科判分锚例、学情看板、成长报告与导出。
- 账号注册/邀请/重置密码、完整管理后台、SSO、生产分布式会话。
- 正式备份恢复工具、生产告警、合规审查和 200 会话压力测试。

请将本版本作为可运行的开发基线，不要直接对真实学生投入生产。后续按设计计划分阶段推进，而非把 16 周建设内容视为本次已全部完成。

## 8. CI 模板

当前 GitHub 授权允许提交业务代码，但不允许写入 Actions 工作流。`docs/ci-template.yml` 提供 CI 模板；仓库维护者可将其复制为 `.github/workflows/ci.yml` 后启用。当前未在仓库启用、未宣称 GitHub Actions 已通过。

## 9. PC 在线演示部署

可在自己的浏览器打开 [Render 部署入口](https://render.com/deploy?repo=https://github.com/17605201371/OCKC)，按 `docs/RENDER-DEMO.md` 创建 Free 演示服务。此链接是部署配置入口，不是已上线的网址。需要登录 Render、设置演示密码、确认免费计划并等待构建；数据可能在重启/休眠后丢失。当前仅补齐配置，尚未完成云端构建验收。
