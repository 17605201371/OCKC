-- AI 建议：从一次性缓存升级为可采纳、可定位的条目，与教师批注并列展示但不参与评分。
CREATE TABLE ai_suggestion (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 entry_id BIGINT NOT NULL,
 revision INT NOT NULL,
 paragraph_id VARCHAR(80),
 quote_text VARCHAR(600),
 dimension VARCHAR(8) NOT NULL,
 title VARCHAR(200) NOT NULL,
 body VARCHAR(1000) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'OPEN',
 created_at VARCHAR(40) NOT NULL
);
CREATE INDEX idx_ai_suggestion_entry_rev ON ai_suggestion(entry_id, revision);
