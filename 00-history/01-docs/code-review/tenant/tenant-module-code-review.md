# Tenant 模組 Code Review

> 審查日期：2026-05-19
> 審查範圍：`backend/src/main/java/com/taipei/iot/tenant/` 全部檔案 + `frontend/src/views/admin/tenant/` + 相關 API、型別、路由、Sidebar

---

## 模組結構總覽

### Backend

```
tenant/
├── TenantContext.java                     # ThreadLocal 租戶 ID 容器
├── TenantAware.java                       # 標記介面（Entity 需實作）
├── TenantEntity.java                      # 租戶 JPA Entity
├── TenantEntityListener.java              # @PrePersist/@PreUpdate/@PreRemove 寫入防護
├── TenantFilterAspect.java                # AOP 切面：自動啟用 Hibernate @Filter
├── TenantInterceptor.java                 # MVC Interceptor：single/multi 模式切換
├── TenantProperties.java                  # @ConfigurationProperties (tenant.mode/defaultId)
├── TenantRepository.java                  # JPA Repository
├── TenantScopedRepository.java            # 標記介面（需 tenant filter 的 Repository）
├── package-info.java                      # Hibernate @FilterDef 全域定義
├── controller/
│   └── TenantAdminController.java         # REST API (SUPER_ADMIN only)
├── dto/
│   ├── CreateTenantRequest.java           # 建立請求 DTO
│   ├── UpdateTenantRequest.java           # 更新請求 DTO
│   └── TenantDto.java                     # 回應 DTO
└── service/
    └── TenantAdminService.java            # 業務邏輯層
```

### Frontend

```
frontend/src/
├── api/tenant/admin.ts                    # API 呼叫封裝
├── types/tenant.ts                        # TypeScript 介面定義
├── views/admin/tenant/
│   └── TenantManageView.vue              # 場域管理頁面（列表 + Dialog CRUD）
├── components/AppSidebar.vue              # 側邊欄（SUPER_ADMIN 限定入口）
├── router/index.ts                        # 路由設定 (meta: { superAdminOnly: true })
└── locales/zh-TW.ts, zh-CN.ts, en.ts     # i18n 字串
```

---

## 總體評價

模組分為兩層：

1. **Tenant 基礎設施**（TenantContext / TenantFilterAspect / TenantEntityListener / TenantInterceptor）— 提供整個系統的多租戶隔離基礎，設計紮實、fail-closed 策略正確。
2. **Tenant 管理功能**（TenantAdminService / Controller / 前端畫面）— 提供 SUPER_ADMIN 的 CRUD 管理介面，邏輯簡潔但存在若干安全和健壯性問題。

---

## 優點

### 1. Fail-Closed 的租戶隔離策略 (`TenantFilterAspect.java`)

```java
if (tenantId == null) {
    throw new IllegalStateException(
        "TenantContext is not set. All repository operations require a tenant context.");
}
```

未設定 TenantContext 時直接拋異常，不允許任何查詢通過，從源頭杜絕資料外洩。相比 fail-open（忽略 filter）策略，這是安全性更高的正確做法。

### 2. 三層寫入防護 (`TenantEntityListener.java`)

- **@PrePersist**：新增時自動填入 tenantId
- **@PreUpdate**：更新前驗證 Entity 的 tenantId == 當前租戶
- **@PreRemove**：刪除前同樣驗證

補足了 Hibernate @Filter 只保護讀取（SELECT）的缺陷。攻擊者即使繞過 Filter 取得其他租戶的 Entity 引用，也無法修改或刪除。

### 3. TenantScopedRepository 標記介面設計

透過 `instanceof TenantScopedRepository` 區分「需要 tenant filter 的 Repository」和「全域 Repository」（如 `UserRepository`、`TenantRepository`），避免在不需要租戶隔離的查詢上加上無謂限制。語義清晰，新增 Repository 時只需決定是否 implements 即可。

### 4. single / multi 模式切換 (`TenantInterceptor.java`)

```java
if ("single".equals(tenantProperties.getMode())) {
    TenantContext.setCurrentTenantId(tenantProperties.getDefaultId());
}
```

支援開發環境單租戶模式（省去選擇場域步驟）和生產環境多租戶模式，切換只需改 application.yml 設定。`afterCompletion()` 確保每次 request 結束後清除 ThreadLocal。

### 5. 管理 API 雙重權限保護

