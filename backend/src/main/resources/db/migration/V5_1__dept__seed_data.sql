-- ============================================================
-- DEPT Flyway V5_1: Seed default departments + role data_scope
-- ============================================================

-- 1. Seed departments for TENANT_A (4 depts: root + 3 children)
INSERT INTO dept_info (tenant_id, pid, dept_name, dept_sort, status, hierarchy_path, create_by)
VALUES ('TENANT_A', NULL, '總公司', 1, 1, NULL, 'system');

-- Get the root dept_id and build children
INSERT INTO dept_info (tenant_id, pid, dept_name, dept_sort, status, hierarchy_path, create_by)
VALUES ('TENANT_A', 1, '研發部', 1, 1, NULL, 'system');

INSERT INTO dept_info (tenant_id, pid, dept_name, dept_sort, status, hierarchy_path, create_by)
VALUES ('TENANT_A', 1, '營運部', 2, 1, NULL, 'system');

INSERT INTO dept_info (tenant_id, pid, dept_name, dept_sort, status, hierarchy_path, create_by)
VALUES ('TENANT_A', 1, '管理部', 3, 1, NULL, 'system');

-- 2. Set hierarchy_path (root = /dept_id/, children = /parent_id/dept_id/)
UPDATE dept_info SET hierarchy_path = '/' || dept_id || '/' WHERE pid IS NULL;
UPDATE dept_info d SET hierarchy_path = (
    SELECT '/' || p.dept_id || '/' || d.dept_id || '/'
    FROM dept_info p WHERE p.dept_id = d.pid
) WHERE d.pid IS NOT NULL;

-- 3. Update user_tenant_mapping with new dept references
-- user-admin-001 → 總公司 (dept_id=1)
UPDATE user_tenant_mapping SET dept_id = 1 WHERE user_id = 'user-admin-001' AND tenant_id = 'TENANT_A';
-- user-viewer-001 → 研發部 (dept_id=2)
UPDATE user_tenant_mapping SET dept_id = 2 WHERE user_id = 'user-viewer-001' AND tenant_id = 'TENANT_A';

-- 4. Set role data_scope defaults
UPDATE roles SET data_scope = 'ALL' WHERE code IN ('SUPER_ADMIN', 'ADMIN');
UPDATE roles SET data_scope = 'THIS_LEVEL_AND_BELOW' WHERE code = 'MONITOR';
UPDATE roles SET data_scope = 'THIS_LEVEL' WHERE code IN ('OPERATOR', 'FIELD_USER', 'VIEWER');
