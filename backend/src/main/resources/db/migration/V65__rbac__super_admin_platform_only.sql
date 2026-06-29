-- =============================================================================
-- V65: ROLE_SUPER_ADMIN 只綁定 PLATFORM_* 權限（Phase 3 / ADR-001）
--
-- 背景：
--   3.1.1 已移除 AuthServiceImpl.resolvePermissions() 內「super_admin 回傳全部
--   permission」的旁路。super_admin 改與其他 role 一樣，從 role_permissions
--   表取得權限集合。
--
--   為了讓 super_admin 在 token 中只持有平台層權限（PLATFORM_*），本 migration
--   將下列 4 個 PLATFORM_* 權限以 tenant_id = NULL（全租戶共用）綁定到
--   ROLE_SUPER_ADMIN：
--     1. PLATFORM_TENANT_MANAGE          - 管理租戶（含認證方式設定）
--     2. PLATFORM_PASSWORD_POLICY_MANAGE - 管理平台密碼策略預設值
--     3. PLATFORM_USER_TENANT_MAPPING    - 管理跨租戶使用者—角色對應
--     4. PLATFORM_IMPERSONATE            - 以租戶身份操作（impersonate）
--
--   並先刪除 ROLE_SUPER_ADMIN 既有的任何 role_permissions，避免歷史環境
--   殘留（V58 之前若有人手動授過 row，會造成 super_admin 在租戶 API 上取得
--   不應有的權限，破壞 Phase 3 強制隔離）。此清理只影響 ROLE_SUPER_ADMIN
--   本身，不會動到其他 role。
--
-- Reference: 01-docs/new-feature/platform-tenant-separation/02-adr.md ADR-001
--            01-docs/new-feature/platform-tenant-separation/03-phased-plan.md 3.1.2
-- =============================================================================

-- ── 1. 清空 ROLE_SUPER_ADMIN 既有 role_permissions（防止歷史殘留） ───────────
DELETE FROM role_permissions
WHERE role_id = 'ROLE_SUPER_ADMIN';

-- ── 2. 綁定 4 個 PLATFORM_* 權限（tenant_id = NULL，全租戶共用） ─────────────
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT 'ROLE_SUPER_ADMIN', p.permission_id, NULL
FROM permissions p
WHERE p.code IN (
    'PLATFORM_TENANT_MANAGE',
    'PLATFORM_PASSWORD_POLICY_MANAGE',
    'PLATFORM_USER_TENANT_MAPPING',
    'PLATFORM_IMPERSONATE'
)
ON CONFLICT DO NOTHING;
