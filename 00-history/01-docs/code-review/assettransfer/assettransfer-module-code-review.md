# AssetTransfer 模組 Code Review & Security Review

> 審查日期：2026-06-15
> 審查範圍：
> - `backend/src/main/java/com/taipei/iot/assettransfer/`
> - `backend/src/main/resources/db/migration/V76__create_asset_transfer_application.sql`
> - `backend/src/main/resources/db/migration/V79__rbac__asset_transfer_permissions.sql`
> - `frontend/src/views/assetTransfer/`（CreateView、PendingView、DetailView）
> - `frontend/src/api/assetTransfer/index.ts`
> - `frontend/src/types/assetTransfer.ts`

---

## 模組現況快照

| 項目 | 現況 |
|---|---|
| 後端檔案 | controller×1、service×1、entity×1、repository×1、dto×4 |
| 前端檔案 | view×3（Create / Pending＋My / Detail）、api×1、type×1 |
| 測試覆蓋 | ❌ **0 個測試類別**（無 Service / Controller 測試） |
| DB | 1 table (`asset_transfer_applications`) + 3 indexes + UNIQUE constraint |
| 整合模組 | `workflow` engine（start / approve / reject / resubmit）、`rbac`、`audit` |

---

## 總體評價

AssetTransfer 模組的整體流程設計（草稿 → 送出 → 審核 → 完成／退回 → 重送）邏輯清晰，與 workflow engine 整合完整，`@Filter` + `TenantScopedRepository` 的基礎多租戶架構已到位。

但本輪審查發現數個**高嚴重性安全漏洞**，其中最關鍵的兩項為：  
1. Controller 直接回傳 JPA Entity（`tenantId` 等敏感欄位外洩）  
2. `approve`/`reject` service 層**未驗證操作者是否為 `currentAssignee`**（IDOR）

另外，本模組**完全無測試覆蓋**，屬高風險狀態，建議同步補齊。

---

## 一、安全審查（Security Review）

### 🔴 [P1 — Critical] Controller 直接回傳 JPA Entity，敏感欄位外洩✅ 已修正（2026-06-15）

**問題**

`AssetTransferController` 所有端點的回傳型別均為 `AssetTransferApplicationEntity`（JPA entity）：

```java
public BaseResponse<AssetTransferApplicationEntity> create(...) { ... }
public BaseResponse<AssetTransferApplicationEntity> approve(...) { ... }
public BaseResponse<List<AssetTransferApplicationEntity>> myApplications() { ... }
// ... 其餘端點相同
```

前端 `AssetTransferApplicationDto` 已明確定義 `tenantId` 欄位並接收：

```typescript
export interface AssetTransferApplicationDto {
  tenantId: string   // ⚠️ 多租戶隔離的核心 key 直接暴露給瀏覽器
  workflowInstanceId: number | null  // ⚠️ 內部流程 ID
  // ...
}
```

**影響**
- `tenant_id` 直接暴露給前端（OWASP A03 敏感資料外洩）
- `workflow_instance_id`（內部資料庫 ID）可被用於 workflow 相關側通道探測
- 無法在不破壞 API 契約的情況下重構 entity 欄位

**建議修正**

新增 `AssetTransferApplicationDto`（Response 用），去除 `tenantId`、`workflowInstanceId` 等內部欄位；controller 統一回傳 DTO，由 service 或 mapper 負責轉換：

```java
// AssetTransferResponse.java（新增）
public record AssetTransferResponse(
    Long id,
    String applicationNo,
    String applicantId,
    String applicantName,
    Long departmentId,
    String departmentName,
    String assetCode,
    String assetName,
    String transferType,
    Long targetDepartmentId,
    String reason,
    BigDecimal assetValue,
    String status,
    String currentAssigneeName,  // 顯示名稱，非 userId
    LocalDateTime createdAt,
    LocalDateTime approvedAt,
    String approvedBy,
    String rejectReason
) {}
```

---

