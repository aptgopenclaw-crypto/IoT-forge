-- V29__seed_vms_menus.sql
-- Sync the IDENTITY sequence with existing rows (pg_dump inserts explicit IDs without updating the sequence)
SELECT setval(pg_get_serial_sequence('menus', 'menu_id'), MAX(menu_id)) FROM menus;

INSERT INTO menus (parent_id, name, menu_type, route_path, component, permission_code, icon, sort_order, visible, keep_alive, scope)
VALUES (NULL, 'VMS', 'DIRECTORY', NULL, NULL, NULL, 'video', 50, true, false, 'TENANT');

INSERT INTO menus (parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, keep_alive, scope)
SELECT m.menu_id, child.name, child.menu_type, child.route_name, child.route_path, child.component, child.permission_code, child.icon, child.sort_order, true, false, 'TENANT'
FROM menus m
CROSS JOIN (VALUES
    ('即時播放',   'PAGE', 'VmsLive',        '/vms/live',         'views/vms/VmsLiveView.vue',         'VMS_LIVE',         'video',    10),
    ('歷史播放',   'PAGE', 'VmsPlayback',    '/vms/playback',     'views/vms/VmsPlaybackView.vue',     'VMS_PLAYBACK',     'time',     20),
    ('VMS 伺服器', 'PAGE', 'VmsServers',     '/vms/servers',      'views/vms/VmsServerManageView.vue', 'VMS_SERVER',       'server',   30),
    ('攝影機管理', 'PAGE', 'VmsCameras',     '/vms/cameras',      'views/vms/VmsCameraManageView.vue', 'VMS_CAMERA',       'camera',   40),
    ('串流記錄',   'PAGE', 'VmsStreamLogs',  '/vms/stream-logs',  'views/vms/VmsStreamLogView.vue',    'VMS_STREAM_LOG',   'document', 50)
) AS child(name, menu_type, route_name, route_path, component, permission_code, icon, sort_order)
WHERE m.name = 'VMS' AND m.parent_id IS NULL;
