-- =============================================================================
-- V66: 為 super_admin 平台 shell 新增「系統管理」子目錄（選單管理 + 系統設定）
--
-- 背景：
--   super_admin 以 PLATFORM token 登入後，目前僅看到「平台管理 > 租戶管理」。
--   選單管理（建立/編輯全域 menu 結構）和系統設定屬於平台層管理功能，
--   應在 PLATFORM scope 下可見。
--
--   本 migration：
--     1. 在 平台管理 (menu_id=100) 下新增 DIRECTORY「系統管理」(menu_id=102)
--     2. 在其下新增 PAGE「選單管理」(menu_id=103) 和「系統設定」(menu_id=104)
--     3. 將對應 permission 綁定到 ROLE_SUPER_ADMIN
-- =============================================================================

-- ── 1. 系統管理 DIRECTORY (PLATFORM scope) ──────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_path, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (102, 100, '系統管理', 'DIRECTORY', '/platform/system', 'Setting', 20, true, 'PLATFORM')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 2. 選單管理 PAGE ────────────────────────────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (103, 102, '選單管理', 'PAGE', 'PlatformMenuManage', '/platform/menus',
     'views/admin/menu/MenuManageView.vue', 'MENU_LIST', 'Menu', 10, true, 'PLATFORM')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 3. 系統設定 PAGE ────────────────────────────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (104, 102, '系統設定', 'PAGE', 'PlatformSystemSettings', '/platform/system-settings',
     'views/admin/setting/SystemSettingsView.vue', 'SYSTEM_SETTINGS_VIEW', 'Setting', 20, true, 'PLATFORM')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 4. 綁定權限到 ROLE_SUPER_ADMIN ──────────────────────────────────────────
--   選單管理需要: MENU_LIST, MENU_CREATE, MENU_UPDATE, MENU_DELETE
--   系統設定需要: SYSTEM_SETTINGS_VIEW, SYSTEM_SETTINGS_MANAGE

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT 'ROLE_SUPER_ADMIN', p.permission_id, NULL
FROM permissions p
WHERE p.code IN (
    'MENU_LIST',
    'MENU_CREATE',
    'MENU_UPDATE',
    'MENU_DELETE',
    'SYSTEM_SETTINGS_VIEW',
    'SYSTEM_SETTINGS_MANAGE'
)
ON CONFLICT DO NOTHING;

-- ── 5. Reset sequence ───────────────────────────────────────────────────────

SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 104));
