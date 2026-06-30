-- =============================================================================
-- V77: 新增「資產異動」選單（TENANT scope）
--
-- 新增三個頁面供租戶使用者操作資產異動申請流程：
--   1. 新增申請  → /asset-transfer/create
--   2. 待審案件  → /asset-transfer/pending
--   3. 我的申請  → /asset-transfer/my
--
-- permission_code = NULL 表示所有已認證的租戶使用者均可見。
-- =============================================================================

-- ── 1. 資產異動 DIRECTORY (TENANT scope, top-level) ──────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_path, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (110, NULL, '資產異動', 'DIRECTORY', '/asset-transfer', 'ArrowLeftRight', 50, true, 'TENANT')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 2. 新增申請 PAGE ─────────────────────────────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (111, 110, '新增申請', 'PAGE', 'AssetTransferCreate', '/asset-transfer/create',
     'views/assetTransfer/AssetTransferCreateView.vue', NULL, 'FilePlus', 10, true, 'TENANT')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 3. 待審案件 PAGE ─────────────────────────────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (112, 110, '待審案件', 'PAGE', 'AssetTransferPending', '/asset-transfer/pending',
     'views/assetTransfer/AssetTransferPendingView.vue', NULL, 'ClipboardCheck', 20, true, 'TENANT')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 4. 我的申請 PAGE ─────────────────────────────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (113, 110, '我的申請', 'PAGE', 'AssetTransferMy', '/asset-transfer/my',
     'views/assetTransfer/AssetTransferPendingView.vue', NULL, 'FileText', 30, true, 'TENANT')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 5. Reset sequence ────────────────────────────────────────────────────────

SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 113));
