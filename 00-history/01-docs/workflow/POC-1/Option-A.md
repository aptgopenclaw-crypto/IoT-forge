Search complete: Find org structure code

Searched codebase for "JPA entity user role department organization", 27 results

Searched for files matching `**/*Entity.java`, 26 matches

Read [](file:///home/kevin/workspaces/kevin-projects/taipei-iot/backend/src/main/java/com/taipei/iot/auth/entity/RoleEntity.java)

Read [](file:///home/kevin/workspaces/kevin-projects/taipei-iot/backend/src/main/java/com/taipei/iot/auth/entity/UserEntity.java#1-1), lines 1 to 80

Read [](file:///home/kevin/workspaces/kevin-projects/taipei-iot/backend/src/main/java/com/taipei/iot/dept/entity/DeptInfoEntity.java)

Read [](file:///home/kevin/workspaces/kevin-projects/taipei-iot/backend/src/main/java/com/taipei/iot/rbac/entity/PermissionEntity.java)

Read [](file:///home/kevin/workspaces/kevin-projects/taipei-iot/backend/src/main/java/com/taipei/iot/rbac/entity/RolePermissionEntity.java#1-1), lines 1 to 60

Read [](file:///home/kevin/workspaces/kevin-projects/taipei-iot/01-docs/er-model/index.html#100-100), lines 100 to 350

Searched for files matching `**/backend/src/main/java/com/taipei/iot/**/*Repository.java`, 30 matches

Read [](file:///home/kevin/workspaces/kevin-projects/taipei-iot/backend/src/main/java/com/taipei/iot/auth/entity/UserTenantMappingEntity.java)

審閱完成。整體方向正確，但看了現有程式碼後有幾個可以補充的觀察：

---

## 關鍵發現：組織架構已有現成基礎

現有表結構已有 `user_tenant_mapping`（`userId + tenantId + roleId + deptId`）、`dept_info`、`roles`，且 `UserTenantMappingRepository` 已有以下方法：

```java
findByTenantIdAndDeptIdAndEnabledTrue(tenantId, deptId)       // 部門成員
findByTenantIdAndRoleIdAndEnabledTrue(tenantId, roleId)        // 跨部門角色
```

這表示 Option A 不需要另建 `OrganizationRepository`，直接組合現有 repository 即可，工時應比文件估計的少。

---

## 實作 Option A 所需的最小工作量

| 任務 | 說明 |
|------|------|
| `RoleRepository.findByCode(String code)` | `roles` 有 `code` 欄位但 repo 沒有 `findByCode`，需補一行 |
| `WorkflowContext` 加 `tenantId` | `ConfigurableAssigneeResolver` 需要 `tenantId` 才能查詢 |
| `OrgAssigneeResolver` | 邏輯：`role_code` → `roleId` → `user_tenant_mapping` 查詢 |

---

## 策略對應邏輯（基於現有表）

```
ROLE_DEPT_USER       → context.applicantId（直接回傳，不查表）
ROLE_DEPT_ADMIN      → findByTenantId + deptId + roleId（對應 context.departmentId）
ROLE_PROPERTY_MANAGER → findByTenantId + roleId（跨部門，不限 deptId）
```

---

## 對三個選擇的意見

| 選擇 | 意見 |
|------|------|
| **A（接組織架構）** | 可行且工時較短，建議優先 |
| **B（接業務模組）** | 依賴 Option A 完成，順序不能換 |
| **C（補測試）** | 不依賴 DB，可隨時做；建議 A 完成後補 UC-2/UC-4 驗證代理人路徑 |

---

建議確認方向：要先做 Option A 嗎？如是，需要先確認 `WorkflowContext` 是否加入 `tenantId`，以及 `step_applicant` 的邏輯（申請人直接從 context 取，不查表）是否符合預期。