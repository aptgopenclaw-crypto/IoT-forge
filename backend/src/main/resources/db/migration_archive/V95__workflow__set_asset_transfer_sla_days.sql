-- V95: 為資產異動簽核流程定義加入各步驟 SLA 時效設定（sla_days）
--
-- 各步驟 SLA 設計：
--   申請人送審（step_applicant）：1 天（送出後隔天就應進入審核）
--   部門主管審核（step_manager）  ：3 天
--   財產管理審核（step_property）  ：5 天
--   結案（step_end）              ：不設時效（null）
--
-- 採 UPDATE 方式直接替換 steps_json，保留既有流程邏輯不變，僅新增 sla_days 欄位。
UPDATE workflow_definitions
SET steps_json = '{
  "initial_step": "step_applicant",
  "steps": [
    {
      "id": "step_applicant",
      "name": "申請人送審",
      "type": "normal",
      "role_code": "ROLE_DEPT_USER",
      "next": "step_manager",
      "reject_target": null,
      "sla_days": 1
    },
    {
      "id": "step_manager",
      "name": "部門主管審核",
      "type": "normal",
      "role_code": "ROLE_DEPT_ADMIN",
      "next": "step_property",
      "reject_target": "step_applicant",
      "sla_days": 3
    },
    {
      "id": "step_property",
      "name": "財產管理審核",
      "type": "normal",
      "role_code": "ROLE_PROPERTY_MANAGER",
      "next": "step_end",
      "reject_target": "step_manager",
      "sla_days": 5
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
WHERE code = 'asset_transfer';
