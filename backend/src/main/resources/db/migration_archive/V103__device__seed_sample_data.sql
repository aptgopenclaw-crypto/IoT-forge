-- =============================================================================
-- V103: IoT 設備管理模組 — 測試種子資料
--
-- 適用 Tenant：T_D029426BA10C
-- 所屬部門：12（智慧路灯管理科）
-- =============================================================================

-- ── 電力迴路 ────────────────────────────────────────────────────────────────

INSERT INTO circuits (id, tenant_id, panel_box_device_id, circuit_number, circuit_name, taipower_account, usage_type, status)
VALUES
(1, 'T_D029426BA10C', NULL, 'CKT-12-A', '智慧路燈科A迴路', '07-7001-0001', 'LIGHTING', 'ACTIVE'),
(2, 'T_D029426BA10C', NULL, 'CKT-12-B', '智慧路燈科B迴路', '07-7001-0002', 'LIGHTING', 'ACTIVE'),
(3, 'T_D029426BA10C', NULL, 'CKT-12-C', '智慧路燈科C迴路', '07-7001-0003', 'LIGHTING', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- ── 標案契約 ────────────────────────────────────────────────────────────────

INSERT INTO contracts (id, tenant_id, contract_code, contract_name, budget_year, procurement_number, contractor_name, contractor_contact, asset_category, quantity, start_date, end_date, status)
VALUES
(1, 'T_D029426BA10C', 'C-115-001', '115年度智慧燈桿管理維護契約', 115, 'P-115-00001', '光明照明股份有限公司', '王經理 02-2771-0001', 'STREETLIGHT', 300, '2026-01-01', '2026-12-31', 'ACTIVE'),
(2, 'T_D029426BA10C', 'C-115-002', '115年度IoT控制器佈建契約', 115, 'P-115-00002', '遠傳電信股份有限公司', '陳專案 02-7723-5000', 'CONTROLLER', 500, '2026-03-01', '2027-02-28', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- ── 設備主表 ────────────────────────────────────────────────────────────────

-- 燈桿（POLE）：5 支，dept_id=12，circuit_id=1~3
INSERT INTO devices (id, tenant_id, device_type, device_code, device_name, twd97_x, twd97_y, lng, lat, elevation, dept_id, contract_id, property_owner, status, installed_at, parent_device_id, connectivity_type, network_config, circuit_id, attributes, created_by)
VALUES
(1, 'T_D029426BA10C', 'POLE', 'SL-12-001', '科內燈桿001', 303500.000, 2771300.000, 121.5200000, 25.0380000, 35.000, 12, 1, '臺北市政府', 'ACTIVE', '2026-01-15', NULL, 'NONE', '{}'::jsonb, 1,
 '{"height_m": 10, "material": "鍍鋅鋼", "arm_count": 1, "road_name": "智慧大道一段", "arm_length_m": 2.5, "foundation_type": "混凝土基座"}'::jsonb, 'system'),

(2, 'T_D029426BA10C', 'POLE', 'SL-12-002', '科內燈桿002', 303600.000, 2771400.000, 121.5210000, 25.0390000, 36.200, 12, 1, '臺北市政府', 'ACTIVE', '2026-01-15', NULL, 'NONE', '{}'::jsonb, 1,
 '{"height_m": 12, "material": "鍍鋅鋼", "arm_count": 2, "road_name": "智慧大道一段", "arm_length_m": 2.0, "foundation_type": "混凝土基座"}'::jsonb, 'system'),

(3, 'T_D029426BA10C', 'POLE', 'SL-12-003', '科內燈桿003', 303700.000, 2771500.000, 121.5220000, 25.0400000, 34.800, 12, 1, '臺北市政府', 'ACTIVE', '2026-02-01', NULL, 'NONE', '{}'::jsonb, 1,
 '{"height_m": 8, "material": "鋁合金", "arm_count": 1, "road_name": "智慧大道二段", "arm_length_m": 2.5, "foundation_type": "混凝土基座"}'::jsonb, 'system'),

(4, 'T_D029426BA10C', 'POLE', 'SL-12-004', '科內燈桿004', 303800.000, 2771600.000, 121.5230000, 25.0410000, 33.500, 12, 1, '臺北市政府', 'ACTIVE', '2026-02-01', NULL, 'NONE', '{}'::jsonb, 2,
 '{"height_m": 10, "material": "鍍鋅鋼", "arm_count": 1, "road_name": "智慧大道二段", "arm_length_m": 2.5, "foundation_type": "混凝土基座"}'::jsonb, 'system'),

(5, 'T_D029426BA10C', 'POLE', 'SL-12-005', '科內燈桿005', 303900.000, 2771700.000, 121.5240000, 25.0420000, 32.100, 12, 1, '臺北市政府', 'ACTIVE', '2026-03-01', NULL, 'NONE', '{}'::jsonb, 2,
 '{"height_m": 15, "material": "鍍鋅鋼", "arm_count": 2, "road_name": "智慧大道三段", "arm_length_m": 2.0, "foundation_type": "混凝土基座"}'::jsonb, 'system')
ON CONFLICT (id) DO NOTHING;

-- 燈具（LUMINAIRE）：每支燈桿下各掛 1 盞
INSERT INTO devices (id, tenant_id, device_type, device_code, device_name, dept_id, contract_id, status, installed_at, parent_device_id, circuit_id, mount_position, attributes, created_by)
VALUES
(11, 'T_D029426BA10C', 'LUMINAIRE', 'LM-12-001', '150W LED燈具A1', 12, 1, 'ACTIVE', '2026-01-20', 1, 1, 'ARM_1',
 '{"brand": "台達電", "model": "TL-LED-150", "wattage": 150, "ip_rating": "IP66", "beam_angle": 90, "color_temp_k": 4000, "light_source": "LED", "luminous_flux_lm": 19500}'::jsonb, 'system'),

(12, 'T_D029426BA10C', 'LUMINAIRE', 'LM-12-002', '200W LED燈具B1', 12, 1, 'ACTIVE', '2026-01-20', 2, 1, 'ARM_1',
 '{"brand": "飛利浦", "model": "BRP392", "wattage": 200, "ip_rating": "IP66", "beam_angle": 60, "color_temp_k": 4000, "light_source": "LED", "luminous_flux_lm": 26000}'::jsonb, 'system'),

(13, 'T_D029426BA10C', 'LUMINAIRE', 'LM-12-003', '100W LED燈具C1', 12, 1, 'ACTIVE', '2026-02-05', 3, 1, 'ARM_1',
 '{"brand": "歐司朗", "model": "STREETLIGHT-100", "wattage": 100, "ip_rating": "IP66", "beam_angle": 90, "color_temp_k": 3000, "light_source": "LED", "luminous_flux_lm": 13000}'::jsonb, 'system'),

(14, 'T_D029426BA10C', 'LUMINAIRE', 'LM-12-004', '200W LED燈具D1', 12, 1, 'ACTIVE', '2026-02-05', 4, 2, 'ARM_1',
 '{"brand": "東貝光電", "model": "SL-PRO-200", "wattage": 200, "ip_rating": "IP66", "beam_angle": 120, "color_temp_k": 5000, "light_source": "LED", "luminous_flux_lm": 26000}'::jsonb, 'system'),

(15, 'T_D029426BA10C', 'LUMINAIRE', 'LM-12-005', '250W LED燈具E1', 12, 1, 'ACTIVE', '2026-03-05', 5, 2, 'ARM_1',
 '{"brand": "飛利浦", "model": "TL-LED-250", "wattage": 250, "ip_rating": "IP66", "beam_angle": 60, "color_temp_k": 4000, "light_source": "LED", "luminous_flux_lm": 32500}'::jsonb, 'system')
ON CONFLICT (id) DO NOTHING;

-- 控制器（CONTROLLER）：每支燈桿各掛 1 顆
INSERT INTO devices (id, tenant_id, device_type, device_code, device_name, dept_id, contract_id, status, installed_at, parent_device_id, circuit_id, connectivity_type, network_config, mount_position, firmware_version, attributes, created_by)
VALUES
(21, 'T_D029426BA10C', 'CONTROLLER', 'CT-12-001', '智慧控制器A1', 12, 2, 'ACTIVE', '2026-01-25', 1, 1, 'GATEWAY',
 '{"port": 8443, "protocol": "NB-IoT", "ip_address": "10.0.1.101"}'::jsonb, 'CTRL_1', '2.4.3',
 '{"model": "CHT-SLC100", "sim_iccid": "89880000000000000001", "manufacturer": "中華電信", "dimming_support": true, "schedule_enabled": true}'::jsonb, 'system'),

(22, 'T_D029426BA10C', 'CONTROLLER', 'CT-12-002', '智慧控制器B1', 12, 2, 'ACTIVE', '2026-01-25', 2, 1, 'DIRECT',
 '{"port": 502, "protocol": "LoRa", "ip_address": "10.0.1.102"}'::jsonb, 'CTRL_1', '2.3.6',
 '{"model": "FET-SC200", "sim_iccid": "89880000000000000002", "manufacturer": "遠傳電信", "dimming_support": true, "schedule_enabled": true}'::jsonb, 'system'),

(23, 'T_D029426BA10C', 'CONTROLLER', 'CT-12-003', '智慧控制器C1', 12, 2, 'ACTIVE', '2026-02-10', 3, 1, 'GATEWAY',
 '{"port": 5683, "protocol": "NB-IoT", "ip_address": "10.0.1.103"}'::jsonb, 'CTRL_1', '2.2.1',
 '{"model": "WISE-4610", "sim_iccid": "89880000000000000003", "manufacturer": "研華科技", "dimming_support": false, "schedule_enabled": true}'::jsonb, 'system'),

(24, 'T_D029426BA10C', 'CONTROLLER', 'CT-12-004', '智慧控制器D1', 12, 2, 'ACTIVE', '2026-02-10', 4, 2, 'GATEWAY',
 '{"port": 8443, "protocol": "4G", "ip_address": "10.0.1.104"}'::jsonb, 'CTRL_1', '2.4.5',
 '{"model": "CHT-SLC100", "sim_iccid": "89880000000000000004", "manufacturer": "中華電信", "dimming_support": true, "schedule_enabled": true}'::jsonb, 'system'),

(25, 'T_D029426BA10C', 'CONTROLLER', 'CT-12-005', '智慧控制器E1', 12, 2, 'ACTIVE', '2026-03-10', 5, 2, 'DIRECT',
 '{"port": 8080, "protocol": "LoRa", "ip_address": "10.0.1.105"}'::jsonb, 'CTRL_1', '2.0.8',
 '{"model": "FET-SC200", "sim_iccid": "89880000000000000005", "manufacturer": "研華科技", "dimming_support": true, "schedule_enabled": true}'::jsonb, 'system')
ON CONFLICT (id) DO NOTHING;

-- 變電箱（PANEL_BOX）：2 個，作為電力中繼
INSERT INTO devices (id, tenant_id, device_type, device_code, device_name, dept_id, contract_id, status, installed_at, connectivity_type, network_config, circuit_id, attributes, created_by)
VALUES
(31, 'T_D029426BA10C', 'PANEL_BOX', 'PB-12-001', '科內變電箱A', 12, 1, 'ACTIVE', '2025-12-01', 'NONE', '{}'::jsonb, 1,
 '{"capacity_a": 200, "voltage_v": 220, "breaker_count": 6}'::jsonb, 'system'),

(32, 'T_D029426BA10C', 'PANEL_BOX', 'PB-12-002', '科內變電箱B', 12, 1, 'ACTIVE', '2025-12-01', 'NONE', '{}'::jsonb, 2,
 '{"capacity_a": 150, "voltage_v": 220, "breaker_count": 4}'::jsonb, 'system')
ON CONFLICT (id) DO NOTHING;

-- 重置 auto-increment 序列
SELECT setval('devices_id_seq', GREATEST((SELECT MAX(id) FROM devices), 100));
SELECT setval('circuits_id_seq', GREATEST((SELECT MAX(id) FROM circuits), 10));
SELECT setval('contracts_id_seq', GREATEST((SELECT MAX(id) FROM contracts), 10));
