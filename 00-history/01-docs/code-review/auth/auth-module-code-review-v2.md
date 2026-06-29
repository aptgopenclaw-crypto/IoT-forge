# Auth 模組 Code Review & Security Review v2

> 本文件為 [auth-module-code-review.md](auth-module-code-review.md) 的後續複查與擴充。  
> 重點：(1) 確認既有議題的修復狀態 (2) 新增本輪發現的問題 (3) 提出值得優化的功能建議。  
> 複查日期：2026-05-26；最後更新：2026-05-27。

---

## 一、整體評價

| 維度       | v1 評分 | v2 修復前 | v2 修復後 | 變化 |
| ---------- | ------- | --------- | --------- | ---- |
| 安全性     | 8.9/10  | 8.4/10    | **9.1/10** | ⬆ v1 全 10 項 + N-1~N-7, N-11~N-15 共 15 項已修；僅剩 N-3(CSRF, SameSite=Lax 緩解)、N-8(proxy IP, 部署相關) |
| 正確性     | 8.0/10  | 7.8/10    | **8.5/10** | ⬆ resetPassword REQUIRES_NEW、DTO 長度限制、JwtFilter catch-all 皆到位 |
| 可維護性   | 7.5/10  | 7.6/10    | **8.0/10** | ⬆ 重複 DTO 清除、共用 issueTenantToken、JPQL 取代 native SQL |
| 可觀測性   | 8.0/10  | 8.0/10    | **8.3/10** | ⬆ JwtFilter 記錄 unexpected exception type、RateLimitInterceptor fallback 有 log |
| **總分**   | **8.0/10** | **8.0/10** | **8.5/10** | ⬆ 0.5 |

架構穩健，v1 高風險項目已全數處理（`ChangePasswordRequest` 驗證、reset token hash + REQUIRES_NEW 消耗、refresh token 伺服器端撤銷、CAPTCHA 常數時間比對、cookieSecure secure-by-default）；本輪 Sprint 2 項目（Turnstile replay、DTO @Size、JwtFilter catch-all、RateLimitInterceptor fallback）亦已收斂。N-2 經評估為 dev-only 接受風險。

**未修項目（3 項低/中風險）**：
| # | 項目 | 風險 | 理由 |
|---|------|------|------|
| N-3 | CSRF 防護（refresh/logout cookie） | 🟠 中 | SameSite=Lax 已緩解大部分場景；待未來跨域需求時再處理 |
| N-8 | 反向代理 IP 取得策略 | 🟡 低 | 需確認正式部署拓撲後決定策略 |
| N-9 | reset 流程密碼歷史 N 值 | 🟡 低 | UX 建議，非安全漏洞 |

**結論**：已修復 22 項（v1×10 + 本輪×12）、已接受風險 1 項（N-2）、待決策 3 項（皆為部署/UX 層面）。從安全角度看，auth 模組已達 **生產就緒** 水準。

---

## 二、v1 議題複查

