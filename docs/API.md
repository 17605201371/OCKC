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
| PUT | /entries/{id}/draft | 作者；revision、content、title、evidence |
| POST | /entries/{id}/submissions | 作者；body revision；Idempotency-Key |
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
