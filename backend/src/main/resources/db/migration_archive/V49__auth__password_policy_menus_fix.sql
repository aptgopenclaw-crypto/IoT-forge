-- =============================================================================
-- V49: Fix password policy menu entries (V48 silently conflicted on menu_id 33/34)
-- =============================================================================
-- V48 attempted to INSERT menu_id 33 and 34 for password policy pages, but those
-- IDs were already occupied by the announcement module. This migration inserts
-- with new IDs (35, 36).
-- =============================================================================

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(35, 10, '密碼策略', 'PAGE', 'TenantPasswordPolicy', '/admin/security/password-policy',
   'views/admin/setting/TenantPasswordPolicyView.vue', 'PASSWORD_POLICY_TENANT_VIEW', 'Lock', 50, true),
(36, 10, '平台密碼策略', 'PAGE', 'PlatformPasswordPolicy', '/platform/password-policy',
   'views/admin/setting/PlatformPasswordPolicyView.vue', 'PASSWORD_POLICY_PLATFORM_VIEW', 'Shield', 60, true)
ON CONFLICT (menu_id) DO NOTHING;

SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 36));
