-- =============================================================================
-- V68: 平台公告 — super_admin 可發布公告給所有場域（如系統維護通知）
--
-- 內容：
--   1. 建立 platform_announcements 表（無 tenant_id，跨場域）
--   2. 新增權限 PLATFORM_ANNOUNCEMENT_MANAGE
--   3. 將權限綁定至 SUPER_ADMIN 角色
--   4. 在「系統管理」(menu_id=102) 下新增「公告管理」選單
--   5. 新增 ErrorCode PLATFORM_ANNOUNCEMENT_NOT_FOUND
-- =============================================================================

-- ── 1. 建立 platform_announcements 表 ──────────────────────────────────────
CREATE TABLE platform_announcements (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title            VARCHAR(200)  NOT NULL,
    content          TEXT          NOT NULL,
    content_text     TEXT,
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    category         VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    publish_at       TIMESTAMP,
    expire_at        TIMESTAMP,
    created_by       VARCHAR(50),
    created_by_name  VARCHAR(100),
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP
);

COMMENT ON TABLE  platform_announcements                IS '平台級公告（跨場域，由 super_admin 管理）';
COMMENT ON COLUMN platform_announcements.status         IS 'DRAFT / PUBLISHED';
COMMENT ON COLUMN platform_announcements.category       IS 'SYSTEM / MAINTENANCE / GENERAL';
COMMENT ON COLUMN platform_announcements.content_text   IS '純文字版（供搜尋用）';
COMMENT ON COLUMN platform_announcements.publish_at     IS '排程發佈時間；null 表示立即發佈';
COMMENT ON COLUMN platform_announcements.expire_at      IS '失效時間；null 表示永不過期';

-- ── 2. 新增權限 ────────────────────────────────────────────────────────────
INSERT INTO permissions (permission_id, code, name, group_name, sort_order)
VALUES ('PERM_PLATFORM_ANNOUNCEMENT_MANAGE', 'PLATFORM_ANNOUNCEMENT_MANAGE',
        '管理平台公告', '平台管理', 10)
ON CONFLICT (code) DO NOTHING;

-- ── 3. 綁定至 SUPER_ADMIN ─────────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT 'ROLE_SUPER_ADMIN', p.permission_id, NULL
  FROM permissions p
 WHERE p.code = 'PLATFORM_ANNOUNCEMENT_MANAGE'
ON CONFLICT DO NOTHING;

-- ── 4. 新增選單：系統管理 > 公告管理 ──────────────────────────────────────
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path,
                   component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
(105, 102, '公告管理', 'PAGE', 'PlatformAnnouncementManage',
 '/platform/announcements',
 'views/platform/PlatformAnnouncementManageView.vue',
 'PLATFORM_ANNOUNCEMENT_MANAGE', 'ChatDotRound', 30, true, 'PLATFORM')
ON CONFLICT (menu_id) DO NOTHING;
