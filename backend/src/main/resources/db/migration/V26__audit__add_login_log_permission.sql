-- V26: Add separate LOGIN_LOG_LIST permission for Login Logs

-- 1. Create new permission
INSERT INTO permissions (permission_id, code, name, group_name, sort_order) VALUES
('PERM_LOGIN_LOG_LIST', 'LOGIN_LOG_LIST', 'Login log list', 'Audit management', 42)
ON CONFLICT (code) DO NOTHING;

-- 2. Update Login Logs menu to use its own permission
UPDATE menus SET permission_code = 'LOGIN_LOG_LIST' WHERE menu_id = 23;

-- 3. Bind LOGIN_LOG_LIST to roles that already have AUDIT_LIST
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT rp.role_id, p.permission_id, rp.tenant_id
FROM role_permissions rp
JOIN permissions p ON p.code = 'LOGIN_LOG_LIST'
WHERE rp.permission_id = (SELECT permission_id FROM permissions WHERE code = 'AUDIT_LIST')
ON CONFLICT DO NOTHING;
