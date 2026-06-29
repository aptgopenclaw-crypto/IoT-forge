# Workflow 模組 Code Review & Security Review

> 審查日期：2026-06-16
> 審查範圍：
> - `backend/src/main/java/com/taipei/iot/workflow/`
> - `backend/src/main/resources/db/migration/V73__workflow__create_tables.sql`
> - `backend/src/main/resources/db/migration/V74__workflow__add_tenant_id.sql`
> - `backend/src/main/resources/db/migration/V75__workflow__fix_asset_transfer_role_code.sql`
> - `frontend/src/views/assetTransfer/AssetTransferDetailView.vue`
> - `frontend/src/api/assetTransfer/index.ts`、`frontend/src/types/assetTransfer.ts`
> - `backend/src/test/java/com/taipei/iot/workflow/`（3 個測試類別）

---

## 模組現況快照

| 項目 | 現況 |
|---|---|
| 後端檔案 | controller×1、service×3（engine＋2 resolver）、entity×4、repository×4、dto×5、exception×7、model×3 |
| 前端檔案 | view×1（AssetTransferDetailView）、api×1、type×1 |
| 測試覆蓋 | 3 個測試類別（`WorkflowEngineTest`×9、`WorkflowUseCase1Test`×1 整合流程、`MockAssigneeResolverTest`） |
| DB | 4 tables (`workflow_definitions` / `workflow_instances` / `workflow_step_logs` / `delegate_settings`) + 多項 index + 3 migration scripts |
| 定位 | Controller 明確標示「**POC**（開發驗證用）」，但引擎邏輯完整，已在 `AssetTransfer` 生產路徑中使用 |

---

## 總體評價

引擎核心（`WorkflowEngine`）設計嚴謹：悲觀鎖、步驟跳轉驗證、代理人套用、防重複完成，皆有實作。但本輪審查發現數個**高嚴重度安全問題**，主要集中在：

1. **Controller 層缺乏身份驗證機制**（POC 標記掩蓋了生產風險）
2. **`userId` 由 Client 端請求體傳入**，任何已登入者皆可假冒他人身份審核
3. **代理設定端點缺乏授權管制**，任何人可替任意使用者設定代理
4. **`OrgAssigneeResolver.firstUser` 無排序保證**，多人同角色時審核人不確定

此外有若干程式品質與效能議題需修正。

---

## 一、安全審查（Security Review）

### ✅ 已驗證到位

| 防護 | 機制 | 結果 |
|---|---|---|
| 租戶隔離 — 定義 | `@Filter(tenant_id = :tenantId OR tenant_id = 'DEFAULT')` | ✅ |
| 租戶隔離 — 實例 | `@Filter` + `TenantScopedRepository` + `TenantEntityListener` | ✅ |
| 租戶隔離 — step logs | `@Filter` + `TenantScopedRepository` | ✅ |
| 租戶隔離 — 代理設定 | `@Filter` + `TenantScopedRepository` | ✅ |
| 步驟跳轉驗證 | `reject_target` 白名單比對，任意 `targetStepId` 被拒 | ✅ |
| 重複審核防護 | `completedAt != null` → 拋 `WorkflowStepAlreadyCompletedException` | ✅ |
| 悲觀鎖 | `findByIdForUpdate` → `PESSIMISTIC_WRITE`，防止並發競態 | ✅ |
| 代理衝突偵測 | 代理人與申請人相同時跳過代理，避免自我審核 | ✅ |
| 代理有效期 | `effectiveFrom <= today <= effectiveTo` 且有 DB CHECK 約束 | ✅ |
| Input validation | `@NotBlank` / `@NotNull` on all required DTO fields | ✅ |

---

### 🔴 [P1 — Critical] `userId` 從 Request Body 傳入，任意使用者可假冒他人審核  ✅ 已修正（2026-06-16）

**問題**

`WorkflowApproveRequest`、`WorkflowRejectRequest`、`WorkflowResubmitRequest` 均將 `userId` 作為請求欄位：

```java
// WorkflowApproveRequest.java
public record WorkflowApproveRequest(@NotNull Long instanceId, @NotBlank String userId, String comment) {}

// WorkflowRejectRequest.java
public record WorkflowRejectRequest(@NotNull Long instanceId, @NotBlank String targetStepId,
        @NotBlank String userId, String comment) {}
```

