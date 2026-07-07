-- V11: 修正工單審核權限配置
--
-- 問題：PERM_WORK_ORDER_APPROVE 只有 ROLE_OPERATOR 擁有，導致
--   - 待審核（menu permission: WORK_ORDER_APPROVE）只有 OPERATOR 看得到（應為 DEPT_ADMIN）
--   - 待驗證（menu permission: WORK_ORDER_APPROVE）只有 OPERATOR 看得到（應為 DEPT_USER）
--   - OPERATOR 反而可以看審核頁面（不應該）
--
-- 修正後角色職責：
--   DEPT_USER  → 建立工單（MANAGE）+ 看自己的工單（VIEW）+ 最終驗證結案（APPROVE）
--   DEPT_ADMIN → 審核派工（MANAGE + APPROVE）
--   OPERATOR   → 施工反饋（MANAGE + VIEW），不需要 APPROVE

-- 移除 OPERATOR 的 APPROVE（OPERATOR 負責施工，不負責審核）
DELETE FROM role_permissions
WHERE role_id = 'ROLE_OPERATOR' AND permission_id = 'PERM_WORK_ORDER_APPROVE';

-- 給 DEPT_ADMIN 加上 APPROVE（負責指派/駁回 待審核工單）
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES ('ROLE_DEPT_ADMIN', 'PERM_WORK_ORDER_APPROVE', NULL)
ON CONFLICT DO NOTHING;

-- 給 DEPT_USER 加上 APPROVE（負責最終驗證結案 待驗證工單）
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES ('ROLE_DEPT_USER', 'PERM_WORK_ORDER_APPROVE', NULL)
ON CONFLICT DO NOTHING;
