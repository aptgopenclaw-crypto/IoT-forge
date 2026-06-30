-- V43__auth__user_session.sql
--
-- [v2 N-7] Persistent user session table to support:
--   * 「目前登入裝置」清單頁面（GET /v1/auth/sessions）
--   * 使用者 / 管理員選擇性踢出單一裝置（DELETE /v1/auth/sessions/{id}）
--   * 配合 refresh token rotation：每次 refresh 產生新 jti = 新 session row，
--     舊 row 立即 revoked。session_id 與 JWT 的 jti claim 同值，方便 Redis
--     撤銷表（auth:revoked_refresh:{jti}）對齊。

CREATE TABLE user_session (
    session_id    VARCHAR(64)  PRIMARY KEY,
    user_id       VARCHAR(50)  NOT NULL,
    tenant_id     VARCHAR(50),
    ip_address    VARCHAR(64),
    user_agent    VARCHAR(512),
    issued_at     TIMESTAMP    NOT NULL,
    last_seen_at  TIMESTAMP    NOT NULL,
    expires_at    TIMESTAMP    NOT NULL,
    revoked       BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at    TIMESTAMP
);

-- 主查詢路徑：使用者列出自己的活躍 session（最近活動排序）
CREATE INDEX idx_user_session_user_active
    ON user_session (user_id, revoked, last_seen_at DESC);

-- 清理過期 session 用
CREATE INDEX idx_user_session_expires_at
    ON user_session (expires_at);
