-- V21: VMS (Video Management System) 核心資料表 + 權限 + 選單
-- package: com.taipei.iot.vms
--
-- 依賴: V1 baseline (permissions, role_permissions, menus tables)
-- 向前相容: schema-agnostic，不指定 schema 前綴

-- ── 1. VMS 伺服器設定 ───────────────────────────────────────────
CREATE TABLE vms_servers (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(50)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    vms_type      VARCHAR(30)  NOT NULL CHECK (vms_type IN ('NX_WITNESS', 'MILESTONE', 'AXXON')),
    base_url      VARCHAR(500) NOT NULL,
    auth_type     VARCHAR(20)  NOT NULL DEFAULT 'BASIC' CHECK (auth_type IN ('BASIC', 'TOKEN', 'CERT')),
    auth_username VARCHAR(100),
    auth_password VARCHAR(255),
    api_token     VARCHAR(500),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP
);

CREATE INDEX idx_vms_servers_tenant ON vms_servers (tenant_id);

-- ── 2. 攝影機映射表 ─────────────────────────────────────────────
CREATE TABLE vms_cameras (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(50)  NOT NULL,
    server_id       BIGINT       NOT NULL REFERENCES vms_servers(id) ON DELETE CASCADE,
    vms_camera_id   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(200),
    device_id       BIGINT,
    rtsp_url        VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ONLINE' CHECK (status IN ('ONLINE', 'OFFLINE', 'ERROR')),
    metadata        JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    CONSTRAINT uq_vms_cameras_server_camera UNIQUE (server_id, vms_camera_id)
);

CREATE INDEX idx_vms_cameras_tenant   ON vms_cameras (tenant_id);
CREATE INDEX idx_vms_cameras_server   ON vms_cameras (server_id);
CREATE INDEX idx_vms_cameras_status   ON vms_cameras (status);

-- ── 3. VMS 事件紀錄（來自 VMS webhook） ──────────────────────────
CREATE TABLE vms_camera_events (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(50)  NOT NULL,
    camera_id   BIGINT       NOT NULL REFERENCES vms_cameras(id) ON DELETE CASCADE,
    event_type  VARCHAR(50)  NOT NULL,
    payload     JSONB,
    occurred_at TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vms_events_tenant_camera ON vms_camera_events (tenant_id, camera_id, occurred_at DESC);
CREATE INDEX idx_vms_events_occurred_at   ON vms_camera_events (occurred_at DESC);

-- ── 4. 權限 ──────────────────────────────────────────────────────
INSERT INTO permissions (permission_id, code, name, group_name, sort_order)
VALUES
    ('PERM_VMS_VIEW',   'VMS_VIEW',   '即時影像檢視', 'VMS', 0),
    ('PERM_VMS_MANAGE', 'VMS_MANAGE', '影像系統管理', 'VMS', 1);

-- 授予 ROLE_ADMIN
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES
    ('ROLE_ADMIN', 'PERM_VMS_VIEW',   NULL),
    ('ROLE_ADMIN', 'PERM_VMS_MANAGE', NULL);

-- ── 5. 選單 ──────────────────────────────────────────────────────
-- 使用 menu_id=150~154 區段（V20 使用 145/148，保留 149 給未來事件管理擴充）
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component,
                   permission_code, icon, sort_order, visible, keep_alive, redirect, create_time, update_time, scope)
OVERRIDING SYSTEM VALUE VALUES
    -- 主目錄：影像監控
    (150, NULL, '影像監控', 'DIRECTORY', NULL, '/vms', NULL,
     NULL, 'Camera', 50, TRUE, FALSE, NULL, NOW(), NULL, 'TENANT'),
    -- 即時影像
    (151, 150, '即時影像', 'PAGE', 'VmsLive', '/vms/live',
     'views/admin/vms/VmsLiveView.vue', 'VMS_VIEW',
     'VideoCamera', 10, TRUE, FALSE, NULL, NOW(), NULL, 'TENANT'),
    -- 歷史回放
    (152, 150, '歷史回放', 'PAGE', 'VmsPlayback', '/vms/playback',
     'views/admin/vms/VmsPlaybackView.vue', 'VMS_VIEW',
     'ClockHistory', 20, TRUE, FALSE, NULL, NOW(), NULL, 'TENANT'),
    -- VMS 管理
    (153, 150, 'VMS 管理', 'PAGE', 'VmsServers', '/vms/servers',
     'views/admin/vms/VmsServerManagement.vue', 'VMS_MANAGE',
     'Setting', 30, TRUE, FALSE, NULL, NOW(), NULL, 'TENANT'),
    -- 事件記錄
    (154, 150, '事件記錄', 'PAGE', 'VmsEvents', '/vms/events',
     'views/admin/vms/VmsEventLogs.vue', 'VMS_VIEW',
     'AlarmClock', 40, TRUE, FALSE, NULL, NOW(), NULL, 'TENANT');
