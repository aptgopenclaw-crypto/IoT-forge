-- =============================================================================
-- V110: 派工管理 — 獨立頂層選單 + 角色專用子頁面
--
-- 選單結構：
--   130 派工管理 (DIRECTORY)  [WORK_ORDER_VIEW]
--     ├─ 131 所有工單 (PAGE)  [WORK_ORDER_VIEW]
--     ├─ 132 待審核   (PAGE)  [WORK_ORDER_APPROVE]
--     ├─ 133 施工任務 (PAGE)  [WORK_ORDER_VIEW]
--     └─ 134 待驗證   (PAGE)  [WORK_ORDER_APPROVE]
--
-- 角色可見性：
--   ADMIN / DEPT_ADMIN        → 所有子頁面
--   DEPT_USER                 → 所有工單、待驗證
--   OPERATOR                  → 所有工單、施工任務
-- =============================================================================

-- ── 1. 派工管理 DIRECTORY（sort_order 46，緊接在設備管理 45 之後）─────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_path, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (130, NULL, '派工管理', 'DIRECTORY', '/dispatch', 'ClipboardList', 46, true, 'TENANT')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 2. 子頁面 PAGE ────────────────────────────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (131, 130, '所有工單',     'PAGE', 'DispatchAll',      '/dispatch/all',
     'views/admin/device/AllWorkOrdersView.vue',    'WORK_ORDER_VIEW',    'List',      10, true, 'TENANT'),
    (132, 130, '待審核',       'PAGE', 'DispatchApproval', '/dispatch/pending-approval',
     'views/admin/device/PendingApprovalView.vue',  'WORK_ORDER_APPROVE', 'Check',     20, true, 'TENANT'),
    (133, 130, '施工任務',     'PAGE', 'DispatchMyTasks',  '/dispatch/my-tasks',
     'views/admin/device/MyTasksView.vue',           'WORK_ORDER_VIEW',   'Tools',     30, true, 'TENANT'),
    (134, 130, '待驗證',       'PAGE', 'DispatchReview',   '/dispatch/pending-review',
     'views/admin/device/PendingReviewView.vue',     'WORK_ORDER_APPROVE', 'Select',    40, true, 'TENANT')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 3. Reset sequence ─────────────────────────────────────────────────────

SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 134));
