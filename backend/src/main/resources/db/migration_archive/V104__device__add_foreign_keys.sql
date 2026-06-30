-- =============================================
-- V104: Device module — add database-level foreign keys
-- =============================================
-- Phase 1 建立 device 模組時遺漏了 FK 約束。
-- 此 migration 補上所有邏輯關聯的 FOREIGN KEY。
-- 採用 application-level integrity 為主的策略：
--   - NOT NULL FK → ON DELETE CASCADE（子不可無父）
--   - NULLable FK  → ON DELETE SET NULL（斷開關聯但不刪子）
-- =============================================

-- ── devices ─────────────────────────────────────────────────────────
ALTER TABLE devices
    ADD CONSTRAINT fk_devices_dept
        FOREIGN KEY (dept_id) REFERENCES dept_info(dept_id)
        ON DELETE SET NULL;

ALTER TABLE devices
    ADD CONSTRAINT fk_devices_contract
        FOREIGN KEY (contract_id) REFERENCES contracts(id)
        ON DELETE SET NULL;

ALTER TABLE devices
    ADD CONSTRAINT fk_devices_parent
        FOREIGN KEY (parent_device_id) REFERENCES devices(id)
        ON DELETE SET NULL;

ALTER TABLE devices
    ADD CONSTRAINT fk_devices_circuit
        FOREIGN KEY (circuit_id) REFERENCES circuits(id)
        ON DELETE SET NULL;

-- ── device_events ───────────────────────────────────────────────────
ALTER TABLE device_events
    ADD CONSTRAINT fk_device_events_device
        FOREIGN KEY (device_id) REFERENCES devices(id)
        ON DELETE CASCADE;

ALTER TABLE device_events
    ADD CONSTRAINT fk_device_events_repair_ticket
        FOREIGN KEY (repair_ticket_id) REFERENCES work_orders(id)
        ON DELETE SET NULL;

-- ── device_managers ─────────────────────────────────────────────────
ALTER TABLE device_managers
    ADD CONSTRAINT fk_device_managers_device
        FOREIGN KEY (device_id) REFERENCES devices(id)
        ON DELETE CASCADE;

-- ── circuits ────────────────────────────────────────────────────────
ALTER TABLE circuits
    ADD CONSTRAINT fk_circuits_panel_box_device
        FOREIGN KEY (panel_box_device_id) REFERENCES devices(id)
        ON DELETE SET NULL;

-- ── work_orders ─────────────────────────────────────────────────────
ALTER TABLE work_orders
    ADD CONSTRAINT fk_work_orders_device
        FOREIGN KEY (device_id) REFERENCES devices(id)
        ON DELETE SET NULL;

ALTER TABLE work_orders
    ADD CONSTRAINT fk_work_orders_circuit
        FOREIGN KEY (circuit_id) REFERENCES circuits(id)
        ON DELETE SET NULL;

ALTER TABLE work_orders
    ADD CONSTRAINT fk_work_orders_contract
        FOREIGN KEY (contract_id) REFERENCES contracts(id)
        ON DELETE SET NULL;

ALTER TABLE work_orders
    ADD CONSTRAINT fk_work_orders_workflow_instance
        FOREIGN KEY (review_workflow_instance_id) REFERENCES workflow_instances(id)
        ON DELETE SET NULL;

-- ── work_order_logs ─────────────────────────────────────────────────
ALTER TABLE work_order_logs
    ADD CONSTRAINT fk_work_order_logs_work_order
        FOREIGN KEY (work_order_id) REFERENCES work_orders(id)
        ON DELETE CASCADE;
