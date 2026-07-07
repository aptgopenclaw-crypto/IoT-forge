-- V9: 補齊 ROLE_ADMIN 遺漏的 PERM_WORKFLOW_DELEGATE_MANAGE
-- 原因：V2 seed 只將此 permission 指派給 ROLE_DEPT_USER / ROLE_DEPT_ADMIN / ROLE_PROPERTY_MANAGER，
--       導致 ROLE_ADMIN 無法對下層角色指派此 permission（超權防護攔截）。

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES ('ROLE_ADMIN', 'PERM_WORKFLOW_DELEGATE_MANAGE', NULL)
ON CONFLICT DO NOTHING;
