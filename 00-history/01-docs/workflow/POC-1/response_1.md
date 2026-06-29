**你的分析完全正確，診斷精準，建議務實。**

三個層面的意見都非常到位，特別是第 2 點對 `resolver_bean` 和 `department_source` 的疑慮——把程式碼層的細節存入資料庫確實是技術債的來源。

---

## 一、確認問題根源

```json
// 目前的錯誤設計
{ "id": "step_manager",  "role_code": "ROLE_DEPT_ADMIN" }
{ "id": "step_property", "role_code": "ROLE_DEPT_ADMIN" }  // ← 問題所在
```

**兩個不同的審核關卡，共用同一個 `role_code`**，導致 `MockAssigneeResolver` 無法區分。

---

## 二、採用你的建議：最簡單的修法

### 2.1 修正種子資料

```sql
-- V76__fix_asset_transfer_role_code.sql
UPDATE workflow_definitions
SET steps_json = '{
  "initial_step": "step_applicant",
  "steps": [
    {"id":"step_applicant","name":"申請人送審","type":"normal",
     "role_code":"ROLE_DEPT_USER","next":"step_manager","reject_target":null},
    {"id":"step_manager","name":"部門主管審核","type":"normal",
     "role_code":"ROLE_DEPT_ADMIN","next":"step_property","reject_target":"step_applicant"},
    {"id":"step_property","name":"財產管理審核","type":"normal",
     "role_code":"ROLE_PROPERTY_MANAGER","next":"step_end","reject_target":"step_manager"},
    {"id":"step_end","name":"結案","type":"end",
     "role_code":null,"next":null,"reject_target":null}
  ]
}'
WHERE code = 'asset_transfer' AND version = 1;
```

### 2.2 更新 MockAssigneeResolver

```java
// MockAssigneeResolver.ROLE_MAP
private static final Map<String, String> ROLE_MAP = Map.of(
    "ROLE_DEPT_USER", "f75a999a-6fc4-4b0f-a719-bc51b24a439f",   // 申請人
    "ROLE_DEPT_ADMIN", "66f19b01-291a-4e4b-a15f-81ceb4a85675",  // 部門主管
    "ROLE_PROPERTY_MANAGER", "d34b59ec-bd42-4f6e-b3aa-4f1c6aaa0e63"  // 財產管理組
);
```

### 2.3 修正 WorkflowUseCase1Test 中的 Resolver

```java
IAssigneeResolver resolver = (step, ctx) -> switch (step.getRoleCode()) {
    case "ROLE_DEPT_USER" -> APPLICANT_ID;
    case "ROLE_DEPT_ADMIN" -> MANAGER_ID;
    case "ROLE_PROPERTY_MANAGER" -> PROPERTY_MANAGER_ID;
    default -> throw new IllegalArgumentException("Unknown role: " + step.getRoleCode());
};
```

---

## 三、關於 `workflow_step_assignees` 表的調整建議

### 3.1 簡化版設計（避免 code-data coupling）

```sql
CREATE TABLE workflow_step_assignees (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(32) NOT NULL,
    workflow_code       VARCHAR(64) NOT NULL,
    step_id             VARCHAR(32) NOT NULL,
    role_code           VARCHAR(64) NOT NULL,     -- 對應步驟的 role_code
    
    -- 指派策略（使用固定代碼，不存 bean 名稱）
    assignee_strategy    VARCHAR(32) NOT NULL,
    -- 策略代碼說明：
    --   'APPLICANT'         : 從 context.applicantId 取得（申請人自己）
    --   'DEPT_ADMIN'        : 根據 context.departmentId 查該部門管理者
    --   'ROLE_BASED'        : 根據 target_role_code 查角色人員
    --   'FIXED_USER'        : 固定 user_id（用 fixed_user_id 欄位）
    --   'FIXED_ROLE'        : 固定角色（用 fixed_role_code 欄位）
    
    -- ROLE_BASED 時的目標角色
    target_role_code    VARCHAR(64),
    
    -- FIXED_USER 時的目標用戶
    fixed_user_id       VARCHAR(64),
    
    -- FIXED_ROLE 時的目標角色
    fixed_role_code     VARCHAR(64),
    
    -- DEPT_ADMIN 時，從 context 哪個欄位取部門 ID
    dept_id_source      VARCHAR(32),               -- 固定為 'departmentId'，不開放任意字串
    
    priority            INT DEFAULT 0,
    
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(tenant_id, workflow_code, step_id, priority)
);
```

