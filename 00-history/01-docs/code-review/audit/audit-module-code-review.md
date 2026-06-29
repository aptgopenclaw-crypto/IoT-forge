# Audit 模組 Code Review

> 初次審查：2026-05-09
> 第二次審查：2026-05-19
> 審查範圍：`backend/src/main/java/com/taipei/iot/audit/` 全部 17 個檔案 + 6 個測試檔 + 5 個 migration SQL

---

## 模組結構總覽

```
audit/
├── annotation/AuditEvent.java          # @AuditEvent 自訂標註
├── aspect/BaseLoggerAspect.java        # AOP 切面，攔截 @AuditEvent 方法
├── async/AuditAsyncWriter.java         # @Async 非同步寫入 user_event_log
├── config/AuditAsyncConfig.java        # 線程池設定 (auditExecutor)
├── controller/AuditController.java     # REST API（查詢、匯出）
├── dto/
│   ├── AuditQueryRequest.java          # 查詢請求 DTO
│   ├── LoginLogDto.java                # 登入日誌 DTO
│   └── UserEventLogDto.java            # 事件日誌 DTO
├── entity/
│   ├── AuditRevisionEntity.java        # Envers Revision 實體
│   ├── AuditRevisionListener.java      # Envers Revision 監聽器
│   └── UserEventLogEntity.java         # 使用者事件日誌實體
├── enums/
│   ├── AuditCategory.java              # 事件分類 enum（12 類）
│   └── AuditEventType.java             # 事件類型 enum（含 RBAC/IoT/Tenant/KPI/Dashboard）
├── job/AuditPurgeJob.java              # 排程：每晚 2:00 清除 7 天前日誌
├── repository/UserEventLogRepository.java
├── service/AuditService.java           # 查詢、匯出 CSV/XLSX
└── util/PayloadSanitizer.java          # 敏感欄位遮罩（密碼、token）

migrations/
├── V4__audit__create_tables.sql        # user_event_log + indexes
├── V4_1__audit__create_rev_info.sql    # rev_info (Envers)
├── V20__audit__add_login_logs_menu.sql # Login Logs 選單
├── V21__audit__backfill_dept_id.sql    # 回填 dept_id/user_label/email
├── V25__audit__drop_stats.sql          # 移除 Audit Statistics 選單
├── V26__audit__add_login_log_permission.sql  # 新增 LOGIN_LOG_LIST 權限
└── V27__audit__drop_login_logs_menu.sql      # 移除 Login Logs 選單
```

---

## 總體評價

整體架構設計良好，採用 AOP + @Async 實現了對業務程式碼無侵入的審計日誌記錄。PayloadSanitizer 對敏感欄位的防護、TenantContext 的跨租戶查詢處理、DataScope 整合都是深思熟慮的設計。測試覆蓋率令人滿意，核心流程和邊界條件都有對應的測試。

**第二次審查變更**：
- `AuditEventType` 新增 RBAC（CREATE_ROLE, UPDATE_ROLE, TOGGLE_ROLE_ENABLED, ASSIGN_ROLE_PERMISSIONS, UPDATE_MENU）、IOT（16 個）、TENANT（3 個）、KPI（8 個）、DASHBOARD（3 個）事件類型
- `AuditCategory` 新增 TENANT、IOT、KPI、DASHBOARD 分類
- `AuditControllerTest` 修正：新增 `@MockitoBean TenantEnabledCache` 解決 context 載入失敗
- 原始 10 個問題均未修正

以下依安全性、正確性、可維護性三個維度展開。

---

## 優點

### 1. AOP 非侵入式設計 (`BaseLoggerAspect.java`)

使用 `@Around("@annotation(auditEvent)")` 攔截標註 `@AuditEvent` 的方法，業務程式碼只需加一個 annotation 即可啟用審計，完全解耦。切面在 `finally` 區塊中寫入日誌，確保無論成功或異常都會記錄。

### 2. 非同步寫入不阻塞業務 (`AuditAsyncWriter.java`, `AuditAsyncConfig.java`)

`@Async("auditExecutor")` 將日誌寫入移到獨立線程池（core=2, max=8, queue=500），CallerRunsPolicy 確保隊列滿時由呼叫線程執行，不丟失日誌。`saveAsync()` 內部 catch 所有異常僅 log error，實現 best-effort 語義 — 審計失敗不影響業務。

### 3. PayloadSanitizer 敏感欄位遮罩 (`PayloadSanitizer.java`)

