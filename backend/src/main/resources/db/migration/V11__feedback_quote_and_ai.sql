-- 教师批注：支持「四维类型 + 选中文字」的轻量批注，并缓存 AI 反馈结果。
ALTER TABLE feedback ADD COLUMN quote_text VARCHAR(600);

CREATE TABLE ai_feedback_cache (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 entry_id BIGINT NOT NULL,
 revision INT NOT NULL,
 payload_json TEXT NOT NULL,
 created_at VARCHAR(40) NOT NULL
);
CREATE UNIQUE INDEX idx_ai_feedback_entry_rev ON ai_feedback_cache(entry_id, revision);
