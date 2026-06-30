Created 2 todos

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/repository/WorkOrderRepository.java)

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/controller/WorkOrderController.java#1-1), lines 1 to 100




```

```


Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/service/WorkOrderService.java#1-1), lines 1 to 50




```

```


Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/controller/WorkOrderController.java#1-1), lines 1 to 20




```

```





```

```


Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/workflow/controller/WorkflowPocController.java#100-100), lines 100 to 140




```

```


Checked [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/service/WorkOrderService.java), [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/controller/WorkOrderController.java), [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/device/repository/WorkOrderRepository.java), [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/java/com/taipei/iot/workflow/controller/WorkflowPocController.java), 1 problem found

Ran terminal command:  ./venv/bin/python scripts/generate-contract.py -q 2>&1 | tail -5

Ran terminal command:  ./venv/bin/python scripts/generate-contract.py --all 2>&1 | tail -10

Completed: *Phase 3.1-3: WorkOrder query APIs* (1/2)

Phase 3 完成。新增了 4 個 API 端點：

### 新增端點

| # | API | 用途 |
|---|---|---|
| 3.1 | `GET /v1/auth/work-orders?status=PENDING` | **待審核列表** — 直接用既有 `list()` 加上 status 參數即可 |
| 3.2 | `GET /v1/auth/work-orders/my-tasks` | **我的施工任務** — 依當前使用者過濾 ASSIGNED / IN_PROGRESS |
| 3.3 | `GET /v1/auth/work-orders?status=REVIEWING` | **待驗證列表** — 直接用既有 `list()` 加上 status 參數 |
| 3.4 | `GET /v1/api/poc/workflow/pending` | **Workflow 待辦清單** — 當前使用者的未完成流程步驟 |

### 修改的檔案

| 檔案 | 變更 |
|---|---|
| WorkOrderRepository.java | 新增 `findByAssignedToAndStatusIn` 分頁查詢 |
| WorkOrderService.java | 新增 `listMyTasks()` |
| WorkOrderController.java | 新增 `GET /my-tasks` |
| WorkflowPocController.java | 新增 `GET /pending` |

前端接下來可以直接用這些 API 實作 Phase 4 的 4 個頁面內容。要繼續嗎？

Made changes.