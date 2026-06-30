Created 2 todos

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/workflow/event/WorkflowStepCompletedEvent.java)

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/workflow/event/WorkflowNotificationListener.java)

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/workflow/event/WorkflowStepAssignedEvent.java)

Starting: *Create WorkOrderWorkflowListener* (2/2)

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/service/WorkOrderWorkflowListener.java)

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/repository/WorkOrderRepository.java#1-1), lines 1 to 45




```

```


Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/repository/WorkOrderRepository.java#1-1), lines 1 to 15




```

```


Checked [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/service/WorkOrderWorkflowListener.java), [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/repository/WorkOrderRepository.java), 1 problem found






```

```


Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/service/WorkOrderWorkflowListener.java#95-95), lines 95 to 112




```

```


Checked [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/service/WorkOrderWorkflowListener.java), [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/repository/WorkOrderRepository.java), no problems found

Completed: *Create WorkOrderWorkflowListener* (2/2)

完成。建立了 `WorkOrderWorkflowListener`，監聽兩個事件：

### `WorkflowStepAssignedEvent` — step_execution 被指派

當 DEPT_ADMIN 核准派工後，workflow 建立 `step_execution` 待辦給 ROLE_OPERATOR。這時 listener 捕捉到事件，自動：

```
step_execution 被指派給 operator_user
  → 更新工單: assignedTo = operator_user, status = ASSIGNED
```

### `WorkflowStepCompletedEvent` — step_verify 完成

當 DEPT_USER 在施工驗證步驟按下核准或駁回時：

```
step_verify APPROVE
  → 更新工單: status = COMPLETED, reviewerId = 審核者

step_verify REJECT
  → 更新工單: status = REJECTED, reviewerId = 審核者
```

### 事件流完整圖

```
create()           DEPT_ADMIN 核准     OPERATOR complete()    DEPT_USER 核准
   │                    │                   │                     │
   ▼                    ▼                   ▼                     ▼
step_report ──→ step_dispatch ──→ step_execution ──→ step_verify ──→ step_end
  auto-pass        assign to            approve →             complete/
                   OPERATOR             REVIEWING              reject work
                   (AssignedEvent)      (WorkOrderService)     (CompletedEvent)
```

### 目前進度

| 順序 | 項目 | 狀態 |
|---|---|---|
| 1 | Migration: workflow definition | ✅ V108 |
| 2 | Migration: WORK_ORDER_APPROVE 權限 | ✅ V109 |
| 3 | 後端: WorkOrderService 串接 workflow | ✅ |
| **4** | **後端: 監聽 WorkflowStepCompletedEvent** | **✅ 完成** |
| 5 | 前端: 工單列表 + 待審列表 | ⬜ 待做 |

Made changes.