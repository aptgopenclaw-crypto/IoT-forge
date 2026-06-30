-- =============================================
-- V29: 系統公告發佈
-- =============================================

-- 公告主表
CREATE TABLE announcements (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       VARCHAR(50)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    scope           VARCHAR(20)  NOT NULL DEFAULT 'ALL',
    pinned          BOOLEAN      NOT NULL DEFAULT false,
    publish_at      TIMESTAMP,
    expire_at       TIMESTAMP,
    created_by      VARCHAR(50),
    created_by_name VARCHAR(100),
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_announcements_tenant_id ON announcements(tenant_id);
CREATE INDEX idx_announcements_status ON announcements(tenant_id, status, publish_at DESC);

-- 公告目標部門（多對多）：scope=DEPT 時指定一或多個目標部門
CREATE TABLE announcement_depts (
    announcement_id BIGINT NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    dept_id         BIGINT NOT NULL REFERENCES dept_info(dept_id),
    PRIMARY KEY (announcement_id, dept_id)
);

CREATE INDEX idx_announcement_depts_dept ON announcement_depts(dept_id);

-- 已讀追蹤表
CREATE TABLE announcement_reads (
    id              BIGSERIAL   PRIMARY KEY,
    announcement_id BIGINT      NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    user_id         VARCHAR(50) NOT NULL,
    read_at         TIMESTAMP   NOT NULL DEFAULT now(),
    UNIQUE (announcement_id, user_id)
);

CREATE INDEX idx_announcement_reads_user ON announcement_reads(user_id);

-- =============================================
-- Menu: 公告管理 (管理頁 menu_id=33) + 公告欄 (前台 menu_id=34)
-- =============================================
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(33, 10, '公告管理', 'PAGE', 'AnnouncementManagement', '/admin/system/announcements', 'views/admin/announcement/AnnouncementManagementView.vue', 'ANNOUNCEMENT_VIEW', 'ChatDotRound', 50, true),
(34, NULL, '公告欄', 'PAGE', 'Announcements', '/announcements', 'views/announcement/AnnouncementListView.vue', NULL, 'Notification', 0, true)
ON CONFLICT (menu_id) DO NOTHING;

SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 34));

-- =============================================
-- Permissions
-- =============================================
INSERT INTO permissions (permission_id, code, name, group_name, sort_order)
VALUES
('PERM_ANNOUNCEMENT_VIEW', 'ANNOUNCEMENT_VIEW', '檢視公告管理頁', '系統公告', 1),
('PERM_ANNOUNCEMENT_MANAGE', 'ANNOUNCEMENT_MANAGE', '管理系統公告', '系統公告', 2)
ON CONFLICT (code) DO NOTHING;

-- =============================================
-- Role binding: ADMIN + DEPT_ADMIN
-- =============================================
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code IN ('ANNOUNCEMENT_VIEW', 'ANNOUNCEMENT_MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'DEPT_ADMIN' AND p.code IN ('ANNOUNCEMENT_VIEW', 'ANNOUNCEMENT_MANAGE')
ON CONFLICT DO NOTHING;
