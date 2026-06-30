-- =============================================================================
-- V58: SUPER_ADMIN role redesign — Phase A
--   Add explicit platform-scope permission codes that platform endpoints
--   guard against, plus a tenant-scope password-policy permission code that
--   TENANT_ADMIN (role code 'ADMIN') needs.
--
--   Note: SUPER_ADMIN is auto-granted all permission codes at token-issue time
--   (see AuthServiceImpl.resolvePermissions → findAllCodesOrderByCode), so no
--   role_permissions rows for SUPER_ADMIN are required.
--
--   Reference: 01-docs/new-feature/tenant/02-role-redesign.md §4
-- =============================================================================

-- ── Permissions ─────────────────────────────────────────────────────────────

INSERT INTO permissions (permission_id, code, name, group_name, sort_order)
VALUES
  ('PERM_PASSWORD_POLICY_MANAGE',         'PASSWORD_POLICY_MANAGE',         '管理租戶密碼策略',           '密碼策略', 10),
  ('PERM_PLATFORM_TENANT_MANAGE',         'PLATFORM_TENANT_MANAGE',         '管理租戶（含認證方式設定）', '平台管理', 20),
  ('PERM_PLATFORM_PASSWORD_POLICY_MANAGE','PLATFORM_PASSWORD_POLICY_MANAGE','管理平台密碼策略預設值',     '平台管理', 21),
  ('PERM_PLATFORM_USER_TENANT_MAPPING',   'PLATFORM_USER_TENANT_MAPPING',   '管理跨租戶使用者—角色對應', '平台管理', 22),
  ('PERM_PLATFORM_IMPERSONATE',           'PLATFORM_IMPERSONATE',           '以租戶身份操作（impersonate）','平台管理', 23)
ON CONFLICT (code) DO NOTHING;

-- ── Role bindings ───────────────────────────────────────────────────────────
-- TENANT_ADMIN (role code 'ADMIN') manages its own tenant password policy.
-- Platform perms are intentionally NOT bound to ADMIN; only SUPER_ADMIN holds
-- them (via the auto-grant in AuthServiceImpl.resolvePermissions).

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code = 'PASSWORD_POLICY_MANAGE'
ON CONFLICT DO NOTHING;