Controller 直接轉交 Engine 進行 `validateAssignee()` 比對：

```java
@PostMapping("/approve")
public BaseResponse<WorkflowInstanceEntity> approve(@Valid @RequestBody WorkflowApproveRequest req) {
    WorkflowInstanceEntity result = workflowEngine.approve(req.instanceId(), req.comment(), req.userId());
    return BaseResponse.success(result);
}
```

**攻擊情境**

1. 攻擊者 A（已登入）取得目標流程實例 ID（例如 `instanceId=5`）
2. 查詢 `GET /api/poc/workflow/history/5` 得知當前待辦審核人 `assigneeUserId`
3. 偽造請求：`POST /api/poc/workflow/approve` with `{ "instanceId": 5, "userId": "victim-uuid", "comment": "..." }`
4. `validateAssignee` 比對通過（因 `userId == assigneeUserId`），審核強制推進

**影響**：OWASP A01 Broken Access Control — 完全繞過審核人身份驗證，任意操控審核流程。

**建議修正**：從 `SecurityContext` 取得當前登入使用者，移除 DTO 中的 `userId` 欄位：

```java
// WorkflowApproveRequest.java（移除 userId 欄位）
public record WorkflowApproveRequest(@NotNull Long instanceId, String comment) {}

// WorkflowPocController.java
@PostMapping("/approve")
public BaseResponse<WorkflowInstanceEntity> approve(@Valid @RequestBody WorkflowApproveRequest req) {
    String userId = SecurityContextUtils.getCurrentUserId();
    WorkflowInstanceEntity result = workflowEngine.approve(req.instanceId(), req.comment(), userId);
    return BaseResponse.success(result);
}
```

`WorkflowStartRequest.applicantId` 同理，應從 SecurityContext 取得而非由客戶端傳入。

---

### 🔴 [P1 — Critical] Controller 完全缺乏身份驗證與授權  ✅ 已修正（2026-06-16）

**問題**

`WorkflowPocController` 所有端點（含寫入操作）**無任何 `@PreAuthorize` 或 Spring Security 授權規則**。Controller 的 JavaDoc 明確寫道「不掛 Spring Security 細粒度權限」：

```java
/**
 * POC 簽核引擎 REST API — 僅供開發驗證使用，不掛 Spring Security 細粒度權限。
 */
@RestController
@RequestMapping("/api/poc/workflow")
```

但此路徑已在 `AssetTransfer` 生產業務流程中實際使用，意指：

- 任何能通過系統認證的使用者（包含普通員工）皆可呼叫 `POST /approve`、`POST /reject`、`POST /delegate`
- 無法稽核「是誰執行了哪個審核操作」

**建議修正**

1. 移除 POC 標記，將路徑改為正式路徑（如 `/api/v1/workflows`）
2. 在每個寫入端點加上 `@PreAuthorize`：

```java
@PostMapping("/approve")
@PreAuthorize("isAuthenticated()")
public BaseResponse<WorkflowInstanceEntity> approve(...) { ... }

// 代理設定建議限管理員
@PostMapping("/delegate")
@PreAuthorize("hasAnyAuthority('WORKFLOW_MANAGE', 'DEPT_ADMIN')")
public BaseResponse<DelegateSettingEntity> setDelegate(...) { ... }
```

---

### 🔴 [P1 — Critical] `POST /delegate` 允許任意使用者替他人設定代理  ✅ 已修正（2026-06-16）

**問題**

```java
@PostMapping("/delegate")
public BaseResponse<DelegateSettingEntity> setDelegate(@Valid @RequestBody DelegateSetRequest req) {
    DelegateSettingEntity entity = DelegateSettingEntity.builder()
        .delegateFor(req.delegateFor())    // ← 由 client 任意指定「被代理人」
        .delegateTo(req.delegateTo())
        // ...
        .build();
    return BaseResponse.success(delegateSettingRepository.save(entity));
}
```

**攻擊情境**：攻擊者可替任意部門主管設定代理人（將審核人轉移至共謀帳號），使後續所有該主管的審核步驟被攔截。

**建議修正**

1. `delegateFor` 應從 SecurityContext 取得（使用者只能替自己設定代理）：

