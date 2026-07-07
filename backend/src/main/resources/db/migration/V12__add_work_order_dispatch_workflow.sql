-- V12: 補充缺少的 work_order_dispatch 工作流程定義
-- 步驟：通報(DEPT_USER) → 派工審核(DEPT_ADMIN) → 施工執行(OPERATOR) → 施工驗證(DEPT_USER) → 結案

INSERT INTO workflow_definitions
    (id, code, version, name, steps_json, enabled, created_at, updated_at, tenant_id)
VALUES
    (3, 'work_order_dispatch', 1, '設備障礙派工簽核',
     '{"steps": [{"id": "step_report", "name": "設備障礙通報", "next": "step_dispatch", "type": "normal", "sla_days": 1, "role_code": "ROLE_DEPT_USER", "reject_target": null}, {"id": "step_dispatch", "name": "派工審核", "next": "step_execution", "type": "normal", "sla_days": 2, "role_code": "ROLE_DEPT_ADMIN", "reject_target": "step_report"}, {"id": "step_execution", "name": "施工執行", "next": "step_verify", "type": "normal", "sla_days": 7, "role_code": "ROLE_OPERATOR", "reject_target": null}, {"id": "step_verify", "name": "施工驗證", "next": "step_end", "type": "normal", "sla_days": 3, "role_code": "ROLE_DEPT_USER", "reject_target": "step_execution"}, {"id": "step_end", "name": "結案", "next": null, "type": "end", "sla_days": null, "role_code": null, "reject_target": null}], "initial_step": "step_report"}'::jsonb,
     true, '2026-06-25 11:04:40.479', '2026-06-25 11:04:40.479', 'T_D029426BA10C')
ON CONFLICT (id) DO NOTHING;
