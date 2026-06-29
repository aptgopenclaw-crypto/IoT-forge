-- ============================================================
-- V20: Add Login Logs menu under Audit Center
-- ============================================================

-- 1. Menu: Login Logs page under Audit Center (parent_id=20)
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(23, 20, 'Login Logs', 'PAGE', 'LoginLogs', '/admin/audit/login-logs', 'views/admin/audit/LoginLogsView.vue', 'AUDIT_LIST', 33, true)
ON CONFLICT (menu_id) DO NOTHING;

-- 2. Reset menus sequence
SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 23));
