-- 发布时是否把教师批注一并公开。
-- 批注是对「某一版」说的过程性意见，不是成品的一部分：默认不公开，
-- 作者在申请发布时可以显式勾选「公开批注作为范例」，让同学看到这篇文章是怎么被改出来的。
ALTER TABLE publication ADD COLUMN share_feedback BOOLEAN NOT NULL DEFAULT FALSE;
