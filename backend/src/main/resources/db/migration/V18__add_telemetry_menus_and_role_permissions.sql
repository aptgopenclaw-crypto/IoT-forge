-- V18: 補齊遙測資料主選單、三個子功能選單與角色權限
--
-- 需求：ROLE_ADMIN / ROLE_MONITOR / ROLE_DEPT_ADMIN / ROLE_DEPT_USER
-- 都需要使用：
--   1. 即時資料
--   2. 歷史資料查詢
--   3. 資料驗證規則
--
-- 做法：
--   * 將既有 menu_id=140 從「事件規則」改為主選單「遙測資料」
--   * menu_id=141 改名為「資料驗證規則」
--   * menu_id=142（觸發記錄）保留路由但隱藏，不列為主需求子選單
--   * 新增 menu_id=143 / 144 作為即時資料 / 歷史資料查詢
--   * 補齊四個角色所需的 DEVICE_VIEW / EVENT_RULE_VIEW 權限

-- ── 1. 主選單與既有子選單調整 ───────────────────────────────────────
UPDATE menus
SET name = '遙測資料',
    route_path = '/telemetry',
    icon = 'Monitor',
    sort_order = 47,
    update_time = NOW()
WHERE menu_id = 140;

UPDATE menus
SET name = '資料驗證規則',
    update_time = NOW()
WHERE menu_id = 141;

UPDATE menus
SET visible = FALSE,
    update_time = NOW()
WHERE menu_id = 142;

-- ── 2. 新增兩個遙測子選單 ───────────────────────────────────────────
INSERT INTO menus
    (menu_id, parent_id, name, menu_type, route_name, route_path, component,
     permission_code, icon, sort_order, visible, keep_alive, redirect, create_time, update_time, scope)
OVERRIDING SYSTEM VALUE VALUES
    (143, 140, '即時資料', 'PAGE', 'TelemetryLive', '/telemetry/live',
     'views/admin/telemetry/TelemetryLiveView.vue',
     'DEVICE_VIEW', 'DataLine', 10, TRUE, FALSE, NULL, NOW(), NULL, 'TENANT'),
    (144, 140, '歷史資料查詢', 'PAGE', 'TelemetryHistory', '/telemetry/history',
     'views/admin/telemetry/TelemetryHistoryView.vue',
     'DEVICE_VIEW', 'Histogram', 20, TRUE, FALSE, NULL, NOW(), NULL, 'TENANT')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 3. 補齊四個角色的權限 ───────────────────────────────────────────
-- DEVICE_VIEW（權限主鍵歷史上拼成 PREM_DEVICE_VIEW）
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES
    ('ROLE_MONITOR', 'PREM_DEVICE_VIEW', NULL),
    ('ROLE_DEPT_ADMIN', 'PREM_DEVICE_VIEW', NULL),
    ('ROLE_DEPT_USER', 'PREM_DEVICE_VIEW', NULL)
ON CONFLICT DO NOTHING;

-- EVENT_RULE_VIEW：四個角色都能查看資料驗證規則頁面
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES
    ('ROLE_MONITOR', 'PERM_EVENT_RULE_VIEW', NULL),
    ('ROLE_DEPT_ADMIN', 'PERM_EVENT_RULE_VIEW', NULL),
    ('ROLE_DEPT_USER', 'PERM_EVENT_RULE_VIEW', NULL)
ON CONFLICT DO NOTHING;
