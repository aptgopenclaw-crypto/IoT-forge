-- =============================================================================
-- V98: 補強 IoT 設備管理模組 — Role-Permission 綁定
--
-- 背景：
--   V97 已新增 device 相關 permission 與 menu，但 ADMIN 區段僅包含新增權限，
--   遺漏 DEVICE_VIEW / DEVICE_CREATE / DEVICE_UPDATE 基礎權限。
--   本 migration 確保所有 device 相關權限正確綁定到 ADMIN / DEPT_ADMIN / DEPT_USER。
--
--  idempotent：所有 INSERT 皆使用 ON CONFLICT DO NOTHING。
-- =============================================================================

-- ── ADMIN：補齊所有 device 權限（idempotent）───────────────────────────────

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('DEVICE_VIEW', 'DEVICE_CREATE', 'DEVICE_UPDATE', 'DEVICE_DELETE',
                 'DEVICE_TEMPLATE_MANAGE',
                 'WORK_ORDER_VIEW', 'WORK_ORDER_MANAGE',
                 'CIRCUIT_VIEW', 'CIRCUIT_MANAGE',
                 'CONTRACT_VIEW', 'CONTRACT_MANAGE')
ON CONFLICT DO NOTHING;

-- ── DEPT_ADMIN：補齊所有 device 權限（idempotent）──────────────────────────

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'DEPT_ADMIN'
  AND p.code IN ('DEVICE_VIEW', 'DEVICE_CREATE', 'DEVICE_UPDATE', 'DEVICE_DELETE',
                 'WORK_ORDER_VIEW', 'WORK_ORDER_MANAGE',
                 'CIRCUIT_VIEW', 'CIRCUIT_MANAGE',
                 'CONTRACT_VIEW', 'CONTRACT_MANAGE')
ON CONFLICT DO NOTHING;

-- ── DEPT_USER：補齊檢視 + 工單權限（idempotent）────────────────────────────

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'DEPT_USER'
  AND p.code IN ('DEVICE_VIEW',
                 'WORK_ORDER_VIEW', 'WORK_ORDER_MANAGE')
ON CONFLICT DO NOTHING;
