-- Workflow 狀態約束：加入 CANCELLED / CANCEL
--
-- WorkflowStatus 已新增 CANCELLED，WorkflowAction 已新增 CANCEL，
-- 但 DB CHECK 約束尚未放行，導致取消操作寫入時拋 ConstraintViolationException。

-- 1. workflow_instances.status CHECK 約束 — 加入 CANCELLED
ALTER TABLE workflow_instances
    DROP CONSTRAINT IF EXISTS chk_instance_status;

ALTER TABLE workflow_instances
    ADD CONSTRAINT chk_instance_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED'));

-- 2. workflow_step_logs.action CHECK 約束 — 加入 CANCEL
ALTER TABLE workflow_step_logs
    DROP CONSTRAINT IF EXISTS chk_step_log_action;

ALTER TABLE workflow_step_logs
    ADD CONSTRAINT chk_step_log_action
        CHECK (action IN ('APPROVE', 'REJECT', 'RESUBMIT', 'CANCEL'));
