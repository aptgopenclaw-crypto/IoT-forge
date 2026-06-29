-- V88: 確保同一 tenant + code 下同時只能有一個 enabled=true 的流程定義版本。
-- 使用 partial unique index（僅對 enabled=true 的列生效），允許多個 enabled=false 歷史版本並存。
CREATE UNIQUE INDEX idx_workflow_def_unique_enabled_per_code
    ON workflow_definitions (tenant_id, code)
    WHERE enabled = true;
