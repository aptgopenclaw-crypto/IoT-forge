-- =============================================================================
-- V78: 新增「財產管理員」角色（ROLE_PROPERTY_MANAGER）
--
-- 背景：
--   資產異動審核流程（asset_transfer）的第三步 step_property 要求審核人具備
--   ROLE_PROPERTY_MANAGER 角色。本 migration 將其列為平台 built-in 角色，
--   供所有租戶使用。
--
--   role_id = 'ROLE_PROPERTY_MANAGER' 與 workflow_definitions.steps_json
--   中的 role_code 保持一致，OrgAssigneeResolver 透過 role_id 查詢。
-- =============================================================================

-- ── 1. 角色定義 ──────────────────────────────────────────────────────────────

INSERT INTO roles (role_id, code, name, description, built_in, data_scope)
VALUES
  ('ROLE_PROPERTY_MANAGER', 'PROPERTY_MANAGER', '財產管理員',
   '負責審核資產異動申請，跨部門範圍', true, 'ALL')
ON CONFLICT (role_id) DO NOTHING;

-- ── 2. 角色權限（可視需求擴充）───────────────────────────────────────────────

INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT 'ROLE_PROPERTY_MANAGER', p.permission_id, NULL
FROM permissions p
WHERE p.code IN ('USER_LIST', 'DEPT_LIST', 'AUDIT_LIST')
ON CONFLICT DO NOTHING;
