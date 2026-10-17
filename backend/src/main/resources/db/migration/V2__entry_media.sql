CREATE TABLE entry_media (
 id VARCHAR(36) PRIMARY KEY,
 entry_id BIGINT NOT NULL,
 mime_type VARCHAR(32) NOT NULL,
 width INT NOT NULL,
 height INT NOT NULL,
 body LONGBLOB NOT NULL,
 created_at VARCHAR(40) NOT NULL,
 FOREIGN KEY(entry_id) REFERENCES entry(id)
);
CREATE INDEX idx_media_entry ON entry_media(entry_id);
