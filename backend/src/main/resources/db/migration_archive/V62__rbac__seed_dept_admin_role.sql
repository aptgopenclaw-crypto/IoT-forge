-- =============================================================================
-- V62: Seed ROLE_DEPT_ADMIN as tenant-agnostic baseline
--
-- 背景：
--   ROLE_DEPT_ADMIN 之前僅在 V13 (streetlight 種子) 中宣告，造成「角色定義」與
--   「tenant 種子」耦合。本 migration 將其提升為平台層級的 built-in 角色，
--   並對齊 ADR-005 規範的最小權限集合（USER_LIST / USER_UPDATE / DEPT_LIST /
--   AUDIT_LIST）。
--
--   所有寫入皆使用 ON CONFLICT DO NOTHING，對已執行 V13 的環境完全無副作用：
--   - role 已存在 → 不覆寫 name / description / data_scope
--   - role_permissions 已存在 → 不重複插入
--   - 不會移除 V13 額外授予的 USER_CREATE / DEPT_CREATE / DEVICE_* 等
--
--   ADR-005 概念性 dataScope=DEPT，對應現行 DataScopeEnum 即 THIS_LEVEL_AND_BELOW
--   （見 com.taipei.iot.dept.enums.DataScopeEnum）。i18n 顯示名稱見 1.1.11。
--
-- Reference: 01-docs/new-feature/platform-tenant-separation/02-adr.md ADR-005
-- =============================================================================

-- ── Role ───────────────────────────────────────────────────────────────────
INSERT INTO roles (role_id, code, name, description, built_in, data_scope)
VALUES
  ('ROLE_DEPT_ADMIN', 'DEPT_ADMIN', '部門管理者',
   '管理本部門範圍內的使用者與部門資訊', true, 'THIS_LEVEL_AND_BELOW')
ON CONFLICT (role_id) DO NOTHING;

-- ── Role bindings（ADR-005 最小集合） ───────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT 'ROLE_DEPT_ADMIN', p.permission_id, NULL
FROM permissions p
WHERE p.code IN ('USER_LIST', 'USER_UPDATE', 'DEPT_LIST', 'AUDIT_LIST')
ON CONFLICT DO NOTHING;
