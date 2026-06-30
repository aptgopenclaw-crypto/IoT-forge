-- LOG-SUMMARY Flyway V12_3: Seed permission + menu + ADMIN role binding

-- 1. Permission: log-summary:view
INSERT INTO permissions (permission_id, code, name, group_name, sort_order) VALUES
('PERM_LOG_SUMMARY_VIEW', 'LOG_SUMMARY_VIEW', 'Log summary view', 'Monitoring', 60)
ON CONFLICT (code) DO NOTHING;

-- 2. Menu: Monitoring Center directory (menu_id=30, top-level)
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_path, icon, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(30, NULL, 'Monitoring Center', 'DIRECTORY', '/monitoring', 'Monitor', 25, true)
ON CONFLICT (menu_id) DO NOTHING;

-- 3. Menu: Log Summary page (menu_id=31, under Monitoring Center)
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(31, 30, 'Log Summary', 'PAGE', 'LogSummary', '/monitoring/log-summary', 'views/monitoring/LogSummary.vue', 'LOG_SUMMARY_VIEW', 26, true)
ON CONFLICT (menu_id) DO NOTHING;

-- 4. Reset menus sequence
SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 31));

-- 5. Bind LOG_SUMMARY_VIEW to ADMIN role
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code = 'LOG_SUMMARY_VIEW'
ON CONFLICT DO NOTHING;
