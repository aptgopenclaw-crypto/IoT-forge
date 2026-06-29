-- V27: Remove Login Logs menu + LOGIN_LOG_LIST permission

-- 1. Remove role_permissions binding
DELETE FROM role_permissions
WHERE permission_id IN (SELECT permission_id FROM permissions WHERE code = 'LOGIN_LOG_LIST');

-- 2. Remove Login Logs menu entry (menu_id = 23)
DELETE FROM menus WHERE menu_id = 23;

-- 3. Remove permission
DELETE FROM permissions WHERE code = 'LOGIN_LOG_LIST';
