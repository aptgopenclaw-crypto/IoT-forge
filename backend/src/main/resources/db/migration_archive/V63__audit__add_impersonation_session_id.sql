-- =============================================================================
-- V63: Audit log impersonation session linkage
--
-- 補強 V60 (audit__add_impersonated_by)：除「誰代操（impersonated_by）」外，
-- 再記錄「屬於哪一場代操（impersonation_session_id）」，方便：
--   - 撈出單一 session 內所有操作（追溯該 session 影響範圍）
--   - 與 impersonation_session 表 JOIN 取 reason / expiresAt / revokedAt
--
-- 與 V60 命名對齊：
--   - V60 用 `impersonated_by`（= operator userId，與 ADR-002 文字
--     `impersonated_by_user_id` 同義）
--   - 本 migration 增加 `impersonation_session_id`
--
-- Reference: 01-docs/new-feature/platform-tenant-separation/02-adr.md ADR-002
-- =============================================================================

ALTER TABLE user_event_log
    ADD COLUMN IF NOT EXISTS impersonation_session_id VARCHAR(50);
ALTER TABLE user_info_log
    ADD COLUMN IF NOT EXISTS impersonation_session_id VARCHAR(50);

COMMENT ON COLUMN user_event_log.impersonation_session_id IS
    'NULL=一般操作；NOT NULL=指向 impersonation_session.id，標記此筆 log 屬於哪一場代操';
COMMENT ON COLUMN user_info_log.impersonation_session_id IS
    'NULL=一般操作；NOT NULL=指向 impersonation_session.id，標記此筆 log 屬於哪一場代操';

-- 部分索引：只索引代操紀錄，加速「列出某 session 內所有操作」查詢
CREATE INDEX IF NOT EXISTS idx_user_event_log_imp_session
    ON user_event_log(impersonation_session_id)
    WHERE impersonation_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_info_log_imp_session
    ON user_info_log(impersonation_session_id)
    WHERE impersonation_session_id IS NOT NULL;
