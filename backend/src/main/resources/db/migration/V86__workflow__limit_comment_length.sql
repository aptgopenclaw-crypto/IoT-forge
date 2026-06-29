-- =============================================================================
-- V86: workflow_step_logs — 限制 comment 欄位長度為 2000 字元
--
-- 背景：
--   原本 comment 欄位為 TEXT（無限制），惡意使用者可提交極長字串造成
--   異常資源消耗（OWASP A05 Security Misconfiguration）。
--   對應 Entity 同步加入 @Size(max = 2000) 驗證。
-- =============================================================================

ALTER TABLE workflow_step_logs
    ALTER COLUMN comment TYPE VARCHAR(2000);

ALTER TABLE workflow_step_logs
    ADD CONSTRAINT chk_step_log_comment_length CHECK (length(comment) <= 2000);
