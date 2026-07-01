--
-- Fix system_settings_id_seq after V2 inserted rows with explicit IDs
-- without advancing the sequence, causing duplicate PK errors when
-- new tenants are created (see TenantAdminService.seedDefaultSettings).
--
SELECT setval('system_settings_id_seq', COALESCE((SELECT MAX(id) FROM system_settings), 1));

INSERT INTO users
(user_id, email, password_hash, display_name, phone, enabled, "locked", locked_at, login_fail_count, is_super_admin, last_login_at, create_time, update_time, deleted, deleted_at, notify_email_flag, notify_sms_flag, password_changed_at, force_change_password, auth_type, external_id)
VALUES('user-super-001', 'super@test.com', '$2a$10$kRxROhoc8z/4urhjvkB5x.uHq7hu3hO6QuABUCkEivNGeXnQIWbj6', 'Super Admin', NULL, true, false, NULL, 0, true, NULL, '2026-06-25 20:03:44.786', '2026-06-25 20:03:44.786', false, NULL, false, false, '2026-06-25 20:03:47.251', false, 'LOCAL', NULL);