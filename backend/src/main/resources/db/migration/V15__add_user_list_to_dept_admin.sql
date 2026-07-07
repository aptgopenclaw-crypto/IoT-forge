-- V15: 給 ROLE_DEPT_ADMIN 補上 USER_LIST 權限
-- 原因：PendingApprovalView 指派施工人員時需要呼叫 GET /v1/auth/users?roleId=ROLE_OPERATOR
--       該端點需要 USER_LIST 權限，而 DEPT_ADMIN 目前沒有。

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES ('ROLE_DEPT_ADMIN', 'PERM_USER_LIST', NULL)
ON CONFLICT DO NOTHING;
