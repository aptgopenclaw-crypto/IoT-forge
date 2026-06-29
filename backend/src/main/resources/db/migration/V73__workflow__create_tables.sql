-- =============================================================================
-- V73: 簽核引擎 POC — 建立 workflow 相關資料表
--
-- 包含：
--   1. workflow_definitions  — 流程定義（JSON 描述步驟）
--   2. workflow_instances    — 流程執行實例
--   3. workflow_step_logs    — 步驟執行歷程
--   4. delegate_settings     — 代理人設定
-- =============================================================================

-- ── 1. workflow_definitions ──────────────────────────────────────────────────

CREATE TABLE workflow_definitions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(100) NOT NULL,
    version     INTEGER      NOT NULL DEFAULT 1,
    name        VARCHAR(200) NOT NULL,
    steps_json  JSONB        NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workflow_def_code_version UNIQUE (code, version)
);

COMMENT ON TABLE  workflow_definitions             IS '流程定義';
COMMENT ON COLUMN workflow_definitions.code        IS '流程代碼，如 asset_transfer';
COMMENT ON COLUMN workflow_definitions.steps_json  IS 'JSON 格式的步驟定義';

-- ── 2. workflow_instances ────────────────────────────────────────────────────

CREATE TABLE workflow_instances (
    id                  BIGSERIAL    PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    workflow_def_id     BIGINT       NOT NULL REFERENCES workflow_definitions(id),
    business_id         VARCHAR(100) NOT NULL,
    business_type       VARCHAR(100) NOT NULL,
    current_step_id     VARCHAR(100),
    status              VARCHAR(50)  NOT NULL DEFAULT 'IN_PROGRESS',
    context_json        JSONB,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workflow_instance_business UNIQUE (tenant_id, business_type, business_id)
);

COMMENT ON TABLE  workflow_instances                  IS '流程執行實例';
COMMENT ON COLUMN workflow_instances.status           IS 'IN_PROGRESS | COMPLETED | REJECTED';
COMMENT ON COLUMN workflow_instances.current_step_id  IS '當前步驟 ID，對應 steps_json 中的 step.id';
COMMENT ON COLUMN workflow_instances.context_json     IS '業務上下文（供 AssigneeResolver 使用）';

-- ── 3. workflow_step_logs ────────────────────────────────────────────────────

CREATE TABLE workflow_step_logs (
    id                    BIGSERIAL    PRIMARY KEY,
    tenant_id             VARCHAR(50)  NOT NULL,
    workflow_instance_id  BIGINT       NOT NULL REFERENCES workflow_instances(id),
    step_id               VARCHAR(100) NOT NULL,
    step_name             VARCHAR(200) NOT NULL,
    assignee_user_id      VARCHAR(100),
    action                VARCHAR(50),
    comment               TEXT,
    target_step_id        VARCHAR(100),
    entered_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at          TIMESTAMP
);

COMMENT ON TABLE  workflow_step_logs                  IS '步驟執行歷程';
COMMENT ON COLUMN workflow_step_logs.action           IS 'approve | reject | resubmit | null（進行中）';
COMMENT ON COLUMN workflow_step_logs.target_step_id   IS 'reject 時的退回目標步驟 ID';
COMMENT ON COLUMN workflow_step_logs.assignee_user_id IS '審核人（已套用代理人覆寫後的最終人員）';

-- ── 4. delegate_settings ─────────────────────────────────────────────────────

CREATE TABLE delegate_settings (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       VARCHAR(50)  NOT NULL,
    delegate_for    VARCHAR(100) NOT NULL,
    delegate_to     VARCHAR(100) NOT NULL,
    business_type   VARCHAR(100),
    effective_from  DATE         NOT NULL,
    effective_to    DATE         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_delegate_period CHECK (effective_to >= effective_from)
);

COMMENT ON TABLE  delegate_settings               IS '代理人設定';
COMMENT ON COLUMN delegate_settings.delegate_for  IS '被代理人 user_id';
COMMENT ON COLUMN delegate_settings.delegate_to   IS '代理人 user_id';
COMMENT ON COLUMN delegate_settings.business_type IS 'null 表示適用所有業務類型';

CREATE INDEX idx_delegate_settings_lookup
    ON delegate_settings (delegate_for, effective_from, effective_to);

-- ── 5. 插入 POC 流程定義（資產異動審核，4 步驟）─────────────────────────────

INSERT INTO workflow_definitions (code, version, name, steps_json) VALUES (
    'asset_transfer',
    1,
    '資產異動審核',
    '{
        "initial_step": "step_applicant",
        "steps": [
            {
                "id": "step_applicant",
                "name": "申請人送審",
                "type": "normal",
                "role_code": "ROLE_DEPT_USER",
                "next": "step_manager",
                "reject_target": null
            },
            {
                "id": "step_manager",
                "name": "部門主管審核",
                "type": "normal",
                "role_code": "ROLE_DEPT_ADMIN",
                "next": "step_property",
                "reject_target": "step_applicant"
            },
            {
                "id": "step_property",
                "name": "財產管理審核",
                "type": "normal",
                "role_code": "ROLE_DEPT_ADMIN",
                "next": "step_end",
                "reject_target": "step_manager"
            },
            {
                "id": "step_end",
                "name": "結案",
                "type": "end",
                "role_code": null,
                "next": null,
                "reject_target": null
            }
        ]
    }'
);
