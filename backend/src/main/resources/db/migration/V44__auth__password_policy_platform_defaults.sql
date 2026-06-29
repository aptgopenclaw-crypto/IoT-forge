-- V44: Seed platform-level password policy defaults.
-- These rows live in the existing system_settings table under the reserved
-- sentinel tenant_id = '__PLATFORM__', which is read by PasswordPolicyResolver
-- as the fallback when a tenant does not provide an explicit override.
--
-- Phase 1 keys (see 01-docs/new-feature/auth/password-policy-spec.md §3.1):
--   password.min_length, require_uppercase, require_lowercase,
--   require_digit, require_special, history_count

-- The sentinel tenant row is required because system_settings.tenant_id has a
-- foreign key to tenant.tenant_id. We mark it `enabled = false` so it is hidden
-- from any tenant picker / login flow; PasswordPolicyDao reads it via native
-- SQL that bypasses both the JPA tenant filter and the enabled flag.
INSERT INTO tenant (tenant_id, tenant_code, tenant_name, deployment_mode, enabled)
VALUES ('__PLATFORM__', '__PLATFORM__', 'Platform Defaults (sentinel)', 'CLOUD', false)
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO system_settings (tenant_id, setting_key, setting_value, description, created_at, updated_at)
VALUES
  ('__PLATFORM__', 'password.min_length',        '8',    '平台預設：密碼最小長度（下限 8）', NOW(), NOW()),
  ('__PLATFORM__', 'password.require_uppercase', 'true', '平台預設：必須包含大寫英文字母',   NOW(), NOW()),
  ('__PLATFORM__', 'password.require_lowercase', 'true', '平台預設：必須包含小寫英文字母',   NOW(), NOW()),
  ('__PLATFORM__', 'password.require_digit',     'true', '平台預設：必須包含數字',           NOW(), NOW()),
  ('__PLATFORM__', 'password.require_special',   'true', '平台預設：必須包含特殊字元',       NOW(), NOW()),
  ('__PLATFORM__', 'password.history_count',     '5',    '平台預設：不可重複前 N 次密碼',    NOW(), NOW())
ON CONFLICT (tenant_id, setting_key) DO NOTHING;
