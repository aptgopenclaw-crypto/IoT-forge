-- =============================================================================
-- V72: 在平台「系統管理」下新增「密碼策略」獨立子選單
--
-- 背景：
--   平台密碼策略原本包含在系統設定頁面中，但資訊量大且用途獨立，
--   需要拆分為獨立的子功能選單，方便 super_admin 直接存取。
-- =============================================================================

-- ── 1. 新增選單：系統管理 > 密碼策略 ────────────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path,
                   component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
(106, 102, '密碼策略', 'PAGE', 'PlatformPasswordPolicy', '/platform/password-policy',
 'views/admin/setting/PlatformPasswordPolicyView.vue',
 'PASSWORD_POLICY_PLATFORM_VIEW', 'Lock', 15, true, 'PLATFORM')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 2. 綁定權限到 ROLE_SUPER_ADMIN ──────────────────────────────────────────

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT 'ROLE_SUPER_ADMIN', p.permission_id, NULL
FROM permissions p
WHERE p.code IN ('PASSWORD_POLICY_PLATFORM_VIEW', 'PASSWORD_POLICY_PLATFORM_MANAGE')
ON CONFLICT DO NOTHING;

-- ── 3. Reset sequence ───────────────────────────────────────────────────────

SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 106));
