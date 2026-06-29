-- =============================================================================
-- V52: Add menu entry for Tenant Auth Config page
-- =============================================================================

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(37, 10, '認證方式設定', 'PAGE', 'TenantAuthConfig', '/admin/security/auth-config',
   'views/admin/setting/TenantAuthConfigView.vue', 'AUTH_CONFIG_VIEW', 'ShieldCheck', 70, true)
ON CONFLICT (menu_id) DO NOTHING;

SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 37));
