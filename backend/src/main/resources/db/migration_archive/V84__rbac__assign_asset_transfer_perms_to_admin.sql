-- =============================================================================
-- V84: 確保 ROLE_ADMIN 綁定 ASSET_TRANSFER 相關權限
--
-- 背景：
--   V79 新增資產異動三個 permission（ASSET_TRANSFER_VIEW / CREATE / APPROVE），
--   並僅綁定至 ROLE_DEPT_USER、ROLE_DEPT_ADMIN、ROLE_PROPERTY_MANAGER。
--   V71（確保 ADMIN 擁有所有非 PLATFORM 權限）在 V79 之前執行，
--   因此無法涵蓋這三個後來才新增的 permission。
--
-- 影響：
--   ROLE_ADMIN 缺少上述三個 permission，導致在呼叫 PUT /{roleId}/permissions
--   指派角色權限時，因「呼叫者不可指派超越自身的權限」檢查而收到 403。
-- =============================================================================

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('ASSET_TRANSFER_VIEW', 'ASSET_TRANSFER_CREATE', 'ASSET_TRANSFER_APPROVE')
ON CONFLICT DO NOTHING;