```java
@PostMapping("/delegate")
@PreAuthorize("isAuthenticated()")
public BaseResponse<DelegateSettingEntity> setDelegate(@Valid @RequestBody DelegateSetRequest req) {
    String currentUserId = SecurityContextUtils.getCurrentUserId();
    // delegateFor 強制為當前使用者，忽略 req.delegateFor()
    DelegateSettingEntity entity = DelegateSettingEntity.builder()
        .delegateFor(currentUserId)
        .delegateTo(req.delegateTo())
        // ...
}
```

2. 或若允許管理員替他人設定，則需加 `@PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")`。

---

### 🟡 [P2] `GET /history/{id}` 與 `GET /instance/{id}` 缺乏擁有者驗證

`getHistory` 與 `getInstance` 僅靠 Hibernate `@Filter` 做租戶隔離，任何同租戶下的已登入使用者均可查詢任何流程實例的完整審核歷程（含審核人、意見、退回原因）。

**評估**：不屬於跨租戶洩漏，但同租戶內普通員工可查詢非自身申請的案件歷程，視業務敏感度而定。

**建議**：`getHistory`/`getInstance` 加入擁有者或相關人驗證（`applicantId == currentUserId OR assigneeUserId == currentUserId OR hasAuthority('WORKFLOW_MANAGE')`）。

---

### 🟡 [P2] `DelegateSetRequest` 未驗證 `delegateFor != delegateTo`

DTO 層未加入自代理的防護驗證，僅有 `OrgAssigneeResolver.applyDelegate()` 在 **執行時** 才偵測衝突（且僅針對「代理人 == 申請人」的場景，不覆蓋「代理人 == 被代理人」）：

```java
if (delegateTo.equals(context.getApplicantId())) {
    log.warn("[Delegate] 代理人 {} 與申請人相同，跳過代理...", delegateTo, assignee);
    return assignee;
}
```

若 `delegateTo == delegateFor`，代理設定可被儲存，呼叫時觸發 `delegateTo == assignee`，回傳原審核人（靜默忽略），但不報錯，行為令人困惑。

**建議**：在 DTO 加 `@AssertTrue` 或 service 層驗證：

```java
if (req.delegateTo().equals(delegateFor)) {
    throw new BusinessException(ErrorCode.DELEGATE_SELF_NOT_ALLOWED);
}
```

---

### 🟡 [P3] `workflow_step_logs.comment` 無長度上限

DB `comment` 欄位為 `TEXT`（無限制），Entity `@Column(name = "comment")` 亦無 `@Size` 限制。惡意使用者可提交極長字串造成異常資源消耗（OWASP A05 Security Misconfiguration / DoS 向量）。

**建議**：Entity 加 `@Size(max = 2000)` 並同步 DB 加 CHECK 約束或改 `VARCHAR(2000)`。

---

## 二、程式碼品質審查（Code Review）

### ✅ 優點

1. **引擎設計解耦徹底**：`IAssigneeResolver` 介面將流程引擎與組織架構分離，Mock/Prod 實作可無縫切換，測試友善。
2. **悲觀鎖防競態**：`findByIdForUpdate` 使用 `PESSIMISTIC_WRITE`，在高並發下保護流程狀態一致性。
3. **步驟跳轉白名單**：`reject_target` 只允許定義中的合法目標，防止任意流程操縱。
4. **代理人衝突偵測完整**：代理人 = 申請人時靜默降回原審核人，避免自我審核。
5. **多租戶基礎設施一致**：所有 4 個 Entity 均掛 `@Filter` + `TenantScopedRepository`，且 `WorkflowDefinitionEntity` 的 filter 條件正確支援 `DEFAULT` 共享定義（`tenant_id = :tenantId OR tenant_id = 'DEFAULT'`）。
6. **UC-1 整合測試**：端到端模擬真實使用者 UUID，驗證完整 4 步驟審核流程。

---

### 🔴 [高] `OrgAssigneeResolver.firstUser` 無排序保證，多人同角色時審核人不確定

```java
private String firstUser(List<UserTenantMappingEntity> mappings, String roleCode) {
    return mappings.get(0).getUserId();  // 取第一筆，但查詢無 ORDER BY
}
```

`findByTenantIdAndDeptIdAndRoleIdAndEnabledTrue` 為 Spring Data 方法衍生查詢，**無法保證 DB 回傳順序**。若部門有多位主管（如代假主管），每次 resolve 可能回傳不同人。

