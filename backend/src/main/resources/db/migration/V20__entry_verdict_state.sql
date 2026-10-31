-- 单篇帖子（entry）的终审状态。
-- 作业级已有 manually_closed（整班结束），但每篇作品什么时候算完成一直没有答案：
-- 学生可以一直「提交 → 评分 → 再提交」。这里给 entry 加终态，由教师逐篇裁定。
-- OPEN 进行中 / REVISION 已打回需修订 / PASSED 已通过定稿
ALTER TABLE entry ADD COLUMN state VARCHAR(16) NOT NULL DEFAULT 'OPEN';
ALTER TABLE entry ADD COLUMN state_reason VARCHAR(2000);
ALTER TABLE entry ADD COLUMN state_at VARCHAR(40);
ALTER TABLE entry ADD COLUMN state_by BIGINT;
