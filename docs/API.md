# API 摘要（v0.1）

前缀 `/api`，响应字段使用数据库 snake_case；草稿写入 DTO 使用 camelCase。后续若升级 `/api/v1` 应统一序列化风格并提供兼容迁移。

认证：先 `GET /auth/csrf` 获得 `{token,headerName}`，写请求带对应 header。`POST /auth/login` 为表单 `username`/`password`；登录后重新获取 CSRF token。`GET /auth/me` 返回用户标识、邮箱、姓名；`POST /auth/logout` 销毁会话。

| 方法 | 路径 | 权限 / 功能 |
| --- | --- | --- |
| GET | /courses | 本人课程与角色 |
| POST | /courses | 已有教师身份；创建课程 |
| POST | /courses/{id}/members | 教师；添加已存在用户到本课程 |
| GET/POST | /courses/{id}/assignments | 课程成员读、教师发布 |
| POST | /assignments/{id}/entries | 学生开始或继续本人作品 |
| GET | /entries/{id} | 作者或任课教师读取草稿、历史 |
| PUT | /entries/{id}/draft | 当前本课学生作者；revision、content、title、evidence |
| POST | /entries/{id}/submissions | 当前本课学生作者；body revision；Idempotency-Key |
| GET | /submissions/{id} | 作者、教师/助教，或课程内获批社区版本 |
| GET | /courses/{id}/review-queue | 教师/助教；当前及历史提交 |
| POST | /submissions/{id}/evaluations | 教师；11 项、summary、gateReason、expectedRevision |
| POST | /submissions/{id}/feedback | 教师；段落 ID、主责指标、批注 |
| POST | /feedback/{id}/actions | 作者回应/采纳/忽略；教师解决/重开 |
| GET | /courses/{id}/evidence | 课程成员；核验所需摘录/论断 |
| GET/POST | /evidence/{id}/verifications | 记录读取 / 非作者核验，教师裁定 |
| POST | /submissions/{id}/publication | 作者发起指定版本课程内发布申请 |
| GET | /courses/{id}/publications | 成员读已发布；pending=true 仅教师 |
| POST | /publications/{id}/actions | 教师 APPROVE；作者或教师 WITHDRAW |

- 401：未登录；403：无权限或无效 CSRF；409：版本冲突/重复操作；422：规则不满足。
- 400：参数格式错误。500 不回传堆栈或 SQL，返回 traceId 供运维排查。
- 本阶段没有开放注册、任意文件上传、AI 调用或外部来源抓取 API。

## 工作流增量

| 方法 | 路径（均以 /api 开头） | 权限/要点 |
|---|---|---|
| GET | /notifications?unread=false&before={id} | 本人当前有权接收的消息；游标分页 |
| PUT | /notifications/{id}/read | 本人可见消息标记已读 |
| PUT | /notifications/read-all | 本人当前可见消息全部已读 |
| GET/PUT/DELETE | /submissions/{id}/review-draft | 当前任课教师本人的私有评分草稿；助教和学生不可读写 |

评分草稿 PUT 传 `items, summary, gateReason, revision, baseEvaluationRevision`，DELETE 传 `revision`；409 表示草稿或正式评价已变化，需要显式恢复/合并。草稿不产生正式分数或学生通知。

作业列表增加 `server_now` 与 `due_status`（OPEN / DUE_SOON / CLOSED）；截止后不允许开始新作品或提交新版本，但已有作品仍可保存草稿、读取历史。详细载荷与边界见 [WORKFLOW.md](WORKFLOW.md)。

## 作业管理增量

| 方法 | 路径（均以 /api 开头） | 要点 |
|---|---|---|
| PUT | /assignments/{id} | 本课教师；title、requirements、dueAt、reason、revision |
| POST | /assignments/{id}/state | 本课教师；action=CLOSE/REOPEN、reason、revision |
| GET | /assignments/{id}/history | 本课教师；最近 100 次规则变更，含 before/after |

写请求需 CSRF；规则修改使用 management_revision 乐观锁。新提交查询增加 assignmentSnapshot，旧提交可为 null。详见 [ASSIGNMENT-MANAGEMENT.md](ASSIGNMENT-MANAGEMENT.md)。

## 课程成员管理增量

以下接口仅本课任课教师可用，名单含邮箱，学生/助教/跨课账号不可读取。

| 方法 | 路径（均以 /api 开头） | 要点 |
|---|---|---|
| GET | /courses/{cid}/members?q=&role=&page=1 | 角色 STUDENT/ASSISTANT/TEACHER 或空；搜索最多80字符；每页20条；返回 members、counts、total、page、pageSize、revision |
| POST | /courses/{cid}/members | 已有账号 email、role=STUDENT/ASSISTANT、revision、reason；不创建账号；旧添加客户端可省略 revision |
| POST | /courses/{cid}/members/{uid}/role | role=STUDENT/ASSISTANT、必填 reason、revision；不允许教师交接 |
| POST | /courses/{cid}/members/{uid}/remove | 必填 reason、revision；只移出成员关系，不删除历史数据 |
| GET | /courses/{cid}/members/history | 最近100条变更，操作者、目标账号、前后身份、原因与时间 |

写请求需要 CSRF；stale revision/重复成员409，非法身份/无变化/教师保护422，成员不存在404。revision 是整个课程名单版本，不是单成员版本。移出后，旧作者也不能通过原草稿/提交/图片接口继续访问；改为助教的原作者不可继续学生写操作。详细边界见 [MEMBERS.md](MEMBERS.md)。