### 🔴 [P1 — Critical] `approve`/`reject` 未驗證 `currentAssignee`，任意審核者可操作（IDOR）✅ 已修正（2026-06-15）

**問題**

`service.approve()` / `service.reject()` 僅驗證申請是否為 `PROCESSING` 狀態，**未驗證操作者是否為當前流程步驟的指定審核人**：

```java
@Transactional
public AssetTransferApplicationEntity approve(Long applicationId, String userId, String comment) {
    AssetTransferApplicationEntity app = findByIdAndAssertProcessing(applicationId);
    // ⚠️ 缺少：驗證 userId 是否等於 app.getCurrentAssignee()
    WorkflowInstanceEntity instance = workflowEngine.approve(app.getWorkflowInstanceId(), comment, userId);
    // ...
}
```

migration V79 的設計說明也自述：「APPROVE 僅供檢視待審列表，核准/退回由**後端 currentAssignee 控制**」——但後端 service 根本沒有這個控制。

**攻擊情境**

1. 攻擊者 A（擁有 `ASSET_TRANSFER_APPROVE` 權限）知道或枚舉到某筆申請 ID
2. 呼叫 `POST /v1/auth/asset-transfer/approve/{id}`
3. Service 僅確認 `status == PROCESSING`，直接允許審核通過
4. 非預期的審核人繞過流程管控完成審核

**影響**：OWASP A01 Broken Access Control / IDOR，流程設計形同虛設

**建議修正**

```java
@Transactional
public AssetTransferApplicationEntity approve(Long applicationId, String userId, String comment) {
    AssetTransferApplicationEntity app = findByIdAndAssertProcessing(applicationId);

    // 驗證操作者為當前流程步驟指定的審核人
    if (!userId.equals(app.getCurrentAssignee())) {
        throw new BusinessException(ErrorCode.ASSET_TRANSFER_PERMISSION_DENIED);
    }

    WorkflowInstanceEntity instance = workflowEngine.approve(app.getWorkflowInstanceId(), comment, userId);
    // ...
}
```

`reject()` 方法需套用相同修正。

---

### 🔴 [P1 — Critical] `resubmit` 未驗證申請人身份，他人可代為重送 ✅ 已修正（2026-06-15）

**問題**

`service.resubmit()` 僅呼叫 `findByIdAndAssertHasWorkflow()`，**未驗證 `userId` 是否為申請人本身**：

```java
@Transactional
public AssetTransferApplicationEntity resubmit(Long applicationId, String userId, String comment) {
    AssetTransferApplicationEntity app = findByIdAndAssertHasWorkflow(applicationId);
    // ⚠️ 缺少：if (!app.getApplicantId().equals(userId)) throw ...
    WorkflowInstanceEntity instance = workflowEngine.resubmit(...);
    // ...
}
```

相比之下，`submit()` 有正確驗證申請人身份：

```java
if (!app.getApplicantId().equals(applicantId)) {
    throw new BusinessException(ErrorCode.ASSET_TRANSFER_PERMISSION_DENIED);
}
```

**建議修正**：在 `resubmit()` 加上同樣的申請人身份驗證。

---

### 🟡 [P2] `AssetTransferCreateRequest` 輸入驗證不完整  ✅ 已修正（2026-06-15）

**問題**

目前 DTO 僅有 `@NotBlank` / `@NotNull`，缺少長度與數值邊界保護：

```java
public record AssetTransferCreateRequest(
    @NotBlank String assetCode,        // ⚠️ 無 @Size，可塞入超長字串
    @NotBlank String assetName,        // ⚠️ 無 @Size
    @NotBlank String transferType,     // ⚠️ 無 @Pattern，非法值可寫入 DB
    @NotNull Long departmentId,
    Long targetDepartmentId,
    String reason,                     // ⚠️ 無 @Size，TEXT 欄位可接受無限長字串
    BigDecimal assetValue              // ⚠️ 無 @DecimalMin，可填入負數資產值
) {}
```

**建議修正**

