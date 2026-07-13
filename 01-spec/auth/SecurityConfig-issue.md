檢查 `SecurityConfig` 設定是否有遺漏或潛在風險，不能僅看單一檔案，需要從**程式碼邏輯、授權規則、JWT 配套機制、以及自動化驗證**四個維度進行全面審計。

根據您提供的 `SecurityConfig.java`，您的配置已經具備很高的安全意識（例如詳細的安全 Headers、統一的 JSON 異常處理、預設拒絕原則）。以下為您整理出具體的檢查清單與潛在盲點：

---

### 一、 程式碼層面的潛在矛盾與風險檢查 (Code Review)

#### 1. CSRF 防護的矛盾點 (高風險)
* **現象**：程式碼中寫了 `.csrf(csrf -> csrf.disable())`，但同時又注入了 `csrfCookieFilter` 並放在過濾器鏈中。
* **檢查點**：
  * 如果您的系統**完全**使用 JWT (放在 HTTP Header 如 `Authorization: Bearer <token>`)，禁用 CSRF 是正確的，因為瀏覽器不會自動帶入 Header。
  * **但是**，如果您的 **Refresh Token 是存放在 HttpOnly Cookie 中**，則**絕對不能**完全禁用 CSRF。此時 `csrfCookieFilter` 必須實作嚴格的防護（例如：Double Submit Cookie 機制，或嚴格驗證 `Origin` / `Referer` 標頭）。請檢查 `CsrfCookieFilter` 的實作是否足以彌補 `csrf.disable()` 帶來的風險。

#### 2. CORS 設定的一致性
* **現象**：註解提到 `cors(Customizer.withDefaults())` 委派給 `WebMvcConfig.addCorsMappings()`。
* **檢查點**：請檢查 `WebMvcConfig` 中的 CORS 設定。確保 `allowedOrigins` 沒有使用 `*` (萬用字元)，且 `allowedMethods` 和 `allowedHeaders` 符合前端需求。**Security 層級的 CORS 設定優先級高於 WebMvc**，需確認兩者沒有衝突。

#### 3. 方法級別授權 (`@PreAuthorize`) 的覆蓋率
* **現象**：URL 規則中大量使用 `hasAnyAuthority("USER_LIST", "USER_CREATE"...)`，並註解「細部由 `@PreAuthorize` 控制」。
* **檢查點**：既然開啟了 `@EnableMethodSecurity`，請確保對應的 Controller 方法上**都有**加上 `@PreAuthorize`。如果某個 Controller 方法忘記加 `@PreAuthorize`，它將只受 URL 層級的粗粒度控管，可能導致權限溢出。

---

### 二、 URL 授權規則的健壯性檢查 (Authorization Rules)

#### 1. HTTP Method 的限制
* **現象**：部分路徑沒有指定 `HttpMethod`，例如 `.requestMatchers("/v1/auth/audit/**")`。
* **檢查點**：未指定 Method 意味著 GET, POST, PUT, DELETE 都會匹配。建議盡量明確指定 Method（如 `HttpMethod.GET`），避免攻擊者嘗試用 POST 請求去存取原本只設計給 GET 的查詢接口，從而繞過某些僅在 GET 請求中生效的業務邏輯檢查。

#### 2. 路徑匹配的順序 (Order of Matchers)
* **現象**：Spring Security 的 `requestMatchers` 是**由上往下匹配，一旦匹配成功就不再往下檢查**。
* **檢查點**：目前的順序看起來合理（具體路徑在前，`anyRequest` 在後）。但請特別注意：
  * `/v1/auth/users/**` 允許了多個權限，如果裡面有不需要這些權限的子路徑，必須寫在更前面。
  * 確保沒有把 `permitAll()` 的路徑寫在需要 `authenticated()` 的路徑**之後**，否則後者永遠不會被匹配到。