**建議**：在查詢方法加 `OrderByCreatedAtAsc`，或改為 `...OrderByUserIdAsc`，確保審核人確定性：

```java
List<UserTenantMappingEntity> findByTenantIdAndDeptIdAndRoleIdAndEnabledTrueOrderByCreatedAtAsc(
    String tenantId, Long deptId, String roleId);
```

---

### 🟡 [中] `approve()` 端步驟判斷邏輯重複，存在雙重 save 路徑

`approve()` 方法對「end 步驟」有兩套處理路徑，邏輯複雜易出錯：

```java
// 路徑 A：若「當前步驟」本身是 end 型態
if ("end".equals(currentStep.getType())) {
    instance.setStatus("COMPLETED");
    return instanceRepo.save(instance);
}

// 路徑 B：推進後若「下一步驟」是 end 型態
StepDefinition nextStep = findStep(stepsJson, currentStep.getNext());
instance = instanceRepo.save(instance);          // ← 先 save 一次（IN_PROGRESS 狀態）
if (!"end".equals(nextStep.getType())) {
    createStepLog(instance, nextStep, context);
} else {
    instance.setStatus("COMPLETED");
    instance = instanceRepo.save(instance);      // ← 再 save 一次（COMPLETED）
}
```

路徑 B 在「下一步是 end」時會產生兩次 DB UPDATE：第一次 save status 仍為 `IN_PROGRESS`，第二次才設 `COMPLETED`。除效能浪費外，若第二次 save 失敗，instance 留在錯誤的 `IN_PROGRESS` 狀態。

**建議**：先完整計算最終狀態，再執行單次 save：

```java
StepDefinition nextStep = findStep(stepsJson, currentStep.getNext());
instance.setCurrentStepId(nextStep.getId());
instance.setUpdatedAt(LocalDateTime.now());

if ("end".equals(nextStep.getType())) {
    instance.setStatus("COMPLETED");
    return instanceRepo.save(instance);
} else {
    instance = instanceRepo.save(instance);
    WorkflowContext context = parseContext(instance.getContextJson());
    createStepLog(instance, nextStep, context);
    return instance;
}
```

---

### 🟡 [中] `status` 欄位使用 free-form String，應改 Enum

`WorkflowInstanceEntity.status` 儲存 `"IN_PROGRESS"`、`"COMPLETED"`、`"REJECTED"` 等字串，但：

- 程式碼各處以魔術字串比對（`"end"`, `"IN_PROGRESS"`, `"COMPLETED"`）
- DB 無 CHECK 約束限制合法值
- 若誤寫（如 `"COMPLETE"`）不會有編譯期錯誤

**建議**：定義 `WorkflowStatus` enum，`@Enumerated(EnumType.STRING)` 型別安全：

```java
public enum WorkflowStatus { IN_PROGRESS, COMPLETED, REJECTED, CANCELLED }

@Enumerated(EnumType.STRING)
@Column(name = "status", length = 50, nullable = false)
private WorkflowStatus status = WorkflowStatus.IN_PROGRESS;
```

同樣，`StepDefinition.type` (`"normal"` / `"end"`) 與 `WorkflowStepLogEntity.action` (`"approve"` / `"reject"` / `"resubmit"`) 均有相同問題。

---

### 🟡 [中] `DelegateSettingRepository.findActiveDelegate` 查詢未限制租戶範圍（依賴 Filter 隱式保護）

```jpql
SELECT d FROM DelegateSettingEntity d
WHERE d.delegateFor = :delegateFor
  AND (d.businessType IS NULL OR d.businessType = :businessType)
  AND d.effectiveFrom <= :today AND d.effectiveTo >= :today
```

此查詢未明確含 `d.tenantId = :tenantId` 條件，完全依賴 Hibernate `@Filter` 隱式過濾。若 `TenantFilterAspect` 在特定執行路徑（例如非同步任務、Batch Job）下未正確啟用 filter，代理人可能跨租戶洩漏。

**建議**：在 JPQL 中明確加入 tenant 條件，提供雙重保護：

```jpql
SELECT d FROM DelegateSettingEntity d
WHERE d.tenantId = :tenantId
  AND d.delegateFor = :delegateFor
  AND (d.businessType IS NULL OR d.businessType = :businessType)
  AND d.effectiveFrom <= :today AND d.effectiveTo >= :today
```

---

