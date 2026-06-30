-- LOG-SUMMARY Flyway V12: Create log_summary table
-- Upsert key: (tenant_id, level, source, message)

CREATE TABLE log_summary (
    id          BIGSERIAL       PRIMARY KEY,
    tenant_id   VARCHAR(50)     NOT NULL,
    level       VARCHAR(10)     NOT NULL,
    source      VARCHAR(100),
    message     TEXT            NOT NULL,
    count       INTEGER         NOT NULL DEFAULT 1,
    log_ref     VARCHAR(500),
    first_seen  TIMESTAMP       NOT NULL,
    last_seen   TIMESTAMP       NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_log_summary_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenant(tenant_id)
);

CREATE INDEX idx_log_summary_tenant    ON log_summary(tenant_id);
CREATE INDEX idx_log_summary_last_seen ON log_summary(last_seen);
CREATE INDEX idx_log_summary_level     ON log_summary(level);
CREATE INDEX idx_log_summary_source    ON log_summary(source);
CREATE UNIQUE INDEX idx_log_summary_upsert
    ON log_summary(tenant_id, level, source, message);
