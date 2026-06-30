-- ============================================================
-- RBAC Flyway V3_1: Seed permissions + role_permissions + menus
-- ============================================================

-- ==========================================
-- 1. permissions seed (ON CONFLICT skip existing)
-- ==========================================
INSERT INTO permissions (permission_id, code, name, group_name, sort_order) VALUES
-- User management
('PERM_USER_LIST',      'USER_LIST',      'User list',       'User management',   10),
('PERM_USER_CREATE',    'USER_CREATE',    'Create user',     'User management',   11),
('PERM_USER_UPDATE',    'USER_UPDATE',    'Update user',     'User management',   12),
('PERM_USER_DISABLE',   'USER_DISABLE',   'Disable user',    'User management',   13),
-- Department management
('PERM_DEPT_LIST',      'DEPT_LIST',      'Department list',  'Department management', 20),
('PERM_DEPT_CREATE',    'DEPT_CREATE',    'Create department','Department management', 21),
('PERM_DEPT_UPDATE',    'DEPT_UPDATE',    'Update department','Department management', 22),
('PERM_DEPT_DELETE',    'DEPT_DELETE',    'Delete department','Department management', 23),
-- Menu management
('PERM_MENU_LIST',      'MENU_LIST',      'Menu list',        'Menu management',   30),
('PERM_MENU_CREATE',    'MENU_CREATE',    'Create menu',      'Menu management',   31),
('PERM_MENU_UPDATE',    'MENU_UPDATE',    'Update menu',      'Menu management',   32),
('PERM_MENU_DELETE',    'MENU_DELETE',    'Delete menu',      'Menu management',   33),
-- Audit
('PERM_AUDIT_LIST',     'AUDIT_LIST',     'Audit history',    'Audit management',  40),
('PERM_AUDIT_STATS',    'AUDIT_STATS',    'Audit statistics', 'Audit management',  41),
-- Device (DEVICE_VIEW and DEVICE_CREATE already exist from V1_1, skip via ON CONFLICT)
('PERM_DEVICE_UPDATE',  'DEVICE_UPDATE',  'Update device',    'Device management', 52)
ON CONFLICT (code) DO NOTHING;

-- Also insert DEVICE_VIEW / DEVICE_CREATE if not present (idempotent)
INSERT INTO permissions (permission_id, code, name, group_name, sort_order) VALUES
('PERM_DEVICE_VIEW',    'DEVICE_VIEW',    'View device',      'Device management', 50),
('PERM_DEVICE_CREATE',  'DEVICE_CREATE',  'Create device',    'Device management', 51)
ON CONFLICT (code) DO NOTHING;

-- ==========================================
-- 2. role_permissions (global, tenant_id = NULL)
-- ==========================================
-- Clear existing V1_1 seed data to avoid duplicates (NULL tenant_id not dedup-able via UNIQUE)
DELETE FROM role_permissions;

-- ADMIN: all permissions
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'ADMIN';

-- OPERATOR: device management + user list
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'OPERATOR'
  AND p.code IN ('DEVICE_VIEW', 'DEVICE_CREATE', 'DEVICE_UPDATE', 'USER_LIST');

-- VIEWER: read-only
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'VIEWER'
  AND p.code IN ('DEVICE_VIEW', 'USER_LIST', 'DEPT_LIST', 'AUDIT_LIST');

-- Note: SUPER_ADMIN does not need role_permissions (full bypass in code)

-- ==========================================
-- 3. menus seed (2-level structure)
-- ==========================================

-- FuncGroup: User Management
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_path, icon, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(1, NULL, 'User Management', 'DIRECTORY', '/admin/users', 'user', 10, true);

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(2, 1, 'User List', 'PAGE', 'UserList', '/admin/users', 'views/admin/user/UserListView.vue', 'USER_LIST', 11, true),
(3, 1, 'Create User', 'PAGE', 'CreateUser', '/admin/users/create', 'views/admin/user/CreateUserView.vue', 'USER_CREATE', 12, true);

-- FuncGroup: System Management
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_path, icon, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(10, NULL, 'System Management', 'DIRECTORY', '/admin/system', 'setting', 20, true);

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(11, 10, 'Menu Management', 'PAGE', 'MenuManage', '/admin/system/menus', 'views/admin/menu/MenuManageView.vue', 'MENU_LIST', 21, true),
(12, 10, 'Department Management', 'PAGE', 'DeptManage', '/admin/system/depts', 'views/admin/dept/DeptManageView.vue', 'DEPT_LIST', 22, true);

-- FuncGroup: Audit Center
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_path, icon, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(20, NULL, 'Audit Center', 'DIRECTORY', '/admin/audit', 'audit', 30, true);

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(21, 20, 'Audit History', 'PAGE', 'AuditHistory', '/admin/audit/history', 'views/admin/audit/AuditHistoryView.vue', 'AUDIT_LIST', 31, true),
(22, 20, 'Audit Statistics', 'PAGE', 'AuditStats', '/admin/audit/stats', 'views/admin/audit/AuditStatsView.vue', 'AUDIT_STATS', 32, true);

-- Reset menus sequence
SELECT setval('menus_menu_id_seq', (SELECT MAX(menu_id) FROM menus));
