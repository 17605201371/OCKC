# 本地验证、启动与停机备份

本工具链用于 Linux 本地开发版，不是生产运维认证。没有发布、部署、推送或连接在线数据库的步骤。依赖下载（npm/Maven）可能访问软件仓库。

## 1. 环境与源码检查

需要 JDK 21（本轮实测）、Node.js 22+、Python 3.11+；浏览器验收另需 Playwright Chromium。先用 `JAVA_HOME` 和 `PATH` 选择 JDK。

```bash
python3 scripts/local_preflight.py --verify-source
bash scripts/verify-local.sh --install  # 已安装依赖可省略 --install
```

预检只读配置，不输出密码值。拒绝公开监听地址、非文件 H2、危险/不支持的 JDBC 选项和无效端口；启用演示数据时要求显式设置至少 12 字符、最多 72 UTF-8 字节的演示密码。源码清单逐项校验文件，不是数字签名，不能证明来源可信。

验证执行 Python 工具测试、五组前端单元检查、TypeScript/Vite 构建及 Maven verify。构建前清理两处静态资源，避免旧哈希资源混入 JAR。后端测试使用测试库，不读取应用运行库。

## 2. 本地启动

在自己的终端安全设置 `DEMO_PASSWORD`，不要提交到源码或聊天。可设置 `DATABASE_PASSWORD`、`DATABASE_URL`（`jdbc:h2:file:` 本地文件，路径不带 `.mv.db`）及 `PORT`。然后：

```bash
bash scripts/run-local.sh
```

脚本启用演示种子并仅监听回环地址，默认端口 8080；数据库相对路径以 backend 为工作目录。它会先安装依赖并验证再启动，**不是免测试发布脚本**。请使用独立演示数据库，不要指向真实学生数据。生产/容器部署使用另一套经审阅配置；不要为公网部署绕过本地预检。

## 3. 一键隔离验收

```bash
node scripts/qa-local.mjs --isolated-local --jobs 3
# 可选定向重跑（完整交付应执行不带 --suites 的全量）
node scripts/qa-local.mjs --isolated-local --suites accounts,studio,assignments --jobs 2
```

必须先成功构建 JAR。可用 `CHROMIUM_PATH` 指定浏览器、`JAVA_HOME` 指定 Java。脚本不接受现有站点或数据库地址，为每个专项创建随机回环端口和全新文件库；密码随机生成，不打印。覆盖 12 个浏览器专项及实际数据库恢复集成。运行中的测试会创建、评价和审核**隔离演示记录**，不涉及在线作品。报告在 `.qa/acceptance-*/summary.json`；数据库、日志、截图及下载也只保存在本机，不随源码交付。含有个人记录的真实验收产物应按敏感资料管理。

## 4. Linux H2 停机备份与恢复

先停止使用该数据库的所有 Java 实例，确认没有写入。`--confirm-stopped` 是操作者承诺，不会替你停机；工具另用 POSIX 文件锁检测占用。本轮对真实运行中的 Java/H2 尝试备份已被拒绝。仅限本机常规文件系统，不保证网络盘/异常锁机制的兼容性。

```bash
python3 scripts/local_maintenance.py backup \
  --database /private/data/community.mv.db \
  --output /private/backups/community.h2-backup.tar.gz --confirm-stopped
python3 scripts/local_maintenance.py verify \
  --archive /private/backups/community.h2-backup.tar.gz
python3 scripts/local_maintenance.py restore \
  --archive /private/backups/community.h2-backup.tar.gz \
  --destination /private/recovery/community.mv.db --confirm-stopped
```

- 源/目标必须是 `.mv.db`；输出和恢复目标必须不存在，**不覆盖原库**。拒绝软链接路径、硬链接源、非普通文件、恢复目标旁的 H2 临时/锁文件。归档路径也必须是本机路径。
- 归档只有清单和数据库两项，逐字节校验长度与 SHA-256；拒绝重复、路径穿越、链接、额外成员及损坏数据。不使用 `extractall`。暂存文件校验完成才以不覆盖方式发布；文件权限 0600。
- 备份包含账号密码哈希、作品、教师私有评分草稿、反馈与图片 BLOB 等敏感数据。**不加密、不签名**；SHA-256 仅检测意外损坏，不抵御攻击者重写清单。只恢复可信来源，安全存储并控制访问，不上传公开仓库。
- 恢复后将 `DATABASE_URL` 指向新文件（不带 `.mv.db`），保留原数据库用户/密码，设置 `DEMO_SEED=false`，先在回环地址验证登录、版本、评价、反馈、图片等，再另行决定切换。恢复测试已验证现有教师/学生密码仍有效，不执行账号重置。
- 上限为 2 GiB；不支持热备、MySQL 备份、跨版本恢复保证、自动备份轮换、异地容灾或生产恢复时间承诺。不要直接复制运行中的 H2 文件。本地数据库恢复工具不等于完整生产备份方案。

## 5. 发布边界

源码交付不含数据库、备份、密码、日志、上传文件和构建依赖。未自动部署、启动公开隧道、更新在线数据库或发送邮件。发布与生产迁移须用户明确授权并单独验收。