### 🟡 [中] V73 種子資料與 `WorkflowEngineTest` 的流程定義不一致

V73 種子資料中 `step_property` 使用 `"role_code": "ROLE_DEPT_ADMIN"`（後由 V75 修正為 `ROLE_PROPERTY_MANAGER`）。但 `WorkflowEngineTest` 的靜態 `STEPS_JSON` 仍使用舊版 `"ROLE_DEPT_ADMIN"`：

```java
// WorkflowEngineTest.java（舊定義）
{"id":"step_property","name":"財產管理審核","type":"normal",
 "role_code":"ROLE_DEPT_ADMIN","next":"step_end","reject_target":"step_manager"},
```

而 `WorkflowUseCase1Test` 已正確使用 `"ROLE_PROPERTY_MANAGER"`。這造成：

- 測試覆蓋了不存在於生產的流程定義版本
- 若有人依據 `WorkflowEngineTest` STEPS_JSON 撰寫新測試，可能產生誤導

**建議**：`WorkflowEngineTest` 的 STEPS_JSON 改用 V75 後的正確版本（`ROLE_PROPERTY_MANAGER`），並保持兩個測試檔的定義一致。

---

### 🟡 [中] `WorkflowEngine.resubmit` 的「回傳步驟」語意需說明

```java
// 取「最後一筆 reject 行為」所在步驟（即 reject 的發起方）
String returnStepId = lastReject.getStepId();
```

`resubmit` 將流程回送到「最後執行退回操作的步驟」（如 `step_manager`），而非退回的目標步驟（`step_applicant`）。此語意為：「申請人補件後，直接回到審核主管那關重新審核」。這是刻意設計，但若多次退回，`lastReject` 可能跳過中間步驟，行為不符預期。

**建議**：加入明確的 Javadoc 說明 `returnStepId` 的語意，並補充測試覆蓋「多次退回後的 resubmit 方向」場景。

---

### 🟢 [低] `WorkflowInstanceEntity` 缺乏 `@CreatedDate` / `@LastModifiedDate` 整合

`createdAt`、`updatedAt` 使用 `@Builder.Default = LocalDateTime.now()` 方式初始化，且 `WorkflowEngine` 各處手動呼叫 `instance.setUpdatedAt(LocalDateTime.now())`。雖然已掛 `AuditingEntityListener`，但 `@CreatedDate` / `@LastModifiedDate` 未使用，導致兩套機制並存，`updatedAt` 需每次手動設定且易遺漏。

**建議**：改用標準 Auditing 方式：

```java
@CreatedDate
@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt;

@LastModifiedDate
@Column(name = "updated_at", nullable = false)
private LocalDateTime updatedAt;
```

並移除 `@Builder.Default` 與引擎中所有 `setUpdatedAt(LocalDateTime.now())` 呼叫。

---

### 🟢 [低] `WorkflowDefinitionEntity` 無 `enabled` 版本管理保護

`findByCodeAndEnabledTrue(code)` 若同一 tenant+code 存在多個 enabled=true 的版本，結果不確定（無 `ORDER BY version DESC LIMIT 1`）。應加上排序或在 DB 層加唯一約束確保同時間只有一個 enabled 版本。

---

### 🟢 [低] `WorkflowStepLogRepository.findByAssigneeUserIdAndCompletedAtIsNull` 潛在效能問題

`findByAssigneeUserIdAndCompletedAtIsNull(userId)` 為無 tenant 前綴的查詢（Hibernate filter 會加），但缺乏覆合索引。當系統中 step_logs 量增大時，此查詢（用於取「我的待辦清單」）效能下降。

**建議**：建立複合索引：

```sql
CREATE INDEX idx_step_logs_assignee_pending
    ON workflow_step_logs (tenant_id, assignee_user_id)
    WHERE completed_at IS NULL;
```

---

### 🟢 [低] DTO 缺少 `@Schema` 與 OpenAPI 說明

五個 DTO record 均無 `@Schema` 標注，Swagger 文件對各欄位的業務意義（如 `businessId` 代表什麼、`targetStepId` 允許哪些值）缺乏說明。

---

## 三、效能評估

