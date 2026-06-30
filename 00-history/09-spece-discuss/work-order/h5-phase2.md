Created 1 todo

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V106__permission__seed_device_manage.sql)

Searched for regex `CREATE TABLE.*permissions` (`**/db/migration/*.sql`), 2 results

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V1__auth__create_tables.sql#1-1), lines 1 to 50

Searched for text `WORK_ORDER` (`**/db/migration/*.sql`), 20 results

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V107__permission__role_device_manage.sql)

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V109__workflow__seed_work_order_approve_permission.sql)

權限 migration 已建立。現在也把 V108 的 tenant_id 統一成 `T_D029426BA10C`：



Completed: *Create V109: WORK_ORDER_APPROVE permission + role bindings* (1/1)

完成。已建立 `V109__workflow__seed_work_order_approve_permission.sql`：

### 新增權限

| permission_id | code | group | sort |
|---|---|---|---|
| PERM_WORK_ORDER_APPROVE | WORK_ORDER_APPROVE | Work order management | 57 |

### 角色綁定（tenant_id = T_D029426BA10C）

| 角色 | 權限 | 對應流程步驟 |
|---|---|---|
| `ROLE_ADMIN` | ✅ WORK_ORDER_APPROVE | 全面管理 |
| `ROLE_DEPT_ADMIN` | ✅ WORK_ORDER_APPROVE | **派工審核**（step_dispatch） |
| `ROLE_DEPT_USER` | ✅ WORK_ORDER_APPROVE | **施工驗證**（step_verify） |
| `ROLE_OPERATOR` | ❌ 不需此權限 | 執行工單，不負責審核 |

`ROLE_OPERATOR` 不需要審核權限，因為 `step_execution` 的核准是由系統在 `WorkOrderService.complete()` 時自動觸發，非手動操作。

Made changes.