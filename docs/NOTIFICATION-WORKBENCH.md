# 消息工作台

按十类工作流事件筛选；全部已读在指定分类内操作，不改变其他分类或其他用户。全局未读数与分类未读数分开展示。

`GET /api/notifications/summary?kind=` 返回 unreadCount、scopeUnreadCount、latestId、accessStamp，不返回正文。列表仍为每页 20 条、before 游标分页。统计与权限快照使用可重复读；只展示当前仍有课程权限和相应受众身份的消息。

后台检查更新只提示，不替换当前阅读列表；权限快照变化则清除旧权限缓存并重新读取。关闭、切换分类、卸载或晚到响应不能复活旧列表。失败可重试；原生模态框限制后台操作、保留关闭按钮、处理 Escape/Tab 和全局快捷键。

NotificationWorkbenchTest 14 项覆盖分类、计数口径、分页、CSRF、撤销权限和 accessStamp。所有消息都是路由元数据，不包含教师私有评分草稿。
