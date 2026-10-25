-- 作业类型与证据核验策略。
-- kind: POST 知识帖（自由创作，按段落组织）/ STEP 步骤（用二级标题划分步骤，逐步评分与标注问题类型）
ALTER TABLE assignment ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'POST';

-- evidence_policy: REQUIRED 逐条核验后才能发分 / DISPUTED 仅争议阻断（默认）/ OFF 不要求核验
-- 默认值刻意选择 DISPUTED：此前「任一条证据待核验就不能发分」让教师必须逐条处理全班的全部证据，
-- 实际不可持续。历史作业一并落到新默认，教师可在单个作业上改回 REQUIRED。
ALTER TABLE assignment ADD COLUMN evidence_policy VARCHAR(16) NOT NULL DEFAULT 'DISPUTED';

-- 步骤分：仅 STEP 作业使用；知识帖作业保持 NULL。
ALTER TABLE evaluation ADD COLUMN step_scores_json LONGTEXT;

-- 评分草稿同样保存步骤分，避免教师刷新后重填。
ALTER TABLE review_draft ADD COLUMN step_scores_json LONGTEXT;
