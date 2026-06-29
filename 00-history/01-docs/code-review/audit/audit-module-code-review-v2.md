# Audit 模組 Code Review & Security Review

> 審查日期：2026-05-27  
> 範圍：`com.taipei.iot.audit` 下所有 production + test code  
> 涵蓋：controller、service、aspect、async writer、job、repository、DTO、utility、entity、enums

---

## 一、整體評價

| 維度       | 修復前   | 修復後   | 說明 |
| ---------- | -------- | -------- | ---- |
| 安全性     | 8.0/10   | 9.0/10   | sortBy 白名單 ✅、CSV injection ✅、export rate limit ✅、DTO @Size + @Valid ✅ |
| 正確性     | 8.2/10   | 8.7/10   | DataScope 邏輯正確；SUPER_ADMIN 排除正確；purge job per-tenant ✅；deptId scope 語意修正 ✅ |
| 可維護性   | 8.5/10   | 8.5/10   | AOP + 非同步寫入設計清晰；enum 強制型別安全；DTO 層分離乾淨 |
| 可觀測性   | 7.5/10   | 7.5/10   | async 寫入失敗僅 log.error 無 metric；purge job 無告警機制 |
| **總分**   | **8.1/10** | **8.6/10** | A-1～A-6 修復後安全性 +1.0、正確性 +0.5 |

**架構亮點**：
- `@AuditEvent` annotation + AOP 切面，業務程式碼零侵入
- `AuditAsyncWriter` 非同步寫入，不影響主要請求效能
- `PayloadSanitizer` 自動遮蔽 password/token 等敏感欄位
- `CallerRunsPolicy` 確保佇列滿時不丟事件（退化為同步寫入）
- SYSTEM context 繞過 @Filter 後手動重建完整 tenantId + DataScope 過濾

---

## 二、發現問題

### 🔴 高風險

#### A-1. ~~`sortBy` 參數未做白名單驗證 — 潛在 Property Injection~~ ✅ 已修復

**檔案**：[AuditService.java](../../backend/src/main/java/com/taipei/iot/audit/service/AuditService.java)

**修復內容**：新增 `ALLOWED_SORT_FIELDS` 白名單（createTime, username, userLabel, eventType, eventDesc, ipAddress, executionTime），非白名單值一律 fallback 為 `createTime`。

**測試覆蓋**：`AuditServiceTest` 新增 3 個測試驗證白名單拒絕/允許/完整性。

---

#### A-2. ~~CSV Export 存在 CSV Injection（Formula Injection）風險~~ ✅ 已修復

**檔案**：[AuditService.java](../../backend/src/main/java/com/taipei/iot/audit/service/AuditService.java) `csvEscape()`

**修復內容**：在 `csvEscape()` 方法中新增前綴防護 — 若值以 `=`, `+`, `-`, `@`, `\t`, `\r` 開頭，自動加上單引號 `'` 中和公式執行。同時改為 package-visible 以利單元測試。

**測試覆蓋**：`AuditServiceTest` 新增 7 個測試驗證各種公式注入字元（=, +, -, @）以及正常值不受影響、整合 exportCsv 防護。

---

### 🟠 中風險

#### A-3. ~~Export 端點缺少速率限制 — DoS 風險~~ ✅ 已修復

**檔案**：[AuditController.java](../../backend/src/main/java/com/taipei/iot/audit/controller/AuditController.java)

**修復內容**：在 `exportUsageHistory()` 加上 `@RateLimit(key = "audit-export", limit = 5, period = 60)` — 同一 IP 每分鐘最多 5 次匯出請求，超過則返回 429。

---

#### A-4. ~~`AuditQueryRequest` 缺少輸入長度限制~~ ✅ 已修復（含 A-12）

**檔案**：[AuditQueryRequest.java](../../backend/src/main/java/com/taipei/iot/audit/dto/AuditQueryRequest.java)、[AuditController.java](../../backend/src/main/java/com/taipei/iot/audit/controller/AuditController.java)

**修復內容**：
1. `AuditQueryRequest` 加上 `@Size` 驗證：userName(100)、eventDesc(50)、startTimestamp/endTimestamp(30)、sortBy(50)、sort(10)
2. Controller 改用 `@Valid AuditQueryRequest request` 取代手動 `new` + `set`，由 Spring MVC 自動繫結 + 觸發 Bean Validation（同時修復 A-12）

---

#### A-5. ~~`AuditPurgeJob` 固定 7 天刪除 — 缺乏可設定性且無保留策略~~ ✅ 已修復