- `SecurityConfig`: `.requestMatchers("/v1/admin/tenants/**").hasRole("SUPER_ADMIN")`
- `TenantAdminController`: `@PreAuthorize("hasRole('SUPER_ADMIN')")`

即使一層失效，另一層仍能阻擋。Defense-in-depth 設計正確。

### 6. 前端路由守衛獨立於後端選單系統

```typescript
if (to.meta.superAdminOnly) {
    const isSuperAdmin = authStore.userInfo?.isSuperAdmin === true
    if (!isSuperAdmin) next('/')
    else next()
    return
}
```

場域管理路由不依賴後端動態選單（因為它不屬於任何租戶的選單），獨立用 `meta.superAdminOnly` 判斷，避免與 `hasRouteAccess()` 衝突。

### 7. 建立場域時同步建立初始 ADMIN 帳號

一次 API 呼叫完成場域建立 + 管理員帳號建立，降低操作步驟。使用 `TenantContext.setSystemContext()` 繞過 Aspect 限制來寫入 UserTenantMapping，`finally` 區塊確保恢復先前的 context。

### 8. 前端 API 呼叫使用 `encodeURIComponent`

```typescript
export const updateTenant = (tenantId: string, payload: UpdateTenantRequest) =>
  axiosIns.put<...>(`/admin/tenants/${encodeURIComponent(tenantId)}`, payload)
```

正確處理 URL 中的特殊字元，防止路徑注入。

---

## 需要改進的問題

### 1. [高] `CreateTenantRequest` 驗證不完整 — 部分填寫 Admin 欄位可能導致不一致

```java
// TenantAdminService.java:65-67
if (req.getAdminEmail() != null && !req.getAdminEmail().isBlank()
        && req.getAdminPassword() != null && !req.getAdminPassword().isBlank()) {
```

**問題**: 只檢查 email + password 是否同時存在，但 DTO 上的 `@Email` 和 `@Size(min=8)` 在欄位為 null 時會被 Bean Validation **跳過**（`@Email` 和 `@Size` 預設允許 null）。這意味著：

- 使用者傳入 `adminEmail: "not-an-email"` + `adminPassword: "short"` → Bean Validation 會驗證不通過 ✓
- 使用者傳入 `adminEmail: "test@example.com"` + `adminPassword: null` → 不會建立帳號，但使用者可能以為會建立 ✗
- 使用者傳入 `adminEmail: null` + `adminPassword: "password123"` → 不會建立帳號，密碼白填 ✗

**建議**: 加入自訂驗證邏輯或 class-level constraint，確保「要嘛 email + password 都填，要嘛都不填」的語義：

```java
@AssertTrue(message = "若提供 adminEmail 則 adminPassword 為必填")
private boolean isAdminFieldsConsistent() {
    boolean hasEmail = adminEmail != null && !adminEmail.isBlank();
    boolean hasPass = adminPassword != null && !adminPassword.isBlank();
    return hasEmail == hasPass;
}
```

### 2. [高] `tenantCode` 缺少格式驗證 — 可能導致 URL routing 問題

```java
@NotBlank
private String tenantCode;
```

**問題**: `tenantCode` 只驗證 `@NotBlank`，使用者可以傳入含空格、中文、特殊字元（如 `/`, `..`）的值。由於 tenantCode 可能被用在 URL path 或作為識別碼，不規範的值可能造成：
- URL 路由問題
- 資料庫索引效能問題
- 跨系統整合時的兼容性問題

**建議**: 加入正規表達式限制：

```java
@NotBlank
@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$", message = "場域代碼須為 2-30 字元，大寫英文開頭，僅含大寫英文、數字、底線")
private String tenantCode;
```

### 3. [中等] `tenantName` 未限制長度

```java
@NotBlank
private String tenantName;
```

**問題**: Entity 中 `@Column(length = 200)` 限制了 DB 層，但 DTO 沒有 `@Size(max = 200)`。超長字串會直接打到 DB 層才報 `DataTruncation` 或 `DataIntegrityViolationException`，回傳不友善的 500 錯誤。

**建議**: 在 DTO 加入 `@Size(max = 200)`。

### 4. [中等] `listTenants()` 在記憶體中排序 — 資料量大時效能不佳

```java
return tenantRepository.findAll().stream()
        .sorted((a, b) -> { ... })
        .map(this::toDto)
        .collect(Collectors.toList());
```

