-- Auto-generated SQL script #202606121247
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_ADMIN' AND permission_id='PERM_DEVICE_CREATE' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_OPERATOR' AND permission_id='PERM_DEVICE_CREATE' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_DEPT_ADMIN' AND permission_id='PERM_DEVICE_CREATE' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_DEPT_USER' AND permission_id='PERM_DEVICE_CREATE' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_ADMIN' AND permission_id='PERM_DEVICE_CREATE' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_ADMIN' AND permission_id='PERM_DEVICE_UPDATE' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_OPERATOR' AND permission_id='PERM_DEVICE_UPDATE' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_DEPT_ADMIN' AND permission_id='PERM_DEVICE_UPDATE' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_ADMIN' AND permission_id='PERM_DEVICE_UPDATE' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_ADMIN' AND permission_id='PERM_DEVICE_VIEW' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_OPERATOR' AND permission_id='PERM_DEVICE_VIEW' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_VIEWER' AND permission_id='PERM_DEVICE_VIEW' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_DEPT_ADMIN' AND permission_id='PERM_DEVICE_VIEW' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_DEPT_USER' AND permission_id='PERM_DEVICE_VIEW' AND tenant_id IS NULL;
DELETE FROM iot_workflowdb.role_permissions
	WHERE role_id='ROLE_ADMIN' AND permission_id='PERM_DEVICE_VIEW' AND tenant_id IS NULL;


-- Auto-generated SQL script #202606121139
UPDATE iot_workflowdb.permissions
	SET "name"='場域更新'
	WHERE permission_id='PERM_TENANT_UPDATE';

UPDATE iot_workflowdb.permissions
	SET "name"='場域創建'
	WHERE permission_id='PERM_TENANT_CREATE';

-- Auto-generated SQL script #202606121141
UPDATE iot_workflowdb.permissions
	SET "name"='場域清單'
	WHERE permission_id='PERM_TENANT_LIST';

-- Auto-generated SQL script #202606121141
UPDATE iot_workflowdb.permissions
	SET "name"='帳號刪除'
	WHERE permission_id='PERM_USER_DELETE';

-- Auto-generated SQL script #202606121142
UPDATE iot_workflowdb.permissions
	SET "name"='權限指派'
	WHERE permission_id='PERM_ROLE_ASSIGN_PERM';

-- Auto-generated SQL script #202606121143
UPDATE iot_workflowdb.permissions
	SET "name"='角色創建'
	WHERE permission_id='PERM_ROLE_CREATE';


-- Auto-generated SQL script #202606121143
UPDATE iot_workflowdb.permissions
	SET "name"='角色創建'
	WHERE permission_id='PERM_ROLE_CREATE';


-- Auto-generated SQL script #202606121144
UPDATE iot_workflowdb.permissions
	SET "name"='角色列舉'
	WHERE permission_id='PERM_ROLE_LIST';

DELETE FROM iot_workflowdb.permissions
	WHERE permission_id='PERM_DEVICE_UPDATE';

  -- Auto-generated SQL script #202606121145
UPDATE iot_workflowdb.permissions
	SET "name"='歷史稽核紀錄'
	WHERE permission_id='PERM_AUDIT_LIST';

-- Auto-generated SQL script #202606121145
UPDATE iot_workflowdb.permissions
	SET "name"='選單刪除'
	WHERE permission_id='PERM_MENU_DELETE';

-- Auto-generated SQL script #202606121146
UPDATE iot_workflowdb.permissions
	SET "name"='選單更新'
	WHERE permission_id='PERM_MENU_UPDATE';


-- Auto-generated SQL script #202606121146
UPDATE iot_workflowdb.permissions
	SET "name"='選單新增'
	WHERE permission_id='PERM_MENU_CREATE';

-- Auto-generated SQL script #202606121146
UPDATE iot_workflowdb.permissions
	SET "name"='選單列舉'
	WHERE permission_id='PERM_MENU_LIST';

-- Auto-generated SQL script #202606121147
UPDATE iot_workflowdb.permissions
	SET "name"='部門刪除'
	WHERE permission_id='PERM_DEPT_DELETE';

-- Auto-generated SQL script #202606121147
UPDATE iot_workflowdb.permissions
	SET "name"='部門更新'
	WHERE permission_id='PERM_DEPT_UPDATE';

-- Auto-generated SQL script #202606121148
UPDATE iot_workflowdb.permissions
	SET "name"='部門新增'
	WHERE permission_id='PERM_DEPT_CREATE';

-- Auto-generated SQL script #202606121148
UPDATE iot_workflowdb.permissions
	SET "name"='部門列舉'
	WHERE permission_id='PERM_DEPT_LIST';


-- Auto-generated SQL script #202606121148
UPDATE iot_workflowdb.permissions
	SET "name"='帳號停用'
	WHERE permission_id='PERM_USER_DISABLE';

-- Auto-generated SQL script #202606121149
UPDATE iot_workflowdb.permissions
	SET "name"='帳號更新'
	WHERE permission_id='PERM_USER_UPDATE';


-- Auto-generated SQL script #202606121149
UPDATE iot_workflowdb.permissions
	SET "name"='帳號新增'
	WHERE permission_id='PERM_USER_CREATE';


-- Auto-generated SQL script #202606121149
UPDATE iot_workflowdb.permissions
	SET "name"='帳號列舉'
	WHERE permission_id='PERM_USER_LIST';

-- Auto-generated SQL script #202606121150
DELETE FROM iot_workflowdb.permissions
	WHERE permission_id='PERM_DEVICE_VIEW';

DELETE FROM iot_workflowdb.permissions
	WHERE permission_id='PERM_DEVICE_CREATE';
