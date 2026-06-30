-- =============================================================================
-- V109: 設備障礙派工簽核 — 權限與角色綁定
--
-- 新增權限：
--   WORK_ORDER_APPROVE  — 審核工單（派工審核 / 施工驗證）
--
-- 角色綁定（tenant_id = 'T_D029426BA10C'）：
--   ROLE_ADMIN         → 全面管理
--   ROLE_DEPT_ADMIN    → 派工審核
--   ROLE_DEPT_USER     → 施工驗證
--   ROLE_OPERATOR      → 僅執行工單（不需審核權限）
-- =============================================================================

-- ── 1. 新增 permission 定義（idempotent）───────────────────────────────────

INSERT INTO permissions (permission_id, code, name, group_name, sort_order) VALUES
  ('PERM_WORK_ORDER_APPROVE', 'WORK_ORDER_APPROVE', '審核工單', 'Work order management', 57)
ON CONFLICT (code) DO NOTHING;

-- ── 2. Role-Permission 綁定（指定租戶 T_D029426BA10C）──────────────────────

-- ROLE_ADMIN: 審核工單
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT 'ROLE_ADMIN', p.permission_id, 'T_D029426BA10C'
FROM permissions p
WHERE p.code = 'WORK_ORDER_APPROVE'
ON CONFLICT DO NOTHING;

-- ROLE_DEPT_ADMIN: 派工審核
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT 'ROLE_DEPT_ADMIN', p.permission_id, 'T_D029426BA10C'
FROM permissions p
WHERE p.code = 'WORK_ORDER_APPROVE'
ON CONFLICT DO NOTHING;

-- ROLE_DEPT_USER: 施工驗證
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT 'ROLE_DEPT_USER', p.permission_id, 'T_D029426BA10C'
FROM permissions p
WHERE p.code = 'WORK_ORDER_APPROVE'
ON CONFLICT DO NOTHING;
