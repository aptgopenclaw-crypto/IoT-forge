-- V25: Remove Audit Statistics menu + permission

-- 1. Remove role_permissions binding
DELETE FROM role_permissions
WHERE permission_id IN (SELECT permission_id FROM permissions WHERE code = 'AUDIT_STATS');

-- 2. Remove menu entry (Audit Statistics page, menu_id = 22)
DELETE FROM menus WHERE menu_id = 22;

-- 3. Remove permission
DELETE FROM permissions WHERE code = 'AUDIT_STATS';
