-- 停留时长：学生在编辑器里写作品的时间（WRITE，target_id=entry.id），
-- 教师在批改页批改某个提交版本的时间（GRADE，target_id=submission.id）。
-- 只累计页面在前台、且最近 2 分钟内有操作的秒数；每人每个对象一行，累加 seconds。
CREATE TABLE activity_time (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 kind VARCHAR(16) NOT NULL,
 target_id BIGINT NOT NULL,
 seconds INT NOT NULL DEFAULT 0,
 first_at VARCHAR(40) NOT NULL,
 last_at VARCHAR(40) NOT NULL,
 CONSTRAINT uq_activity_time UNIQUE (user_id, kind, target_id),
 FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_activity_time_target ON activity_time(kind, target_id);
