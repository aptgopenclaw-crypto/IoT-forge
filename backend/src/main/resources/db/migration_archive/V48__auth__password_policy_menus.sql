-- =============================================================================
-- V48: Password policy settings — menu entries + permissions (Phase 1.5 frontend)
-- =============================================================================
-- Adds two PAGE menus under the System Management parent (id=10):
--   - 33 / TenantPasswordPolicy   — TENANT_ADMIN scope
--   - 34 / PlatformPasswordPolicy — SUPER_ADMIN scope (frontend gates via meta)
-- Plus the permissions that gate the backend controllers so the menu access
-- check (frontend) and @PreAuthorize (backend) stay in sync.
-- =============================================================================

-- ── Menus ───────────────────────────────────────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(33, 10, '密碼策略', 'PAGE', 'TenantPasswordPolicy', '/admin/security/password-policy',
   'views/admin/setting/TenantPasswordPolicyView.vue', 'PASSWORD_POLICY_TENANT_VIEW', 'Lock', 50, true),
(34, 10, '平台密碼策略', 'PAGE', 'PlatformPasswordPolicy', '/platform/password-policy',
   'views/admin/setting/PlatformPasswordPolicyView.vue', 'PASSWORD_POLICY_PLATFORM_VIEW', 'Shield', 60, true)
ON CONFLICT (menu_id) DO NOTHING;

SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 34));

-- ── Permissions ─────────────────────────────────────────────────────────────

INSERT INTO permissions (permission_id, code, name, group_name, sort_order)
VALUES
  ('PERM_PWD_POLICY_TENANT_VIEW',     'PASSWORD_POLICY_TENANT_VIEW',     '檢視租戶密碼策略', '密碼策略', 1),
  ('PERM_PWD_POLICY_TENANT_MANAGE',   'PASSWORD_POLICY_TENANT_MANAGE',   '管理租戶密碼策略', '密碼策略', 2),
  ('PERM_PWD_POLICY_PLATFORM_VIEW',   'PASSWORD_POLICY_PLATFORM_VIEW',   '檢視平台密碼策略', '密碼策略', 3),
  ('PERM_PWD_POLICY_PLATFORM_MANAGE', 'PASSWORD_POLICY_PLATFORM_MANAGE', '管理平台密碼策略', '密碼策略', 4)
ON CONFLICT (code) DO NOTHING;

-- ── Role bindings ───────────────────────────────────────────────────────────
-- TENANT_ADMIN (role code 'ADMIN' in this codebase) — tenant-scope view + manage.
-- Platform perms are intentionally NOT auto-bound here; SUPER_ADMIN bypasses
-- the permission check at runtime, and tenant admins must never see/edit the
-- platform defaults.

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('PASSWORD_POLICY_TENANT_VIEW', 'PASSWORD_POLICY_TENANT_MANAGE')
ON CONFLICT DO NOTHING;