## 证据工作台增量

| 方法 | 路径（均以 /api 开头） | 要点 |
|---|---|---|
| GET | /courses/{cid}/evidence-workbench | 本课成员；q最多80字符；state空/PENDING/PASS/FAIL/DISPUTED；relation=ALL/MINE/TODO/DONE；assignmentId=0为全部；page>=1，每页20条；返回items/counts/total/page/pageSize |
| GET | /evidence/{id}/review | 本课成员；证据、只含引用段落的paragraph_text、records、record_count、can_verify/blocked_reason/locked/my_verified/can_read_submission |
| POST | /evidence/{id}/decisions | 本课非作者；verdict=PASS/FAIL、必填reason<=2000字符、必填expectedCount>=0；教师裁定/同伴核验按当前身份处理 |

counts为全课程全部提交版本的状态统计，不跟随列表筛选。未发布提交的全文权限不因review接口扩大。expectedCount与详情record_count一致；409表示历史已变化，请保留输入、刷新记录后重新判断。写操作需要CSRF。正式发分后422，作者自核与跨课程403。旧/verifications接口仍兼容；新历史多一个可空verifier_role，旧记录不回填虚构身份。详见 [EVIDENCE-WORKBENCH.md](EVIDENCE-WORKBENCH.md)。

## 发布审核记录（V8）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/courses/{cid}/publication-records` | 当前成员；教师读本课全部记录，其他角色只读本人记录；不返回正文 |
| GET | `/publications/{id}/review` | 当前本课作者或教师；指定版本元数据、最近理由和 events 历史 |
| POST | `/submissions/{sid}/publication-request` | 作者；`{revision: integer>=0, reason: string<=2000}`，reason 可为空，首次 revision=0 |
| POST | `/publications/{id}/decisions` | `{action: APPROVE|RETURN|WITHDRAW, revision: integer>=0, reason: nonblank string<=2000}`；通过/退回仅教师；撤回仅作者/教师 |

新写接口需要 CSRF；状态/修订冲突为 409；无正式评价为 422。`RETURNED` 与 `WITHDRAWN` 可重新申请；每次改变追加事件。已公开列表和普通同伴的提交读取不暴露私有申请/审核理由。复评自动撤回待审核或已发布记录。旧端点保留兼容默认说明及行锁，但没有新端点的必填客户端修订号；新版 UI 不再使用它们。发布记录和历史目前全量按权限读取，分页为前端 20 行展示，并非服务端分页。详见 [发布流程](PUBLICATIONS.md)。

## 本地待发布：个人账号

- `GET /api/account`：本人安全资料；no-store。
- `PUT /api/account/profile`：`{displayName, revision}`；需要 CSRF；过期版本返回 409。
- `PUT /api/account/password`：`{currentPassword, newPassword, confirmation}`；验证旧密码；密码不是纯空白，至少 12 码点、最多 72 UTF-8 字节。成功注销当前会话，其他旧会话下次受保护请求返回 401。
- 当前密码第 5 次失败触发 15 分钟锁定，429/Retry-After；错误当前密码为 400，不触发前端会话注销。
- 不接受目标账号、邮箱或角色覆盖，不提供注册/找回/SSO。
- 详情：[ACCOUNT-SETTINGS.md](ACCOUNT-SETTINGS.md)。


## 课程量规版本（本地待发布）

目录/版本、教师修订、启用、默认切换与历史接口详见 [RUBRIC-VERSIONS.md](RUBRIC-VERSIONS.md)。新建作业可附 `rubricId`，提交详情增加 `rubricSnapshot` 与 `rubricSnapshotSource`。基础历史提交未保存锚点时明确标记 `BUILTIN_LEGACY`，不以新增锚点补写历史。


## 学习报告导出（本地待发布）

- `GET /api/courses/{cid}/progress-export.csv?q=&assignmentId=0&authorId=0&state=ALL&sort=recent`：CSV 文件，覆盖全部匹配页，最多 2000 篇，超限 422，不截断。筛选与进展页面同源；学生仅本人，教师/助教按本课只读权限。
- `GET /api/progress-entries/{id}/report`：schemaVersion 1 的作品报告 JSON；最多 200 个提交、500 条反馈、1500 条反馈动作。非作者学生即使能读公开提交也不可读取私有报告。
- 两个响应均 `Cache-Control: no-store`；CSV 带附件文件名、UTF-8 BOM。无新增数据库迁移。报告不含正文、未提交草稿、邮箱、私有评分草稿或原始量规 JSON。详见 [PROGRESS-REPORTS.md](PROGRESS-REPORTS.md)。


## 批改工作台（本地待发布）

`GET /api/courses/{cid}/review-workbench` 提供 q、assignmentId、authorId、state、drafts、evidence、versions、sort、page 筛选和 20 行分页；`/dashboard` 返回全课统计与最多 6 行摘录；`/next` 在当前范围实时选择另一份无正式评价提交，excludeId 必须属于本课。三个端点只读且 no-store；教师/助教可读，助教不能读取私有评分草稿标记或用 drafts 参数筛选。参数枚举、统计口径和兼容行为详见 [REVIEW-WORKBENCH.md](REVIEW-WORKBENCH.md)。无新增数据库迁移。