```java
public record AssetTransferCreateRequest(
    @NotBlank @Size(max = 64) String assetCode,
    @NotBlank @Size(max = 256) String assetName,
    @NotBlank @Pattern(regexp = "INTERNAL|EXTERNAL|DISPOSAL|RETURN") String transferType,
    @NotNull Long departmentId,
    Long targetDepartmentId,
    @Size(max = 2000) String reason,
    @DecimalMin("0.00") BigDecimal assetValue
) {}
```

同樣，`AssetTransferActionRequest.comment` 與 `AssetTransferRejectRequest.comment` 也需加上 `@Size(max = 1000)`。

---

### 🟡 [P2] `departmentId` 未驗證跨租戶歸屬 ✅ 已修正（2026-06-15）

**問題**

`service.create()` 直接使用 `req.departmentId()` 查詢 `deptInfoRepository.findByDeptId()`，但未驗證該 dept 是否屬於當前租戶。若 `deptInfoRepository` 查無資料，`departmentName` 設為 `null`，申請仍可正常建立——惡意用戶可指定他租戶的 `departmentId` 寫入申請單。

**建議修正**：透過 tenant-scoped 的 dept repository 查詢，或在建立前加上 tenant 歸屬驗證。

---

### 🟡 [P3] `getPendingTasks` 中間查詢未過濾租戶 ✅ 誤判（N/A）— `WorkflowStepLogRepository` extends `TenantScopedRepository`，`TenantFilterAspect` 已自動啟用 `@Filter` 

**問題**

`getPendingTasks()` 的第一步查詢 `stepLogRepository.findByAssigneeUserIdAndCompletedAtIsNull(userId)` **不包含租戶過濾**，會跨租戶返回所有符合 `userId` 的 step log。雖然後續 `repository.findByWorkflowInstanceIdIn(instanceIds)` 受 `@Filter` 保護，最終回傳資料仍限於當前租戶，但**中間的 workflowInstanceIds 集合可能包含跨租戶資料**，帶來資訊洩漏風險。

**建議**：`WorkflowStepLogRepository` 補充 tenant-scoped 查詢方法，或傳入 `tenantId` 參數過濾。

---

### 🟡 [P3] 前端 `AssetTransferDetailView` 直接顯示 `workflowInstanceId` ✅ 已修正（2026-06-15）— 隨 #1 一併處理

```html
<span class="info-value mono">{{ app.workflowInstanceId }}</span>
```

`workflowInstanceId` 是內部資料庫 BIGINT ID，暴露給一般使用者既無意義，也可能被用於 workflow 相關 API 的探測。

**建議**：從 Response DTO 移除 `workflowInstanceId`（對應 P1 第一條修正），UI 不顯示此欄位。

---

### 🟢 [P4] `generateApplicationNo` 在高並發下有碰撞風險 ✅ 已修正（2026-06-15）

```java
private String generateApplicationNo() {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    return "AT-" + timestamp;
}
```

毫秒精度在高並發下仍可能重複（尤其容器化環境多實例並行時）。DB 有 `UNIQUE (tenant_id, application_no)` 保護，但碰撞時會拋出未處理的 `DataIntegrityViolationException` 而非業務異常。

**建議**：改用 `UUID.randomUUID()` 或資料庫 sequence，例如：
```java
return "AT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
```

---

## 二、程式碼品質審查（Code Review）

### ✅ 完全無測試覆蓋 ── 已修復

已補充以下測試類別（共 52 個測試，全數通過）：

| 測試類別 | 重點場景 |
|---|---|
| `AssetTransferServiceTest` | `create` 部門驗證、UUID applicationNo、`submit` 申請人驗證 + 狀態機、`approve`/`reject` currentAssignee IDOR 保護、最終步驟 COMPLETED 轉換、`resubmit` 申請人驗證 + 無流程保護、`getById`/`getMyApplications` 查詢 |
| `AssetTransferControllerTest` | 401（無 token）、403（無權限）、200（正確權限）、`@Valid` 驗證失敗（blank assetCode / 無效 transferType / 缺 departmentId / 缺 targetStepId） |

