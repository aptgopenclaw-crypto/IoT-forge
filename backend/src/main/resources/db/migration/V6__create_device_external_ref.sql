--
-- Telemetry Step 4 — ingest 模組：外部設備碼 → 內部 deviceCode 映射。
--
-- 第三方常以自家編碼上報；HTTP ingest 依 (tenant_id, external_code) 解出內部 device_code。
-- 全域表（非租戶過濾）：以明確 tenant_id 欄位查詢。schema-agnostic：無 schema 前綴、無 search_path。
--

CREATE TABLE device_external_ref (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(50)  NOT NULL,
    client_id     BIGINT       REFERENCES telemetry_ingest_client(id),
    external_code VARCHAR(100) NOT NULL,
    device_code   VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_device_external_ref UNIQUE (tenant_id, external_code)
);

CREATE INDEX idx_device_external_ref_lookup ON device_external_ref (tenant_id, external_code);
