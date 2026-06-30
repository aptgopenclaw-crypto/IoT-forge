-- =============================================================================
-- V64: Create impersonation_session table
--
-- 記錄 SUPER_ADMIN 對某 tenant 發起的代操 session：
--   - 由 POST /v1/platform/impersonations 建立（status='ACTIVE'）
--   - 由 DELETE /v1/platform/impersonations/{id} 結束（status='REVOKED', revoked_at=NOW()）
--   - 超過 expires_at 視為 'EXPIRED'（背景任務 / 查詢時 lazy 判斷）
--
-- 與 audit log 連動：
--   - user_event_log / user_info_log 的 impersonation_session_id 指向本表 id
--   - impersonated_by (V60) = operator_user_id（為冗餘欄位，避免每次 JOIN）
--
-- Reference: 01-docs/new-feature/platform-tenant-separation/02-adr.md ADR-002
-- =============================================================================

CREATE TABLE IF NOT EXISTS impersonation_session (
    id                 VARCHAR(50)  PRIMARY KEY,
    operator_user_id   VARCHAR(50)  NOT NULL REFERENCES users(user_id),
    target_tenant_id   VARCHAR(50)  NOT NULL REFERENCES tenant(tenant_id),
    reason             VARCHAR(500) NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    started_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at         TIMESTAMP WITH TIME ZONE,
    revoked_by_user_id VARCHAR(50)  REFERENCES users(user_id),
    create_time        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    update_time        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_imp_session_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT chk_imp_session_expires_after_start CHECK (expires_at > started_at)
);

CREATE INDEX idx_imp_session_operator ON impersonation_session(operator_user_id);
CREATE INDEX idx_imp_session_tenant   ON impersonation_session(target_tenant_id);
CREATE INDEX idx_imp_session_status   ON impersonation_session(status);
-- 部分索引：活躍 session 查詢（hot path：發 token 前檢查是否已有同 operator+tenant 的 ACTIVE session）
CREATE INDEX idx_imp_session_active
    ON impersonation_session(operator_user_id, target_tenant_id, expires_at)
    WHERE status = 'ACTIVE';

COMMENT ON TABLE impersonation_session IS
    'SUPER_ADMIN 代操 session 紀錄；對應 ADR-002 / Phase 1';
COMMENT ON COLUMN impersonation_session.reason IS
    '建立時必填的代操原因（稽核用）';
COMMENT ON COLUMN impersonation_session.status IS
    'ACTIVE=進行中；REVOKED=已主動結束；EXPIRED=逾時';
COMMENT ON COLUMN impersonation_session.revoked_by_user_id IS
    '主動結束者 userId（通常同 operator_user_id；系統自動結束時為 NULL）';