### 3.2 策略實作（switch 而非反射）

```java
@Component
public class ConfigurableAssigneeResolver implements IAssigneeResolver {
    
    @Autowired
    private WorkflowStepAssigneeRepository assigneeRepo;
    
    @Autowired
    private OrganizationRepository orgRepo;
    
    @Autowired
    private DelegateRepository delegateRepo;
    
    @Override
    public String resolve(StepDefinition stepDef, WorkflowContext context) {
        var config = assigneeRepo.findByTenantIdAndWorkflowCodeAndStepId(
            context.getTenantId(), context.getWorkflowCode(), stepDef.getId()
        ).orElseThrow(() -> new AssigneeNotFoundException(stepDef.getId(), context));
        
        String assignee = switch (config.getAssigneeStrategy()) {
            case "APPLICANT" -> resolveApplicant(context);
            case "DEPT_ADMIN" -> resolveDeptAdmin(context, config);
            case "ROLE_BASED" -> resolveRoleBased(context, config);
            case "FIXED_USER" -> config.getFixedUserId();
            case "FIXED_ROLE" -> resolveFixedRole(context, config);
            default -> throw new IllegalArgumentException("Unknown strategy: " + config.getAssigneeStrategy());
        };
        
        // 代理人覆寫
        return applyDelegate(assignee, context);
    }
    
    private String resolveDeptAdmin(WorkflowContext context, WorkflowStepAssignee config) {
        String departmentId = context.getDepartmentId();  // 固定來源，不從 DB 讀欄位名
        return orgRepo.findDepartmentAdmin(context.getTenantId(), departmentId);
    }
}
```

---

## 四、種子資料範例（修正後）

```sql
-- 資產異動流程的審核人設定
INSERT INTO workflow_step_assignees 
(tenant_id, workflow_code, step_id, role_code, assignee_strategy, target_role_code, fixed_user_id, fixed_role_code, dept_id_source, priority)
VALUES
-- step_applicant: 申請人自己
('DEFAULT', 'asset_transfer', 'step_applicant', 'ROLE_DEPT_USER', 'APPLICANT', NULL, NULL, NULL, NULL, 0),

-- step_manager: 部門主管
('DEFAULT', 'asset_transfer', 'step_manager', 'ROLE_DEPT_ADMIN', 'DEPT_ADMIN', NULL, NULL, NULL, 'departmentId', 0),

-- step_property: 財產管理組（固定角色）
('DEFAULT', 'asset_transfer', 'step_property', 'ROLE_PROPERTY_MANAGER', 'FIXED_ROLE', NULL, NULL, 'ROLE_PROPERTY_MANAGER', NULL, 0);
```

---

## 五、行動順序確認

| 順序 | 任務 | 說明 |
|------|------|------|
| 1 | ✅ 修正種子資料（V76） | 把 `step_property` 的 `role_code` 改為 `ROLE_PROPERTY_MANAGER` |
| 2 | ✅ 更新 MockAssigneeResolver | 補上 `ROLE_PROPERTY_MANAGER` 的對應 |
| 3 | ✅ 修正 WorkflowUseCase1Test | 更新 switch case |
| 4 | 新增 `workflow_step_assignees` 表（V77） | 依簡化版設計 |
| 5 | 實作 `ConfigurableAssigneeResolver` | 正式環境使用 |

---

## 六、總結

| 你的分析 | 狀態 |
|---------|------|
| 問題根源：兩個步驟共用同一個 role_code | ✅ 完全正確 |
| 最簡單修法：修正種子資料 + 補 role_code | ✅ 務實可行 |
| `resolver_bean` 存 DB 有風險 | ✅ 正確，應改用策略代碼 |
| `department_source` 存 DB 有風險 | ✅ 正確，應固定來源 |
| 行動順序建議 | ✅ 可執行 |