遞迴遍歷 JSON 樹，對 `secret`, `password`, `newPassword`, `token`, `accessToken`, `refreshToken` 欄位替換為 `***`。支援巢狀物件和陣列，且限制最大長度 2000 字元。測試覆蓋了密碼、token、巢狀敏感欄位、超長 payload 截斷、空參數等場景。

### 4. DataScope 整合 (`AuditService.java:71-77`)

管理員查詢審計日誌時，透過 `dataScopeHelper.getVisibleDeptIds()` 限制只能看到自己權限範圍內的部門資料。`DEPT_ADMIN` 看自己部門、`ADMIN` 看租戶內全部、`SUPER_ADMIN` 看全部。對 `ALL` scope 回傳空 list，不添加部門過濾條件。

### 5. TenantContext 安全處理

- **BaseLoggerAspect**: 在主線程上捕獲 ThreadLocal 值（line 34-41），傳入 async writer，避免跨線程 ThreadLocal 丟失。
- **AuditAsyncWriter**: `@Async` 執行緒中手動 `TenantContext.setSystemContext()` 再 `finally { clear() }`，確保非同步線程的租戶隔離正確。
- **AuditService.getUserUsageHistory()**: 先備份當前 tenantId → setSystemContext 繞過 Hibernate @Filter → 在手寫 Specification 中自行過濾 tenantId → finally 恢復。
- **AuditPurgeJob**: 排程任務無 HTTP request context，手動 setSystemContext + finally clear。

### 6. AuditEventType 編譯期安全

`@AuditEvent(AuditEventType.XXX)` 強制從 enum 選取，無法傳入任意字串。每個 enum 值關聯了 `AuditCategory` 和 `success` 屬性，`errorCode()` 方法自動回傳 "00000" 或 "99999"，避免手寫錯誤碼不一致。

### 7. 匯出功能完整 (`AuditService.java:176-239`)

支援 CSV（含 BOM for Excel 相容）和 XLSX 兩種格式，CSV 有正確的 RFC 4180 轉義（逗號、雙引號、換行），XLSX 有標題列樣式和自動欄寬。匯出上限 10000 筆防止記憶體耗盡。

### 8. AuditController 權限控制

- `/user/usage/history`: `hasAnyRole('ADMIN','SUPER_ADMIN','DEPT_ADMIN') or hasAuthority('AUDIT_LIST')`
- `/user/usage/history/export`: `hasAnyRole('ADMIN','SUPER_ADMIN','DEPT_ADMIN') or hasAuthority('AUDIT_EXPORT')`
- `/user/login/my`: `authenticated()`（SecurityConfig 中設定）

匯出功能自身也標註了 `@AuditEvent(EXPORT_AUDIT)`，記錄誰在何時匯出了審計日誌 — 自己審計自己的做法很好。

### 9. 測試覆蓋率高

| 測試類別 | 覆蓋場景 |
|----------|----------|
| `BaseLoggerAspectTest` | 成功寫入、BusinessException 錯誤碼、RuntimeException 預設錯誤碼、X-Forwarded-For IP 提取 |
| `AuditAsyncWriterTest` | 正常持久化、DB 失敗不拋異常、null tenantId |
| `AuditControllerTest` | 管理員查詢、超級管理員查詢、VIEWER 403、已認證查自己日誌、未認證 401、CSV/XLSX 匯出授權 |
| `AuditPurgeJobTest` | 7 天前刪除、0 筆刪除不報錯 |
| `AuditServiceTest` | 分頁查詢、username/eventDesc/eventType/時間範圍過濾、CSV 內容/轉義/XLSX 輸出 |
| `PayloadSanitizerTest` | 密碼/token/巢狀/refreshToken 遮罩、空陣列、超長截斷 |

---

## 需要改進的問題

> ⚠️ 以下問題自 2026-05-09 首次審查至今（2026-05-19）均未修正。

### 1. [中等] `BaseLoggerAspect.getClientIp()` 信任 `X-Forwarded-For` header

```java
// BaseLoggerAspect.java:73-77
private String getClientIp() {
    HttpServletRequest req = getRequest();
    if (req == null) return null;
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
        return xff.split(",")[0].trim();
    }
    return req.getRemoteAddr();
}
```

**問題**: `X-Forwarded-For` 是客戶端可任意偽造的 HTTP header。攻擊者可以在請求中帶入任意 IP，使審計日誌中的 IP 欄位不可信。這與 `RateLimitInterceptor` 的處理方式（直接使用 `getRemoteAddr()`）不一致 — 同一個專案中，速率限制用了安全的 TCP 層 IP，審計日誌卻用了可偽造的 header。

