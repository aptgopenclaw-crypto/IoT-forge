-- =============================================================================
-- V59: Menu scope separation (PLATFORM / TENANT / PUBLIC)
--   Adds a `scope` column and seeds a top-level "平台管理" directory that
--   collects platform-only entries (currently 租戶管理). Existing platform-only
--   menus are relocated under this new parent.
--
--   Reference: 01-docs/new-feature/tenant/03-menu-separation.md
-- =============================================================================

-- ── 1. Add scope column ─────────────────────────────────────────────────────

ALTER TABLE menus
    ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'TENANT';

ALTER TABLE menus
    DROP CONSTRAINT IF EXISTS menus_scope_check;
ALTER TABLE menus
    ADD CONSTRAINT menus_scope_check CHECK (scope IN ('PLATFORM', 'TENANT', 'PUBLIC'));

CREATE INDEX IF NOT EXISTS idx_menus_scope ON menus(scope);

-- ── 2. New top-level directory: 平台管理 (PLATFORM) ─────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_path, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (100, NULL, '平台管理', 'DIRECTORY', '/platform', 'OfficeBuilding', 5, true, 'PLATFORM')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 3. New PAGE: 租戶管理 (under 平台管理) ─────────────────────────────────

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
    (101, 100, '租戶管理', 'PAGE', 'TenantManage', '/platform/tenants',
     'views/admin/tenant/TenantManageView.vue', 'PLATFORM_TENANT_MANAGE', 'OfficeBuilding', 10, true, 'PLATFORM')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 4. Relocate existing PLATFORM menus under id=100 ────────────────────────
--   menu_id=11  Menu Management        → PLATFORM (僅平台層編輯)
--   menu_id=37  認證方式設定            → PLATFORM (route also renamed)
--   (menu_id=36 already removed by V53; menu_id=34 公告欄 marked PUBLIC below)

UPDATE menus
   SET parent_id = 100,
       scope = 'PLATFORM',
       permission_code = 'PLATFORM_TENANT_MANAGE',
       route_path = '/platform/auth-config'
 WHERE menu_id = 37;

UPDATE menus
   SET parent_id = 100,
       scope = 'PLATFORM'
 WHERE menu_id = 11;

-- ── 5. Mark public-facing pages ─────────────────────────────────────────────
--   menu_id=34 公告欄 (Announcement) is reachable by every authenticated user
--   regardless of tenant role.

UPDATE menus SET scope = 'PUBLIC' WHERE menu_id = 34;

-- ── 6. Reset sequence ───────────────────────────────────────────────────────

SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 101));
