-- =============================================================================
-- V76: 建立資產異動申請表
--
-- 業務模組：asset_transfer
-- 設計說明：
--   1. 含 tenant_id 支援多租戶隔離
--   2. department_id 為 BIGINT，對應 dept_info.dept_id
--   3. application_no 在 tenant 內唯一
--   4. workflow_instance_id 關聯 workflow_instances.id
--   5. status 由 Service 層在 submit/approve/reject 時主動同步，不使用 event listener
-- =============================================================================

CREATE TABLE asset_transfer_applications (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            VARCHAR(50)    NOT NULL,
    application_no       VARCHAR(64)    NOT NULL,
    applicant_id         VARCHAR(64)    NOT NULL,
    applicant_name       VARCHAR(128),
    department_id        BIGINT         NOT NULL,
    department_name      VARCHAR(128),

    -- 資產資訊
    asset_code           VARCHAR(64)    NOT NULL,
    asset_name           VARCHAR(256)   NOT NULL,
    transfer_type        VARCHAR(32)    NOT NULL,         -- TRANSFER / DISPOSAL / SCRAP
    target_department_id BIGINT,
    reason               TEXT,

    -- 金額
    asset_value          DECIMAL(20, 2),

    -- 流程關聯
    workflow_instance_id BIGINT,

    -- 狀態：DRAFT / PROCESSING / COMPLETED / REJECTED
    status               VARCHAR(32)    NOT NULL DEFAULT 'DRAFT',
    current_assignee     VARCHAR(64),

    -- 時間戳
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(64),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_by           VARCHAR(64),

    -- 審核結果
    approved_at          TIMESTAMP,
    approved_by          VARCHAR(64),
    reject_reason        TEXT,

    CONSTRAINT uq_asset_transfer_tenant_appno UNIQUE (tenant_id, application_no)
);

CREATE INDEX idx_asset_transfer_tenant_applicant
    ON asset_transfer_applications (tenant_id, applicant_id);

CREATE INDEX idx_asset_transfer_tenant_status
    ON asset_transfer_applications (tenant_id, status);

CREATE INDEX idx_asset_transfer_workflow
    ON asset_transfer_applications (workflow_instance_id);
