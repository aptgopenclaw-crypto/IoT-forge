我的理解是「在實作 IOrganizationRepository 時，要使用現有系統的 users, roles, departments 表進行 JOIN 查詢，並利用 WorkflowContext 傳入的 targetDeptId 作為過濾條件。

> 文件上這樣就可以說明清楚了，理解正確嗎

**完全正確。**

你的理解精準地抓住了組織適配層實作的核心要點。以下確認與補充。

---

## 一、你的理解確認

| 項目 | 你的理解 | 狀態 |
|------|---------|------|
| 查詢現有系統表格 | users, roles, departments | ✅ 正確 |
| 使用 JOIN 查詢 | 跨表關聯取得負責人 | ✅ 正確 |
| 利用 WorkflowContext 過濾 | 特別是 targetDeptId | ✅ 正確 |
| 文件說明清楚即可 | POC 階段不需真實實作 | ✅ 正確 |

---

## 二、文件補充說明（建議加入 POC 文檔）

### 2.1 實作說明

```typescript
/**
 * 組織適配層實作說明
 * 
 * 當 POC 驗證通過後，將 MockAssigneeResolver 替換為實際的組織查詢實作。
 * 
 * 查詢邏輯：
 * 1. 根據 role_code 決定要查詢的角色類型
 * 2. 根據 WorkflowContext 中的過濾條件（如 department、district、contractId）
 * 3. JOIN 現有系統的 users、roles、departments 表
 * 4. 回傳符合條件的人員 ID
 * 
 * 範例查詢（以部門主管為例）：
 * 
 * SELECT u.id 
 * FROM users u
 * JOIN user_roles ur ON u.id = ur.user_id
 * JOIN roles r ON ur.role_id = r.id
 * JOIN departments d ON u.department_id = d.id
 * WHERE r.code = 'MANAGER'           -- role_code
 *   AND d.id = ?                       -- context.departmentId
 *   AND u.is_active = true
 * LIMIT 1
 */
interface IOrganizationRepository {
  /**
   * 根據角色代碼和業務上下文查詢負責人
   * @param roleCode - 角色代碼（如 MANAGER, PROPERTY_MANAGER）
   * @param context - 業務上下文，可能包含以下過濾條件：
   *   - departmentId / department: 部門篩選
   *   - district: 行政區篩選
   *   - contractId: 契約篩選（不同契約可能對應不同承辦人）
   *   - businessType: 業務類型（某些角色依案件類型分流）
   * @returns 負責人 ID
   */
  findAssigneeByRole(roleCode: string, context: WorkflowContext): Promise<string | null>;
}
```

### 2.2 常見角色查詢範例

| role_code | 查詢邏輯 | 過濾條件 |
|-----------|---------|---------|
| `APPLICANT` | 查案件申請人 | 從業務表單取得（非組織表） |
| `MANAGER` | 查部門主管 | `context.departmentId` |
| `PROPERTY_MANAGER` | 查財產管理人員 | 依行政區或契約分區 |
| `DISTRICT_INSPECTOR` | 查區巡查員 | `context.district` |
| `VENDOR_CONTACT` | 查廠商窗口 | `context.contractId` |
| `DIRECTOR` | 查機關首長 | 固定人員（無需過濾） |

### 2.3 Context 傳遞範例

```typescript
// 業務模組啟動流程時，傳入足夠的過濾資訊
await workflowEngine.start({
  workflowCode: 'asset_transfer',
  businessId: 'AST-2026-001',
  businessType: 'ASSET_TRANSFER',
  context: {
    // 組織適配層需要的過濾條件
    departmentId: 'DEPT_ENGINEERING',     // 申請單位
    district: '大安區',                    // 行政區
    contractId: 'CONTRACT_2025_001',      // 相關契約
    amount: 500000,                       // 金額（用於條件分支）
    applicantId: 'user_employee_001'      // 申請人（用於衝突檢查）
  }
});
```

---

## 三、總結一句話

> **IOrganizationRepository 的實作就是「用 WorkflowContext 傳入的過濾條件，JOIN 現有系統的 users/roles/departments 表格，查出符合 role_code 的負責人」。**

