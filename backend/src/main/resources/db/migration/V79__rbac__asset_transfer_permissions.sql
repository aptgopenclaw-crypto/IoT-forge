-- =============================================================================
-- V79: 資產異動 — permission 權限定義與角色綁定
--
-- 背景：
--   V77 建立的資產異動選單 permission_code = NULL（對所有已認證使用者開放），
--   本 migration 將其改為 RBAC 管控，並為相關角色綁定對應權限。
--
-- Permission 設計：
--   ASSET_TRANSFER_VIEW    — 檢視資產異動模組（目錄 + 我的申請）
--   ASSET_TRANSFER_CREATE  — 新增資產異動申請
--   ASSET_TRANSFER_APPROVE — 審核待審案件（檢視待審列表 + 核准/退回操作）
--
-- 角色綁定策略：
--   ROLE_DEPT_USER        → ASSET_TRANSFER_VIEW + CREATE + APPROVE
--                            （APPROVE 僅供檢視待審列表，核准/退回由後端 currentAssignee 控制）
--   ROLE_DEPT_ADMIN       → ASSET_TRANSFER_VIEW + CREATE + APPROVE
--   ROLE_PROPERTY_MANAGER → ASSET_TRANSFER_VIEW + APPROVE
-- =============================================================================

-- ── 1. 新增 permission 定義 ──────────────────────────────────────────────────

INSERT INTO permissions (permission_id, code, name, group_name, sort_order)
VALUES
  ('PERM_ASSET_TRANSFER_VIEW',   'ASSET_TRANSFER_VIEW',   '檢視資產異動',       '資產異動', 1),
  ('PERM_ASSET_TRANSFER_CREATE', 'ASSET_TRANSFER_CREATE', '新增資產異動申請',   '資產異動', 2),
  ('PERM_ASSET_TRANSFER_APPROVE','ASSET_TRANSFER_APPROVE','審核資產異動申請',   '資產異動', 3)
ON CONFLICT (code) DO NOTHING;

-- ── 2. 更新 V77 選單的 permission_code ───────────────────────────────────────
--     原本皆為 NULL（全員開放），現在改為 RBAC 管控。

UPDATE menus SET permission_code = 'ASSET_TRANSFER_CREATE'  WHERE menu_id = 111 AND permission_code IS NULL;
UPDATE menus SET permission_code = 'ASSET_TRANSFER_APPROVE' WHERE menu_id = 112 AND permission_code IS NULL;
UPDATE menus SET permission_code = 'ASSET_TRANSFER_VIEW'    WHERE menu_id = 113 AND permission_code IS NULL;

-- ── 3. Role-Permission 綁定（global, tenant_id = NULL）───────────────────────

-- ROLE_DEPT_USER（code = 'DEPT_USER'）
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'DEPT_USER'
  AND p.code IN ('ASSET_TRANSFER_VIEW', 'ASSET_TRANSFER_CREATE', 'ASSET_TRANSFER_APPROVE')
ON CONFLICT DO NOTHING;

-- ROLE_DEPT_ADMIN（code = 'DEPT_ADMIN'）
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'DEPT_ADMIN'
  AND p.code IN ('ASSET_TRANSFER_VIEW', 'ASSET_TRANSFER_CREATE', 'ASSET_TRANSFER_APPROVE')
ON CONFLICT DO NOTHING;

-- ROLE_PROPERTY_MANAGER（code = 'PROPERTY_MANAGER'）
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'PROPERTY_MANAGER'
  AND p.code IN ('ASSET_TRANSFER_VIEW', 'ASSET_TRANSFER_APPROVE')
ON CONFLICT DO NOTHING;