#### 3. Actuator 端點的暴露
* **現象**：`/actuator/health/**` 開放，其餘 `/actuator/**` 需要 `SYSTEM_OPS`。
* **檢查點**：確認 `application.yml` 中 `management.endpoints.web.exposure.include` 的設定。即使 Security 擋住了，也應該在配置檔中只暴露必要的端點（如 `health, info, prometheus`），避免意外暴露 `env`, `beans`, `mappings` 等敏感端點。

---

### 三、 JWT 與無狀態架構的配套檢查 (Stateless & JWT)

#### 1. 登出 (Logout) 與 Token 黑名單
* **現象**：`/v1/auth/logout` 設為 `permitAll()`，且 Session 為 `STATELESS`。
* **檢查點**：因為 JWT 是無狀態的，後端無法主動使 JWT 失效。請檢查後端是否實作了 **Token 黑名單機制**（通常使用 Redis）。當使用者登出時，必須將該 JWT 的 `jti` (JWT ID) 或整個 Token 加入黑名單，並設定過期時間與 JWT 剩餘有效期一致。否則，登出後在 Token 自然過期前，該 Token 仍可繼續使用。

#### 2. ScopeEnforcementFilter 的狀態
* **現象**：註解提到 `[ADR-007] Phase 1.1.2 — observe scope claim vs path prefix in warning mode`。
* **檢查點**：這表示目前 Scope 驗證僅在「觀察/警告模式」，並未真正阻擋非法請求。請確認是否有計畫或已經切換到「強制模式 (enforce mode)」，否則 JWT 中的 Scope 宣告將形同虛設。

#### 3. JwtAuthenticationFilter 的驗證邏輯
* **檢查點**：請檢查該 Filter 的實作，確保它不僅驗證簽名 (Signature)，還驗證了：
  * **過期時間 (exp)**
  * **發行者 (iss)** 與 **接收者 (aud)** (防止 Token 被其他系統盜用)
  * **使用者狀態** (例如：使用者被停用後，即使 Token 未過期也應拒絕)

---

### 四、 如何進行自動化驗證與測試？

要確認設定是否真的生效，不能只靠肉眼檢查，建議執行以下驗證：

#### 1. 開啟 Security 除錯日誌
在 `application-dev.yml` 中加入以下設定，觀察過濾器鏈的執行順序與決策過程：
```yaml
logging:
  level:
    org.springframework.security: DEBUG
    org.springframework.security.web.FilterChainProxy: DEBUG
```

#### 2. 編寫 Security 整合測試 (使用 MockMvc)
使用 `@SpringBootTest` 和 `@AutoConfigureMockMvc` 編寫測試案例，覆蓋以下場景：
* **未帶 Token** 存取 `/v1/auth/users` -> 應返回 401 (ACCESS_TOKEN_INVALID)。
* **帶有效 Token 但無權限** 存取 `/v1/auth/users` -> 應返回 403 (PERMISSION_DENIED)。
* **帶有效 Token** 存取 `/v1/noauth/public` -> 應返回 200。
* **使用錯誤的 HTTP Method** (例如用 DELETE 存取只允許 GET 的接口) -> 應返回 403 或 405。

#### 3. 使用 OWASP ZAP 或 Burp Suite 進行 DAST 掃描
* 掃描您的 API，確認 **Security Headers** (CSP, HSTS, X-Frame-Options 等) 是否真的在 Response 中正確返回。
* 測試 **CORS** 設定，嘗試發送帶有惡意 `Origin` 的請求，確認是否被正確阻擋。
* 測試 **CSRF**，如果前端有使用 Cookie，嘗試構造跨站請求，確認 `csrfCookieFilter` 是否發揮作用。

### 總結建議

您的 `SecurityConfig` 基礎非常扎实。接下來最優先的檢查動作是：
1. **確認 CSRF 策略**：釐清 Refresh Token 的存放位置，確保 `csrf.disable()` 不會造成漏洞。
2. **檢查 Controller 的 `@PreAuthorize`**：確保沒有漏網之魚。
3. **確認登出黑名單機制**：確保 JWT 登出後真的無法使用。