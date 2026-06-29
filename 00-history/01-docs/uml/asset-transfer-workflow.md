# 資產異動申請 — 流程與整合 UML

> 以下所有圖表均以 **Mermaid** 語法撰寫，可在 GitHub / VS Code Markdown Preview 直接渲染。

---

## 1. 整體狀態機（AssetTransferStatus）

```mermaid
stateDiagram-v2
    [*] --> DRAFT : create()
    DRAFT --> PROCESSING : submit()
    DRAFT --> PROCESSING : createAndSubmit()
    PROCESSING --> COMPLETED : approve()（最後一關通過）
    PROCESSING --> REJECTED : reject()（退回至申請人）
    PROCESSING --> PROCESSING : approve()（中間步驟通過）
    PROCESSING --> PROCESSING : reject()（退回至中間步驟）
    PROCESSING --> CANCELLED : cancel()（申請人主動取消）
    REJECTED --> PROCESSING : resubmit()
    CANCELLED --> [*]
    COMPLETED --> [*]
```

---

## 2. 申請流程（Sequence Diagram）

### 2-1 建立草稿並送出

```mermaid
sequenceDiagram
    actor 申請人
    participant Controller as AssetTransferController
    participant Service as AssetTransferService
    participant Engine as WorkflowEngine
    participant Resolver as OrgAssigneeResolver
    participant DB

    申請人->>Controller: POST /create-and-submit
    Controller->>Service: createAndSubmit(req, userId)
    Service->>DB: save(DRAFT)
    Service->>Engine: start(workflowCode, businessId, ASSET_TRANSFER, context)
    Engine->>Resolver: resolve(step_applicant, context)
    Resolver-->>Engine: applicantId（申請人自己）
    Engine->>DB: workflow_instances + step_log(step_applicant, completed)
    Engine->>Resolver: resolve(step_manager, context)
    Resolver->>DB: findByTenantIdAndDeptIdAndRoleId(ROLE_DEPT_ADMIN)
    Resolver->>DB: findActiveDelegate(deptAdmin, ASSET_TRANSFER, today)
    note right of Resolver: 若有代理人設定，回傳代理人 userId<br/>否則回傳 deptAdmin userId
    Resolver-->>Engine: assigneeUserId
    Engine->>DB: step_log(step_manager, pending, assigneeUserId)
    Service->>DB: update app status = PROCESSING, currentAssignee = assigneeUserId
    Service-->>Controller: AssetTransferResponse
    Controller-->>申請人: 200 OK
```

### 2-2 審核通過（中間步驟）

```mermaid
sequenceDiagram
    actor 審核人
    participant Controller as AssetTransferController
    participant Service as AssetTransferService
    participant Engine as WorkflowEngine
    participant Resolver as OrgAssigneeResolver
    participant DB

    審核人->>Controller: POST /approve/{id}
    Controller->>Service: approve(id, userId, comment)
    Service->>DB: findById(id) → app
    note over Service: assertCanAct(userId, app.currentAssignee)<br/>允許：本人 OR 今日有效代理人
    Service->>Engine: approve(workflowInstanceId, comment, currentAssignee)
    Engine->>DB: complete current step_log
    Engine->>Resolver: resolve(next_step, context)
    Resolver-->>Engine: nextAssigneeId
    Engine->>DB: new step_log(next_step, pending, nextAssigneeId)
    Service->>DB: update app.currentAssignee = nextAssigneeId
    Service-->>Controller: AssetTransferResponse
    Controller-->>審核人: 200 OK
```

### 2-3 審核通過（最終步驟 → 結案）

```mermaid
sequenceDiagram
    actor 最終審核人
    participant Service as AssetTransferService
    participant Engine as WorkflowEngine
    participant DB

    最終審核人->>Service: approve(id, userId, comment)
    Service->>Engine: approve(workflowInstanceId, comment, currentAssignee)
    Engine->>DB: complete last step_log
    Engine->>DB: workflow_instance.status = COMPLETED
    Service->>DB: app.status = COMPLETED, approvedAt, approvedBy
    Service->>DB: app.currentAssignee = null
    Service-->>最終審核人: AssetTransferResponse(COMPLETED)
```

### 2-4 退回申請

```mermaid
sequenceDiagram
    actor 審核人
    participant Service as AssetTransferService
    participant Engine as WorkflowEngine
    participant DB

    審核人->>Service: reject(id, userId, comment, targetStepId)
    Service->>Engine: reject(workflowInstanceId, targetStepId, comment, currentAssignee)
    Engine->>DB: complete current step_log(REJECTED)
    Engine->>DB: new step_log(targetStepId, pending, targetAssignee)
    note over Service: 若 targetStepId 的 assignee == applicantId<br/>→ app.status = REJECTED<br/>否則維持 PROCESSING
    Service->>DB: update app.status, rejectReason, currentAssignee
    Service-->>審核人: AssetTransferResponse
```

