-- V89: 為「我的待辦清單」查詢（findByAssigneeUserIdAndCompletedAtIsNull）建立複合 partial index。
-- 僅對 completed_at IS NULL 的列建立索引，排除已完成的歷史記錄，大幅縮小索引大小並加速查詢。
CREATE INDEX idx_step_logs_assignee_pending
    ON workflow_step_logs (tenant_id, assignee_user_id)
    WHERE completed_at IS NULL;