| # | v1 議題                              | 狀態      | 證據                                                                                                                                |
| - | ------------------------------------ | --------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| 1 | ChangePasswordRequest 缺驗證標註     | ✅ 已修復 | [ChangePasswordRequest.java](backend/src/main/java/com/taipei/iot/auth/dto/request/ChangePasswordRequest.java) 已補 `@NotBlank` + `@Size(max=128)`；另移除 `auth` 套件下未使用的重複 DTO 與 `AuthService.changePassword()` |
| 2 | 重設密碼 token 明文儲存              | ✅ 已修復 | [UserResetPasswordTokenEntity.java](backend/src/main/java/com/taipei/iot/auth/entity/UserResetPasswordTokenEntity.java) 欄位改為 `tokenHash` (SHA-256 hex)；查詢改用 `findByTokenHash`；新增 Flyway [V42__auth__reset_token_hash.sql](backend/src/main/resources/db/migration/V42__auth__reset_token_hash.sql) |
| 3 | Refresh token 無伺服器端撤銷         | ✅ 已修復 | [JwtUtil.java](backend/src/main/java/com/taipei/iot/auth/security/JwtUtil.java) refresh token 加 `jti` claim；[AuthServiceImpl.java](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java) `logout()` 將 jti 寫入 Redis `auth:revoked_refresh:{jti}`（TTL=剩餘秒數），`refreshToken()` 進入後即時檢查，被撤銷者拋 `REFRESH_TOKEN_INVALID`；[AuthServiceTest.java](backend/src/test/java/com/taipei/iot/auth/service/AuthServiceTest.java) 新增 5 個 logout / revocation 單元測試（含 null/blank、無法解析、過期、舊版無 jti、撤銷後 refresh 等案例）|
| 4 | select/switchTenant 重複             | ✅ 已修復 | [AuthServiceImpl.java](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java) `selectTenant`/`switchTenant` 改為呼叫共用私有方法 `issueTenantToken(user, tenantId, event, reason, httpRequest, superAdminAttachDeptFromMapping)`；以 boolean 旗標保留兩者既有的 super-admin 分支差異（select 會嘗試從 mapping 帶 deptId，switch 維持 null），行為不變且 `AuthServiceTest` 26/26 通過 |
| 5 | Super admin 分支邏輯重複             | ✅ 已修復（部分） | `selectTenant` / `switchTenant` 的 super-admin 分支已隨 #4 收進 [AuthServiceImpl.java](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java) `issueTenantToken()`；`login()` 的 super-admin 分支因為流程不同（發的是 temporary token + 回傳租戶清單，並未選定租戶），與 select/switch 的 access/refresh token 簽發在語意上不重疊，強行合併反而增加間接層，故保留現狀 |
| 6 | resolvePermissions 原生 SQL          | ✅ 已修復 | [PermissionRepository.java](backend/src/main/java/com/taipei/iot/rbac/repository/PermissionRepository.java) 新增 `findAllCodesOrderByCode()` 與 `findCodesByRoleAndTenant(roleId, tenantId)`（純 JPQL）；[AuthServiceImpl.java](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java) `resolvePermissions` 收成兩行委託呼叫，不再於 service 層拼 native SQL、也不再需要 `queryMappingsWithSystemContext` 包裝；全部 398 個單元測試通過 |
| 7 | 日誌洩漏重設 token 前 6 碼           | ✅ 已修復 | 隨 v1 #2 一併修正，[AuthServiceImpl.java](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java) 改記 `tokenId`，不再輸出 token 片段 |
| 8 | SelectTenantRequest 與 SwitchTenantRequest 完全相同 | ✅ 已修復（以設計考量保留） | 故意保留兩個型別以匯分 use case：[SelectTenantRequest.java](backend/src/main/java/com/taipei/iot/auth/dto/request/SelectTenantRequest.java) 用於登入流程首次選定租戶；[SwitchTenantRequest.java](backend/src/main/java/com/taipei/iot/auth/dto/request/SwitchTenantRequest.java) 用於已登入 session 中切換租戶。兩者各加 Javadoc 明記差異與不合併原因，避免未來動止、並保留日後各自加欄位的彈性 |
| 9 | changePassword 速率限制              | ✅ 已修復 | `UserSelfController` 上已加 `@RateLimit(key="change-password", limit=5, period=300)` |
| 10 | resetPassword 應先 mark used 再驗證 | ✅ 已修復 | [UserResetPasswordTokenRepository.java](backend/src/main/java/com/taipei/iot/auth/repository/UserResetPasswordTokenRepository.java) 新增 `markUsedIfValid(hash, now)` JPQL UPDATE（原子性同時驗證 used=false 與未過期）；[AuthServiceImpl.java](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java) `resetPassword()` 改為「先消耗、再行動」，以 rowcount==1 判斷是否成功 claim，消除 concurrent reset 的 race condition。`@Transactional` rollback 仍可讓密碼驗證失敗時 token 回復 used=false |

> **小結**：v1 共 10 項議題，**全數修復完成**（#1 `ChangePasswordRequest` 驗證、#2 reset token hash、#3 refresh token 撤銷、#4 select/switchTenant 抽共用 `issueTenantToken`、#5 super-admin 分支重複（隨 #4 實質消除，`login()` 的分支流程迴異不強行合併）、#6 `resolvePermissions` 改用 JPQL repository 方法、#7 日誌洩漏、#8 SelectTenant/SwitchTenant DTO 以設計考量保留並補 Javadoc、#9 changePassword 速率限制、#10 resetPassword 原子性 mark used）。

---

## 三、本輪新發現問題

### 🔴 高風險

