-- ============================================================
-- AUDIT Flyway V4: Create user_event_log table
-- ============================================================

CREATE TABLE user_event_log (
    user_event_log_pk  BIGSERIAL PRIMARY KEY,
    tenant_id          VARCHAR(50),
    user_id            VARCHAR(50)  NOT NULL,
    username           VARCHAR(100),
    user_label         VARCHAR(100),
    email              VARCHAR(200),
    event_type         VARCHAR(50)  NOT NULL,
    event_desc         VARCHAR(50),
    api_endpoint       VARCHAR(100),
    payload            VARCHAR(2000),
    error_code         VARCHAR(50),
    message            VARCHAR(50),
    ip_address         VARCHAR(50),
    user_agent         VARCHAR(500),
    execution_time     BIGINT,
    dept_id            BIGINT,
    create_time        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_uel_tenant_id   ON user_event_log(tenant_id);
CREATE INDEX idx_uel_user_id     ON user_event_log(user_id);
CREATE INDEX idx_uel_create_time ON user_event_log(create_time);
CREATE INDEX idx_uel_event_type  ON user_event_log(event_type);