**問題**: `findAll()` 一次載入所有租戶到記憶體，再用 Java Comparator 排序。雖然目前系統租戶數量有限（通常 < 100），但作為管理功能不應假設規模。

**建議**: 使用 JPA 的 `Sort` 或 `@Query(... ORDER BY ...)` 在 DB 層排序：

```java
return tenantRepository.findAll(Sort.by(Sort.Direction.DESC, "createTime"))
        .stream().map(this::toDto).collect(Collectors.toList());
```

### 5. [中等] `createTenant` 的 `@AuditEvent(AuditEventType.CREATE_USER)` 語義錯誤

```java
@PostMapping
@AuditEvent(AuditEventType.CREATE_USER)
public BaseResponse<TenantDto> createTenant(...) {
```

**問題**: 建立場域使用了 `CREATE_USER` 的審計事件類型，語義不符。審計日誌中會記錄為「建立使用者」而非「建立場域」，影響審計追蹤的可讀性。

**建議**: 
- 新增 `AuditEventType.CREATE_TENANT` 枚舉值
- 或至少使用更通用的事件類型

### 6. [中等] `updateTenant` 和 `toggleEnabled` 缺少審計記錄

**問題**: `createTenant` 有 `@AuditEvent`，但 `updateTenant` 和 `toggleEnabled` 沒有。場域的啟停用是重要的安全操作（停用場域會影響該場域下所有使用者），應該留下審計軌跡。

**建議**: 為 `updateTenant` 和 `toggleEnabled` 添加適當的 `@AuditEvent` 標註。

### 7. [中等] 停用場域未考慮對現有 session 的影響

```java
public void toggleEnabled(String tenantId, boolean enabled) {
    TenantEntity tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND));
    tenant.setEnabled(enabled);
    tenantRepository.save(tenant);
}
```

**問題**: 停用場域後，該場域下已登入的使用者仍持有有效的 JWT token，可以繼續操作直到 token 過期。

**建議**: 停用場域時應：
- 在 JWT 驗證流程（`JwtAuthenticationFilter`）中加入場域啟用狀態檢查，或
- 將被停用場域的所有 active refresh token 標記為失效（若有 token blacklist 機制）

### 8. [中等] `TenantContext` 恢復邏輯在異常場景下可能不正確

```java
String previous = TenantContext.getCurrentTenantId();
try {
    TenantContext.setSystemContext();
    // ... save mapping ...
} finally {
    if (previous != null) {
        TenantContext.setCurrentTenantId(previous);
    } else {
        TenantContext.clear();
    }
}
```

**問題**: 如果 `previous` 是 `"SYSTEM"`（即在已是 System Context 的情境下再次進入），恢復時會 `setCurrentTenantId("SYSTEM")` 而非 `setSystemContext()`。雖然目前 `isSystemContext()` 檢查的是 `"SYSTEM".equals(get())`，效果相同，但語義不清晰，未來若 system context 的判斷邏輯改變可能產生 bug。

**建議**: 封裝為 utility method：

```java
public static void runInSystemContext(Runnable action) {
    String previous = CURRENT_TENANT.get();
    try {
        setSystemContext();
        action.run();
    } finally {
        if (previous != null) CURRENT_TENANT.set(previous);
        else CURRENT_TENANT.remove();
    }
}
```

### 9. [低] 前端 `handleSubmit` 中 error 解析依賴 response 結構假設

```typescript
const error = err as { response?: { data?: { errorCode?: string } } }
const code = error?.response?.data?.errorCode
```

**問題**: axios response interceptor 已經 unwrap `res.data`，但 error path 中 response 的結構取決於 interceptor 是否 reject 原始 error。如果 interceptor 在 error path 修改了結構，這段 code 可能取不到 `errorCode`。

**建議**: 確認 axios error interceptor 的 reject 行為，考慮在 interceptor 統一將 error body 提取到 `err.data` 或類似標準化欄位。

### 10. [低] `deploymentMode` 未使用枚舉約束

```java
// CreateTenantRequest.java
private String deploymentMode = "CLOUD";

// TenantEntity.java
@Column(name = "deployment_mode", length = 20, nullable = false)
private String deploymentMode;
```

**問題**: `deploymentMode` 在後端為 String，前端 select 只提供 `CLOUD` / `ON_PREMISE` 兩個選項，但後端不做白名單驗證。惡意 API 呼叫可以傳入任意值。

