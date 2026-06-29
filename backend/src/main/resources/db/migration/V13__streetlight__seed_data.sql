-- ============================================================
-- Flyway V13: 台北市路燈平台 — 種子資料
-- Tenant: TPE_LIGHT / TP_ST_LIGHT / 台北市路燈
-- Roles:  ROLE_DEPT_ADMIN, ROLE_DEPT_USER
-- Depts:  臺北市政府工務局公燈處 + 5 子單位
-- Users:  9 位（密碼 = Test0123456!）
-- ============================================================

-- ============================================================
-- 1. Tenant
--    V1_1 已將 TENANT_A 建立為 KHH_WATER，現更新為台北市路燈
-- ============================================================
UPDATE tenant
SET tenant_code = 'TP_ST_LIGHT',
    tenant_name = '台北市路燈',
    update_time = NOW()
WHERE tenant_id = 'TENANT_A';

-- ============================================================
-- 2. Roles（新增兩個部門層級角色）
-- ============================================================
INSERT INTO roles (role_id, code, name, description, built_in, data_scope) VALUES
    ('ROLE_DEPT_ADMIN', 'DEPT_ADMIN', '部門管理者', '部門層級管理權限，可管理本部門及下級', true, 'THIS_LEVEL_AND_BELOW'),
    ('ROLE_DEPT_USER',  'DEPT_USER',  '部門使用者', '部門一般操作權限，僅限本層級',       true, 'THIS_LEVEL')
ON CONFLICT (role_id) DO NOTHING;

-- ============================================================
-- 3. Role Permissions（為新角色指派權限）
-- ============================================================
-- DEPT_ADMIN：用戶管理 + 部門檢視 + 設備全功能 + 稽核檢視
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT 'ROLE_DEPT_ADMIN', p.permission_id, NULL
FROM permissions p
WHERE p.code IN (
    'USER_LIST', 'USER_CREATE', 'USER_UPDATE', 'USER_DISABLE',
    'DEPT_LIST', 'DEPT_CREATE', 'DEPT_UPDATE',
    'DEVICE_VIEW', 'DEVICE_CREATE', 'DEVICE_UPDATE',
    'AUDIT_LIST'
)
ON CONFLICT DO NOTHING;

-- DEPT_USER：唯讀 + 設備基本操作
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT 'ROLE_DEPT_USER', p.permission_id, NULL
FROM permissions p
WHERE p.code IN (
    'USER_LIST',
    'DEPT_LIST',
    'DEVICE_VIEW', 'DEVICE_CREATE',
    'AUDIT_LIST'
)
ON CONFLICT DO NOTHING;

-- ============================================================
-- 4. Departments（臺北市政府工務局公燈處 + 5 子單位）
-- ============================================================

-- Root
INSERT INTO dept_info (tenant_id, pid, dept_name, dept_sort, status, create_by)
VALUES ('TENANT_A', NULL, '臺北市政府工務局公燈處', 1, 1, 'system');

-- Children（pid 指向 root）
INSERT INTO dept_info (tenant_id, pid, dept_name, dept_sort, status, create_by)
VALUES
    ('TENANT_A', (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND pid IS NULL AND dept_name = '臺北市政府工務局公燈處'), '第一分隊（北區）',   1, 1, 'system'),
    ('TENANT_A', (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND pid IS NULL AND dept_name = '臺北市政府工務局公燈處'), '第二分隊（南區）',   2, 1, 'system'),
    ('TENANT_A', (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND pid IS NULL AND dept_name = '臺北市政府工務局公燈處'), '工程股',            3, 1, 'system'),
    ('TENANT_A', (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND pid IS NULL AND dept_name = '臺北市政府工務局公燈處'), '行政股',            4, 1, 'system'),
    ('TENANT_A', (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND pid IS NULL AND dept_name = '臺北市政府工務局公燈處'), '智慧路燈管理中心',   5, 1, 'system');

-- hierarchy_path
UPDATE dept_info
SET hierarchy_path = '/' || dept_id || '/'
WHERE tenant_id = 'TENANT_A' AND pid IS NULL;

UPDATE dept_info d
SET hierarchy_path = (
    SELECT '/' || p.dept_id || '/' || d.dept_id || '/'
    FROM dept_info p WHERE p.dept_id = d.pid
)
WHERE d.tenant_id = 'TENANT_A' AND d.pid IS NOT NULL;

