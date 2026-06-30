-- ============================================================
-- V33: Add missing permission codes for permission-based access control
-- Allows dynamically-created roles to access APIs via permissions
-- ============================================================

-- 1. Add missing permissions
INSERT INTO permissions (permission_id, code, name, group_name, sort_order) VALUES
-- Role management: ROLE_CREATE was missing
('PERM_ROLE_CREATE',       'ROLE_CREATE',       'Create role',         'Role management',   34),
('PERM_ROLE_ASSIGN_PERM',  'ROLE_ASSIGN_PERM',  'Assign permissions',  'Role management',   37),
-- User management: USER_DELETE (soft-delete) was missing
('PERM_USER_DELETE',       'USER_DELETE',        'Delete user',         'User management',   14),
-- Tenant management (SUPER_ADMIN only, for completeness)
('PERM_TENANT_LIST',       'TENANT_LIST',       'Tenant list',         'Tenant management', 70),
('PERM_TENANT_CREATE',     'TENANT_CREATE',     'Create tenant',       'Tenant management', 71),
('PERM_TENANT_UPDATE',     'TENANT_UPDATE',     'Update tenant',       'Tenant management', 72)
ON CONFLICT (code) DO NOTHING;

-- 2. Bind new permissions to ADMIN role (except TENANT_* which are SUPER_ADMIN only)
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('ROLE_CREATE', 'ROLE_ASSIGN_PERM', 'USER_DELETE')
ON CONFLICT DO NOTHING;