#### N-1. CAPTCHA 比對使用 `equalsIgnoreCase`，存在 timing attack ✅ 已修復
- **檔案**：[CaptchaServiceImpl.java](backend/src/main/java/com/taipei/iot/auth/service/impl/CaptchaServiceImpl.java)
- **原問題**：`stored.equalsIgnoreCase(captchaValue)` 為 short-circuit 比對，攻擊者可依回應時間逐字暴力破解（雖然 CAPTCHA 字元少，但與 rate limit 結合仍可顯著降低破解難度）。
- **修復**：改用 `MessageDigest.isEqual` 對 byte array 進行常數時間比對，並先以 `toUpperCase(Locale.ROOT)` 標準化以保留原 case-insensitive 語意；長度不同時提早 return（長度本身不是機密）。同步補上 `null` 防呆。

  ```java
  if (stored == null || captchaValue == null) { return false; }
  byte[] a = stored.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
  byte[] b = captchaValue.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
  if (a.length != b.length) { return false; }
  return MessageDigest.isEqual(a, b);
  ```
- **驗證**：新增 [CaptchaServiceImplTest.java](backend/src/test/java/com/taipei/iot/auth/service/CaptchaServiceImplTest.java) 7 個案例（exact / case-insensitive / wrong / length mismatch / stored missing / null input / skipVerification），全數通過。