### 2-5 補件重送

```mermaid
sequenceDiagram
    actor 申請人
    participant Service as AssetTransferService
    participant Engine as WorkflowEngine
    participant DB

    申請人->>Service: resubmit(id, userId, comment)
    note over Service: 驗證 app.status == REJECTED<br/>且 userId == app.applicantId
    Service->>Engine: resubmit(workflowInstanceId, comment, userId)
    Engine->>DB: complete current step_log
    Engine->>DB: new step_log(next_step, pending)
    Service->>DB: app.status = PROCESSING, currentAssignee = nextAssignee
    Service-->>申請人: AssetTransferResponse(PROCESSING)
```

---

## 3. 代理人機制（Delegate）

### 3-1 代理人設定流程

```mermaid
sequenceDiagram
    actor 被代理人 as 被代理人（如 Admin1）
    participant Controller as WorkflowPocController
    participant DB as delegate_settings

    被代理人->>Controller: POST /v1/api/poc/workflow/delegate
    note right of 被代理人: { delegateTo, businessType,<br/>effectiveFrom, effectiveTo }
    Controller->>DB: save DelegateSettingEntity
    note over DB: delegate_for = 被代理人 userId<br/>delegate_to = 代理人 userId<br/>有效期間：effectiveFrom ~ effectiveTo
    Controller-->>被代理人: 200 OK
```

### 3-2 代理人指派解析（OrgAssigneeResolver）

```mermaid
flowchart TD
    A[WorkflowEngine 需要解析 step assignee] --> B[OrgAssigneeResolver.resolve]
    B --> C{role_code?}
    C -->|ROLE_DEPT_USER| D[回傳 context.applicantId]
    C -->|ROLE_DEPT_ADMIN| E[查 user_tenant_mapping\ntenant + dept + role]
    C -->|其他角色| F[查 user_tenant_mapping\ntenant + role 跨部門]
    E --> G[取第一筆 userId]
    F --> G
    G --> H[applyDelegate 查詢代理設定]
    H --> I{今日有效代理存在?}
    I -->|否| J[回傳原審核人]
    I -->|是| K{代理人 == 申請人?}
    K -->|是 利益衝突| L[⚠️ 跳過代理，回傳原審核人]
    K -->|否| M[回傳代理人 userId]
    D --> N[step_log.assignee_user_id]
    J --> N
    L --> N
    M --> N
```

### 3-3 代理人執行審核（assertCanAct）

```mermaid
flowchart TD
    A[approve / reject 請求] --> B{userId == currentAssignee?}
    B -->|是 本人| C[✅ 允許操作]
    B -->|否| D[查 delegate_settings\n找今日有效的 delegate_for]
    D --> E{currentAssignee 在 delegatedFrom 清單?}
    E -->|是| F[✅ 允許操作\n代理人代為執行]
    E -->|否| G[❌ 拋出 ASSET_TRANSFER_PERMISSION_DENIED]
    C --> H[workflowEngine 以 currentAssignee 操作]
    F --> H
```

---

## 4. 待審案件查詢（getPendingTasks）

```mermaid
flowchart TD
    A[GET /pending\ncurrentUserId] --> B[組合 assigneeIds]
    B --> C[加入 currentUserId 本人]
    B --> D[查 delegate_settings\n找今日有效 delegateFor 清單]
    D --> E[加入所有被代理人 userId]
    C --> F[workflow_step_logs\nfindByAssigneeUserIdIn AND completedAt IS NULL]
    E --> F
    F --> G[取得 workflowInstanceId 清單]
    G --> H[asset_transfer_applications\nfindByWorkflowInstanceIdIn]
    H --> I[回傳待審清單]
```

---

## 5. 資料模型關係圖（ERD）

```mermaid
erDiagram
    asset_transfer_applications {
        bigint id PK
        varchar application_no
        varchar applicant_id FK
        varchar applicant_name
        bigint department_id
        varchar current_assignee FK
        varchar status
        bigint workflow_instance_id FK
        timestamp approved_at
        varchar approved_by
        timestamp created_at
    }

    workflow_instances {
        bigint id PK
        varchar code
        varchar business_id
        varchar business_type
        varchar status
        varchar current_step
        jsonb context
        varchar tenant_id
    }

    workflow_step_logs {
        bigint id PK
        bigint workflow_instance_id FK
        varchar step_id
        varchar assignee_user_id FK
        varchar action
        varchar comment
        timestamp completed_at
        varchar tenant_id
    }

    delegate_settings {
        bigint id PK
        varchar tenant_id
        varchar delegate_for FK
        varchar delegate_to FK
        varchar business_type
        date effective_from
        date effective_to
        timestamp created_at
    }

    workflow_definitions {
        bigint id PK
        varchar code
        int version
        varchar name
        jsonb steps_json
        varchar tenant_id
    }

    asset_transfer_applications ||--o{ workflow_instances : "workflow_instance_id"
    workflow_instances ||--o{ workflow_step_logs : "workflow_instance_id"
    workflow_instances }o--|| workflow_definitions : "code"
    delegate_settings }o--|| users : "delegate_for"
    delegate_settings }o--|| users : "delegate_to"
    workflow_step_logs }o--|| users : "assignee_user_id"
```

