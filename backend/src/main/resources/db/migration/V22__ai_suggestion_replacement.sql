-- AI 建议要能直接落到正文：「采纳」时用这段文字替换 quote_text 覆盖的范围。
-- 为空表示这条建议只做说明、不提供可替换文本（例如整段结构问题）。
ALTER TABLE ai_suggestion ADD COLUMN replacement_text LONGTEXT;