-- ============================================================
-- 5. Users（密碼 = Test0123456!）
-- ============================================================
INSERT INTO users (user_id, email, password_hash, display_name) VALUES
    ('u-tpe-admin',  'admin@tpe-light.gov.tw',      '$2a$10$kRxROhoc8z/4urhjvkB5x.uHq7hu3hO6QuABUCkEivNGeXnQIWbj6', '系統管理員'),
    ('u-squad1-mgr', 'squad1-mgr@tpe-light.gov.tw', '$2a$10$kRxROhoc8z/4urhjvkB5x.uHq7hu3hO6QuABUCkEivNGeXnQIWbj6', '北區分隊長 李明華'),
    ('u-squad1-off1','squad1-a@tpe-light.gov.tw',   '$2a$10$kRxROhoc8z/4urhjvkB5x.uHq7hu3hO6QuABUCkEivNGeXnQIWbj6', '北區承辦 張志遠'),
    ('u-squad1-off2','squad1-b@tpe-light.gov.tw',   '$2a$10$kRxROhoc8z/4urhjvkB5x.uHq7hu3hO6QuABUCkEivNGeXnQIWbj6', '北區承辦 王美玲'),
    ('u-squad2-mgr', 'squad2-mgr@tpe-light.gov.tw', '$2a$10$kRxROhoc8z/4urhjvkB5x.uHq7hu3hO6QuABUCkEivNGeXnQIWbj6', '南區分隊長 陳國強'),
    ('u-squad2-off1','squad2-a@tpe-light.gov.tw',   '$2a$10$kRxROhoc8z/4urhjvkB5x.uHq7hu3hO6QuABUCkEivNGeXnQIWbj6', '南區承辦 林雅琪'),
    ('u-eng-mgr',   'eng-mgr@tpe-light.gov.tw',    '$2a$10$kRxROhoc8z/4urhjvkB5x.uHq7hu3hO6QuABUCkEivNGeXnQIWbj6', '工程股長 黃建中'),
    ('u-eng-off1',  'eng-a@tpe-light.gov.tw',       '$2a$10$kRxROhoc8z/4urhjvkB5x.uHq7hu3hO6QuABUCkEivNGeXnQIWbj6', '工程承辦 吳佳穎'),
    ('u-adm-mgr',   'adm-mgr@tpe-light.gov.tw',    '$2a$10$kRxROhoc8z/4urhjvkB5x.uHq7hu3hO6QuABUCkEivNGeXnQIWbj6', '行政股長 鄒志明')
ON CONFLICT (user_id) DO NOTHING;

-- ============================================================
-- 6. User-Tenant Mapping
--    data_scope 由角色決定：
--      ROLE_ADMIN      = ALL
--      ROLE_DEPT_ADMIN = THIS_LEVEL_AND_BELOW
--      ROLE_DEPT_USER  = THIS_LEVEL
-- ============================================================

-- 公燈處（根）— 系統管理員 (ROLE_ADMIN → ALL)
INSERT INTO user_tenant_mapping (user_id, tenant_id, role_id, dept_id)
VALUES (
    'u-tpe-admin', 'TENANT_A', 'ROLE_ADMIN',
    (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND dept_name = '臺北市政府工務局公燈處')
);

-- 第一分隊（北區）— 分隊長 (ROLE_DEPT_ADMIN → THIS_LEVEL_AND_BELOW)
INSERT INTO user_tenant_mapping (user_id, tenant_id, role_id, dept_id)
VALUES (
    'u-squad1-mgr', 'TENANT_A', 'ROLE_DEPT_ADMIN',
    (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND dept_name = '第一分隊（北區）')
);

-- 北區承辦 (ROLE_DEPT_USER → THIS_LEVEL)
INSERT INTO user_tenant_mapping (user_id, tenant_id, role_id, dept_id)
VALUES (
    'u-squad1-off1', 'TENANT_A', 'ROLE_DEPT_USER',
    (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND dept_name = '第一分隊（北區）')
);

INSERT INTO user_tenant_mapping (user_id, tenant_id, role_id, dept_id)
VALUES (
    'u-squad1-off2', 'TENANT_A', 'ROLE_DEPT_USER',
    (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND dept_name = '第一分隊（北區）')
);

-- 第二分隊（南區）— 分隊長 (ROLE_DEPT_ADMIN → THIS_LEVEL_AND_BELOW)
INSERT INTO user_tenant_mapping (user_id, tenant_id, role_id, dept_id)
VALUES (
    'u-squad2-mgr', 'TENANT_A', 'ROLE_DEPT_ADMIN',
    (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND dept_name = '第二分隊（南區）')
);

-- 南區承辦 (ROLE_DEPT_USER → THIS_LEVEL)
INSERT INTO user_tenant_mapping (user_id, tenant_id, role_id, dept_id)
VALUES (
    'u-squad2-off1', 'TENANT_A', 'ROLE_DEPT_USER',
    (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND dept_name = '第二分隊（南區）')
);

-- 工程股 — 股長 (ROLE_DEPT_ADMIN → THIS_LEVEL_AND_BELOW)
INSERT INTO user_tenant_mapping (user_id, tenant_id, role_id, dept_id)
VALUES (
    'u-eng-mgr', 'TENANT_A', 'ROLE_DEPT_ADMIN',
    (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND dept_name = '工程股')
);

-- 工程承辦 (ROLE_DEPT_USER → THIS_LEVEL)
INSERT INTO user_tenant_mapping (user_id, tenant_id, role_id, dept_id)
VALUES (
    'u-eng-off1', 'TENANT_A', 'ROLE_DEPT_USER',
    (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND dept_name = '工程股')
);

-- 行政股 — 股長 (ROLE_DEPT_ADMIN → THIS_LEVEL_AND_BELOW)
INSERT INTO user_tenant_mapping (user_id, tenant_id, role_id, dept_id)
VALUES (
    'u-adm-mgr', 'TENANT_A', 'ROLE_DEPT_ADMIN',
    (SELECT dept_id FROM dept_info WHERE tenant_id = 'TENANT_A' AND dept_name = '行政股')
);
