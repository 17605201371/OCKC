ALTER TABLE course ADD COLUMN roster_revision INT NOT NULL DEFAULT 0;
CREATE TABLE course_member_change (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 course_id BIGINT NOT NULL,
 revision_no INT NOT NULL,
 target_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 action_type VARCHAR(20) NOT NULL,
 before_role VARCHAR(20),
 after_role VARCHAR(20),
 reason VARCHAR(1000) NOT NULL,
 created_at VARCHAR(40) NOT NULL,
 UNIQUE(course_id,revision_no),
 FOREIGN KEY(course_id) REFERENCES course(id),
 FOREIGN KEY(target_id) REFERENCES app_user(id),
 FOREIGN KEY(actor_id) REFERENCES app_user(id)
);
