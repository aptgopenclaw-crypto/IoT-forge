-- =============================================================================
-- V54: Add audit_retention_days setting for each tenant (default 180 days)
-- =============================================================================

INSERT INTO system_settings (tenant_id, setting_key, setting_value, description)
SELECT tenant_id, 'audit_retention_days', '180', '稽核日誌保留天數'
FROM tenant
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings ss
    WHERE ss.tenant_id = tenant.tenant_id AND ss.setting_key = 'audit_retention_days'
);
