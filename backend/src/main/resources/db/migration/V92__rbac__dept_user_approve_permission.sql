-- =============================================================================
-- V91: 補齊 DEPT_USER 的 ASSET_TRANSFER_APPROVE 權限
--
-- 背景：
--   V79 應已為 DEPT_USER 綁定 ASSET_TRANSFER_APPROVE，但資料庫中缺少此筆記錄。
--   代理人設計允許將審核任務代理給 DEPT_USER，因此 DEPT_USER 需能看到「待審案件」選單。
--   實際審核授權仍由後端 WorkflowEngine.validateAssignee() 控管（只有 currentAssignee 可操作）。
-- =============================================================================

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'DEPT_USER'
  AND p.code = 'ASSET_TRANSFER_APPROVE'
ON CONFLICT DO NOTHING;
