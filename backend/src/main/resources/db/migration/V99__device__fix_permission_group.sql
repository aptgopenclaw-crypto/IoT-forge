-- =============================================================================
-- V99: 修復 DEVICE_VIEW / DEVICE_CREATE group_name 與 sort_order
--
-- 背景：
--   V1_1 以 INSERT INTO permissions (...) VALUES (...) 寫入 DEVICE_VIEW /
--   DEVICE_CREATE，未包含 sort_order，且 group_name 為中文「設備管理」。
--   後續 V3_1 嘗試以 ON CONFLICT (code) DO NOTHING 補上 sort_order 並改為
--   英文 group_name "Device management"，但因 code 已存在而被跳過。
--   導致權限管理畫面中 DEVICE_VIEW / DEVICE_CREATE 獨立成另一群組「設備管理」，
--   而非與 DEVICE_UPDATE / DEVICE_DELETE / DEVICE_TEMPLATE_MANAGE 同群組。
--
-- 修正：
--   將 DEVICE_VIEW、DEVICE_CREATE 的 group_name 改為 "Device management"，
--   並補上 sort_order 50、51，使其與其他 device 權限並列。
-- =============================================================================

UPDATE permissions
SET group_name = 'Device management',
    sort_order = 50
WHERE code = 'DEVICE_VIEW'
  AND (group_name != 'Device management' OR sort_order IS NULL OR sort_order != 50);

UPDATE permissions
SET group_name = 'Device management',
    sort_order = 51
WHERE code = 'DEVICE_CREATE'
  AND (group_name != 'Device management' OR sort_order IS NULL OR sort_order != 51);
