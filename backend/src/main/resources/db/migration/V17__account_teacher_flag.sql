-- 账号级教师身份：此前只能靠「已经在某门课里是 TEACHER」来推断，
-- 结果零课程的新账号永远开不出第一门课，而学生又能绕过界面直接调接口开课。
-- 改为账号上的显式标记，开课只认这个标记。
ALTER TABLE app_user ADD COLUMN is_teacher BOOLEAN NOT NULL DEFAULT FALSE;

-- 迁移既有数据：所有已经是某门课教师的账号，升级为教师账号。
UPDATE app_user SET is_teacher=TRUE WHERE id IN (SELECT user_id FROM course_member WHERE role='TEACHER');
