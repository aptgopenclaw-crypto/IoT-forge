Created 1 todo

問題不在 OPERATOR 的權限，而是 **DEPT_ADMIN 核准派工時沒有推進 workflow**。

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/dispatch/service/WorkOrderService.java#103-103), lines 103 to 130




```

```


Searched for text `assign` (`**/dispatch/controller/WorkOrderController.java`), 5 results

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/dispatch/controller/WorkOrderController.java#76-76), lines 76 to 85




```

```





```

```


Searched for text `\.assign\(` (`**/dispatch/WorkOrderServiceTest.java`), 4 results




```

```


Ran terminal command:  mvn compile -q 2>&1 | tail -10

Ran terminal command:  mvn spring-javaformat:apply -q 2>&1 | tail -5 && mvn compile -q 2>&1 | tail -5

Ran terminal command:  mvn test 2>&1 | grep "BUILD"

Ran terminal command:  mvn test 2>&1 | grep "ERROR" | head -10

Ran terminal command:  cat target/surefire-reports/com.taipei.iot.dispatch.WorkOrderControllerTest.txt | head -30

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/test/java/com/taipei/iot/dispatch/WorkOrderControllerTest.java#128-128), lines 128 to 140




```

```


Ran terminal command:  cat target/surefire-reports/com.taipei.iot.dispatch.WorkOrderServiceTest\$AssignTests.txt 2>/dev/null | head -40

Searched for text `\.assign\(` (`**/dispatch/WorkOrderServiceTest.java`), 4 results



Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/test/java/com/taipei/iot/dispatch/WorkOrderServiceTest.java#135-135), lines 135 to 155




```

```


Searched for regex `\.assign\(1L, \"tech01\"\)` (`**/dispatch/WorkOrderServiceTest.java`), 1 result



Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/test/java/com/taipei/iot/dispatch/WorkOrderServiceTest.java#210-210), lines 210 to 220




```

```


Ran terminal command:  mvn test 2>&1 | grep "BUILD"

Completed: *Fix: assign() must advance workflow* (1/1)

**BUILD SUCCESS** ✅

問題原因：**DEPT_ADMIN 核准派工時只更新了工單狀態，沒有推進 workflow**，導致 workflow 卡在 `step_dispatch`，後續 OPERATOR 的 `complete()` 呼叫 `workflowEngine.approve()` 時去審核了錯誤的步驟。

### 修正

```
核准派工流程（修正後）
═══════════════════════════════════════

create()
  → workflowEngine.start()           → step_report
  → workflowEngine.approve()         → step_report → step_dispatch ✓

assign(operatorUserId, deptAdminUserId)      ← 新增 currentUserId 參數
  → 更新工單: status = ASSIGNED
  → workflowEngine.approve(..., deptAdminUserId)   ← ★ 新增！推進 workflow
                                          → step_dispatch → step_execution ✓

complete()
  → 更新工單: status = REVIEWING
  → workflowEngine.approve(...)       → step_execution → step_verify ✓
```

現在完整流程是：

```
create() → approve(step_report) → assign() → approve(step_dispatch)
→ startWork() → complete() → approve(step_execution) → 等待 DEPT_USER 驗證
```

Made changes.