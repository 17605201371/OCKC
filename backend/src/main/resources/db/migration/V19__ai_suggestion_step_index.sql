-- 步骤型作业的 AI 建议要落到具体某一步，不再只按段落。
ALTER TABLE ai_suggestion ADD COLUMN step_index INT;