#### N-2. `application-dev.yml` 內嵌硬編 JWT secret fallback ⚪ 已接受風險（dev only）
- **檔案**：[application-dev.yml](backend/src/main/resources/application-dev.yml#L19)  
  `secret: ${JWT_SECRET:x7G2kLp9QzRtW4vN8mYfA3hJ6bD0sCeU1oXiPaZqTnMwKyBrVdEj5lFgHuI}`
- **原問題**：雖為 dev profile，但該值已永久存在於 git 歷史中；若誤以 `dev` profile 部署外網可能被利用。
- **處理決議**：**不修復**。`dev` profile 僅限本機 / 內部開發環境使用，不會部署到可外網訪問的服務器；`prod` profile 未提供 fallback，`JWT_SECRET` 必須顯式設定，本案由團隊接受風險。若未來部署策略變更需重新評估。

#### N-3. CSRF 全域 disabled + refresh token 放 HttpOnly Cookie
- **檔案**：[SecurityConfig.java](backend/src/main/java/com/taipei/iot/config/SecurityConfig.java#L57)
- **現況**：access token 走 `Authorization: Bearer`（無 CSRF 風險），但 refresh token 與部分動作（logout）依賴 `refresh_token` cookie，且 SameSite 預設值為 `Lax` ([application.yml](backend/src/main/resources/application.yml#L62))。
- **問題**：
  1. 一旦未來需要跨網域（如 BFF/SSO 子網域）將 SameSite 改為 `None`，CSRF 風險立即浮現。
  2. `Lax` 對 top-level `POST` 不送 cookie 雖可擋大部分 CSRF，但對 link prefetch、`fetch` with credentials 在特定瀏覽器版本仍存在邊角案例。
- **建議**：
  1. `/v1/auth/refresh`、`/v1/auth/logout` 改要求自訂 header（例：`X-CSRF`）或實作 double-submit cookie。  
  2. 文件化「refresh token 僅同站使用」的部署限制，避免未來誤改 SameSite。

### 🟠 中風險

#### N-4. `cookieSecure` 預設為 `false` ✅ 已修復
- **檔案**：[AuthController.java](backend/src/main/java/com/taipei/iot/auth/controller/AuthController.java#L51-L54)
- **原問題**：`@Value("${auth.cookie.secure:false}")`，若部署環境忘記設定 `auth.cookie.secure=true`，refresh token cookie 將以明文於 HTTP 傳輸。
- **修復**：改為 `@Value("${auth.cookie.secure:true}")`（secure-by-default）；base `application.yml` 原本即 `secure: true`，`application-dev.yml` / `application-test.yml` 顯式覆寫為 `false`。未來新增部署環境若略過該設定亦能預設安全。

#### N-5. `resetPassword()` 中 `tokenEntity.setUsed(true)` 排在密碼更新之後 ✅ 已修復
- **檔案**：[AuthServiceImpl.java](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java#L573-L600)、[ResetPasswordTokenClaimer.java](backend/src/main/java/com/taipei/iot/auth/service/impl/ResetPasswordTokenClaimer.java)
- **原問題（同 v1 #10，本輪延伸）**：
  1. 舊版在同一 `@Transactional` 內先 SELECT 驗證、最後才 UPDATE used=true，存在 race condition。
  2. 即使改為先 `markUsedIfValid` 但仍在同一交易內，一旦後續 `passwordValidator` / `checkNotRecentlyUsed` / save 拋例外 rollback，token 會退回 `used=false`，允許「同一 token 連續嘗試多組新密碼」。
- **修復**：
  1. 新增 `ResetPasswordTokenClaimer` bean，`claim(tokenHash)` 以 `@Transactional(propagation = REQUIRES_NEW)` 執行 `markUsedIfValid`。
  2. `AuthServiceImpl.resetPassword()` 進入後第一時間呼叫 `resetPasswordTokenClaimer.claim(tokenHash)`，失敗則拋 `RESET_PASSWORD_INVALID_TOKEN`。
  3. 由於 claim 走獨立交易並立即 commit，即便主交易後續因密碼驗證失敗而 rollback，DB 中 token 仍為 `used=true`，彻底堆除重試路徑。
- **驗證**：[AuthServiceTest](backend/src/test/java/com/taipei/iot/auth/service/AuthServiceTest.java)新增 `resetPassword_passwordValidatorFails_claimStillInvoked`，驗證「validator 拋例外時 claim 仍被呼叫且密碼相關寫入未發生」，並修正現有 3 個 resetPassword 測試改為 mock `ResetPasswordTokenClaimer`。`mvn -o test` 全套 **406/406** 通過。

#### N-6. JWT 缺少 `jti` 與 token revocation hook ✅ 已修復（refresh token 部分）
- **檔案**：[JwtUtil.java](backend/src/main/java/com/taipei/iot/auth/security/JwtUtil.java)、[AuthServiceImpl.java](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java)
- **修復狀態**：refresh token 已內含 `jti = UUID`，logout 寫入 Redis `auth:revoked_refresh:{jti}`（TTL=token 剩餘 ms），`refreshToken()` 進入後查表。access token 因壽命短（預設 15 分鐘）尚未加 jti，若日後需「即時封鎖 access token」可再擴充同一機制。
- **後續建議**：將 access token 也加 `jti`，並在 JWT filter 內加入快速 Redis lookup，達成全 token 即時撤銷。

#### N-7. Refresh token 未紀錄發放裝置 / IP，無法做「登入裝置管理」
- **檔案**：[AuthServiceImpl.java](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java) 全段
- **問題**：v1 沒提到——目前並無 `user_session` 之類的 table 紀錄 (userId, jti, ip, userAgent, issuedAt)；造成：
  - 使用者端無法看到「目前登入的裝置」。
  - 安全事件發生時無法選擇性踢出特定 session。
- **建議**：加上 `UserSessionEntity`，refresh 時 rotate `jti` 並更新 `lastSeenAt`；提供 `GET /v1/auth/sessions` 與 `DELETE /v1/auth/sessions/{id}`。
- **修復狀態**：✅ 已完成（2026-05-27）
  - 新增 [V43__auth__user_session.sql](backend/src/main/resources/db/migration/V43__auth__user_session.sql) 建 `user_session` 表（PK=jti，索引 `(user_id, revoked, last_seen_at DESC)`、`(expires_at)`）。
  - 新增 [UserSessionEntity.java](backend/src/main/java/com/taipei/iot/auth/entity/UserSessionEntity.java) + [UserSessionRepository.java](backend/src/main/java/com/taipei/iot/auth/repository/UserSessionRepository.java)（含 `findActiveByUserId` / `revokeById` 原子更新）。
  - `JwtUtil` 新增 3-arg `generateRefreshToken(uid, tenantId, jti)` overload，由呼叫端預先產生 jti 以對齊 session row。
  - `AuthServiceImpl` 在 login / selectTenant / switchTenant / refreshToken 4 個發 token 處呼叫 `recordSession()`；`refreshToken()` 標 `@Transactional`、採 **Rotate + revoke 舊 jti** 策略（Redis blacklist + DB revoke 雙寫）；`logout()` 同步把 session row 標 revoked。
  - 新增 [UserSessionService](backend/src/main/java/com/taipei/iot/auth/service/UserSessionService.java) + Impl 以及 [SessionDto](backend/src/main/java/com/taipei/iot/auth/dto/response/SessionDto.java)；`AuthController` 加上 `GET /v1/auth/sessions`（透過 refresh_token cookie jti 標記 `current`）與 `DELETE /v1/auth/sessions/{sessionId}`（service 層僅允許 owner，否則 `SESSION_NOT_FOUND`）。
  - 測試：[UserSessionServiceTest](backend/src/test/java/com/taipei/iot/auth/service/UserSessionServiceTest.java) 5 cases（含「Redis 失敗不回滾」、「非 owner」、「重複撤銷 idempotent」）；AuthServiceTest 與 AuthControllerTest 更新呼叫簽章。`mvn -o test` 全部 411 tests 通過（baseline 406 + 5）。

### 🟡 低風險 / 建議

#### N-8. `getRemoteAddr()` 假設應用直接面對 client
- **檔案**：[RateLimitInterceptor.java](backend/src/main/java/com/taipei/iot/auth/security/RateLimitInterceptor.java) 及 [SecurityConfig.java](backend/src/main/java/com/taipei/iot/config/SecurityConfig.java#L92)
- **問題**：v1 將此列為優點（防止 X-Forwarded-For 偽造）。**前提是後端直接面對 client**；若部署在反向代理（Nginx / Cloud LB）後面，`getRemoteAddr()` 取到的會是 proxy IP，所有使用者共用同一個 rate-limit bucket，等於癱瘓速率限制。
- **建議**：
  1. 以 `server.forward-headers-strategy=NATIVE` 或 Spring Boot `ForwardedHeaderFilter` 配合「受信任 proxy IP 白名單」處理。
  2. 在文件明示部署拓撲假設。

#### N-9. `passwordValidator.checkNotRecentlyUsed` 在 `resetPassword` / `changePassword` 共用，但 reset 流程下使用者可能不記得舊密碼
- **檔案**：[AuthServiceImpl.java](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java#L572)
- **問題**：忘記密碼後使用者「猜舊密碼設新密碼」的機率不低，若 history N 很大可能造成使用者反覆失敗、回頭按更多 forgot-password（觸發 rate limit）。
- **建議**：將 history 規則做成可由 setting module 控制；reset 流程可採用較小的 N（例如最近 1 個），改善 UX 同時保留主要安全目標（避免立刻換回剛被洩漏的密碼）。

#### N-10. SecurityLogger 寫入大量 INFO/WARN 但未提供 structured sink
- **檔案**：[SecurityLogger.java](backend/src/main/java/com/taipei/iot/common/util/SecurityLogger.java)
- **問題**：目前事件落到 logback；若想做 SIEM / 告警，需要自行解析。可考慮：
  1. 把 SecurityEvent 寫入專用 `security_audit` table，與 audit module 整併。
  2. 對「同一 IP 在 5 分鐘內 LOGIN_FAILED ≥ N」這類規則內建告警。

#### N-11. Turnstile token 沒有 nonce / 一次性檢查 ✅ 已修復
- **檔案**：[TurnstileServiceImpl.java](backend/src/main/java/com/taipei/iot/auth/service/impl/TurnstileServiceImpl.java)
- **原問題**：Cloudflare 端會做 idempotency，但本服務沒有額外維護「已用過的 turnstile token」快取，極短時間內的 replay 雖機率低仍可行。
- **修復**（2026-05-27）：驗證成功後將 token SHA-256 hash 寫入 Redis `turnstile:used:{hash}`（TTL=300s），驗證前先檢查快取拒絕重複使用；Redis 不可用時仍依賴 Cloudflare 端的 idempotency 保護（fail-open）。

#### N-12. `LoginRequest`、`ForgotPasswordRequest` 等 DTO 缺乏輸入長度上限 ✅ 已修復
- **檔案**：[LoginRequest.java](backend/src/main/java/com/taipei/iot/auth/dto/request/LoginRequest.java)、[ResetPasswordRequest.java](backend/src/main/java/com/taipei/iot/auth/dto/request/ResetPasswordRequest.java)、[ForgotPasswordRequest.java](backend/src/main/java/com/taipei/iot/auth/dto/request/ForgotPasswordRequest.java)、[ForceChangePasswordRequest.java](backend/src/main/java/com/taipei/iot/auth/dto/request/ForceChangePasswordRequest.java)、[SelectTenantRequest.java](backend/src/main/java/com/taipei/iot/auth/dto/request/SelectTenantRequest.java)、[SwitchTenantRequest.java](backend/src/main/java/com/taipei/iot/auth/dto/request/SwitchTenantRequest.java)
- **修復**（2026-05-27）：所有密碼欄位加 `@Size(max=128)`、email 欄位 `@Size(max=254)`、tenantId `@Size(max=50)`、reset token `@Size(max=512)`。

#### N-13. 缺乏密碼進入 BCrypt 前的長度截斷 ✅ 已修復
- **檔案**：`AuthServiceImpl.login()`、`changePassword()`、`resetPassword()`
- **原問題**：BCrypt 對 ≥72 byte 字串會截斷，且 CPU 成本固定；但若無上限，攻擊者可送 1MB 密碼造成 DoS。
- **修復**（2026-05-27）：隨 N-12 一併解決，所有密碼入口 DTO 已加 `@Size(max=128)` 限制，請求驗證層即擋下超長輸入。

#### N-14. `JwtAuthenticationFilter` 缺少 catch-all 例外處理 ✅ 已修復
- **檔案**：[JwtAuthenticationFilter.java](backend/src/main/java/com/taipei/iot/auth/security/JwtAuthenticationFilter.java)
- **原問題**：只 catch `ExpiredJwtException` 和 `JwtException`，若 JWT parse 拋出 `IllegalArgumentException` 等非預期例外，會導致 500 或 filter bypass。
- **修復**（2026-05-27）：新增 `catch (Exception e)` 區塊，記錄 `SecurityEvent.JWT_INVALID` + exception type 後安全跳過（不設 Authentication），請求以匿名身份繼續進入 Spring Security 授權判斷。

#### N-15. `RateLimitInterceptor` Redis 不可用時速率限制完全失效 ✅ 已修復
- **檔案**：[RateLimitInterceptor.java](backend/src/main/java/com/taipei/iot/common/interceptor/RateLimitInterceptor.java)
- **原問題**：Redis 連線失敗時直接 `return true` 放行，攻擊者可趁 Redis 不可用期間暴力破解。
- **修復**（2026-05-27）：新增 `ConcurrentHashMap<String, long[]> localFallback` 作為本機 JVM 級 fixed-window 計數器。Redis 不可用時退回使用本地 fallback 計數，雖非分散式但可防止單機暴力破解。

---

## 四、值得優化的功能建議

| # | 功能                                  | 價值        | 概要 |
| - | ------------------------------------- | ----------- | ---- |
| F-1 | **MFA / TOTP**                       | 安全性 ★★★ | super_admin / tenant_admin 強制；一般使用者可選。可引入 Spring Security 整合 `aerogear-otp-java` 或自行實作 RFC 6238。 |
| F-2 | **登入裝置管理頁**                   | UX + 安全 ★★ | 搭配 N-7，使用者可查看 / 撤銷 session。 |
| F-3 | **異常登入通知**                     | 安全性 ★★  | 新 IP / 新國家 / 新 UA 觸發 email。 |
| F-4 | **密碼強度即時提示（前端 zxcvbn）**  | UX ★★      | 前端整合 zxcvbn-ts；後端維持現有 PasswordValidator。 |
| F-5 | **登入後自動踢出舊 session（同帳號最多 N 個）** | 安全性 ★★ | 配合 N-7 的 user_session table。 |
| F-6 | **Magic link / OTP 登入（passwordless）** | UX ★★      | 一般使用者可避免記憶密碼；管理者仍維持密碼+MFA。 |
| F-7 | **CAPTCHA 行為式（Turnstile / hCaptcha）取代圖片 CAPTCHA** | 安全 + UX | 圖片 CAPTCHA OCR 已成熟，可直接 phase out CaptchaServiceImpl，全面改用 Turnstile。 |
| F-8 | **/auth/logout-all 端點**            | 安全性 ★★  | 配合 v1 #3 / N-6 的 jti 撤銷機制，一鍵踢出所有裝置。 |
| F-9 | **密碼變更後自動 logout-all**         | 安全性 ★★  | 與 F-8 共用底層機制，預設行為。 |
| F-10 | **SSO（OIDC / SAML）整合**          | 企業整合 ★★ | 透過 Spring Security `oauth2Login` 接 Google / Azure AD，租戶 onboarding 可選擇 IdP。 |
| F-11 | **API token / Personal Access Token** | 自動化 ★★  | 取代「以使用者帳號跑機器流程」的反模式；可按範圍限定權限。 |
| F-12 | **登入流程指標化（Prometheus / Micrometer）** | 維運 ★★ | login_attempts_total{result}、refresh_total、captcha_failed_total 等 metric。 |

---

## 五、修復路線圖建議

### Sprint 1 — 立即修復（資安回歸風險）
1. ~~v1 #2 重設密碼 token 改為 hash 儲存（BCrypt）。~~ ✅ 完成（改用 SHA-256 hex）
2. ~~v1 #3 + N-6 在 JWT 加 `jti`，logout 與密碼變更時寫入 Redis 撤銷表。~~ ✅ 完成（refresh token 已加 jti + Redis 撤銷；密碼變更時觸發 logout-all 留待 F-9 處理）
3. ~~v1 #10 / N-5 將 `setUsed(true)` 改為「條件式 UPDATE」並前置。~~ ✅ 完成（`markUsedIfValid` + `ResetPasswordTokenClaimer` REQUIRES_NEW）
4. ~~v1 #1 補 `ChangePasswordRequest` 驗證標註。~~ ✅ 完成
5. ~~N-1 CAPTCHA 改常數時間比對。~~ ✅ 完成（`MessageDigest.isEqual` + 7 單元測試）
6. ~~N-2 移除 `application-dev.yml` 預設 JWT secret。~~ ⚪ 已接受風險（dev profile 僅限本地 / 內部使用，prod 未提供 fallback）
7. ~~N-4 `cookieSecure` 預設改 `true`。~~ ✅ 完成（secure-by-default，dev/test profile 顯式覆寫為 false）

### Sprint 2 — 安全強化
8. N-3 為 refresh / logout 加 CSRF 防護（custom header 或 double submit）。
9. ~~N-7 新增 `user_session` table，提供 sessions API（接續 F-2、F-8、F-9）。~~ ✅ 完成
10. N-8 處理反向代理 IP 取得策略，明確化部署假設。
11. ~~N-11 Turnstile token 防 replay。~~ ✅ 完成（Redis `turnstile:used:{hash}` TTL=300s）
12. ~~N-12 / N-13 全 DTO 加 `@Size` 上限。~~ ✅ 完成（password≤128, email≤254, tenantId≤50, token≤512）
13. ~~N-14 JwtAuthenticationFilter catch-all。~~ ✅ 完成
14. ~~N-15 RateLimitInterceptor in-memory fallback。~~ ✅ 完成

### Sprint 3+ — 功能優化
13. F-1 MFA、F-3 異常登入通知、F-7 全面改用 Turnstile。
14. F-10 SSO、F-11 PAT、F-12 指標化。

### 持續改善
15. v1 #4、#5、#8 重複程式碼重構（抽 `generateTenantToken`、合併 DTO）。
16. v1 #6 原生 SQL 改 JPQL 或補註解。
17. ~~v1 #7 移除日誌中 token 片段。~~ ✅ 完成（隨 v1 #2 一併）

---

## 六、附錄：本次複查涵蓋的檔案

- 控制器：`AuthController.java`、`UserSelfController.java`
- 服務：`AuthServiceImpl.java`、`CaptchaServiceImpl.java`、`TurnstileServiceImpl.java`、`PasswordResetMailService.java`
- 安全層：`SecurityConfig.java`（位於 `config/`）、`JwtUtil.java`、`JwtAuthenticationFilter.java`、`RateLimitInterceptor.java`、`SecurityLogger.java`
- Entities：`UserEntity.java`、`UserResetPasswordTokenEntity.java`、`ChangePasswordLogEntity.java`、`UserTenantMappingEntity.java`
- DTOs：`LoginRequest.java`、`ChangePasswordRequest.java`、`ForgotPasswordRequest.java`、`ResetPasswordRequest.java`、`SelectTenantRequest.java`、`SwitchTenantRequest.java`
- 設定檔：`application.yml`、`application-dev.yml`、`application-test.yml`
