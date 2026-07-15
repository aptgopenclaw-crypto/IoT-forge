-- V30__seed_vms_permissions.sql
INSERT INTO permissions (permission_id, code, name, group_name, sort_order) VALUES
    ('PERM_VMS_LIVE',       'VMS_LIVE',       'VMS 即時播放', 'VMS', 1),
    ('PERM_VMS_PLAYBACK',   'VMS_PLAYBACK',   'VMS 歷史播放', 'VMS', 2),
    ('PERM_VMS_SERVER',     'VMS_SERVER',     'VMS 伺服器管理', 'VMS', 3),
    ('PERM_VMS_CAMERA',     'VMS_CAMERA',     'VMS 攝影機管理', 'VMS', 4),
    ('PERM_VMS_STREAM_LOG', 'VMS_STREAM_LOG', 'VMS 串流記錄', 'VMS', 5);

-- ROLE_ADMIN: full access
INSERT INTO role_permissions (role_id, permission_id, tenant_id) VALUES
    ('ROLE_ADMIN', 'PERM_VMS_LIVE',       NULL),
    ('ROLE_ADMIN', 'PERM_VMS_PLAYBACK',   NULL),
    ('ROLE_ADMIN', 'PERM_VMS_SERVER',     NULL),
    ('ROLE_ADMIN', 'PERM_VMS_CAMERA',     NULL),
    ('ROLE_ADMIN', 'PERM_VMS_STREAM_LOG', NULL);

-- ROLE_MONITOR: live + playback + stream log (read-only style)
INSERT INTO role_permissions (role_id, permission_id, tenant_id) VALUES
    ('ROLE_MONITOR', 'PERM_VMS_LIVE',       NULL),
    ('ROLE_MONITOR', 'PERM_VMS_PLAYBACK',   NULL),
    ('ROLE_MONITOR', 'PERM_VMS_STREAM_LOG', NULL);

-- ROLE_VIEWER: live only
INSERT INTO role_permissions (role_id, permission_id, tenant_id) VALUES
    ('ROLE_VIEWER', 'PERM_VMS_LIVE', NULL);