**建議**: 
- 若前方有受信任的 Nginx/反向代理，應使用 Spring 的 `ForwardedHeaderFilter` 統一處理，而非手動讀取 header
- 若無反向代理（目前架構為 Client → Tomcat），應直接使用 `getRemoteAddr()`
- 至少應與 `RateLimitInterceptor` 保持一致的 IP 取得策略

**風險**: 審計日誌的 IP 欄位被用於安全調查和合規審查，若 IP 可偽造，將削弱事件溯源的可信度。

### 2. [中等] `AuditPurgeJob` 硬編碼 7 天保留期

```java
// AuditPurgeJob.java:26
LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
```

保留天數應可設定（如 `@Value("${audit.retention-days:7}")`），方便不同環境調整（開發環境 3 天、生產環境 90 天）。

### 3. [中等] `AuditQueryRequest` 缺少輸入驗證

```java
// AuditQueryRequest.java — 無任何 validation annotation
public class AuditQueryRequest {
    private String userName;
    private String eventDesc;
    private String startTimestamp;
    private String endTimestamp;
    private String sortBy;
    private String sort;
}
```

- `sortBy` 沒有白名單驗證，攻擊者可以傳入任意欄位名稱，雖然 JPA Specification 會忽略無效欄位（不會 SQL injection），但可能導致非預期行為或錯誤
- `startTimestamp` / `endTimestamp` 沒有格式驗證，傳入非法格式會在 `OffsetDateTime.parse()` 拋出 `DateTimeParseException`，目前依賴 GlobalExceptionHandler 捕獲後回傳 500
- `pageSize` 沒有上限，攻擊者可以傳入 `pageSize=999999` 造成大量 DB 查詢

**建議**:
- `sortBy` 加入白名單檢查（只允許 `createTime`, `eventType`, `username` 等）
- `startTimestamp` / `endTimestamp` 在 Controller 層加入 `@Pattern` 或自訂 validator
- `pageSize` 在 Controller 中限制最大值，例如 `Math.min(pageSize, 100)`

### 4. [低] `AuditController.isAdminRole()` 重複判斷角色

```java
// AuditController.java:128-137
private static final Set<String> ADMIN_ROLES = Set.of(
        "ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_DEPT_ADMIN");
```

這個判斷邏輯與 `SecurityConfig` 中的 `hasAnyRole('ADMIN', 'SUPER_ADMIN', 'DEPT_ADMIN')` 重複。若新增管理角色，需要同時修改兩處。建議使用 `@PreAuthorize` 的 expression 或提取為共用的權限判斷 util。

### 5. [低] `LoginLogDto` 看似未被使用

```java
// LoginLogDto.java — 定義了但未在 controller 或 service 中被引用
```

`LoginLogDto` 在 audit 模組中定義但未被任何程式碼 import（僅存在於 DTO 目錄中）。這是從舊版 `user_login_log` 表選移遺留的，對應的 migration `V23__drop_user_login_log.sql` 已刪除該表。建議移除未使用的 DTO，或加註解說明保留原因。

### 6. [低] `AuditService.getMyEventLogs()` 未設定 SYSTEM context

```java
// AuditService.java:133-155
public Page<UserEventLogDto> getMyEventLogs(...) {
    // ...
    return userEventLogRepository.findAll(spec, pageable).map(this::toDto);
}
```

與 `getUserUsageHistory()` 不同，`getMyEventLogs()` 沒有手動設定 `TenantContext.setSystemContext()`。它依賴呼叫方的 TenantContext（即 HTTP request 的租戶），但 Repository 實作了 `TenantScopedRepository`，Hibernate `@Filter` 會自動加入 `tenant_id = :tenantId` 條件。這表示一般使用者查自己的登入記錄時，只會看到「在當前租戶下的」登入記錄 — 這可能是預期行為（因為 API 就是 `/v1/auth/audit/user/login/my`，使用者在某租戶下查看自己的記錄），但值得在程式碼中加註解說明。

### 7. [低] CSV 匯出存在 CSV injection 風險

```java
// AuditService.java:181
pw.println(String.join(",", csvEscape(log.getUsername()), ...));
```

雖然 `csvEscape()` 處理了逗號、雙引號、換行，但未處理 CSV injection（CWE-1236）。若 `userLabel` 包含 `=cmd|' ...` 或以 `@`、`+`、`-` 開頭的值，在 Excel 中打開時可能被解釋為公式。

