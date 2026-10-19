ALTER TABLE publication ADD COLUMN revision INT NOT NULL DEFAULT 0;
ALTER TABLE publication ADD COLUMN requested_evaluation_revision INT;
ALTER TABLE publication ADD COLUMN last_reason VARCHAR(2000) NOT NULL DEFAULT '';
ALTER TABLE publication ADD COLUMN published_at VARCHAR(40);
CREATE TABLE publication_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 publication_id BIGINT NOT NULL,
 revision_no INT NOT NULL,
 actor_id BIGINT NOT NULL,
 actor_role VARCHAR(20) NOT NULL,
 action_type VARCHAR(30) NOT NULL,
 before_state VARCHAR(20) NOT NULL,
 after_state VARCHAR(20) NOT NULL,
 reason VARCHAR(2000) NOT NULL,
 evaluation_revision INT,
 created_at VARCHAR(40) NOT NULL,
 UNIQUE(publication_id,revision_no),
 FOREIGN KEY(publication_id) REFERENCES publication(id)
);