| 場景 | 現況 | 評估 |
|---|---|---|
| `approve` / `reject` / `resubmit` | 悲觀鎖 + 2–4 次 SQL | ✅ 可接受，並發衝突概率低 |
| `start` | 2 次 SQL（findDef + saveInstance + saveLog） | ✅ 優秀 |
| `getHistory` | 1 次 SQL（單表 scan by instanceId） | ✅ 優秀 |
| `findByAssigneeUserIdAndCompletedAtIsNull`（待辦清單） | `idx_step_logs_assignee_pending` partial index（tenant_id, assignee_user_id WHERE completed_at IS NULL） | ✅ 已優化 |
| `findActiveDelegate` | 1 次 SQL | ✅ 已有 index on (delegate_for, effective_from, effective_to) |
| `OrgAssigneeResolver` 解析審核人 | 最多 2 次 SQL（findRole + findByTenantId...） | ✅ 可接受 |

---

## 四、功能優化建議（產品向）

以下為新功能或 UX 強化建議，非缺陷：

### 高價值（建議優先評估）

1. **🌟 流程實例狀態機守衛**
   - 目前 `approve`/`reject`/`resubmit` 未驗證 instance status（例如 COMPLETED 的流程仍可被呼叫，因為 `requireCurrentLog` 找不到未完成的 log 而拋 `NotFoundException`，算是間接保護）。
   - 建議在引擎入口明確加入 status 守衛，提供更清楚的錯誤訊息：
     ```java
     if (!"IN_PROGRESS".equals(instance.getStatus())) {
         throw new WorkflowInvalidTransitionException("流程已" + instance.getStatus() + "，不可再操作");
     }
     ```

2. **🌟 取消（CANCEL）流程操作**
   - 目前 `WorkflowInstanceEntity.status` 的 DB 備註包含 `REJECTED`，但無 `CANCELLED`，申請人無法主動撤銷送出的申請。
   - 建議新增 `cancel` 端點，限定 `status=IN_PROGRESS` + `currentStepId=step_applicant`（僅限在申請人待辦階段允許撤銷）。

3. **🌟 通知整合**
   - 每次步驟推進（`createStepLog`）後，系統應通知新任審核人（Bell 通知 / Email）。目前引擎對通知無任何整合，審核人需自行輪詢待辦列表。
   - 建議 `WorkflowEngine` 在 `createStepLog` 後發布 `WorkflowStepAssignedEvent`（Spring Event），通知模組訂閱處理。

4. **🌟 流程定義版本管理（Live Migration 保護）**
   - 若 `workflow_definitions` 更新（新增步驟、修改 role），正在執行中的 instance 仍引用舊版 `workflow_def_id`，行為正確。但無機制防止舊版 definition 被 `enabled=false`（舊 instance 的 `loadDef` 會拋 `WorkflowNotFoundException`）。
   - 建議：禁止 disable 仍有 `IN_PROGRESS` instance 關聯的 definition 版本。

### 中等價值

5. **審核期限（SLA）**
   - 在 `StepDefinition` 中加入 `deadlineHours INT`，建立 step_log 時寫入期限時間戳，逾期未審核觸發告警或自動轉派。

6. **多核准人（Any/All）**
   - 目前每步驟只有單一審核人；若業務需要「任一主管同意即可」或「所有主管均需同意」，需擴充 `step.approvalType: ANY | ALL`。

7. **流程視覺化 API**
   - 新增 `GET /api/workflow/definition/{code}/steps` 端點，回傳流程步驟圖（JSON → 前端 El-Steps 或 Mermaid 渲染），取代前端硬編碼的 4 步驟顯示。

8. **批次待辦查詢優化**
   - `GET /pending-tasks` 端點若一次回傳所有待辦，應加分頁；若待辦量大，建議加入 `business_type` 篩選。

### 低價值（看實際需求）

9. **Email / 推播通知**：步驟指派時透過 notification module 推送。
10. **審核轉派**：允許審核人將待辦轉交給同部門其他人（不同於代理設定，為即時操作）。
11. **流程歷程 PDF 匯出**：完成案件可匯出包含所有審核紀錄的報告。
12. **跨流程定義版本統計**：Dashboard 顯示各流程平均審核天數、退回率等 KPI。

---

## 五、優先級總表

