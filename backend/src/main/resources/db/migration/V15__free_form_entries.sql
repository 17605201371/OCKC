-- 自由创作：不挂作业要求与截止时间的知识帖。用 free_form 标记，使既有「entry 必须属于 assignment」的联表逻辑保持不变。
ALTER TABLE assignment ADD COLUMN free_form BOOLEAN NOT NULL DEFAULT FALSE;
