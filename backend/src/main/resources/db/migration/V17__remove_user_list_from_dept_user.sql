-- DEPT_USER (部門使用者) should not have user management permissions.
-- Only ADMIN, SUPER_ADMIN, and DEPT_ADMIN can manage users.
DELETE FROM role_permissions
WHERE role_id = 'ROLE_DEPT_USER'
  AND permission_id = (SELECT permission_id FROM permissions WHERE code = 'USER_LIST');
