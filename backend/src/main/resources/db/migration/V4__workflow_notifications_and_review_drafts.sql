CREATE TABLE notification (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 recipient_id BIGINT NOT NULL,
 course_id BIGINT NOT NULL,
 kind VARCHAR(40) NOT NULL,
 audience VARCHAR(20) NOT NULL,
 title VARCHAR(160) NOT NULL,
 target_type VARCHAR(30) NOT NULL,
 target_id BIGINT NOT NULL,
 anchor_id VARCHAR(80) NOT NULL,
 created_at VARCHAR(40) NOT NULL,
 read_at VARCHAR(40),
 FOREIGN KEY(recipient_id) REFERENCES app_user(id),
 FOREIGN KEY(course_id) REFERENCES course(id)
);
CREATE INDEX idx_notification_inbox ON notification(recipient_id,id);
CREATE INDEX idx_notification_unread ON notification(recipient_id,read_at);
CREATE TABLE review_draft (
 submission_id BIGINT NOT NULL,
 teacher_id BIGINT NOT NULL,
 details_json LONGTEXT NOT NULL,
 summary VARCHAR(2000) NOT NULL,
 gate_reason VARCHAR(1000) NOT NULL,
 base_evaluation_revision INT NOT NULL,
 revision INT NOT NULL,
 updated_at VARCHAR(40) NOT NULL,
 PRIMARY KEY(submission_id,teacher_id),
 FOREIGN KEY(submission_id) REFERENCES submission(id),
 FOREIGN KEY(teacher_id) REFERENCES app_user(id)
);
