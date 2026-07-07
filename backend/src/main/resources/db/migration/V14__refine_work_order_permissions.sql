-- V14: 引入細粒度工單權限，完全分離三個角色的操作畫面
--
-- 問題：V11 給 DEPT_ADMIN / DEPT_USER 都加了 WORK_ORDER_APPROVE，
--       導致兩者都能看到「待審核」和「待驗證」；
--       施工任務只用 WORK_ORDER_VIEW，所有角色都看得到。
--
-- 修正後對應：
--   DEPT_ADMIN  → WORK_ORDER_DISPATCH → 只看「待審核」（指派/駁回）
--   DEPT_USER   → WORK_ORDER_APPROVE  → 只看「待驗證」（最終結案/駁回）
--   OPERATOR    → WORK_ORDER_EXECUTE  → 只看「施工任務」（到場/完工）

-- ── 1. 新增兩個 permission ──────────────────────────────────────────

INSERT INTO permissions (permission_id, code, name, group_name, sort_order)
VALUES
    ('PERM_WORK_ORDER_DISPATCH', 'WORK_ORDER_DISPATCH', '派工審核', 'Work order management', 58),
    ('PERM_WORK_ORDER_EXECUTE',  'WORK_ORDER_EXECUTE',  '施工執行', 'Work order management', 59)
ON CONFLICT (permission_id) DO NOTHING;

-- ── 2. 調整角色 permissions ─────────────────────────────────────────

-- DEPT_ADMIN: 移除 APPROVE（V11 加的），改為 DISPATCH
DELETE FROM role_permissions
WHERE role_id = 'ROLE_DEPT_ADMIN' AND permission_id = 'PERM_WORK_ORDER_APPROVE';

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES ('ROLE_DEPT_ADMIN', 'PERM_WORK_ORDER_DISPATCH', NULL)
ON CONFLICT DO NOTHING;

-- DEPT_USER: 保留 APPROVE（待驗證結案用），不另外新增

-- OPERATOR: 加上 EXECUTE（施工執行用）
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES ('ROLE_OPERATOR', 'PERM_WORK_ORDER_EXECUTE', NULL)
ON CONFLICT DO NOTHING;

-- ROLE_ADMIN: 補齊所有工單權限（管理者全覽）
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES
    ('ROLE_ADMIN', 'PERM_WORK_ORDER_DISPATCH', NULL),
    ('ROLE_ADMIN', 'PERM_WORK_ORDER_EXECUTE',  NULL),
    ('ROLE_ADMIN', 'PERM_WORK_ORDER_APPROVE',  NULL)
ON CONFLICT DO NOTHING;

-- ── 3. 更新選單 permission_code ──────────────────────────────────────

-- 待審核 (menu_id=132): WORK_ORDER_APPROVE → WORK_ORDER_DISPATCH
UPDATE menus SET permission_code = 'WORK_ORDER_DISPATCH' WHERE menu_id = 132;

-- 施工任務 (menu_id=133): WORK_ORDER_VIEW → WORK_ORDER_EXECUTE
UPDATE menus SET permission_code = 'WORK_ORDER_EXECUTE' WHERE menu_id = 133;