**檔案路徑**：
- `backend/src/test/java/com/taipei/iot/assettransfer/service/AssetTransferServiceTest.java`
- `backend/src/test/java/com/taipei/iot/assettransfer/controller/AssetTransferControllerTest.java`

---

### ✅ `status` 使用裸字串而非 Enum ── ✅ 已修正（2026-06-15）

建立 `AssetTransferStatus` enum（`com.taipei.iot.assettransfer.enums`）：

```java
public enum AssetTransferStatus {
    DRAFT, PROCESSING, COMPLETED, REJECTED, CANCELLED
}
```

**更新清單**：
- `AssetTransferApplicationEntity.status`：改為 `AssetTransferStatus`，加上 `@Enumerated(EnumType.STRING)`
- `AssetTransferService`：所有字串比較改用 `==`，`setStatus()` 一律傳入 enum 常數
- `AssetTransferResponse` DTO：`status` 欄型改為 `AssetTransferStatus`
- `AssetTransferServiceTest` / `AssetTransferControllerTest`： fixture builder 與 assertion 改用 enum 常數

---

### ✅ 前端 `handleSubmit` 兩步操作無原子性 ── ✅ 已修正（2026-06-15）

**方案：後端新增 `POST /v1/auth/asset-transfer/create-and-submit` 原子端點**

```java
// AssetTransferService
@Transactional
public AssetTransferResponse createAndSubmit(AssetTransferCreateRequest req, String applicantId) {
    AssetTransferResponse draft = create(req, applicantId);
    return submit(draft.id(), applicantId);
}
```

建立草稿與啟動流程在同一個 transaction 內完成：若流程啟動失敗，草稿也隨 rollback 一併撤銷，不留孤兒記錄。

前端 `handleSubmit` 改呼叫 `createAndSubmitApplication()`（單一 API 呼叫），移除原本的 `submitApplication()` 二次呼叫。

**修改清單**：
- `AssetTransferService.createAndSubmit()`（新增）
- `AssetTransferController` `POST /create-and-submit`（新增）
- `frontend/src/api/assetTransfer/index.ts` `createAndSubmitApplication()`（新增）
- `AssetTransferCreateView.vue` `handleSubmit()`（改呼叫新 API）

---

### ✅ `Objects.requireNonNull(app, "app")` 在 builder 回傳後多餘 — N/A

檢查現有程式碼，`AssetTransferService` 的所有 `repository.save()` 呼叫均已為直接傳入 `app`，無 `Objects.requireNonNull` 包裝，此問題不存在於目前程式碼中。

---

### ✅ 前端 `currentAssignee` 顯示 userId 而非顯示名稱 — N/A

已隨 #1 一併處理：
- 後端 `AssetTransferResponse` 已包含 `currentAssigneeName`，由 service 層透過 `userRepository` 解析顯示名稱
- 前端 `AssetTransferApplicationDto` 已定義 `currentAssigneeName: string | null`
- `AssetTransferDetailView` 已改為 `{{ app.currentAssigneeName || app.currentAssignee }}`（優先顯示名稱，無名稱時 fallback 到 userId）

---

### ✅ `AssetTransferDetailView` 的 `stepActiveIndex` 硬編碼且有錯誤 ── 已修復（2026-06-15）

**修正一：`stepStatus`**：`'APPROVED'` 不存在於 `AssetTransferStatus`，改為 `'COMPLETED'`

```typescript
// Before
if (app.value?.status === 'APPROVED') return 'success'
// After
if (app.value?.status === 'COMPLETED') return 'success'
```

**修正二：`stepActiveIndex`**：`COMPLETED` / `REJECTED` 回傳 `4`（超出 4 個 steps 的最後 index），讓 `el-steps` 的 `:finish-status` 正確驅動所有步驟的完成樣式

```typescript
case 'COMPLETED': return 4  // 原為 3
case 'REJECTED':  return 4  // 原為 3
```

---

### ✅ DB migration 的 `transfer_type` 備註値與實際使用値不一致 ── 已修復（2026-06-15）