**建議**: 對以 `=`, `+`, `-`, `@` 開頭的欄位值，在前面加上單引號 `'` 前綴：
```java
private static String csvEscape(String value) {
    if (value == null) return "";
    if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@")) {
        value = "'" + value;
    }
    // ... 原有邏輯
}
```

### 8. [低] `UserEventLogEntity.message` 欄位長度僅 50

```java
// UserEventLogEntity.java:59
@Column(name = "message", length = 50)
private String message;
```

以及 migration SQL:
```sql
message VARCHAR(50),
```

50 字元對某些事件描述可能不足。例如 auth 模組中的登入失敗訊息 `"Wrong password (fail count: 5)"` 已達 31 字元。建議擴大到 255 或使用 `TEXT` 類型。

### 9. [建議] 缺少 `user_event_log` 的 `event_desc` 和 `dept_id` composite index

現有 index:
```sql
CREATE INDEX idx_uel_tenant_id   ON user_event_log(tenant_id);
CREATE INDEX idx_uel_user_id     ON user_event_log(user_id);
CREATE INDEX idx_uel_create_time ON user_event_log(create_time);
CREATE INDEX idx_uel_event_type  ON user_event_log(event_type);
```

審計查詢最常見的模式是「在某租戶下、某時間範圍內、依事件類別過濾、按時間排序」，但目前缺少 `(tenant_id, create_time)` 的複合 index。對於大量日誌的租戶，這會導致查詢效能下降。

**建議**: 新增 `CREATE INDEX idx_uel_tenant_time ON user_event_log(tenant_id, create_time DESC);`

### 10. [建議] `AuditAsyncWriter` 每次寫入都查詢 UserRepository

```java
// AuditAsyncWriter.java:35-41
if (userId != null) {
    UserEntity user = userRepository.findById(userId).orElse(null);
    // ...
}
```

每次審計事件寫入都對 `users` 表做一次 PK 查詢以取得 `displayName` 和 `email`。由於 `@Async` 已經在獨立線程執行，這不會阻塞 HTTP 回應，但高流量時會對 DB 產生額外負載。可以考慮：
- 由 `BaseLoggerAspect` 在 HTTP 線程中查出 displayName/email（利用 JPA 一級快取），直接傳入 `saveAsync()`
- 或使用 Redis 快取使用者基本資料

---

## 架構總評

| 維度 | 評分 | 說明 |
|------|------|------|
| 安全性 | **7.5/10** | PayloadSanitizer 遮罩敏感欄位、AOP 非同步寫入不影響業務、TenantContext 隔離處理正確。主要扣分在 X-Forwarded-For IP 偽造風險和 CSV injection。（與首次審查相同，未有改善） |
| 正確性 | **8/10** | 核心流程（攔截 → 寫入 → 查詢 → 匯出）邏輯正確。BusinessException/RuntimeException 的錯誤碼分別處理。TenantContext 的 set/restore/clear 模式一致。Edge case 處理到位（null tenantId、空 args、DB 失敗）。 |
| 可維護性 | **8/10** | AOP + annotation 設計使新增審計點非常簡單（加 `@AuditEvent` 即可）。enum 管理所有事件類型，編譯期安全。`AuditEventType` 已擴展至涵蓋 RBAC/IoT/Tenant/KPI/Dashboard 全部事件。Service 層使用 JPA Specification 動態查詢，查詢條件靈活。未使用的 `LoginLogDto` 和重複的角色判斷邏輯可清理。 |
| 可觀測性 | **7.5/10** | 審計日誌的欄位設計完整（使用者、IP、UA、耗時、payload、錯誤碼）。匯出功能支援 CSV/XLSX。缺少 Grafana/Prometheus metrics 整合和查詢效能指標。 |
| 效能 | **7.5/10** | @Async 非同步寫入不阻塞 HTTP 回應，獨立線程池避免資源競爭。每次寫入多做一次 User PK 查詢，高流量時可優化。缺少 `(tenant_id, create_time)` 複合 index。 |

---

## 第二次審查結論（2026-05-19）

本次複查確認 2026-05-09 首次審查提出的 10 個問題**全部未修正**。模組在功能面有所擴展（新增多個事件類型和分類以支援 RBAC/IoT/KPI/Dashboard/Tenant 功能），`AuditControllerTest` 已修正 Spring Context 載入問題（新增 `TenantEnabledCache` MockBean），但安全、效能和可維護性方面的改進項目尚未排入開發排程。

**建議下一步**：優先處理 P1（IP 偽造）和 P2（保留天數可配、輸入驗證）項目，這些修改工作量小但對安全和維運影響大。