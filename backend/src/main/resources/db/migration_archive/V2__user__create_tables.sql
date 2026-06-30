-- ============================================================
-- USER Flyway V2: Create USER tables
-- ============================================================

-- 1. user_info_log (per tenant)
CREATE TABLE user_info_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(50)     NOT NULL,
    action_type     VARCHAR(20)     NOT NULL,
    action_user_id  VARCHAR(50)     NOT NULL,
    target_user_id  VARCHAR(50)     NOT NULL,
    email           VARCHAR(200),
    display_name    VARCHAR(200),
    role_code       VARCHAR(50),
    dept_id         VARCHAR(50),
    detail          VARCHAR(1000),
    create_time     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_user_info_log_target ON user_info_log(target_user_id);
CREATE INDEX idx_user_info_log_tenant ON user_info_log(tenant_id);

-- 2. password_history (global)
CREATE TABLE password_history (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         VARCHAR(50)     NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    create_time     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_password_history_user ON password_history(user_id);
