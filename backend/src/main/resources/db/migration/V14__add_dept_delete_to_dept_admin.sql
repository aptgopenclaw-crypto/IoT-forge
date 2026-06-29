-- ============================================================
-- Flyway V14: 補 DEPT_DELETE 權限給 ROLE_DEPT_ADMIN
-- ============================================================
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT 'ROLE_DEPT_ADMIN', p.permission_id, NULL
FROM permissions p
WHERE p.code = 'DEPT_DELETE'
ON CONFLICT DO NOTHING;
