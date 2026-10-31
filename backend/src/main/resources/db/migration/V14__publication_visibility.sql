-- 发布可见范围：COURSE 仅本课程（默认）／PUBLIC 全站公开（需作者显式同意）。
ALTER TABLE publication ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'COURSE';
