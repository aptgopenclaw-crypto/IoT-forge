-- =============================================================================
-- V101: 設備模板初始種子資料
--
-- 為預設 tenant（TENANT_DEFAULT）建立 6 種設備類型的空模板。
-- 使用者後續可在 UI 上透過 JSON Schema 編輯器定義各類型的 attributes 結構。
-- =============================================================================

-- 僅在無資料時插入，避免覆蓋既有設定
INSERT INTO device_templates (tenant_id, device_type, schema, version, created_by, created_at, updated_at)
SELECT 'TENANT_DEFAULT', dt.device_type, '{}'::jsonb, 1, 'system', now(), now()
FROM (VALUES
    ('STREET_LIGHT'),
    ('LUMINAIRE'),
    ('PANEL_BOX'),
    ('CONTROLLER'),
    ('POWER_EQUIPMENT'),
    ('ATTACHMENT')
) AS dt(device_type)
WHERE NOT EXISTS (
    SELECT 1 FROM device_templates t
    WHERE t.tenant_id = 'TENANT_DEFAULT' AND t.device_type = dt.device_type
);
