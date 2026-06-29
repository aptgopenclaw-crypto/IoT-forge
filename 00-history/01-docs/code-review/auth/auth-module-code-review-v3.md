# Auth 模組 Code Review & Security Review v3

> 本文件為 [auth-module-code-review-v2.md](auth-module-code-review-v2.md) 之後續複查，聚焦：
> 1. v2 遺留 3 項（N-3 / N-8 / N-9）目前狀態
> 2. v2 後新增子系統的完整 review：`auth/provider/`（multi-auth-provider）、`auth/policy/`（password-policy）、`auth/security/` 中新增的 Scope 機制（ScopeEnforcementFilter + TokenScope）
> 3. AuthServiceImpl 在 v2 後新增的 refresh rotation / sessions / impersonation 相關邏輯
>
> 複查日期：2026-06-01

---

## 一、整體評價

| 維度       | v2 評分    | v3 評分    | 變化 |
| ---------- | ---------- | ---------- | ---- |
| 安全性     | 9.1/10     | **7.8/10** | ⬇ 新子系統引入新風險，且 v2 遺留 N-3 / N-8 / N-9 仍未修；其中 Refresh token rotation 缺 reuse detection、Scope filter fail-open、Dispatcher fallback 預設 true 等屬高風險 |
| 正確性     | 8.5/10     | **8.0/10** | ⬇ AuthServiceImpl 呼叫 dispatcher 未傳 tenantId，multi-provider 形同未接通；expire_days 政策方向反向 |
| 可維護性   | 8.0/10     | **7.8/10** | ⬇ AuthServiceImpl 已超過 1000 行；權限碼命名分歧（PASSWORD_POLICY_MANAGE / TENANT_MANAGE / VIEW） |
| 可觀測性   | 8.3/10     | **7.8/10** | ⬇ Provider / Dispatcher / Encryptor 完全沒 metrics；fallback 觸發只有 log.warn |
| **總分**   | **8.5/10** | **7.9/10** | ⬇ 0.6（新功能引入新風險，但 v2 已修項目仍保持） |

**結論**：v2 達成的「auth 核心 production-ready」仍維持，但 v3 涵蓋的三大新子系統（provider / policy / scope）尚未達到同等成熟度。建議在新子系統實際開放使用（特別是接 LDAP/OIDC、開放租戶自助設定密碼政策）之前，至少處理本輪標記為 🔴 的 7 項。

**本輪新發現問題彙整**：
- 🔴 高風險：7 項（V3-H1~H7）
- 🟠 中風險：13 項（V3-M1~M13）
- 🟡 低風險：18 項（V3-L1~L18）
- v2 遺留：N-3 / N-8 / N-9 皆**仍未處理**

---

## 二、v2 遺留 3 項狀態複查

