--
-- Telemetry Step 3 — telemetry_data 時序儲存（原生 PostgreSQL declarative range partition）。
--
-- 不依賴 TimescaleDB；按 ts 月分區，複合 PK (ts, id)（分區鍵須屬於每個 PK/UNIQUE）。
-- 索引定義於父表自動套用至各分區；ts 另用 BRIN（時序天然單調遞增，體積小、掃描高效）。
-- 含 DEFAULT 分區避免漏寫；近月分區先建，後續由 TelemetryPartitionMaintenanceJob 維護。
-- schema-agnostic：無 schema 前綴、無 search_path。
--

CREATE TABLE telemetry_data (
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    tenant_id        VARCHAR(50)  NOT NULL,
    device_id        BIGINT       NOT NULL REFERENCES devices(id),
    device_type      VARCHAR(30)  NOT NULL,
    ts               TIMESTAMP    NOT NULL,
    received_at      TIMESTAMP    NOT NULL DEFAULT now(),
    source           VARCHAR(20)  NOT NULL DEFAULT 'MQTT',
    source_client_id VARCHAR(50),
    payload          JSONB        NOT NULL,
    raw_payload      JSONB,
    valid            BOOLEAN      NOT NULL DEFAULT true,
    validation_msg   VARCHAR(500),
    PRIMARY KEY (ts, id)
) PARTITION BY RANGE (ts);

CREATE INDEX idx_telemetry_device_ts ON telemetry_data (device_id, ts DESC);
CREATE INDEX idx_telemetry_tenant_ts ON telemetry_data (tenant_id, ts DESC);
CREATE INDEX idx_telemetry_type_ts   ON telemetry_data (device_type, ts DESC);
CREATE INDEX idx_telemetry_ts_brin   ON telemetry_data USING BRIN (ts);

-- 近月分區（當月 + 預建下月）
CREATE TABLE telemetry_data_2026_06 PARTITION OF telemetry_data
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE telemetry_data_2026_07 PARTITION OF telemetry_data
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

-- DEFAULT 分區：任何落在已建月分區外的列都落這裡，避免漏寫（排程缺漏時的安全網）
CREATE TABLE telemetry_data_default PARTITION OF telemetry_data DEFAULT;