---

## 6. 元件架構圖

```mermaid
graph TB
    subgraph Frontend["Frontend (Vue 3)"]
        AT[AssetTransferDetailView]
        PD[AssetTransferPendingView]
        DL[WorkflowDelegateView]
    end

    subgraph Controller["Controller Layer"]
        ATC[AssetTransferController\n/v1/auth/asset-transfer/*]
        WPC[WorkflowPocController\n/v1/api/poc/workflow/*]
    end

    subgraph Service["Service Layer"]
        ATS[AssetTransferService]
        WE[WorkflowEngine]
        OAR[OrgAssigneeResolver\n@Primary]
    end

    subgraph Repository["Repository Layer"]
        ATAR[AssetTransferApplicationRepository]
        WSLR[WorkflowStepLogRepository]
        WIR[WorkflowInstanceRepository]
        DSR[DelegateSettingRepository]
        WDR[WorkflowDefinitionRepository]
    end

    AT --> ATC
    PD --> ATC
    DL --> WPC

    ATC --> ATS
    WPC --> WE
    WPC --> DSR

    ATS --> WE
    ATS --> ATAR
    ATS --> DSR
    WE --> OAR
    WE --> WSLR
    WE --> WIR
    WE --> WDR
    OAR --> DSR
```

---

## 7. 流程步驟定義（workflow_definitions.steps_json）

```
asset_transfer 流程定義：

step_applicant  →  step_manager  →  step_property  →  step_end
(ROLE_DEPT_USER)   (ROLE_DEPT_ADMIN)  (ROLE_PROPERTY_MANAGER)  (END)

退回路徑：
  step_manager  ──reject──▶  step_applicant
  step_property ──reject──▶  step_manager
```

---

## 8. 權限對照

| 操作 | 所需 Permission | 說明 |
|---|---|---|
| 建立草稿 | `ASSET_TRANSFER_CREATE` | 申請人 |
| 送出申請 | `ASSET_TRANSFER_CREATE` | 申請人 |
| 建立並送出 | `ASSET_TRANSFER_CREATE` | 申請人（原子操作）|
| 審核通過 | `ASSET_TRANSFER_APPROVE` | 指派審核人或代理人 |
| 退回申請 | `ASSET_TRANSFER_APPROVE` | 指派審核人或代理人 |
| 補件重送 | `ASSET_TRANSFER_CREATE` | 申請人 |
| 取消申請 | `ASSET_TRANSFER_CREATE` | 申請人 |
| 查詢明細 | `ASSET_TRANSFER_VIEW` | 任何相關人員 |
| 查詢待審 | `ASSET_TRANSFER_APPROVE` | 指派審核人或代理人 |
| 設定代理 | `WORKFLOW_DELEGATE_MANAGE` | 任何有審核權的使用者 |

---

## 9. 通知整合

### 9-1 事件架構

```mermaid
sequenceDiagram
    participant Engine as WorkflowEngine
    participant Publisher as ApplicationEventPublisher
    participant Listener as WorkflowNotificationListener
    participant Notif as NotificationService
    participant InApp as InAppChannel

    Engine->>Publisher: publishEvent(WorkflowStepAssignedEvent)
    Publisher-->>Listener: @TransactionalEventListener(afterCommit)
    Listener->>Notif: send(NotificationPayload)
    Notif->>InApp: channel.send(payload)
    InApp->>DB: insert notification
    InApp->>WebSocket: /topic/notifications/{userId}
```

### 9-2 事件類型

| 事件 | 發布時機 | payload 內容 |
|---|---|---|
| `WorkflowStepAssignedEvent` | `createStepLog()` 後 | 通知新任審核人有新的待辦 |
| `WorkflowStepCompletedEvent` | `completeLog()` 後 | 記錄步驟完成（供 audit / 擴充） |

### 9-3 通知內容

| 情境 | 標題 | 內容 |
|---|---|---|
| 步驟指派 | 你有新的審核待辦 | 「{步驟名稱}」步驟已指派給你，請前往處理。 |