**建議**: 後端定義 enum `DeploymentMode { CLOUD, ON_PREMISE }`，或在 Service 層加白名單驗證。

### 11. [低] `zh-CN.ts` 缺少場域管理的 i18n 字串

```typescript
// zh-CN.ts
tenant: {
    selectTitle: '请选择场域',
    selectSubtitle: '请选择您要进入的场域',
    expiredWarning: '临时 token 已过期，请重新登录',
    selectFailed: '场域选择失败，请重试',
},
```

**問題**: zh-TW 和 en 已加入完整的場域管理 i18n 字串（navLabel, title, subtitle, addBtn 等），但 zh-CN 缺少。切換到簡體中文後，場域管理頁面會顯示 fallback 語系（zh-TW）的繁體文字。

**建議**: 補齊 zh-CN 的 tenant 管理 i18n 字串。

### 12. [低] 前端沒有分頁機制

```typescript
const res = await listTenants()
tenants.value = res.body
```

**問題**: 一次載入所有場域，沒有分頁或搜尋功能。雖然場域數量通常不多，但作為管理介面的最佳實踐，應考慮未來擴展。

---

## 安全性總結

| 面向 | 狀態 | 說明 |
|------|------|------|
| 權限控制 | ✅ 良好 | 雙重保護（SecurityConfig + @PreAuthorize） |
| 前端路由守衛 | ✅ 良好 | `meta.superAdminOnly` 獨立判斷 |
| 租戶隔離（讀） | ✅ 良好 | Hibernate @Filter + fail-closed |
| 租戶隔離（寫） | ✅ 良好 | TenantEntityListener PreUpdate/PreRemove |
| 輸入驗證 | ⚠️ 不足 | tenantCode 無格式限制、tenantName 無長度限制 |
| 審計追蹤 | ⚠️ 不足 | update/toggle 缺審計、create 用錯 EventType |
| Session 失效 | ⚠️ 未實作 | 停用場域不影響已登入使用者 |

---

## 建議優先修復順序

1. **tenantCode 格式驗證** — 防止注入非法字元（問題 #2）
2. **Admin 欄位一致性驗證** — 避免使用者混淆（問題 #1）
3. **審計事件修正** — 語義正確、補齊缺失的審計點（問題 #5, #6）
4. **tenantName 長度限制** — 防止 500 錯誤（問題 #3）
5. **zh-CN i18n 補齊** — 功能完整性（問題 #11）
6. **deploymentMode 白名單驗證** — 資料正確性（問題 #10）
7. **停用場域 session 處理** — 安全性提升（問題 #7）

---

## 修改結果

> 修改日期：2026-05-19

所有高、中、低等級問題均已修復（#12 分頁除外，因場域數量有限無實際需求）。

### 問題 #1 [高] Admin 欄位一致性驗證 ✅ 已修復

**檔案**: `backend/.../tenant/dto/CreateTenantRequest.java`

新增 class-level `@AssertTrue` 驗證：

```java
@AssertTrue(message = "若提供 adminEmail 則 adminPassword 為必填，反之亦然")
private boolean isAdminFieldsConsistent() {
    boolean hasEmail = adminEmail != null && !adminEmail.isBlank();
    boolean hasPass = adminPassword != null && !adminPassword.isBlank();
    return hasEmail == hasPass;
}
```

### 問題 #2 [高] tenantCode 格式驗證 ✅ 已修復

**檔案**: `backend/.../tenant/dto/CreateTenantRequest.java` + `frontend/.../TenantManageView.vue`

- 後端：加入 `@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$")` 限制
- 前端：輸入框加入 `maxlength="30"`、自動轉大寫、字元過濾（僅允許 A-Z、0-9、底線）

### 問題 #3 [中等] tenantName 長度限制 ✅ 已修復

**檔案**: `backend/.../tenant/dto/CreateTenantRequest.java`、`UpdateTenantRequest.java`

兩個 DTO 的 `tenantName` 欄位均加入 `@Size(max = 200)`。

### 問題 #4 [中等] DB 層排序 ✅ 已修復

**檔案**: `backend/.../tenant/service/TenantAdminService.java`

改為使用 `tenantRepository.findAll(Sort.by(Sort.Direction.DESC, "createTime"))`，移除記憶體內排序。

