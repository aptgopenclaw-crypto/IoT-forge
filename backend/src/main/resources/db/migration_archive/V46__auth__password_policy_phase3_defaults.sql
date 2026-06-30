-- V46: Seed Phase 3 platform-default password policy values.
-- Tenant admins may override per-tenant via the standard policy API.

INSERT INTO system_settings (tenant_id, setting_key, setting_value, description, created_at, updated_at)
VALUES
  ('__PLATFORM__', 'password.expire_days',                 '90',   '平台預設：密碼有效天數（0 = 永不過期）',     NOW(), NOW()),
  ('__PLATFORM__', 'password.force_change_on_first_login', 'true', '平台預設：首次登入強制改密',                NOW(), NOW()),
  ('__PLATFORM__', 'password.force_change_on_admin_reset', 'true', '平台預設：管理者重設密碼後強制改密',        NOW(), NOW())
ON CONFLICT (tenant_id, setting_key) DO NOTHING;
