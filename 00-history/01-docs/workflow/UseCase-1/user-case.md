
/**
businessId	業務案件的「身分證號」	報修單號、資產異動單號
businessType	業務案件的「類型」	報修、換裝、資產異動
applicantId	誰發起這個案件	申請人 ID
 */

# ========================================
# UC-1: 正常流程（申請 → 主管 → 財產管理 → 結案）
# ========================================

# 1. 啟動流程（以 user1@flow.com 登入 , password : Test1234567!, dept_id=12, user_id =f75a999a-6fc4-4b0f-a719-bc51b24a439f）
curl -X POST http://localhost:8080/api/poc/workflow/start \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <employee_token>" \
  -d '{
    "workflowCode": "asset_transfer",
    "businessId": "UC1-001",
    "businessType": "ASSET_TRANSFER",
    "context": {
      "departmentId": "12",
      "amount": 500000,
      "applicantId": "f75a999a-6fc4-4b0f-a719-bc51b24a439f"
    }
  }'
# 預期回應: { "currentStepId": "step_applicant", "currentStepName": "申請人送審" }

# 2. 申請人送審（user1@flow.com, password : Test1234567!, dept_id=12, user_id=f75a999a-6fc4-4b0f-a719-bc51b24a439f）
curl -X POST http://localhost:8080/api/poc/workflow/approve \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <employee_token>" \
  -d '{
    "workflowInstanceId": <INSTANCE_ID>,
    "comment": "申請資產移轉"
  }'
# 預期回應: { "currentStepId": "step_manager", "currentStepName": "部門主管審核" }

# 3. 主管審核通過（admin1@flow.com password: Test1234567!, dept_id = 12, user_id =66f19b01-291a-4e4b-a15f-81ceb4a85675）
curl -X POST http://localhost:8080/api/poc/workflow/approve \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <manager_token>" \
  -d '{
    "workflowInstanceId": <INSTANCE_ID>,
    "comment": "同意"
  }'
# 預期回應: { "currentStepId": "step_property", "currentStepName": "財產管理審核" }

# 4. 財產管理審核通過（admin2@flow.com password:Test1234567! ,dept_id=13, user_id=d34b59ec-bd42-4f6e-b3aa-4f1c6aaa0e63）
curl -X POST http://localhost:8080/api/poc/workflow/approve \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <property_token>" \
  -d '{
    "workflowInstanceId": <INSTANCE_ID>,
    "comment": "核准"
  }'
# 預期回應: { "status": "COMPLETED", "isCompleted": true }