### 問題 #5 [中等] 審計事件類型修正 ✅ 已修復

**檔案**: `backend/.../audit/enums/AuditEventType.java`、`AuditCategory.java`

- 新增 `AuditCategory.TENANT`
- 新增 `CREATE_TENANT`、`UPDATE_TENANT`、`TOGGLE_TENANT_ENABLED` 三個事件類型

### 問題 #6 [中等] 補齊審計記錄 ✅ 已修復

**檔案**: `backend/.../tenant/controller/TenantAdminController.java`

三個 API endpoint 均加入對應的 `@AuditEvent` 標註：
- `createTenant` → `@AuditEvent(AuditEventType.CREATE_TENANT)`
- `updateTenant` → `@AuditEvent(AuditEventType.UPDATE_TENANT)`
- `toggleEnabled` → `@AuditEvent(AuditEventType.TOGGLE_TENANT_ENABLED)`

### 問題 #7 [中等] 停用場域即時 Session 失效 ✅ 已修復

**新增檔案**: `backend/.../tenant/TenantEnabledCache.java`
**修改檔案**: `backend/.../auth/security/JwtAuthenticationFilter.java`、`TenantAdminService.java`

- 新增 `TenantEnabledCache`：ConcurrentHashMap 記憶體快取，記錄被停用的場域 ID
- `JwtAuthenticationFilter` 在驗證 JWT 後檢查 `tenantEnabledCache.isTenantDisabled(tenantId)`，若場域已停用回傳 403（errorCode: `10024`）
- `TenantAdminService.toggleEnabled()` 操作後同步更新快取

### 問題 #8 [中等] TenantContext 恢復邏輯封裝 ✅ 已修復

**檔案**: `backend/.../tenant/TenantContext.java`

新增 `runInSystemContext(Runnable action)` 工具方法，封裝 save/restore 邏輯，避免各處 try-finally 重複且易出錯。

### 問題 #9 [低] 前端 error 解析修正 ✅ 已修復

**檔案**: `frontend/.../views/admin/tenant/TenantManageView.vue`

- 確認 axios interceptor 的 reject 行為：error path 傳遞原始 AxiosError，`err.response.data` 可正確取得後端回應
- 修正 `adminEmailExists` 的錯誤碼比對從 `'20001'`（不存在）改為 `'20015'`（對應 `ErrorCode.USER_ALREADY_EXISTS`）
- 簡化 type casting 寫法

### 問題 #10 [低] deploymentMode 白名單驗證 ✅ 已修復

**新增檔案**: `backend/.../tenant/dto/DeploymentMode.java`
**修改檔案**: `backend/.../tenant/service/TenantAdminService.java`

- 新增 `DeploymentMode` 枚舉（`CLOUD`、`ON_PREMISE`），含安全解析方法 `fromString()`
- Service 層透過 `resolveDeploymentMode()` 做白名單驗證，非法值自動 fallback 為 `CLOUD`

### 問題 #11 [低] zh-CN i18n 補齊 ✅ 已修復

**檔案**: `frontend/src/locales/zh-CN.ts`

補齊場域管理相關的 30+ 個 i18n 字串（navLabel、title、subtitle、addBtn、dialog 標題、欄位標籤、錯誤訊息等）。

### 問題 #12 [低] 前端分頁 — 暫不處理

場域數量通常極少（< 50），加入分頁的開發成本與收益不符，暫時維持現狀。

---

## 修改後安全性總結

| 面向 | 狀態 | 說明 |
|------|------|------|
| 權限控制 | ✅ 良好 | 雙重保護（SecurityConfig + @PreAuthorize） |
| 前端路由守衛 | ✅ 良好 | `meta.superAdminOnly` 獨立判斷 |
| 租戶隔離（讀） | ✅ 良好 | Hibernate @Filter + fail-closed |
| 租戶隔離（寫） | ✅ 良好 | TenantEntityListener PreUpdate/PreRemove |
| 輸入驗證 | ✅ 已修復 | tenantCode 格式限制、tenantName 長度限制、deploymentMode 白名單、admin 欄位一致性 |
| 審計追蹤 | ✅ 已修復 | 三個 API 均有正確的 @AuditEvent，新增 TENANT 審計類別 |
| Session 失效 | ✅ 已修復 | TenantEnabledCache + JwtAuthenticationFilter 即時攔截已停用場域 |
