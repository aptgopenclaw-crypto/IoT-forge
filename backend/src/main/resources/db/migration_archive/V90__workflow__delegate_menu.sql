-- =============================================================================
-- V90: 新增「代理人管理」選單與權限（TENANT scope）
--
-- 選單結構：
--   代理人管理 (DIRECTORY, menu_id=114) → 指派代理 (PAGE, menu_id=115)
--
-- Permission：
--   WORKFLOW_DELEGATE_MANAGE — 指派代理人設定
--
-- 角色綁定：
--   所有具審核職責的租戶角色均可設定代理：
--   DEPT_USER, DEPT_ADMIN, PROPERTY_MANAGER
-- =============================================================================

-- ── 1. 代理人管理 DIRECTORY (TENANT scope, top-level) ────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_path, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (114, NULL, '代理人管理', 'DIRECTORY', '/workflow-delegate', 'UserCheck', 60, true, 'TENANT')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 2. 指派代理 PAGE ─────────────────────────────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (115, 114, '指派代理', 'PAGE', 'WorkflowDelegateAssign', '/workflow-delegate/assign',
     'views/workflowDelegate/WorkflowDelegateView.vue', 'WORKFLOW_DELEGATE_MANAGE', 'UserPlus', 10, true, 'TENANT')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 3. 新增 permission 定義 ──────────────────────────────────────────────────

INSERT INTO permissions (permission_id, code, name, group_name, sort_order)
VALUES
    ('PERM_WORKFLOW_DELEGATE_MANAGE', 'WORKFLOW_DELEGATE_MANAGE', '指派代理人設定', '代理人管理', 1)
ON CONFLICT (code) DO NOTHING;

-- ── 4. Role-Permission 綁定（global, tenant_id = NULL）───────────────────────

-- ROLE_DEPT_USER
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'DEPT_USER'
  AND p.code = 'WORKFLOW_DELEGATE_MANAGE'
ON CONFLICT DO NOTHING;

-- ROLE_DEPT_ADMIN
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'DEPT_ADMIN'
  AND p.code = 'WORKFLOW_DELEGATE_MANAGE'
ON CONFLICT DO NOTHING;

-- ROLE_PROPERTY_MANAGER
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'PROPERTY_MANAGER'
  AND p.code = 'WORKFLOW_DELEGATE_MANAGE'
ON CONFLICT DO NOTHING;

-- ── 5. Reset sequence ────────────────────────────────────────────────────────

SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 115));
