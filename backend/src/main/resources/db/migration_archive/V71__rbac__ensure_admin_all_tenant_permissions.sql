-- =============================================================================
-- V71: 確保 ROLE_ADMIN 綁定所有 TENANT scope 權限
--
-- 背景：
--   多次 migration 分散加入權限（V3_1, V19, V33, V48），可能因操作順序或
--   UI 意外解除導致 ADMIN 缺少某些 permission binding。
--   本 migration 以 cross-join + ON CONFLICT 方式一次性補齊。
--
-- 注意：僅綁定 group_name 不屬於 PLATFORM 類的權限，避免 ADMIN 取得平台層權限。
-- =============================================================================

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ADMIN'
  AND p.code NOT LIKE 'PLATFORM_%'
  AND p.code NOT IN ('TENANT_LIST', 'TENANT_CREATE', 'TENANT_UPDATE')
ON CONFLICT DO NOTHING;
