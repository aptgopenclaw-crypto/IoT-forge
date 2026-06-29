-- Add archived_at column for notification retention mechanism
ALTER TABLE notifications ADD COLUMN archived_at TIMESTAMPTZ NULL;

-- Partial index: queries only need active (non-archived) notifications
CREATE INDEX idx_notifications_active
    ON notifications (user_id, read, created_at DESC)
    WHERE archived_at IS NULL;

-- Seed notification_retention_days setting for all existing tenants
INSERT INTO system_settings (tenant_id, setting_key, setting_value, description)
SELECT tenant_id, 'notification_retention_days', '90', '通知保留天數（已讀通知超過此天數將自動歸檔）'
FROM tenant
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings ss
    WHERE ss.tenant_id = tenant.tenant_id AND ss.setting_key = 'notification_retention_days'
);
