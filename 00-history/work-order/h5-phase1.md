Searched for files matching `**/db/migration/V1*.sql`, 21 matches

Searched for text `workflow_definitions|workflow` (`**/db/migration/V73*.sql`), 20 results

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V73__workflow__create_tables.sql#1-1), lines 1 to 15

Searched for text `INSERT INTO workflow_definitions` (`**/db/migration/*.sql`), 1 result

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V73__workflow__create_tables.sql#90-90), lines 90 to 130

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V73__workflow__create_tables.sql#11-11), lines 11 to 30

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V108__workflow__seed_work_order.sql)

已建立 `V108__workflow__seed_work_order.sql`。流程定義如下：

```
work_order_dispatch (v1) — 設備障礙派工簽核
═══════════════════════════════════════════════

step_report     ROLE_DEPT_USER    設備障礙通報     → step_dispatch    可駁回：無
step_dispatch   ROLE_DEPT_ADMIN   派工審核          → step_execution   可駁回：step_report
step_execution  ROLE_OPERATOR     施工執行          → step_verify      可駁回：無
step_verify     ROLE_DEPT_USER    施工驗證          → step_end         可駁回：step_execution
step_end        —                 結案
```

**駁回邏輯**：
- 派工審核退回 → 通報人修正後重送
- 施工驗證退回 → 施工單位重做後再次送驗

下一步要做第 2 項（權限 + Role 綁定）嗎？

Made changes.