### N-3 CSRF for refresh / logout — 🔴 **仍未處理**
- 檔案：[SecurityConfig.java](backend/src/main/java/com/taipei/iot/config/SecurityConfig.java#L61) 仍 `csrf.disable()`
- 現況：`/v1/auth/refresh`、`/v1/auth/logout`、`/v1/auth/idle-logout`、`DELETE /v1/auth/sessions/{id}` 全部仰賴 SameSite=Lax cookie，無 CSRF token 或 custom header 驗證
- 風險升級：v3 新增 `/v1/auth/sessions/*` 端點後，攻擊面又擴大；強制登出（DoS）攻擊可行
- 建議：對所有 cookie-bearing 寫入端點要求 `X-CSRF` header 或 double-submit cookie；至少驗證 `Origin/Referer`

### N-8 反向代理 IP 取得策略 — 🟠 **仍未處理**
- 檔案：[application.yml](backend/src/main/resources/application.yml) 未設 `server.forward-headers-strategy`；[RateLimitInterceptor.java](backend/src/main/java/com/taipei/iot/common/interceptor/RateLimitInterceptor.java#L174-L178) `getClientIp` 直接用 `request.getRemoteAddr()`（javadoc 承認「未來部署 Nginx 應啟用 ForwardedHeaderFilter」）
- 影響鏈：`AuthServiceImpl.recordSession`、`logLoginEvent`、`SecurityLogger` 全鏈一律用 remoteAddr
- 風險：一旦部署到 Nginx/LB 後面，所有「登入 IP / session IP / rate limit」全部塌縮成 proxy IP，等同 rate limit 失效、稽核失去意義
- 建議：標記為「部署前必做」release blocker；application.yml 加 `server.forward-headers-strategy=NATIVE`，Nginx 設 `set_real_ip_from` 白名單

### N-9 password history N 在 reset vs change — 🟠 **仍未分離**
- 檔案：`PasswordValidator.checkNotRecentlyUsed` 仍使用同一個 `policy.getHistoryCount()`
- 現況：resetPassword 傳 tenantId=null（用平台預設 policy）、forceChangePassword 傳 policyTenantId，但兩者最終都查同一張 password_history、同一個 N
- 建議：在 `PasswordPolicy` 引入 `historyCountForReset`（預設 ≤ historyCountForChange），改善 UX 同時保留主安全目標

---

## 三、新子系統 A：multi-auth-provider（auth/provider/）

### 🔴 高風險

#### [V3-H1] AuthServiceImpl 呼叫 dispatcher 時未傳 tenantId → multi-provider 形同未接通
- 檔案：[AuthServiceImpl.java:117-121](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java#L117)
- 證據：`AuthenticationRequest` 建立時只設 identifier / credential，`tenantId=null`
- 後果鏈：`AuthenticationDispatcher.resolveConfig(null)` 回 null → `determineAuthType` 退回 `AuthType.LOCAL` → 不管租戶設了什麼 LDAP/OIDC/SAML 設定，永遠走 `LocalAuthProvider`
- 風險：整套 multi-auth-provider 功能在 production 形同擺設；租戶以為自己接了 AD 其實使用者仍在用 local 帳號登入
- 建議：登入流程必須先決定 tenantId（例如以 email domain → tenant routing 表查表，或要求前端在 login payload 明示 tenant），再傳入 dispatcher

#### [V3-H2] Dispatcher fallback-to-LOCAL 為 auth bypass 風險
- 檔案：[AuthenticationDispatcher.java:63-74](backend/src/main/java/com/taipei/iot/auth/provider/AuthenticationDispatcher.java#L63)
- 證據：任何 `BusinessException`（包含 `INVALID_CREDENTIAL`、`PROVIDER_UNAVAILABLE`）都會 fallback；`fallbackLocal` config 預設 `true`
- 攻擊路徑：租戶 A 設定「禁用 LOCAL、改用 LDAP」，但 LOCAL 帳號殘留 → 攻擊者故意讓 LDAP timeout / fail → dispatcher 自動 fallback 走 LOCAL → 以 LOCAL 密碼登入成功
- 建議：(a) 預設改 `false`；(b) 僅在 infrastructure exception（network、timeout）才 fallback，憑證錯誤絕不 fallback；(c) 每次 fallback 都寫 `SecurityLogger.warn("AUTH_FALLBACK", ...)` 並產 metric

#### [V3-H3] AuthConfigEncryptor key 未配置時只 log.warn
- 檔案：[AuthConfigEncryptor.java:36-40](backend/src/main/java/com/taipei/iot/auth/provider/crypto/AuthConfigEncryptor.java#L36)
- 風險：production 漏設 `app.auth.config.encryption-key` → 整個 LDAP bindPassword / OIDC clientSecret 以明文存進 DB
- 建議：`@PostConstruct` 在 prod profile 強制 `Assert.hasText(key)`；缺 key 直接 fail-fast

#### [V3-H4] LocalAuthProvider 帳號鎖定 race condition
- 檔案：[LocalAuthProvider.java:79-92](backend/src/main/java/com/taipei/iot/auth/provider/local/LocalAuthProvider.java#L79)
- 證據：`loginFailCount` 走 read → +1 → save 的 lost-update 模式；無 `@Version` / pessimistic lock / atomic SQL UPDATE
- 風險：併發暴力破解可放大實際允許次數（例如 lock threshold=5，併發 10 條請求可能只記 1~2 次失敗）
- 建議：改 `UPDATE users SET login_fail_count = login_fail_count + 1 WHERE user_id=:id`，或加 `@Version` optimistic lock

#### [V3-H5] testConnection 端點 SSRF
- 檔案：[PlatformTenantAuthConfigController.java:71-78](backend/src/main/java/com/taipei/iot/auth/provider/config/controller/PlatformTenantAuthConfigController.java#L71) + [TenantAuthConfigServiceImpl.java:89-95](backend/src/main/java/com/taipei/iot/auth/provider/config/service/impl/TenantAuthConfigServiceImpl.java#L89)
- 證據：允許 admin 提交任意 URL 做 outbound LDAP / OIDC discovery 測試，無 URL 白名單、無內網黑名單
- 風險：可探內網 metadata 服務（`http://169.254.169.254/`）、內部 LDAP（`ldap://10.x.x.x`），等同 SSRF
- 建議：(a) 拒絕 RFC1918 / link-local / loopback 主機；(b) 對 outbound 用獨立 egress proxy；(c) 限定 schema 為 `ldaps://`、`https://`

### 🟠 中風險

| 編號     | 標題                                                                           | 檔案                                                    |
| -------- | ------------------------------------------------------------------------------ | ------------------------------------------------------- |
| V3-M1    | `sanitize()` 黑名單遮蔽易漏網（缺 apiKey/token/accessKey/pwd，未遞迴 nested）   | TenantAuthConfigServiceImpl.java:29-31, 116-125         |
| V3-M2    | `AuthType.fromString` 拋 IllegalArgumentException 未捕捉變 500，message 帶輸入 | AuthType.java:12-17                                     |
| V3-M3    | `createOrUpdate` 強制 `enabled=true`，無法暫停只能 DELETE                      | TenantAuthConfigServiceImpl.java:75                     |
| V3-M4    | `TenantAuthConfigEntity` 缺 `@Version` / `createdBy` / `updatedBy`，無稽核     | TenantAuthConfigEntity.java                             |
| V3-M5    | provider 子系統 metrics 空缺（無 success/fail/latency 區分 provider）          | AuthenticationDispatcher.java                           |

詳細修法見「修復路線圖」。


---

## 四、新子系統 B：password-policy（auth/policy/）

### 🔴 高風險

#### [V3-H6] 平台 floor 提升後，既有租戶覆寫不會被回溯校正 → spec D-4 不變式被破壞
- 檔案：[PasswordPolicyService.java:62-77](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyService.java#L62)（`updatePlatformDefault`）
- 證據：`updatePlatformDefault` 只 `dao.upsert(PLATFORM_SENTINEL, ...)`，完全沒有掃描現有 tenant override 並重新驗證
- 風險路徑：原本平台 `min_length=8`、租戶 A override=8 → SUPER_ADMIN 把平台預設改為 12 → 租戶 A 的 override 仍是 8，`PasswordPolicyResolver.pick()` 以 tenant 值優先 → 租戶 A 實際生效規則仍為 8。整個「平台只能設下限、租戶不能弱化」承諾失效
- 建議：`updatePlatformDefault` 對 INT key 加 post-write scan：`DELETE FROM system_settings WHERE setting_key=:k AND tenant_id<>'__PLATFORM__' AND CAST(setting_value AS INT) < :newFloor`，或拒寫並列出衝突 tenants 讓 admin 手動處理；同步記稽核

#### [V3-H7] Reset-password no-auth 流程一律以平台預設驗證，繞過租戶強化規則
- 檔案：[AuthServiceImpl.java:738-743](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java#L738)
- 證據：註解寫「Reset-password is a no-auth flow → use platform-default policy (tenantId = null)」，直接 `passwordValidator.validate(null, ...)` 與 `checkNotRecentlyUsed(null, ...)`
- 風險：租戶 A 設 `min_length=20`、`require_special=true`，但 reset token 已對應 userId、可反查 `user_tenant_mapping` → 卻硬塞 `tenantId=null` 退回平台 `min_length=8`；攻擊者拿到 reset token 後可設明顯弱於該租戶政策的密碼，且 history N 也以平台預設處理
- 建議：與 `forceChangePassword` 同樣使用 `mappings.size()==1 ? mappings.get(0).tenantId : null`；或在 reset token 發放時把 tenantId 寫入 `UserResetPasswordTokenEntity`，claim 時讀回

### 🟠 中風險

| 編號     | 標題                                                                           | 檔案                                              |
| -------- | ------------------------------------------------------------------------------ | ------------------------------------------------- |
| V3-M6    | `force_change_on_admin_reset` 政策定義但**無實作呼叫端**（UserAdminService 缺 resetPassword） | PasswordPolicyKey.java:42; UserAdminService.java   |
| V3-M7    | `PasswordPolicyResolver` 無快取，登入熱路徑每次 2 條 SQL；`/v1/noauth/password-policy/describe` 公開無限速 → DDoS 放大 | PasswordPolicyResolver.java:59-63; NoauthPasswordPolicyController.java:25 |
| V3-M8    | `PasswordExpiryChecker` 用 `LocalDateTime.now()`（無 TZ）→ 跨 pod TZ 漂移時 EXPIRED 判定不一致 | PasswordExpiryChecker.java:56-59                 |
| V3-M9    | `updateTenantOverride(tenantId, ...)` 未拒絕 `tenantId == __PLATFORM__`，未來 caller 誤傳會把租戶覆寫寫進平台預設 | PasswordPolicyService.java:80-99                 |
| V3-M10   | `HISTORY_COUNT` 平台 floor=1 與 spec「0=關閉」矛盾                            | PasswordPolicyKey.java:26                         |
| V3-M11   | 缺 `min_password_age` → 攻擊者可循環重設 `historyCount+1` 次繞過 history     | spec §1.2 標 out-of-scope                         |
| V3-M12   | `NoauthPasswordPolicyController` 無 auth/無 rate-limit、可枚舉任意 tenantId 規則，外洩「最弱租戶」資訊 | NoauthPasswordPolicyController.java:25-29        |
| V3-M13   | Platform controller 用 `hasAuthority('PLATFORM_PASSWORD_POLICY_MANAGE')`，但 spec 自承 V48 未把該 authority 綁到 SUPER_ADMIN role | PlatformPasswordPolicyController.java:30         |

### 🟡 低風險

- [V3-L1] `PasswordPolicyResolver.intValue` 對非法整數靜默 fallback，無 metric 告警（PasswordPolicyResolver.java:84-93）
- [V3-L2] `validateValueFormat` 對 INT 無上限校驗，admin 可設 `min_length=999999` → 全平台改不了密 DoS（PasswordPolicyService.java:123-140）
- [V3-L3] `UpdatePasswordPolicyRequest.value` 允許前導零字串如 `"00000123"`，audit log 易混淆（UpdatePasswordPolicyRequest.java:22）
- [V3-L4] `PasswordPolicyDao` upsert 倚賴 `UNIQUE(tenant_id, setting_key)` 索引；若 migration 漏建會產生重複列、policy 抖動（PasswordPolicyDao.java:54-58）
- [V3-L5] `notContainsUsername` 用 default Locale `toLowerCase()`，土耳其 Locale 下 i/I 比對歧義（PasswordValidator.java:111-114, 122）
- [V3-L6] `PasswordExpiryChecker` 對 `passwordChangedAt==null` 一律回 EXPIRED 但 `forceChangePassword=false`，產生誤報噪音（PasswordExpiryChecker.java:50-54）

---

## 五、新子系統 C：token scope + UserSession + AuthService v2+ 變更

### 🔴 高風險

#### [V3-H8] Impersonation session 撤銷後 JWT 仍可用（無 token-level revocation）
- 檔案：[ImpersonationService.java:131-151](backend/src/main/java/com/taipei/iot/platform/impersonation/service/ImpersonationService.java#L131)、[JwtAuthenticationFilter.java:42-100](backend/src/main/java/com/taipei/iot/auth/security/JwtAuthenticationFilter.java#L42)
- 證據：`revoke()` 只改 DB `status=REVOKED`；`JwtAuthenticationFilter` 解析 IMPERSONATION JWT 後從未查 `ImpersonationSessionRepository` 是否仍 ACTIVE，也沒寫入 Redis 黑名單（impersonation token 沒有 jti、沒有對應 user_session row）
- 風險：管理員按下「結束代操」後，舊 access token 在剩餘 TTL（最長 1 小時）內仍可對目標租戶做任何 ADMIN 動作，且 audit 仍會打指向已撤銷 session 的 id，事後判讀困難
- 建議：(a) 在 `JwtAuthenticationFilter` 對 `scope==IMPERSONATION` 多查一次 `sessionRepository.findById(impersonation.sessionId)`，非 ACTIVE 或過期 → 401（搭 caffeine cache 降 DB 壓力）；(b) revoke 時同步寫 Redis key `imp:revoked:{sessionId}` TTL=剩餘時間

#### [V3-H9] Refresh token 黑名單 fail-open + 無 reuse-detection
- 檔案：[AuthServiceImpl.java:553-564, 414-505](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java#L553)
- 證據：`isRefreshTokenRevoked` catch 後 `return false`（fail-open）；rotation 只把舊 jti 寫 Redis + 標 DB `revoked=true`，無「使用已 revoked jti → 撤整條 token family」邏輯
- 風險：(1) Redis 中斷時被撤銷的 refresh token 仍可換 access；(2) 攻擊者竊取一份未過期 refresh token 後，使用者下次 refresh 才 rotate 舊 jti，期間攻擊者與使用者各自 rotate 出兩條分裂 family，系統不察覺
- 建議：(a) Redis hard-fail 時 fall back 改查 `userSessionRepository.findById(jti).revoked`（DB 為真相），fail-closed；(b) 在 `refreshToken()` 偵測到「jti 已 revoked 但簽名/exp 仍合法」時，呼叫 `userSessionRepository.revokeAllByUserId(userId, now)` 並寫 `SecurityEvent.REFRESH_TOKEN_REUSE`

#### [V3-H10] SUPER_ADMIN 仍可用 switch-tenant 取得 TENANT scope token，繞過 IMPERSONATION 稽核錨點
- 檔案：[AuthServiceImpl.java:329-369](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java#L329)
- 證據：`issueTenantToken` 直接以 `TokenScope.TENANT` 簽發給 super admin；註解寫「Phase 3 將消除此路徑、改用 IMPERSONATION」，但程式仍允許
- 風險：架構文件聲稱「super admin 必經 impersonation」實際未強制，安全模型與稽核被打洞；後端工程師看不出該路徑是 deprecated
- 建議：在 `issueTenantToken` 內若 `user.isSuperAdmin()` 直接 `throw new BusinessException(ErrorCode.USE_IMPERSONATION_INSTEAD)`，或加 feature flag `app.security.deny-superadmin-direct-tenant=true` 預設 enabled

#### [V3-H11] `recordSession` 失敗被吞 → 「孤兒 token」既無法列出也無法主動撤銷
- 檔案：[AuthServiceImpl.java:570-594](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java#L570)
- 證據：`catch (Exception e) { log.warn(...) }`；上層 `buildPostAuthLoginResult` / `issueTenantToken` / `refreshToken` 仍照常回傳 access/refresh
- 風險：DB 暫故障時簽出的 token 永遠不會出現在 `/v1/auth/sessions`、使用者無法強制登出此裝置；攻擊者若能誘發 insert 失敗（race / constraint violation）即可獲得「無法被列管」的 refresh token，直到自然過期（通常 7 天）。`@Transactional` 邊界與 try/catch 互動也讓 audit log 仍 insert 成功，狀態錯位
- 建議：把 `recordSession` 改強一致：persist 失敗 → 拋例外、整個 login/refresh rollback、回 5xx；try/catch 縮小到「擷取 IP/UA」步驟

#### [V3-H12] ScopeEnforcementFilter 寬鬆 prefix 把整片 menu/system-settings 開給 PLATFORM token，僅靠 @PreAuthorize 兜底
- 檔案：[ScopeEnforcementFilter.java:85-118](backend/src/main/java/com/taipei/iot/auth/security/ScopeEnforcementFilter.java#L85)
- 證據：`AUTH_SCOPE_AGNOSTIC_PREFIXES` 包含 `/v1/auth/menus/`、`/v1/auth/system-settings/`，任何子路徑都不檢查 scope；SecurityConfig 中 `/v1/auth/sessions/**` 也未明確列出，落入 `anyRequest().authenticated()`
- 風險：PLATFORM super_admin 持 PLATFORM-scope token 直接命中 tenant 工具；scope 隔離完全失效，安全退化到「每個 controller method 必須記得寫 @PreAuthorize」。任何新增子端點忘寫即漏洞
- 建議：(a) 改 method-level `@ScopeAllowed({PLATFORM,TENANT})` 註解、filter 從 `HandlerMethod` 讀取；(b) SecurityConfig 對 `/v1/auth/sessions/**`、`/v1/auth/menus/**` 明確 `.hasAuthority(...)` fail-closed

### 🟠 中風險

| 編號     | 標題                                                                           | 檔案                                              |
| -------- | ------------------------------------------------------------------------------ | ------------------------------------------------- |
| V3-M14   | `user_session.lastSeenAt` 永遠不更新；`touch()` 全 repo 無呼叫端              | UserSessionRepository.java:44-51                  |
| V3-M15   | Impersonation token 的 JWT `exp`（access TTL，例 15m）與 `session.expiresAt`（可達 60m）不一致 | JwtUtil.java:80-109                               |
| V3-M16   | ScopeEnforcementFilter 403 response 手寫字串拼 JSON，未過 `ObjectMapper`，含 `"`/換行的錯誤訊息會炸壞 JSON | ScopeEnforcementFilter.java:206-210               |
| V3-M17   | Idle-logout 完全信任前端，無 server-side `lastSeenAt + idle threshold` 驗證 | AuthController.java:194-206                       |
| V3-M18   | `JwtAuthenticationFilter` 對所有 SUPER_ADMIN+tenantId 皆 `setImpersonator(...)`，未檢查 scope；TENANT 與 IMPERSONATION 兩條路徑審計無法區分 | JwtAuthenticationFilter.java:94-97                |

### 🟡 低風險

- [V3-L7] `AUTH_SCOPE_AGNOSTIC_PREFIXES` 維護成本高、新增子端點易遺漏（ScopeEnforcementFilter.java:85-118）
- [V3-L8] `generateImpersonationAccessToken` 對 `originalUserId` / `sessionId` / `expiresAtEpochSeconds` 無 defensive check（JwtUtil.java:80-109）
- [V3-L9] `UserSessionEntity` 不掛 tenant filter（已書面化），但未來新增 admin 端「列他人 sessions」需 service-layer 強制 tenant 比對（UserSessionEntity.java:22-42）
- [V3-L10] `ExpiredJwtException` 對 IMPERSONATION 與一般 token 不分流，缺 `IMPERSONATION_TOKEN_EXPIRED` 事件（JwtAuthenticationFilter.java:102-115）
- [V3-L11] logout / idle-logout 對「沒有 cookie」靜默成功仍寫 audit，產生噪音（AuthController.java:185-206）
- [V3-L12] `UserSessionRepository.revokeById` 的 `@Transactional` 與外層 tx 合併，與 javadoc「自己的 transaction」描述不符（UserSessionRepository.java:34-38）
- [V3-L13] `AuthController.extractJtiSafely` 不檢查 `type==refresh`，cookie 內塞他人 access token 會錯誤標 "current"（AuthController.java:244-253）

---

## 六、修復路線圖

### Sprint 1（release blocker，上線前必修）

| #  | 議題                                                    | 預估   | 驗收 |
| -- | ------------------------------------------------------- | ------ | ---- |
| 1  | V3-H1 dispatcher 必傳 tenantId（routing 策略 + 整合測試） | 2 天   | 接 LDAP 的租戶實際走 LDAP，single-tenant routing test 全綠 |
| 2  | V3-H2 dispatcher fallback 預設改 false + 限縮 + 稽核     | 0.5 天 | 故意讓 LDAP 失敗時必須 401，不會回退 LOCAL |
| 3  | V3-H3 encryptor key fail-fast                            | 0.5 天 | prod profile 缺 key → 啟動失敗 |
| 4  | V3-H6 平台 floor 提升 post-write scan                   | 1 天   | 設 min_length=12 後，所有 override<12 都被清除或拒寫 |
| 5  | V3-H7 reset-password 帶 tenantId                        | 1 天   | 租戶 A 規則 min=20 時，reset 設 12 位密碼必失敗 |
| 6  | V3-H10 super_admin 拒走 switch-tenant TENANT scope       | 0.5 天 | super admin 呼叫 switch-tenant 回 USE_IMPERSONATION |
| 7  | V3-H11 recordSession 改強一致                            | 0.5 天 | DB 故障時 login 整個 5xx，不簽出 token |
| 8  | N-3 對 cookie-bearing 寫入端點強制 CSRF token / Origin   | 1 天   | 缺 X-CSRF 的 refresh/logout 一律 403 |
| 9  | N-8 application.yml 設 `forward-headers-strategy=NATIVE`，部署 SOP 加 Nginx `set_real_ip_from` | 0.5 天 | rate limit 與登入 IP 在 staging 看到真實 client IP |

### Sprint 2（接 production / 開放租戶自助前必修）

| #  | 議題                                                          | 預估   |
| -- | ------------------------------------------------------------- | ------ |
| 10 | V3-H4 LocalAuthProvider 用 atomic UPDATE / @Version            | 0.5 天 |
| 11 | V3-H5 testConnection SSRF 防護                                 | 1 天   |
| 12 | V3-H8 impersonation token 加 session-status 檢查 + 撤銷快取    | 1 天   |
| 13 | V3-H9 refresh token DB-truth fallback + reuse detection        | 1.5 天 |
| 14 | V3-H12 ScopeEnforcementFilter 改 method-level annotation      | 1.5 天 |
| 15 | V3-M6 補 `UserAdminService.resetUserPassword` + force_change_on_admin_reset 落地 | 1 天 |
| 16 | V3-M7 `/v1/noauth/password-policy/describe` 加 rate-limit + per-request cache | 0.5 天 |
| 17 | V3-M12 noauth describe 只回 platform default                  | 0.5 天 |

### Sprint 3（強化與一致性）

- V3-M1~M5、V3-M8~M11、V3-M13~M18 各項
- N-9 password history N 在 reset / change 分離
- 將 V3-L1~L13 視專案行有餘力處理

---

## 七、附錄

### A. 新子系統檔案清單

**provider/**：
- `AuthenticationDispatcher.java`、`AuthenticationProvider.java`、`AuthType.java`、`AuthenticationRequest.java`、`AuthenticationResult.java`
- `local/LocalAuthProvider.java`
- `config/entity/TenantAuthConfigEntity.java`
- `config/service/impl/TenantAuthConfigServiceImpl.java`
- `config/controller/TenantAuthConfigController.java`、`PlatformTenantAuthConfigController.java`
- `crypto/AuthConfigEncryptor.java`

**policy/**：
- `PasswordPolicyService.java`、`PasswordPolicyResolver.java`、`PasswordPolicyDao.java`、`PasswordExpiryChecker.java`
- `PasswordPolicy.java`、`PasswordPolicyKey.java`
- `controller/TenantPasswordPolicyController.java`、`PlatformPasswordPolicyController.java`、`NoauthPasswordPolicyController.java`

**security/（新增）**：
- `ScopeEnforcementFilter.java`、`TokenScope.java`

**session 管理（新增）**：
- `entity/UserSessionEntity.java`、`repository/UserSessionRepository.java`
- `service/UserSessionService.java` + `impl/UserSessionServiceImpl.java`

**impersonation（新增子系統，本輪偵測）**：
- `platform/impersonation/service/ImpersonationService.java`

### B. 測試覆蓋（17 個 auth 測試檔）

- Provider 子系統：4 個（含 dispatcher / local / config / encryptor）
- Policy 子系統：3 個（`PasswordPolicyServiceTest`、`PasswordPolicyResolverTest`、`PasswordExpiryCheckerTest`）
- Scope：2 個（`ScopeEnforcementFilterTest`、`JwtUtilScopeTest`）
- Session：1 個（`UserSessionServiceTest`）
- 其餘為 v2 已存在

### C. 與 v2 評分對照

| 維度       | v1     | v2 修復後 | v3        |
| ---------- | ------ | --------- | --------- |
| 安全性     | 6.5    | 9.1       | **7.8** ⬇ |
| 正確性     | 7.0    | 8.5       | **8.0** ⬇ |
| 可維護性   | 7.5    | 8.0       | **7.8** ⬇ |
| 可觀測性   | 6.0    | 8.3       | **7.8** ⬇ |
| **總分**   | 6.8    | 8.5       | **7.9** ⬇ |

新功能引入新風險屬於預期內，但其中 7 項高風險集中在 **「設計的安全控制沒被實際接上」**（H1 multi-provider 沒接通、H6 floor 不溯及既往、H8 impersonation 撤銷無效、H10 super admin 旁路、H12 scope 路徑放行）— 這類問題若不在這次處理，未來會因外部安全稽核大規模返工。

---

**End of v3**