| 優先級 | 項目 | 類型 | 預估工時 | 狀態 |
|---|---|---|---|---|
| **P1** | `userId` 改從 SecurityContext 取得（移除 DTO userId 欄位） | Security | S | ✅ 已修正（2026-06-16） |
| **P1** | Controller 加入 `@PreAuthorize` 與正式化路徑（移除 POC 標記） | Security | M | ✅ 已修正（2026-06-16） |
| **P1** | `POST /delegate` 加入授權驗證（限自身或管理員） | Security | S | ✅ 已修正（2026-06-16） |
| **P2** | `DelegateSetRequest` 加入 `delegateFor != delegateTo` DTO 驗證 | Correctness | XS | ✅ 已修正（2026-06-16） |
| **P2** | `GET /history` 與 `GET /instance` 加入擁有者授權檢查 | Security | S | ✅ 已修正（2026-06-16） |
| **P2** | `OrgAssigneeResolver.firstUser` 查詢加入 ORDER BY 確保確定性 | Correctness | XS | ✅ 已修正（2026-06-16） |
| **P2** | `findActiveDelegate` JPQL 明確加入 tenantId 條件 | Security | XS | ✅ 已修正（2026-06-16） |
| **P3** | `approve()` 重複 save 路徑重構 | Code Quality | S | ✅ 已修正（2026-06-16） |
| **P3** | `status` / `action` / step `type` 改為 Enum | Code Quality | M | ✅ 已修正（2026-06-16） |
| **P3** | `WorkflowStepLogEntity.comment` 加 `@Size(max = 2000)` | Security | XS | ✅ 已修正（2026-06-16） |
| **P3** | `WorkflowEngineTest` STEPS_JSON 對齊 V75（ROLE_PROPERTY_MANAGER） | Test | XS | ✅ 已修正（2026-06-16） |
| **P3** | 建立 `idx_step_logs_assignee_pending` partial index | Performance | XS | ✅ 已修正（2026-06-16） |
| **P3** | `WorkflowInstanceEntity` 改用 `@CreatedDate` / `@LastModifiedDate` | Code Quality | S | ✅ 已修正（2026-06-16） |
| **P3** | 流程實例狀態機守衛（IN_PROGRESS 前置驗證） | Correctness | S | ✅ 已修正（2026-06-16） |
| **P4** | `resubmit` returnStepId 語意補充 Javadoc 與多次退回測試 | DX | S | ✅ 已修正（2026-06-16） |
| **P4** | `findByCodeAndEnabledTrue` 加 ORDER BY version DESC LIMIT 1 | Correctness | XS | ✅ 已修正（2026-06-16） |
| **P4** | DTO 補 `@Schema` OpenAPI 說明 | DX | M | ✅ 已修正（2026-06-16） |
| **🌟 Feature** | 取消（CANCEL）流程操作 | Product | S | ⬜ 待評估 |
| **🌟 Feature** | 通知整合（`WorkflowStepAssignedEvent`） | Product | M | ⬜ 待評估 |
| **🌟 Feature** | 流程定義版本保護（防 disable 有 IN_PROGRESS 的版本） | Correctness | S | ⬜ 待評估 |
| **🌟 Feature** | 審核期限（SLA）+ 逾期告警 | Product | M | ⬜ 待評估 |

---

## 架構評分

| 維度 | 分數 | 說明 |
|---|---|---|
| 安全性 | **5.5 / 10** | 租戶隔離完整；但 userId 客戶端傳入、Controller 無授權為高嚴重度缺陷 |
| 正確性 | **7.5 / 10** | 核心流程邏輯正確；但 firstUser 無排序、approve 雙重 save 有隱患 |
| 效能 | **8 / 10** | 悲觀鎖適當；待辦查詢缺 partial index |
| 可維護性 | **7 / 10** | IAssigneeResolver 解耦優秀；魔術字串、POC 標記混入生產造成維護負擔 |
| 測試覆蓋 | **7.5 / 10** | 引擎單元測試完整；缺乏 OrgAssigneeResolver 整合測試與多次退回場景 |

**綜合：7.1 / 10**

---

## 結論

`WorkflowEngine` 引擎本體設計嚴謹，悲觀鎖、步驟跳轉驗證、代理衝突偵測均到位。然而，**Controller 層三個 P1 安全缺陷**（userId 客戶端傳入、無 `@PreAuthorize`、代理端點無授權）是本次審查最急迫的修正項目，在修正前不應上線至生產環境。修正後，模組的架構基礎已足夠支撐後續功能擴展（通知整合、SLA 管理、取消流程等）。
