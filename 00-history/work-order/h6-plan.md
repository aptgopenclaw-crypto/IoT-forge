Completed: *Analyze role-based menu & sub-pages* (1/3)

以下是完整的實作階段計畫：

---

## Phase 1 — 基礎設施（已完成）

| # | 項目 | 檔案 | 狀態 |
|---|---|---|---|
| 1.1 | Workflow definition migration | V108__workflow__seed_work_order.sql | ✅ |
| 1.2 | WORK_ORDER_APPROVE 權限 migration | `V109__workflow__seed_work_order_approve_permission.sql` | ✅ |
| 1.3 | WorkOrderService 串接 workflow | WorkOrderService.java | ✅ |
| 1.4 | WorkOrderWorkflowListener | WorkOrderWorkflowListener.java | ✅ |
| 1.5 | Repository: findByReviewWorkflowInstanceId | WorkOrderRepository.java | ✅ |

---

## Phase 2 — 選單與路由（後端 DB + 前端 router）

| # | 項目 | 說明 |
|---|---|---|
| 2.1 | **Migration: 派工管理目錄 + 4 子頁面** | 新增 `派工管理` DIRECTORY（menu_id 130），底下 4 個 PAGE，脫離 `設備管理` |
| 2.2 | **前端路由: 4 條新 route** | `router/index.ts` 加入 `/dispatch/*` 路徑 |
| 2.3 | **選單權限綁定** | 各角色只能看到對應的選單項目 |

### 選單結構

```
130 派工管理 (DIRECTORY)  [WORK_ORDER_VIEW]
  ├─ 131 所有工單 (PAGE)  [WORK_ORDER_VIEW]         → /dispatch/all
  ├─ 132 待審核   (PAGE)  [WORK_ORDER_APPROVE]      → /dispatch/pending-approval
  ├─ 133 施工任務 (PAGE)  [WORK_ORDER_VIEW]         → /dispatch/my-tasks
  └─ 134 待驗證   (PAGE)  [WORK_ORDER_APPROVE]      → /dispatch/pending-review
```

---

## Phase 3 — 後端專屬查詢 API

| # | 項目 | API | 說明 |
|---|---|---|---|
| 3.1 | **待審核列表** | `GET /v1/auth/work-orders/pending-dispatch` | status = PENDING（等待 DEPT_ADMIN） |
| 3.2 | **我的施工任務** | `GET /v1/auth/work-orders/my-tasks` | assignedTo = 當前使用者, status IN (ASSIGNED, IN_PROGRESS) |
| 3.3 | **待驗證列表** | `GET /v1/auth/work-orders/pending-review` | status = REVIEWING（等待 DEPT_USER 驗證） |
| 3.4 | **Workflow 待辦清單** | `GET /v1/api/poc/workflow/pending` | 當前使用者的 workflow step_log 待辦 |

---

## Phase 4 — 前端頁面

| # | 頁面 | Vue 元件 | 功能 |
|---|---|---|---|
| 4.1 | **所有工單** | `AllWorkOrdersView.vue` | 改造現有 WorkOrderListView.vue，加上新增工單 dialog、檢視 drawer、狀態篩選 |
| 4.2 | **待審核** | `PendingApprovalView.vue` | 列出 PENDING 工單，每列有「核准 / 駁回」按鈕，點核准 → 呼叫 workflow approve |
| 4.3 | **施工任務** | `MyTasksView.vue` | 列出指派給我的 ASSIGNED / IN_PROGRESS 工單，有「到場打卡」「完成維修」按鈕 |
| 4.4 | **待驗證** | `PendingReviewView.vue` | 列出 REVIEWING 工單，有「核准 / 駁回」按鈕 |

### 各頁面操作對照

```
所有工單 (4.1)
  ├─ [+ 新增工單]  →  填 deviceId, description → create() → workflow start
  ├─ [檢視]        →  Drawer: 工單明細 + Timeline
  └─ [結案]        →  僅 COMPLETED/REJECTED 狀態

待審核 (4.2)             施工任務 (4.3)           待驗證 (4.4)
┌──────────────┐       ┌──────────────┐        ┌──────────────┐
│ PENDING 工單  │       │ 我的施工單    │        │ REVIEWING 工單│
│              │       │              │        │              │
│ [核准] [駁回] │       │ [到場打卡]    │        │ [核准] [駁回] │
│              │       │ [完成維修]    │        │              │
└──────────────┘       └──────────────┘        └──────────────┘
```

---

## Phase 5 — 整合驗證

| # | 項目 | 說明 |
|---|---|---|
| 5.1 | **跨角色流程測試** | DEPT_USER 建立 → DEPT_ADMIN 審核 → OPERATOR 施工 → DEPT_USER 驗證 |
| 5.2 | **駁回路徑測試** | 派工審核駁回、施工驗證駁回 |
| 5.3 | **權限檢查** | OPERATOR 看不到待審核頁面、DEPT_ADMIN 看不到施工任務 |

---

### 建議執行順序

```
Phase 2 (選單+路由) → Phase 3 (後端API) → Phase 4 (前端頁面) → Phase 5 (驗證)
```

要從 **Phase 2** 開始實做嗎？