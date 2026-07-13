這個 `SecurityConfig` 類是 Spring Boot 應用程式中的**核心安全配置類**，主要負責定義 Web 安全規則、過濾器鏈、授權策略、安全標頭以及異常處理。它基於 Spring Security 框架，並結合了 JWT (JSON Web Token) 來實現無狀態的認證與授權機制。

以下是該類的詳細結構與功能說明：

### 1. 核心註解與依賴
* **`@EnableWebSecurity` & `@EnableMethodSecurity`**：啟用 Spring Security 的 Web 安全功能，並支援方法級別的安全控制（例如在 Controller 上使用 `@PreAuthorize`）。
* **依賴注入 (Lombok `@RequiredArgsConstructor`)**：
  * `CsrfCookieFilter`：自定義的 CSRF 防護過濾器。
  * `JwtAuthenticationFilter`：自定義的 JWT 解析與認證過濾器。
  * `ScopeEnforcementFilter`：自定義的權限範圍 (Scope) 強制執行過濾器。
  * `ObjectMapper`：用於將異常資訊序列化為 JSON 格式回應。

---

### 2. 安全過濾器鏈 (SecurityFilterChain)
該類定義了兩個 `SecurityFilterChain` Bean，透過 `@Order` 決定優先級：

#### A. Swagger/OpenAPI 專用鏈 (`@Order(1)`)
* **條件註冊**：僅在配置檔中啟用 `springdoc.api-docs.enabled=true` 時生效，確保生產環境不會意外暴露 API 文件。
* **規則**：放行所有 Swagger 相關路徑 (`/v3/api-docs/**`, `/swagger-ui/**` 等)，並禁用 CSRF。

#### B. 主業務安全鏈 (`@Order(2)`)
這是應用的核心安全配置，包含以下重要設定：
* **Session 管理**：設定為 `STATELESS`（無狀態），因為系統依賴 JWT 進行認證，不使用 Server-side Session。
* **安全標頭 (Security Headers)**：防禦多種 Web 攻擊，符合 OWASP 規範。
  * **HSTS**：強制 HTTPS，有效期 1 年，包含子網域，防止降級攻擊。
  * **CSP (內容安全政策)**：嚴格限制資源載入來源，有效防禦 XSS 攻擊。
  * **Referrer-Policy**：設定為 `STRICT_ORIGIN_WHEN_CROSS_ORIGIN`，防止敏感 URL 資訊洩漏給第三方。
  * **Permissions-Policy**：禁用攝影機、麥克風、地理位置等敏感裝置 API。
  * **X-XSS-Protection**：顯式關閉舊版瀏覽器的 XSS 審計器。
* **統一異常處理 (Exception Handling)**：
  * **401 未認證**：攔截並回傳統一的 JSON 格式錯誤碼 (`ACCESS_TOKEN_INVALID`)。
  * **403 無權限**：記錄安全日誌 (`SecurityLogger`)，並回傳統一的 JSON 格式錯誤碼 (`PERMISSION_DENIED`)。
* **URL 授權規則 (Authorization)**：採用 **RBAC (基於角色的存取控制)** 模型進行細粒度控管。
  * **公開放行**：`/v1/noauth/**`、WebSocket (`/ws/**`)、健康檢查 (`/actuator/health/**`)、登出 (`POST /v1/auth/logout`)。
  * **系統維運**：其他 Actuator 端點需 `SYSTEM_OPS` 權限。
  * **模組化權限控制**：針對使用者、角色、權限、選單、部門、審計日誌、資產異動等模組，根據 HTTP Method (GET/POST/PUT/DELETE) 和具體權限代碼 (如 `USER_LIST`, `ROLE_CREATE`) 進行嚴格攔截。
  * **預設規則**：所有未明確列出的請求 (`anyRequest()`) 均需認證。
* **自定義過濾器執行順序**：
  1. `csrfCookieFilter` (在 JWT 之前，保護 Cookie 承載的 POST 請求)
  2. `jwtAuthenticationFilter` (解析並驗證 JWT Token)
  3. `scopeEnforcementFilter` (在 JWT 之後，驗證 Token 中的 Scope 與請求路徑是否匹配)

---

### 3. 密碼編碼器
* **`passwordEncoder()`**：提供 `BCryptPasswordEncoder` Bean，用於使用者密碼的雜湊加密與驗證，確保密碼不以明文儲存於資料庫中。

### 總結
這是一個設計嚴謹、符合企業級安全規範的 Spring Security 配置。它不僅實現了基於 JWT 的無狀態認證，還透過嚴格的 CSP、HSTS 等安全標頭防禦常見 Web 攻擊，並結合 RBAC 模型實現了細粒度的 API 權限控管與統一的 JSON 錯誤回應。