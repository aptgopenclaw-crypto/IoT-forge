-- =============================================================================
-- V108: 設備障礙派工簽核流程定義
--
-- 流程代碼: work_order_dispatch
--
-- 流程設計：
--   step_report    (ROLE_DEPT_USER)   → 通報人填寫設備障礙
--   step_dispatch  (ROLE_DEPT_ADMIN)  → 主管審核派工
--   step_execution (ROLE_OPERATOR)    → 施工單位執行（由 WorkOrderService 觸發核准）
--   step_verify    (ROLE_DEPT_USER)   → 通報人驗證施工結果
--   step_end       (——)               → 結案
--
-- 退回邏輯：
--   派工審核 → 可退回通報人修正
--   施工驗證 → 可退回施工單位重做
-- =============================================================================

INSERT INTO workflow_definitions (tenant_id, code, version, name, steps_json) VALUES (
    'T_D029426BA10C',
    'work_order_dispatch',
    1,
    '設備障礙派工簽核',
    '{
        "initial_step": "step_report",
        "steps": [
            {
                "id": "step_report",
                "name": "設備障礙通報",
                "type": "normal",
                "role_code": "ROLE_DEPT_USER",
                "next": "step_dispatch",
                "reject_target": null,
                "sla_days": 1
            },
            {
                "id": "step_dispatch",
                "name": "派工審核",
                "type": "normal",
                "role_code": "ROLE_DEPT_ADMIN",
                "next": "step_execution",
                "reject_target": "step_report",
                "sla_days": 2
            },
            {
                "id": "step_execution",
                "name": "施工執行",
                "type": "normal",
                "role_code": "ROLE_OPERATOR",
                "next": "step_verify",
                "reject_target": null,
                "sla_days": 7
            },
            {
                "id": "step_verify",
                "name": "施工驗證",
                "type": "normal",
                "role_code": "ROLE_DEPT_USER",
                "next": "step_end",
                "reject_target": "step_execution",
                "sla_days": 3
            },
            {
                "id": "step_end",
                "name": "結案",
                "type": "end",
                "role_code": null,
                "next": null,
                "reject_target": null,
                "sla_days": null
            }
        ]
    }'::jsonb
);
