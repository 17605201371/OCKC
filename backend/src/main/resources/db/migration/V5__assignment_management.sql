ALTER TABLE assignment ADD COLUMN management_revision INT NOT NULL DEFAULT 0;
ALTER TABLE assignment ADD COLUMN manually_closed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE assignment ADD COLUMN updated_at VARCHAR(40);
ALTER TABLE submission ADD COLUMN assignment_snapshot_json LONGTEXT;
CREATE TABLE assignment_change (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 assignment_id BIGINT NOT NULL,
 revision_no INT NOT NULL,
 actor_id BIGINT NOT NULL,
 action_type VARCHAR(20) NOT NULL,
 reason VARCHAR(1000) NOT NULL,
 before_json LONGTEXT NOT NULL,
 after_json LONGTEXT NOT NULL,
 created_at VARCHAR(40) NOT NULL,
 UNIQUE(assignment_id,revision_no),
 FOREIGN KEY(assignment_id) REFERENCES assignment(id),
 FOREIGN KEY(actor_id) REFERENCES app_user(id)
);