**檔案**：[AuditPurgeJob.java](../../backend/src/main/java/com/taipei/iot/audit/job/AuditPurgeJob.java)、[SettingKey.java](../../backend/src/main/java/com/taipei/iot/setting/enums/SettingKey.java)、[UserEventLogRepository.java](../../backend/src/main/java/com/taipei/iot/audit/repository/UserEventLogRepository.java)

**修復內容**：
1. 新增 `SettingKey.AUDIT_RETENTION_DAYS`（預設 180 天）
2. PurgeJob 改為遍歷所有啟用租戶，各自查詢 `system_settings` 取得保留天數
3. 新增 `deleteByTenantIdAndCreateTimeBefore` / `deleteByTenantIdNullAndCreateTimeBefore` 按租戶刪除
4. 無效設定值（非數字/負數）自動 fallback 為預設 180 天
5. V54 migration 為所有現有租戶插入預設值

**測試覆蓋**：`AuditPurgeJobTest` 重寫 3 個測試（per-tenant retention、no tenants、invalid value fallback）。

---

#### A-6. ~~`DataScopeHelper.getVisibleDeptIds()` 返回空 = ALL — admin 可看到 deptId=null 的紀錄~~ ✅ 已修復

**檔案**：[AuditService.java](../../backend/src/main/java/com/taipei/iot/audit/service/AuditService.java)、[AuthServiceImpl.java](../../backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java)

**修復內容**（兩部分）：

1. **查詢端**：移除 `cb.isNull(root.get("deptId"))`。非 ALL scope 的管理者只能看到自己可見部門的紀錄，deptId=null 的紀錄（帳號不存在的登入失敗等）只有 ALL scope 才能看到。

2. **寫入端**：新增 `logLoginFailure()` 方法。登入失敗時（密碼錯誤、帳號鎖定等），若帳號存在則查出其所屬場域的 tenantId + deptId 寫入稽核紀錄，讓各場域 DEPT_ADMIN 可在稽核畫面看到「我的部門有帳號被嘗試暴力登入」。

**行為矩陣**：

| 情境 | deptId | 可見性 |
|------|--------|--------|
| 登入失敗 - 帳號不存在 | null | 僅 ALL scope |
| 登入失敗 - 帳號存在（密碼錯/鎖定） | 該帳號的 deptId ✅ | DEPT_ADMIN 可見 |
| 登入失敗 - 帳號存在但未指派部門 | null | 僅 ALL scope |
| SUPER_ADMIN 操作 | null | 已被 subquery 排除 |
| 認證成功的一般操作 | 從 JWT UserInfo 帶入 | DEPT_ADMIN 可見 |

**各角色可見性說明**：

| 查詢者角色 | DataScope | visibleDeptIds | 能否看到 deptId=null 紀錄 |
|-----------|-----------|----------------|--------------------------|
| ADMIN（場域管理者） | ALL | 空（不限制） | ✅ 可以 — 只受 tenantId 過濾 |
| DEPT_ADMIN | THIS_LEVEL / THIS_LEVEL_AND_BELOW | [具體 deptId] | ❌ 不行 — `IN (deptIds)` 不含 null |
| DEPT_USER | — | — | 只看自己的紀錄 |

> **設計決策**：場域管理者（ADMIN, ALL scope）對整個場域負責，可看到所有紀錄含 deptId=null（如未指派部門的帳號被暴力破解）。DEPT_ADMIN 只管自己部門，未歸屬任何部門的事件不在其管轄範圍內。

---

### 🟡 低風險 / 建議

#### A-7. `isAdminRole()` 在 Controller 層判定 — 缺乏分層一致性 ✅

**檔案**：[AuditController.java](../../backend/src/main/java/com/taipei/iot/audit/controller/AuditController.java)

**問題**：
```java
private static final Set<String> ADMIN_ROLES = Set.of(
    "ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_DEPT_ADMIN");

private boolean isAdminRole() { ... }
```
1. `SUPER_ADMIN` 被歸入 admin → service 層又排除 SUPER_ADMIN 的操作紀錄。若 SUPER_ADMIN 呼叫此 API，`isAdmin=true` 但 SUPER_ADMIN 自身的紀錄被 subquery 排除，邏輯正確但不直觀。
2. 角色判定邏輯散落在 Controller，若未來有其他入口（如排程報表）需重複。

**修復**：將 `isAdminRole()` 抽至 `SecurityContextUtils.hasAnyAuthority(String... authorities)` 共用方法，Controller 改呼叫 `SecurityContextUtils.hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_DEPT_ADMIN")`。移除 Controller 中的 `ADMIN_ROLES` 常數與私有方法。

