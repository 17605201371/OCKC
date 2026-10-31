-- 消息里显示「谁做的」：只对新消息记录操作者，旧消息为空，界面不显示。
ALTER TABLE notification ADD COLUMN actor_id BIGINT;
