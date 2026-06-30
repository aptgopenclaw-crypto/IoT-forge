-- =============================================================================
-- V97: IoT 設備管理模組 — Menu / Permission / Role 種子資料
--
-- 新增權限（idempotent）：
--   DEVICE_VIEW         (50)   — 檢視設備
--   DEVICE_CREATE       (51)   — 新增設備  ← 已存在
--   DEVICE_UPDATE       (52)   — 編輯設備  ← 已存在
--   DEVICE_DELETE       (53)   — 刪除設備
--   DEVICE_TEMPLATE_MANAGE (54)— 管理設備模板
--   WORK_ORDER_VIEW     (55)   — 檢視工單
--   WORK_ORDER_MANAGE   (56)   — 管理工單（指派/到場/維修/覆核/結案）
--   CIRCUIT_VIEW        (57)   — 檢視電力迴路
--   CIRCUIT_MANAGE      (58)   — 管理電力迴路
--   CONTRACT_VIEW       (59)   — 檢視標案契約
--   CONTRACT_MANAGE     (60)   — 管理標案契約
--
-- 角色綁定：
--   ADMIN               → 所有 device 權限
--   DEPT_ADMIN          → 檢視 + 管理類（不含模板管理）
--   DEPT_USER           → 檢視 + 工單通報
--   OPERATOR            → DEVICE_VIEW/CREATE/UPDATE（沿用既有）
--   VIEWER              → DEVICE_VIEW（沿用既有）
-- =============================================================================

-- ── 1. 新增 permission 定義（idempotent）───────────────────────────────────

INSERT INTO permissions (permission_id, code, name, group_name, sort_order) VALUES
  ('PERM_DEVICE_DELETE',         'DEVICE_DELETE',          '刪除設備',         'Device management',   53),
  ('PERM_DEVICE_TEMPLATE_MANAGE','DEVICE_TEMPLATE_MANAGE', '管理設備模板',     'Device management',   54),
  ('PERM_WORK_ORDER_VIEW',      'WORK_ORDER_VIEW',        '檢視工單',         'Work order management', 55),
  ('PERM_WORK_ORDER_MANAGE',    'WORK_ORDER_MANAGE',      '管理工單',         'Work order management', 56),
  ('PERM_CIRCUIT_VIEW',         'CIRCUIT_VIEW',            '檢視電力迴路',     'Circuit management',    57),
  ('PERM_CIRCUIT_MANAGE',       'CIRCUIT_MANAGE',          '管理電力迴路',     'Circuit management',    58),
  ('PERM_CONTRACT_VIEW',        'CONTRACT_VIEW',           '檢視標案契約',     'Contract management',   59),
  ('PERM_CONTRACT_MANAGE',      'CONTRACT_MANAGE',         '管理標案契約',     'Contract management',   60)
ON CONFLICT (code) DO NOTHING;

-- ── 2. 新增 Menu 選單（TENANT scope）───────────────────────────────────────

-- 2a. 設備管理 DIRECTORY
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_path, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (120, NULL, '設備管理', 'DIRECTORY', '/device', 'Monitor', 45, true, 'TENANT')
ON CONFLICT (menu_id) DO NOTHING;

-- 2b. 設備管理 PAGE
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (121, 120, '設備列表',     'PAGE', 'DeviceList',   '/device/list',
     'views/admin/device/DeviceListView.vue',     'DEVICE_VIEW',    'Cpu',     10, true, 'TENANT'),
    (122, 120, '設備模板',     'PAGE', 'DeviceTemplate','/device/templates',
     'views/admin/device/DeviceTemplateView.vue', 'DEVICE_TEMPLATE_MANAGE', 'FileJson', 20, true, 'TENANT'),
    (123, 120, '工單管理',     'PAGE', 'WorkOrderList','/device/work-orders',
     'views/admin/device/WorkOrderListView.vue',  'WORK_ORDER_VIEW', 'ClipboardList', 30, true, 'TENANT'),
    (124, 120, '電力迴路',     'PAGE', 'CircuitList',  '/device/circuits',
     'views/admin/device/CircuitListView.vue',    'CIRCUIT_VIEW',   'Zap',     40, true, 'TENANT'),
    (125, 120, '標案契約',     'PAGE', 'ContractList', '/device/contracts',
     'views/admin/device/ContractListView.vue',   'CONTRACT_VIEW',  'FileText', 50, true, 'TENANT')
ON CONFLICT (menu_id) DO NOTHING;

-- 2c. Reset sequence
SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 125));

-- ── 3. Role-Permission 綁定（global, tenant_id = NULL）──────────────────────

-- ADMIN: 所有 device 權限（idempotent）
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('DEVICE_DELETE', 'DEVICE_TEMPLATE_MANAGE',
                 'WORK_ORDER_VIEW', 'WORK_ORDER_MANAGE',
                 'CIRCUIT_VIEW', 'CIRCUIT_MANAGE',
                 'CONTRACT_VIEW', 'CONTRACT_MANAGE')
ON CONFLICT DO NOTHING;

-- DEPT_ADMIN: 檢視 + 管理（不含模板管理）
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'DEPT_ADMIN'
  AND p.code IN ('DEVICE_VIEW', 'DEVICE_CREATE', 'DEVICE_UPDATE', 'DEVICE_DELETE',
                 'WORK_ORDER_VIEW', 'WORK_ORDER_MANAGE',
                 'CIRCUIT_VIEW', 'CIRCUIT_MANAGE',
                 'CONTRACT_VIEW', 'CONTRACT_MANAGE')
ON CONFLICT DO NOTHING;

-- DEPT_USER: 檢視 + 工單通報
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'DEPT_USER'
  AND p.code IN ('DEVICE_VIEW',
                 'WORK_ORDER_VIEW', 'WORK_ORDER_MANAGE')
ON CONFLICT DO NOTHING;