---

#### A-8. XLSX Export 無檔案大小/筆數上限保護 ✅

**檔案**：[AuditService.java](../../backend/src/main/java/com/taipei/iot/audit/service/AuditService.java) `queryForExport()` / `exportXlsx()`

**問題**：
```java
Pageable exportPage = PageRequest.of(0, 10000, ...);
```
10,000 筆 × 9 欄 XLSX 在 XSSFWorkbook（DOM 模式）可消耗 50-100MB heap。

**修復**：
1. `XSSFWorkbook` → `SXSSFWorkbook(100)` streaming mode，記憶體中僅保留 100 列
2. 匯出上限由 10,000 降至 `EXPORT_MAX_ROWS = 5000`
3. 移除 `autoSizeColumn()`（SXSSFWorkbook 不支援且為效能瓶頸）

---

#### A-9. `AuditAsyncWriter.saveAsync()` 失敗無重試且無 metric — 不修

**檔案**：[AuditAsyncWriter.java](../../backend/src/main/java/com/taipei/iot/audit/async/AuditAsyncWriter.java)

**問題**：
```java
} catch (Exception ex) {
    log.error("Audit async write failed: {} {} {}", eventType, apiEndpoint, ex.getMessage());
    // best-effort: 不重試，不丟失靜默
}
```
稽核紀錄丟失在合規場景中是嚴重問題。

**決議**：不加 Micrometer/dead-letter，此 catch 僅在基礎設施故障（DB down/連線池耗盡）才觸發，正常邏輯不會走到。已加 TODO 註解，日後透過 ELK 關鍵字告警規則（match `"Audit async write failed"`）觸發 email 通知即可。

---

#### A-10. `PayloadSanitizer` 的 SENSITIVE_FIELDS 使用精確匹配 — 可能遺漏變體 ✅

**檔案**：[PayloadSanitizer.java](../../backend/src/main/java/com/taipei/iot/audit/util/PayloadSanitizer.java)

**問題**：
```java
private static final Set<String> SENSITIVE_FIELDS =
    Set.of("secret", "password", "newPassword", "token", "accessToken", "refreshToken");
```
不涵蓋 `oldPassword`、`confirmPassword`、`bindPassword`（LDAP）、`clientSecret`（OIDC）等欄位。

**修復**：移除 `SENSITIVE_FIELDS` Set，改用 `isSensitive(String)` 方法以 contains 模式匹配：
```java
private static boolean isSensitive(String fieldName) {
    String lower = fieldName.toLowerCase();
    return lower.contains("password") || lower.contains("secret")
            || lower.contains("token") || lower.equals("authorization");
}
```

---

#### A-11. `getMyEventLogs()` 缺少租戶過濾 — 依賴 @Filter ✅

**檔案**：[AuditService.java](../../backend/src/main/java/com/taipei/iot/audit/service/AuditService.java)

**問題**：`getMyEventLogs()` 不設 SYSTEM context，依賴 Hibernate `@Filter(tenantFilter)` 自動過濾。此設計正確（只查自己的紀錄 + @Filter 保證租戶隔離），但若 @Filter 未啟用（如 `TenantFilterAspect` 被繞過），userId 相同但 tenantId 不同的紀錄可能洩漏。

**修復**：加上顯式 `predicates.add(cb.equal(root.get("tenantId"), currentTenantId))` 作為 defense-in-depth，在 @Filter 失效時仍保證租戶隔離。

---

#### A-12. ~~缺少 `@Validated` on `AuditQueryRequest`~~ ✅ 已隨 A-4 修復

**修復內容**：Controller 兩個端點改用 `@Valid AuditQueryRequest request` 參數，Spring MVC 自動繫結 query params + 觸發 Bean Validation。

---

## 三、安全正面發現（優點）

| # | 優點 | 說明 |
|---|------|------|
| ✅ | **PayloadSanitizer 自動遮蔽** | password、token、secret 等欄位在寫入前即被替換為 `***`，防止機敏資料寫入稽核表 |
| ✅ | **SUPER_ADMIN 排除** | 超級管理員的操作不出現在各租戶的稽核畫面，避免洩漏平台層操作 |
| ✅ | **SYSTEM context 手動恢復** | `getUserUsageHistory()` 在 finally 中正確恢復原 TenantContext |
| ✅ | **CallerRunsPolicy** | 非同步佇列滿時不丟事件，退化為同步寫入 |
| ✅ | **enum 強型別** | `AuditEventType` 編譯期檢查，無法寫入任意字串 |
| ✅ | **IP 取得策略** | 與 RateLimitInterceptor 一致使用 `getRemoteAddr()`，不信任 X-Forwarded-For |
| ✅ | **DEPT_USER 只看自己** | 非管理者 `isAdmin=false` 嚴格限制只查自己的 userId 紀錄 |
| ✅ | **@PreAuthorize AUDIT_LIST** | 查詢/匯出需要 `AUDIT_LIST` 權限，非任意使用者可存取 |

