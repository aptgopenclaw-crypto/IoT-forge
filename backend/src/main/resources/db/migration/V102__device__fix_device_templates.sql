-- =============================================================================
-- V102: 修復 device_templates 遺漏的 id 主鍵與 UNIQUE 約束
--
-- 背景：
--   V96 建立 device_templates 時未能正確建立 id 主鍵（BIGSERIAL）。
--   導致 Hibernate 查詢時報錯 "column dt1_0.id does not exist"。
--
-- 修正方式：
--   1. 新增 id BIGSERIAL 作為主鍵
--   2. 補上 UNIQUE (tenant_id, device_type) 約束
-- =============================================================================

-- 1. 新增 id 欄位（若不存在）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'iot_workflowdb'
      AND table_name = 'device_templates'
      AND column_name = 'id'
  ) THEN
    ALTER TABLE iot_workflowdb.device_templates ADD COLUMN id BIGSERIAL;
    ALTER TABLE iot_workflowdb.device_templates ADD PRIMARY KEY (id);
  END IF;
END $$;

-- 2. 補上 UNIQUE 約束（若不存在）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE table_schema = 'iot_workflowdb'
      AND table_name = 'device_templates'
      AND constraint_type = 'UNIQUE'
  ) THEN
    ALTER TABLE iot_workflowdb.device_templates ADD CONSTRAINT uq_device_templates_type UNIQUE (tenant_id, device_type);
  END IF;
END $$;
