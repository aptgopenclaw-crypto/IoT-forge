-- Remove DEPT_LIST permission from ROLE_DEPT_USER so that
-- department users cannot view the department tree or see the
-- Department Management menu.
DELETE FROM role_permissions
WHERE role_id = 'ROLE_DEPT_USER'
  AND permission_id = (SELECT permission_id FROM permissions WHERE code = 'DEPT_LIST');
