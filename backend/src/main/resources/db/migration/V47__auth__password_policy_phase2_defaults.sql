-- =============================================================================
-- V47: Password policy Phase 2 — advanced complexity platform defaults
-- =============================================================================
-- Seeds the six Phase 2 keys under the __PLATFORM__ sentinel so newly-installed
-- environments and existing tenants without overrides immediately get sane
-- defaults aligned with the spec §3.2 table.
--
-- ON CONFLICT DO NOTHING ensures idempotency: re-running on environments where
-- an operator already inserted a value (e.g. via the platform settings API)
-- leaves their choice intact.
-- =============================================================================

INSERT INTO system_settings (tenant_id, setting_key, setting_value, description, created_at, updated_at)
VALUES
  ('__PLATFORM__', 'password.max_length',            '128',  '平台預設：密碼最大長度（DoS 防護）', NOW(), NOW()),
  ('__PLATFORM__', 'password.min_special_chars',     '1',    '平台預設：至少 N 個特殊字元',     NOW(), NOW()),
  ('__PLATFORM__', 'password.min_digits',            '1',    '平台預設：至少 N 個數字',         NOW(), NOW()),
  ('__PLATFORM__', 'password.min_uppercase',         '1',    '平台預設：至少 N 個大寫',         NOW(), NOW()),
  ('__PLATFORM__', 'password.min_lowercase',         '1',    '平台預設：至少 N 個小寫',         NOW(), NOW()),
  ('__PLATFORM__', 'password.not_contains_username', 'true', '平台預設：不可包含使用者名稱',     NOW(), NOW())
ON CONFLICT (tenant_id, setting_key) DO NOTHING;
