CREATE TABLE course_rubric (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 course_id BIGINT NOT NULL,
 version_no INT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 revision INT NOT NULL DEFAULT 0,
 title VARCHAR(120) NOT NULL,
 instructions VARCHAR(2000) NOT NULL,
 criteria_json LONGTEXT NOT NULL,
 created_by BIGINT NOT NULL,
 created_at VARCHAR(40) NOT NULL,
 updated_at VARCHAR(40) NOT NULL,
 enabled_at VARCHAR(40),
 UNIQUE(course_id,version_no),
 FOREIGN KEY(course_id) REFERENCES course(id),
 FOREIGN KEY(created_by) REFERENCES app_user(id),
 CHECK (state IN ('DRAFT','ENABLED'))
);
ALTER TABLE course ADD COLUMN default_rubric_id BIGINT;
ALTER TABLE course ADD COLUMN rubric_revision INT NOT NULL DEFAULT 0;
ALTER TABLE course ADD FOREIGN KEY(default_rubric_id) REFERENCES course_rubric(id);
ALTER TABLE assignment ADD COLUMN rubric_id BIGINT;
ALTER TABLE assignment ADD FOREIGN KEY(rubric_id) REFERENCES course_rubric(id);
ALTER TABLE submission ADD COLUMN rubric_snapshot_json LONGTEXT;
CREATE TABLE rubric_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 course_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 action_type VARCHAR(20) NOT NULL,
 before_code VARCHAR(40) NOT NULL,
 after_code VARCHAR(40) NOT NULL,
 reason VARCHAR(1000) NOT NULL,
 created_at VARCHAR(40) NOT NULL,
 FOREIGN KEY(course_id) REFERENCES course(id),
 FOREIGN KEY(actor_id) REFERENCES app_user(id)
);
CREATE INDEX idx_rubric_course ON course_rubric(course_id,state,version_no);
CREATE INDEX idx_rubric_event_course ON rubric_event(course_id,id);
