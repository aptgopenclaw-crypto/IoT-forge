-- =============================================================================
-- V74: workflow — 為所有 workflow 資料表加入 tenant_id 欄位並建立 Hibernate Filter
--
-- 背景：
--   V73 建立 workflow 表時未考慮多租戶需求，不同組織（縣市、部門、SaaS 客戶）
--   的流程定義與執行資料應彼此隔離，需補齊 tenant_id。
--
-- 影響範圍：
--   1. workflow_definitions  — 不同租戶可有不同流程定義（台北 2層審核、高雄 3層）
--   2. workflow_instances    — 流程執行實例屬於特定租戶
--   3. workflow_step_logs    — 歷程跟隨 instance，屬於特定租戶
--   4. delegate_settings     — 代理人設定屬於特定租戶
-- =============================================================================

-- ── 1. workflow_definitions ──────────────────────────────────────────────────

ALTER TABLE workflow_definitions
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) NOT NULL DEFAULT 'DEFAULT';

ALTER TABLE workflow_definitions
    ALTER COLUMN tenant_id DROP DEFAULT;

COMMENT ON COLUMN workflow_definitions.tenant_id IS '所屬租戶 ID';

-- 同一租戶下 code+version 唯一（不同租戶可有相同 code）
ALTER TABLE workflow_definitions
    DROP CONSTRAINT IF EXISTS uq_workflow_def_code_version;

DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_workflow_def_code_version_tenant'
    ) THEN
        ALTER TABLE workflow_definitions
            ADD CONSTRAINT uq_workflow_def_code_version_tenant
                UNIQUE (tenant_id, code, version);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_workflow_def_tenant ON workflow_definitions (tenant_id);

-- ── 2. workflow_instances ────────────────────────────────────────────────────

ALTER TABLE workflow_instances
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) NOT NULL DEFAULT 'DEFAULT';

ALTER TABLE workflow_instances
    ALTER COLUMN tenant_id DROP DEFAULT;

COMMENT ON COLUMN workflow_instances.tenant_id IS '所屬租戶 ID';

-- 同一租戶下業務唯一
ALTER TABLE workflow_instances
    DROP CONSTRAINT IF EXISTS uq_workflow_instance_business;

ALTER TABLE workflow_instances
    DROP CONSTRAINT IF EXISTS uq_workflow_instance_business_tenant;

ALTER TABLE workflow_instances
    ADD CONSTRAINT uq_workflow_instance_business_tenant
        UNIQUE (tenant_id, business_type, business_id);

CREATE INDEX IF NOT EXISTS idx_workflow_instance_tenant ON workflow_instances (tenant_id);

-- ── 3. workflow_step_logs ────────────────────────────────────────────────────

ALTER TABLE workflow_step_logs
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) NOT NULL DEFAULT 'DEFAULT';

ALTER TABLE workflow_step_logs
    ALTER COLUMN tenant_id DROP DEFAULT;

COMMENT ON COLUMN workflow_step_logs.tenant_id IS '所屬租戶 ID（冗餘，避免跨表 JOIN 查詢歷程）';

CREATE INDEX IF NOT EXISTS idx_workflow_step_log_tenant ON workflow_step_logs (tenant_id);

-- ── 4. delegate_settings ─────────────────────────────────────────────────────

ALTER TABLE delegate_settings
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) NOT NULL DEFAULT 'DEFAULT';

ALTER TABLE delegate_settings
    ALTER COLUMN tenant_id DROP DEFAULT;

COMMENT ON COLUMN delegate_settings.tenant_id IS '所屬租戶 ID';

DROP INDEX IF EXISTS idx_delegate_settings_lookup;

CREATE INDEX IF NOT EXISTS idx_delegate_settings_lookup
    ON delegate_settings (tenant_id, delegate_for, effective_from, effective_to);

-- ── 5. 更新 V73 種子資料的 tenant_id ─────────────────────────────────────────

UPDATE workflow_definitions SET tenant_id = 'DEFAULT' WHERE tenant_id IS NULL OR tenant_id = '';
