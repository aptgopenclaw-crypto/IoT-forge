-- V29__seed_vms_menus.sql
-- The actual menu seed data depends on the menu system format.
-- Insert a parent VMS menu and 5 child menus referencing the route paths.
-- Use the same pattern as existing seed migrations.
INSERT INTO sys_menu (tenant_id, parent_id, name, permission, route_path, type, sort_order, icon, component, is_frame, is_cache, visible, status, created_by, created_at)
VALUES
(0, NULL, 'VMS', 'vms:manage', NULL, 'M', 1, 'video', NULL, 0, 1, 1, 1, 'system', NOW()),
(0, (SELECT id FROM sys_menu WHERE name = 'VMS' AND parent_id IS NULL), '即時播放', 'vms:live', '/vms/live', 'C', 1, 'video', 'vms/VmsLiveView', 0, 1, 1, 1, 'system', NOW()),
(0, (SELECT id FROM sys_menu WHERE name = 'VMS' AND parent_id IS NULL), '歷史播放', 'vms:playback', '/vms/playback', 'C', 2, 'time', 'vms/VmsPlaybackView', 0, 1, 1, 1, 'system', NOW()),
(0, (SELECT id FROM sys_menu WHERE name = 'VMS' AND parent_id IS NULL), 'VMS 伺服器', 'vms:server', '/vms/servers', 'C', 3, 'server', 'vms/VmsServerManageView', 0, 1, 1, 1, 'system', NOW()),
(0, (SELECT id FROM sys_menu WHERE name = 'VMS' AND parent_id IS NULL), '攝影機管理', 'vms:camera', '/vms/cameras', 'C', 4, 'camera', 'vms/VmsCameraManageView', 0, 1, 1, 1, 'system', NOW()),
(0, (SELECT id FROM sys_menu WHERE name = 'VMS' AND parent_id IS NULL), '串流記錄', 'vms:stream-log', '/vms/stream-logs', 'C', 5, 'document', 'vms/VmsStreamLogView', 0, 1, 1, 1, 'system', NOW());