1. **更新 V76 注解**：`-- TRANSFER / DISPOSAL / SCRAP` 改為 `-- INTERNAL / EXTERNAL / DISPOSAL / RETURN`
2. **新增 V85 migration**：補充 check constraint

```sql
-- V85__asset_transfer__add_transfer_type_check.sql
ALTER TABLE asset_transfer_applications
    ADD CONSTRAINT chk_asset_transfer_type
        CHECK (transfer_type IN ('INTERNAL', 'EXTERNAL', 'DISPOSAL', 'RETURN'));
```

現在 DB 層、後端 DTO `@Pattern`、前端 `TRANSFER_TYPES` 三者一致，非法値在任一層均會被擋回。

---

### 🟢 [低] 前端錯誤處理全部使用相同訊息，不利除錯

所有 `catch` block 均顯示 `t('assetTransfer.loadFailed')`，不論實際錯誤為 400 / 403 / 500 / 網路逾時：

```typescript
} catch {
  ElMessage.error(t('assetTransfer.loadFailed'))
}
```

**建議**：至少區分業務錯誤（後端回傳 `errorCode`）與技術錯誤（網路 / 5xx）。可在 axios interceptor 統一處理，或在呼叫端 catch `BusinessException` 回傳的 error message。

---

## 三、問題清單彙整

| # | 嚴重度 | 類型 | 問題摘要 | 修正狀態 |
|---|---|---|---|---|
| 1 | 🔴 P1 | 安全 | Controller 直接回傳 JPA Entity，`tenantId`/`workflowInstanceId` 外洩 | ✅ 已修正（2026-06-15） |
| 2 | 🔴 P1 | 安全 | `approve`/`reject` 未驗證 `currentAssignee`（IDOR） | ✅ 已修正（2026-06-15） |
| 3 | 🔴 P1 | 安全 | `resubmit` 未驗證申請人身份 | ✅ 已修正（2026-06-15） |
| 4 | 🟡 P2 | 安全 | 輸入驗證不完整（`@Size`、`@Pattern`、`@DecimalMin` 缺失） | ✅ 已修正（2026-06-15） |
| 5 | 🟡 P2 | 安全 | `departmentId` 無跨租戶歸屬驗證 | ✅ 已修正（2026-06-15） |
| 6 | 🟡 P3 | 安全 | `getPendingTasks` stepLog 中間查詢未過濾租戶 | ✅ 誤判（N/A）— `WorkflowStepLogRepository` extends `TenantScopedRepository`，`TenantFilterAspect` 已自動啟用 `@Filter` |
| 7 | 🟡 P3 | 安全 | 前端顯示 `workflowInstanceId` 內部 ID | ✅ 已修正（2026-06-15）— 隨 #1 一併處理 |
| 8 | 🟢 P4 | 安全 | `generateApplicationNo` 並發碰撞 → 非業務異常 | ✅ 已修正（2026-06-15） |
| 9 | ✅ | 品質 | 完全無測試覆蓋 | ✅ 已修 |
| 10 | ✅ | 品質 | `status` 使用裸字串而非 Enum | ✅ 已修 |
| 11 | ✅ | 品質 | `handleSubmit` 兩步操作無原子性，草稿孤兒化 | ✅ 已修 |
| 12 | ✅ | 品質 | `Objects.requireNonNull(app)` 冗餘（×5 處） | ✅ N/A（程式碼中不存在此模式） |
| 13 | ✅ | 品質 | 前端 `currentAssignee` 顯示 userId 而非顯示名稱 | ✅ N/A（已隨 #1 一併處理） |
| 14 | ✅ | 品質 | `stepStatus` computed 使用不存在的 `'APPROVED'` 字串 | ✅ 已修 |
| 15 | ✅ | 品質 | migration 注解 `transferType` 値與程式碼不一致，缺少 check constraint | ✅ 已修 |
| 16 | ✅ | 品質 | 前端錯誤處理全部顯示相同訊息 | ✅ 已修 |

---