---

## 四、測試覆蓋狀態

| 測試檔 | 涵蓋內容 | 狀態 |
|--------|---------|------|
| `AuditServiceTest` | getUserUsageHistory spec 構建、tenant 過濾、DataScope 過濾 | ✅ |
| `AuditControllerTest` | 端點權限、分頁、export format | ✅ |
| `BaseLoggerAspectTest` | AOP 切面觸發、errorCode 捕獲 | ✅ |
| `AuditAsyncWriterTest` | async 寫入、失敗不拋例外 | ✅ |
| `PayloadSanitizerTest` | 遮蔽 password/token、巢狀結構、truncate | ✅ |
| `AuditPurgeJobTest` | purge 刪除邏輯 | ✅ |

覆蓋合理，但缺少：
- CSV injection 案例（A-2）
- sortBy 注入案例（A-1）
- deptId=null 邊界案例（A-6）

---

## 五、修復優先級建議

### 立即修復（本輪 Sprint）
1. **A-1** ~~`sortBy` 白名單驗證~~ ✅ 已修復 + 3 tests
2. **A-2** ~~CSV formula injection 防護~~ ✅ 已修復 + 7 tests
3. **A-4** ~~`AuditQueryRequest` 加 `@Size` + `@Valid`~~ ✅ 已修復（含 A-12）
4. **A-10** `PayloadSanitizer` 改用 contains 匹配 — 涵蓋 `oldPassword`, `bindPassword` 等

### 近期處理（下一 Sprint）
5. **A-3** ~~Export 加 `@RateLimit` 限制~~ ✅ 已修復（limit=5, period=60s）
6. **A-5** ~~Purge job 保留天數改為 per-tenant 可設定~~ ✅ 已修復（預設 180 天 + V54 migration）
7. **A-8** ~~XLSX 改用 SXSSFWorkbook 或降低上限~~ ✅ 已修復（SXSSFWorkbook streaming + 上限 5000 筆）

### 後續優化
8. **A-6** ~~確認 deptId=null 的業務需求~~ ✅ 已修復（查詢端移除 isNull + 寫入端補 deptId）
9. **A-7** ~~isAdminRole() 抽共用~~ ✅ 已抽至 `SecurityContextUtils.hasAnyAuthority()`
10. **A-9** ~~加入寫入失敗 metric + dead-letter 機制~~ ⏭️ 不修 — 依賴 ELK 關鍵字告警即可
11. **A-10** ~~PayloadSanitizer contains match~~ ✅ 已修復（改用 contains 模式匹配）
12. **A-11** ~~getMyEventLogs 加顯式 tenantId 過濾~~ ✅ 已修復（defense-in-depth）

---

## 六、附錄：審查涵蓋的檔案清單

**Production code (12 files)**：
- `controller/AuditController.java`
- `service/AuditService.java`
- `aspect/BaseLoggerAspect.java`
- `async/AuditAsyncWriter.java`
- `job/AuditPurgeJob.java`
- `repository/UserEventLogRepository.java`
- `entity/UserEventLogEntity.java`
- `entity/AuditRevisionEntity.java`
- `entity/AuditRevisionListener.java`
- `dto/AuditQueryRequest.java`、`UserEventLogDto.java`、`LoginLogDto.java`
- `annotation/AuditEvent.java`
- `enums/AuditEventType.java`、`AuditCategory.java`
- `util/PayloadSanitizer.java`
- `config/AuditAsyncConfig.java`

**Test code (6 files)**：
- `AuditServiceTest.java`
- `AuditControllerTest.java`
- `BaseLoggerAspectTest.java`
- `AuditAsyncWriterTest.java`
- `PayloadSanitizerTest.java`
- `AuditPurgeJobTest.java`

**相依模組**：
- `DataScopeHelper` (`dept` module) — `getVisibleDeptIds()` 返回空=ALL/非空=限制
- `SecurityContextUtils` (`common`) — 取得 userId, username, UserInfo
- `TenantContext` (`tenant`) — 多租戶隔離 ThreadLocal
