-- V23: 新增 vms_cameras 選單項目 — 攝影機列表
-- menu_id=155，parent_id=150（影像監控），排序在即時影像(10)與歷史回放(20)之間

INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component,
                   permission_code, icon, sort_order, visible, keep_alive, redirect, create_time, update_time, scope)
OVERRIDING SYSTEM VALUE VALUES
    (155, 150, '攝影機列表', 'PAGE', 'VmsCameras', '/vms/cameras',
     'views/admin/vms/VmsCameraListView.vue', 'VMS_VIEW',
     'Camera', 15, TRUE, FALSE, NULL, NOW(), NULL, 'TENANT');
