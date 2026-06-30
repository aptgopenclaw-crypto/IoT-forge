-- Remove legacy USER_MANAGE permission that was superseded by granular
-- USER_LIST, USER_CREATE, USER_UPDATE, USER_DISABLE, USER_DELETE in V3_1/V33.

DELETE FROM role_permissions WHERE permission_id = 'PERM_USER_MANAGE';
DELETE FROM permissions WHERE permission_id = 'PERM_USER_MANAGE';
