-- V13: 給 ROLE_DEPT_USER 補上 PREM_DEVICE_VIEW
-- 原因：DEPT_USER 可建立工單（WORK_ORDER_MANAGE），
--       但新增工單時需要瀏覽設備清單（GET /v1/auth/devices），
--       而該端點需要 DEVICE_VIEW 權限。

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES ('ROLE_DEPT_USER', 'PREM_DEVICE_VIEW', NULL)
ON CONFLICT DO NOTHING;
