-- =============================================================================
-- V70: 確保「用戶管理」選單存在並可見
--
-- 背景：
--   V3_1 建立 menu_id 1, 2, 3 時使用英文名稱。之後可能透過選單管理 UI 被
--   刪除或隱藏。本 migration 以 UPSERT 方式確保用戶管理選單恢復正確狀態，
--   使用中文名稱與現有 sidebar 風格一致。
-- =============================================================================

-- ── 1. 用戶管理 DIRECTORY (top-level, TENANT scope) ─────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_path, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (1, NULL, '用戶管理', 'DIRECTORY', '/admin/users', 'users', 5, true, 'TENANT')
ON CONFLICT (menu_id) DO UPDATE
    SET name = EXCLUDED.name,
        visible = true,
        scope = 'TENANT',
        parent_id = NULL;

-- ── 2. 用戶列表 PAGE ────────────────────────────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (2, 1, '用戶列表', 'PAGE', 'UserList', '/admin/users', 'views/admin/user/UserListView.vue', 'USER_LIST', 'users', 11, true, 'TENANT')
ON CONFLICT (menu_id) DO UPDATE
    SET name = EXCLUDED.name,
        parent_id = 1,
        visible = true,
        scope = 'TENANT',
        permission_code = 'USER_LIST',
        route_name = 'UserList',
        route_path = '/admin/users',
        component = 'views/admin/user/UserListView.vue';

-- ── 3. 新增用戶 PAGE ────────────────────────────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (3, 1, '新增用戶', 'PAGE', 'CreateUser', '/admin/users/create', 'views/admin/user/CreateUserView.vue', 'USER_CREATE', 'userplus', 12, true, 'TENANT')
ON CONFLICT (menu_id) DO UPDATE
    SET name = EXCLUDED.name,
        parent_id = 1,
        visible = true,
        scope = 'TENANT',
        permission_code = 'USER_CREATE',
        route_name = 'CreateUser',
        route_path = '/admin/users/create',
        component = 'views/admin/user/CreateUserView.vue';
