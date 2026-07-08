-- V20: 建立事件管理主選單，重新組織事件規則相關選單並設定角色權限
--
-- 需求：
--   新增頂層主選單「事件管理」，包含：
--     1. 事件規則定義 (ROLE_ADMIN only)
--     2. 觸發記錄     (ROLE_ADMIN / DEPT_ADMIN / DEPT_USER)
--     3. 通知設定     (ROLE_ADMIN only，placeholder)
--
-- 背景：
--   * V7 建立 event_rule / event_rule_trigger_log 資料表，並以 menu_id=140~142
--     建置「事件規則」主選單及子選單（規則定義、觸發記錄）
--   * V18 將 menu_id=140 更名為「遙測資料」、menu_id=141 更名「資料驗證規則」、
--     menu_id=142 隱藏
--   * 本次將既有選單重新組織至全新「事件管理」主選單下
--
-- 做法：
--   * 新增 DIRECTORY menu_id=145「事件管理」(path=/event-rules)
--   * 將 menu_id=141 從遙測資料移至事件管理下，更名「事件規則定義」、權限改為管理
--   * 將 menu_id=142 從遙測資料移至事件管理下，恢復可見
--   * 新增 menu_id=148 「通知設定」(placeholder)
--   * 補齊 DEPT_ADMIN / DEPT_USER 的 EVENT_RULE_VIEW 權限（檢視觸發記錄）

-- ── 1. 新增主選單 DIRECTORY ───────────────────────────────────────
INSERT INTO menus
    (menu_id, parent_id, name, menu_type, route_name, route_path, component,
     permission_code, icon, sort_order, visible, keep_alive, redirect, create_time, update_time, scope)
OVERRIDING SYSTEM VALUE VALUES
    (145, NULL, '事件管理', 'DIRECTORY', NULL, '/event-rules', NULL,
     NULL, 'AlarmClock', 48, TRUE, FALSE, NULL, NOW(), NULL, 'TENANT');

-- ── 2. 移動既有選單至事件管理下 ───────────────────────────────────
-- menu_id=141: 原「資料驗證規則」(telemetry下) → 更名「事件規則定義」改掛事件管理
UPDATE menus
SET parent_id = 145,
    name = '事件規則定義',
    permission_code = 'EVENT_RULE_MANAGE',
    icon = 'AlarmClock',
    update_time = NOW()
WHERE menu_id = 141;

-- menu_id=142: 原隱藏「觸發記錄」(telemetry下) → 恢復可見、改掛事件管理
UPDATE menus
SET parent_id = 145,
    visible = TRUE,
    update_time = NOW()
WHERE menu_id = 142;

-- ── 3. 新增「通知設定」子選單 (placeholder) ────────────────────────
INSERT INTO menus
    (menu_id, parent_id, name, menu_type, route_name, route_path, component,
     permission_code, icon, sort_order, visible, keep_alive, redirect, create_time, update_time, scope)
OVERRIDING SYSTEM VALUE VALUES
    (148, 145, '通知設定', 'PAGE', 'EventRuleNotification', '/event-rules/notifications',
     'views/admin/eventrule/EventRuleNotificationView.vue',
     'EVENT_RULE_MANAGE', 'Message', 30, TRUE, FALSE, NULL, NOW(), NULL, 'TENANT');

-- ── 4. 補齊角色權限 ───────────────────────────────────────────────
-- DEPT_ADMIN、DEPT_USER 可檢視觸發記錄（EVENT_RULE_VIEW）
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES
    ('ROLE_DEPT_ADMIN', 'PERM_EVENT_RULE_VIEW', NULL),
    ('ROLE_DEPT_USER', 'PERM_EVENT_RULE_VIEW', NULL)
ON CONFLICT DO NOTHING;
