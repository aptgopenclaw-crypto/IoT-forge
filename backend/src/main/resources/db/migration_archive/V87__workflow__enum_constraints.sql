-- =============================================================================
-- V87: workflow — status / action 欄位改為 Enum（大寫值），加 CHECK 約束
--
-- 背景：
--   Java 側已將 WorkflowInstanceEntity.status、WorkflowStepLogEntity.action
--   改為 @Enumerated(EnumType.STRING)，enum 值均為大寫。
--
--   舊資料：
--     workflow_instances.status   → 已是大寫（IN_PROGRESS / COMPLETED / REJECTED），無需轉換
--     workflow_step_logs.action   → 原為小寫（approve / reject / resubmit），需升大寫
--
--   新增 CHECK 約束，防止 DB 層寫入非法值。
-- =============================================================================

-- 1. 將現有小寫 action 值升大寫
UPDATE workflow_step_logs
SET action = UPPER(action)
WHERE action IS NOT NULL;

-- 2. workflow_instances.status CHECK 約束
ALTER TABLE workflow_instances
    ADD CONSTRAINT chk_instance_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'REJECTED'));

-- 3. workflow_step_logs.action CHECK 約束
ALTER TABLE workflow_step_logs
    ADD CONSTRAINT chk_step_log_action
        CHECK (action IN ('APPROVE', 'REJECT', 'RESUBMIT'));
