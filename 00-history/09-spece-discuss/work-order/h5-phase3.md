


```

```





```

```


Checked [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/service/WorkOrderService.java), 1 problem found

沒有編譯錯誤，只有一個既有的 unused field 警告。檢查一下 workflow instance ID 是否有在 response 中：

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/service/WorkOrderService.java#215-215), lines 215 to 260

Completed: *Modify WorkOrderService.create to start workflow* (2/3)

已完成。`WorkOrderService` 串接 workflow 的修改摘要：

### `create()` — 新增工單時自動啟動 workflow

```
create(request)
  │
  ├─ 1. 儲存工單 (status = PENDING)
  ├─ 2. workflowEngine.start("work_order_dispatch", ...)
  │      → 建立流程實例，currentStep = step_report
  ├─ 3. workflowEngine.approve(instanceId, ...)
  │      → 自動通過通報步驟，currentStep = step_dispatch
  │      → 等待 DEPT_ADMIN 審核
  ├─ 4. 回存 reviewWorkflowInstanceId
  └─ 5. 回傳工單明細
```

### `complete()` — 完成維修時推進 workflow

```
complete(id, remark, faultCause, repairCost)
  │
  ├─ 1. 更新工單狀態 (IN_PROGRESS → REVIEWING)
  ├─ 2. workflowEngine.approve(instanceId, ...)
  │      → 推進施工執行步驟，currentStep = step_verify
  │      → 等待 DEPT_USER 驗證
  └─ 3. 回傳工單明細
```

### 目前進度

| 順序 | 項目 | 狀態 |
|---|---|---|
| 1 | Migration: workflow definition | ✅ V108 |
| 2 | Migration: WORK_ORDER_APPROVE 權限 | ✅ V109 |
| **3** | **後端: WorkOrderService 串接 workflow** | **✅ 完成** |
| 4 | 後端: 監聽 WorkflowStepCompletedEvent | ⬜ 待做 |
| 5 | 前端: 工單列表 + 待審列表 | ⬜ 待做 |

Made changes.