# 架构基线与决策

## 目标

平台统一命名为「课程开放知识社区」。以四维评价规范为基线；不实现旧 HTML 的前沿性评分与五维加权。第一阶段优先证明真实持久化的教学闭环，不用前端模拟数据替代业务能力。

## 数据路径

浏览器 Vue/TipTap → 同源 Spring Boot API → Spring Security 会话/CSRF → 课程与作者授权 → 事务服务 → JDBC → H2（本地）/ MySQL（待集成验证）。

草稿可变；submission 正文和来源关联不可变。evaluation 复评追加并将旧记录标为无效；feedback_action、verification、audit_log 追加保留。publication 仅指向指定 submission，复评会撤回该版社区展示。

## 一致性

- 保存使用 revision 乐观锁，409 返回后客户端保留输入，不静默覆盖。
- 提交锁定 entry；`(entry_id, version_no)`、`(entry_id, idempotency_key)` 唯一。
- 提交 hash 包含标题、正文和草稿来源，重复提交同一内容会拒绝。
- 教师评分锁定 submission，客户端 expectedRevision 防止两窗口覆盖；同一事务切换有效评价。
- 段落用 UUID/blockId 定位；删除后的反馈保留原文，不猜测重定位。拆分/合并的高级映射尚未实现。
- 四维计算以二级数值为权威，不允许直接覆盖一级维度形成两套分数。
- 核验与提交、评价、发布各自独立，不以「采纳」当作「解决」，不以来源摘录匹配当作学科事实成立。

## 安全边界

HTTPS 由正式部署反向代理提供；本地默认 HTTP + localhost。密码 BCrypt cost 12；会话 Cookie HttpOnly、SameSite=Strict；生产应启用 Secure。所有写 API 需要 CSRF。

课程内角色来自 course_member，查询、详情和写入都校验，不信任前端隐藏按钮。学生只能编辑本人草稿；其他学生仅可读获批版本以及为证据核验最小化展示的摘录和论断。后台未实现，不自动赋予管理员教学数据权限。

正文为 TipTap JSON，显示通过编辑器 schema，不将用户输入作为任意 HTML 注入。来源 URL 只允许 http/https，外链使用 noopener/noreferrer，不自动下载外部来源，避免 SSRF。

## 开发约束

该代码是单体骨架，业务服务后续应按版本/评价/证据/发布领域进一步拆分。当前没有可靠的外部异步任务能力，因此不暴露虚假的 AI 预评接口。对象存储、备份、压测、监控、账号生命周期是投入生产前的阻断项。

## 与设计书的阶段性差异

本地以 H2 降低试跑门槛，保留 MySQL 驱动与 Compose。未引入 Redis/RabbitMQ；未实现 AI、成长档案、复杂协同与题库。量规固定，正式阈值及 N/A 学科适用范围仍需教学负责人确认。所有限制在 README 和界面显示，不把首阶段开发版称为完整系统。

## 本期终端边界

仅交付 PC 浏览器（建议 1280px 及以上）；移动端暂不开发、不作为本期验收项。既有基础响应式样式不代表移动端功能已验收。
