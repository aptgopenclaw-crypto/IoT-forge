-- =============================================================================
-- V83: 確保 ROLE_ADMIN 綁定 ROLE_ASSIGN_PERM 權限
--
-- 背景：
--   V33 透過 INSERT…SELECT 已將 ROLE_ASSIGN_PERM 綁定至 ADMIN role，
--   V71 則以 cross-join 補齊所有非 PLATFORM 權限。
--   本 migration 再次確保該筆 binding 存在，以防前述 migration 因 out-of-order
--   執行順序或權限表中缺少該筆資料而未生效。
-- =============================================================================

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ADMIN'
  AND p.code = 'ROLE_ASSIGN_PERM'
ON CONFLICT DO NOTHING;
