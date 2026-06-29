-- =============================================
-- V96: IoT 設備管理模組（通用型）
-- =============================================

-- ── 設備主表 ──
CREATE TABLE devices (
    id                  BIGSERIAL    PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    device_type         VARCHAR(30)  NOT NULL,
    device_code         VARCHAR(100) NOT NULL,
    device_name         VARCHAR(200),

    -- 坐標
    twd97_x             NUMERIC(12,3),
    twd97_y             NUMERIC(12,3),
    lng                 NUMERIC(11,7),
    lat                 NUMERIC(10,7),
    elevation           NUMERIC(8,3),
    twd67_x             NUMERIC(12,3),
    twd67_y             NUMERIC(12,3),
    taipower_coord      VARCHAR(100),

    -- 組織歸屬
    dept_id             BIGINT,
    contract_id         BIGINT,
    property_owner      VARCHAR(200),

    -- 狀態
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    installed_at        DATE,
    decommissioned_at   DATE,

    -- 連線拓撲
    parent_device_id    BIGINT,
    mount_position      VARCHAR(50),
    connectivity_type   VARCHAR(20),
    network_config      JSONB,
    last_heartbeat_at   TIMESTAMP,

    -- 回路
    circuit_id          BIGINT,

    -- IoT 擴充欄位
    device_token        VARCHAR(200),
    auth_type           VARCHAR(20),
    firmware_version    VARCHAR(50),
    last_telemetry_at   TIMESTAMP,
    format_id           BIGINT,

    -- 專有屬性（由 DeviceTemplate schema 定義與驗證）
    attributes          JSONB,

    -- 審計
    created_by          VARCHAR(50),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_devices_tenant ON devices(tenant_id);
CREATE INDEX idx_devices_type ON devices(device_type);
CREATE INDEX idx_devices_status ON devices(status);
CREATE INDEX idx_devices_dept ON devices(dept_id);
CREATE INDEX idx_devices_parent ON devices(parent_device_id);
CREATE UNIQUE INDEX idx_devices_token ON devices(device_token) WHERE device_token IS NOT NULL;

-- ── 設備模板 ──
CREATE TABLE device_templates (
    tenant_id       VARCHAR(50)  NOT NULL,
    device_type     VARCHAR(30)  NOT NULL,
    schema          JSONB        NOT NULL,
    version         INTEGER      NOT NULL DEFAULT 1,
    created_by      VARCHAR(50),
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, device_type)
);

CREATE INDEX idx_device_templates_tenant ON device_templates(tenant_id);

-- ── 設備事件 ──
CREATE TABLE device_events (
    id                  BIGSERIAL    PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    device_id           BIGINT       NOT NULL,
    event_type          VARCHAR(30)  NOT NULL,
    event_date          TIMESTAMP    NOT NULL,
    description         TEXT,
    attachments         JSONB,
    repair_ticket_id    BIGINT,
    replacement_item_id BIGINT,
    created_by          VARCHAR(50),
    created_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_device_events_device ON device_events(device_id);
CREATE INDEX idx_device_events_tenant ON device_events(tenant_id);

-- ── 設備責任人 ──
CREATE TABLE device_managers (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       VARCHAR(50)  NOT NULL,
    device_id       BIGINT       NOT NULL,
    user_id         VARCHAR(50)  NOT NULL,
    assigned_at     TIMESTAMP    NOT NULL DEFAULT now(),
    assigned_by     VARCHAR(50)
);

CREATE INDEX idx_device_managers_device ON device_managers(device_id);
CREATE INDEX idx_device_managers_user ON device_managers(user_id);
CREATE INDEX idx_device_managers_tenant ON device_managers(tenant_id);

-- ── 電力迴路 ──
CREATE TABLE circuits (
    id                  BIGSERIAL    PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    panel_box_device_id BIGINT,
    circuit_number      VARCHAR(50)  NOT NULL,
    circuit_name        VARCHAR(200),
    taipower_account    VARCHAR(50),
    usage_type          VARCHAR(50),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_circuits_tenant ON circuits(tenant_id);

-- ── 標案契約 ──
CREATE TABLE contracts (
    id                  BIGSERIAL    PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    contract_code       VARCHAR(100) NOT NULL,
    contract_name       VARCHAR(300) NOT NULL,
    budget_year         INTEGER,
    procurement_number  VARCHAR(100),
    contractor_name     VARCHAR(200),
    contractor_contact  VARCHAR(200),
    asset_category      VARCHAR(50),
    quantity            INTEGER,
    start_date          DATE,
    end_date            DATE,
    acceptance_date     DATE,
    warranty_years      INTEGER,
    warranty_expiry     DATE,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    attributes          JSONB,
    created_by          VARCHAR(50),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_contracts_tenant ON contracts(tenant_id);

-- ── 工單 ──
CREATE TABLE work_orders (
    id                          BIGSERIAL    PRIMARY KEY,
    tenant_id                   VARCHAR(50)  NOT NULL,

    -- 關聯
    device_id                   BIGINT,
    circuit_id                  BIGINT,
    contract_id                 BIGINT,

    -- 類型與來源
    order_type                  VARCHAR(20)  NOT NULL,
    source_type                 VARCHAR(20)  NOT NULL,

    -- 狀態
    status                      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    priority                    VARCHAR(10),

    -- 通報資訊
    reporter_name               VARCHAR(100),
    reporter_contact            VARCHAR(100),
    reported_at                 TIMESTAMP,
    description                 TEXT,
    location_snapshot           JSONB,

    -- 派工
    assigned_to                 VARCHAR(50),
    assigned_at                 TIMESTAMP,
    assigned_by                 VARCHAR(50),

    -- 技師到場（GPS 打卡）
    started_at                  TIMESTAMP,
    start_lat                   NUMERIC(10,7),
    start_lng                   NUMERIC(11,7),

    -- 維修完成
    completed_at                TIMESTAMP,
    completion_remark           TEXT,
    fault_cause                 VARCHAR(100),
    repair_cost                 INTEGER,

    -- 覆核
    reviewer_id                 VARCHAR(50),
    reviewed_at                 TIMESTAMP,
    reject_reason               TEXT,
    review_workflow_instance_id BIGINT,

    -- 結案
    closed_at                   TIMESTAMP,
    closed_by                   VARCHAR(50),

    -- 智能通報
    auto_reported_at            TIMESTAMP,

    -- 附件
    attachments                 JSONB,

    -- 審計
    created_by                  VARCHAR(50),
    created_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_orders_device ON work_orders(device_id);
CREATE INDEX idx_work_orders_status ON work_orders(status, created_at);
CREATE INDEX idx_work_orders_source ON work_orders(source_type);
CREATE INDEX idx_work_orders_tenant ON work_orders(tenant_id);

-- ── 工單操作日誌 ──
CREATE TABLE work_order_logs (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       VARCHAR(50)  NOT NULL,
    work_order_id   BIGINT       NOT NULL,
    action          VARCHAR(30)  NOT NULL,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20),
    operator_id     VARCHAR(50),
    operator_name   VARCHAR(100),
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(11,7),
    note            TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_order_logs_order ON work_order_logs(work_order_id);
CREATE INDEX idx_work_order_logs_tenant ON work_order_logs(tenant_id);
