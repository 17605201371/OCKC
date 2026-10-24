-- 提交与批阅次数上限（0 表示不限）。默认：每份作业最多提交 5 个版本，同一版本最多批阅 3 次。
ALTER TABLE assignment ADD COLUMN max_submissions INT NOT NULL DEFAULT 5;
ALTER TABLE assignment ADD COLUMN max_evaluations INT NOT NULL DEFAULT 3;
