-- =============================================================================
-- V60: Audit columns — impersonated_by (Phase B)
--
-- 語意：
--   NULL     = 一般使用者正常操作（多數情況）
--   NOT NULL = SUPER_ADMIN 在 tenant context 下執行（值 = SUPER_ADMIN userId，
--              與 user_id 欄位相同；此欄位作為「平台代操」標記方便查詢）
--
-- 查詢範例：
--   SELECT * FROM user_event_log
--    WHERE tenant_id = 'X' AND impersonated_by IS NOT NULL;
--   → 撈出租戶 X 內所有由平台管理員代操的紀錄，無需 JOIN users 查角色
--
-- Reference: 01-docs/new-feature/tenant/05-phase-b-impersonation.md §3.5
-- =============================================================================

ALTER TABLE user_event_log ADD COLUMN IF NOT EXISTS impersonated_by VARCHAR(50);
ALTER TABLE user_info_log  ADD COLUMN IF NOT EXISTS impersonated_by VARCHAR(50);

COMMENT ON COLUMN user_event_log.impersonated_by IS
    'NULL=一般操作；NOT NULL=SUPER_ADMIN 在 tenant context 下執行，值為 SUPER_ADMIN userId';
COMMENT ON COLUMN user_info_log.impersonated_by IS
    'NULL=一般操作；NOT NULL=SUPER_ADMIN 在 tenant context 下執行，值為 SUPER_ADMIN userId';

-- 部分索引：只索引代操紀錄（PostgreSQL partial index），加速「列出某租戶內所有代操」查詢
CREATE INDEX IF NOT EXISTS idx_user_event_log_impersonated
    ON user_event_log(tenant_id, impersonated_by)
    WHERE impersonated_by IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_info_log_impersonated
    ON user_info_log(tenant_id, impersonated_by)
    WHERE impersonated_by IS NOT NULL;
