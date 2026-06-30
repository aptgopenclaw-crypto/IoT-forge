-- ============================================================
-- V19: Add role management permission + menu under System Management
-- ============================================================

-- 1. Permissions: ROLE_LIST, ROLE_UPDATE
INSERT INTO permissions (permission_id, code, name, group_name, sort_order) VALUES
('PERM_ROLE_LIST',   'ROLE_LIST',   'Role list',       'Role management', 35),
('PERM_ROLE_UPDATE', 'ROLE_UPDATE', 'Update role',     'Role management', 36)
ON CONFLICT (code) DO NOTHING;

-- 2. Menu: Role Management page under System Management (parent_id=10)
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(13, 10, 'Role Management', 'PAGE', 'RolePermission', '/admin/system/roles', 'views/admin/role/RolePermissionView.vue', 'ROLE_LIST', 23, true)
ON CONFLICT (menu_id) DO NOTHING;

-- 3. Reset menus sequence
SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 13));

-- 4. Bind ROLE_LIST + ROLE_UPDATE to ADMIN role
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code IN ('ROLE_LIST', 'ROLE_UPDATE')
ON CONFLICT DO NOTHING;
