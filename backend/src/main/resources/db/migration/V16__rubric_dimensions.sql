-- 四维从「代码前缀推导 + 等权」升级为可配置：每个维度可有自己的名称、说明与权重。
-- 历史量规没有该列，读取时按四维等权（各 25%）回退，历史提交的评分口径保持不变。
ALTER TABLE course_rubric ADD COLUMN dimensions_json LONGTEXT;
