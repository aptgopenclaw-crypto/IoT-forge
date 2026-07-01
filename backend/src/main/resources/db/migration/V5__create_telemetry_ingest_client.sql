--
-- Telemetry Step 4 — ingest 模組：第三方機器對機器（M2M）接入憑證。
--
-- 全域表（非租戶過濾）：認證以 api_key 跨租戶查得後再切換 tenant_id。
-- secret_hash 存 BCrypt 雜湊，切勿存明文。schema-agnostic：無 schema 前綴、無 search_path。
--

CREATE TABLE telemetry_ingest_client (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          VARCHAR(50)  NOT NULL,
    client_name        VARCHAR(100) NOT NULL,
    api_key            VARCHAR(64)  NOT NULL,
    secret_hash        VARCHAR(100) NOT NULL,
    enabled            BOOLEAN      NOT NULL DEFAULT true,
    rate_limit_per_min INTEGER,
    device_scope       VARCHAR(200),
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP,
    CONSTRAINT uq_ingest_client_api_key UNIQUE (api_key)
);

CREATE INDEX idx_ingest_client_tenant ON telemetry_ingest_client (tenant_id);
