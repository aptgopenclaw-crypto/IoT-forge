-- =============================================================================
-- V75: 修正 asset_transfer 流程定義
--
-- 問題：step_property（財產管理審核）與 step_manager（部門主管審核）共用同一個
--       role_code = 'ROLE_DEPT_ADMIN'，導致 AssigneeResolver 無法區分兩個審核人。
--
-- 修正：將 step_property 的 role_code 改為 'ROLE_PROPERTY_MANAGER'。
-- =============================================================================

UPDATE iot_workflowdb.workflow_definitions
SET steps_json = '{
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
            "role_code": "ROLE_PROPERTY_MANAGER",
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
}',
    updated_at = NOW()
WHERE code = 'asset_transfer'
  AND version = 1;
