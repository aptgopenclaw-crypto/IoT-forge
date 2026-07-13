這個 `AuthController` 是系統中的**核心認證與授權控制器**，負責處理所有與使用者登入、登出、Token 管理、密碼重設、多租戶切換以及 Session 管理相關的 HTTP 請求。

從程式碼中可以看出，這是一個設計非常成熟、具備企業級安全意識的 Controller。以下為該類別的詳細結構與功能說明：

### 1. 核心設計亮點與安全機制
* **雙 Token 機制 (Dual Token)**：
  * **Access Token**：前端透過 HTTP Header (`Authorization: Bearer <token>`) 傳遞，生命週期短。
  * **Refresh Token**：後端透過 **HttpOnly Cookie** 傳遞，生命週期長（預設 7 天），有效防止 XSS 攻擊竊取。
* **Secure-by-default (安全預設) Cookie 設定**：
  * 透過 `@Value` 注入 Cookie 屬性，預設 `secure=true`（僅限 HTTPS 傳輸）、`sameSite=Lax`（防禦 CSRF）、`httpOnly=true`（禁止 JS 讀取）。
* **多租戶架構支援 (Multi-Tenancy)**：
  * 提供 `select-tenant` 和 `switch-tenant` 端點，允許使用者在登入後選擇或切換其所屬的租戶 (Tenant)，JWT 中會攜帶 `tenantId`。
* **全面的速率限制 (Rate Limiting)**：
  * 使用自定義 `@RateLimit` 註解，針對敏感 API（如登入、刷新 Token、忘記密碼）進行 IP 級別的請求頻率限制，有效防禦**暴力破解**與**郵件轟炸**攻擊。
* **審計日誌 (Audit Logging)**：
  * 在登出、閒置登出、強制登出等關鍵操作上使用 `@AuditEvent` 註解，自動記錄安全審計日誌。

---

### 2. API 端點分類說明

#### A. 公開端點 (NoAuth) - 對應 `SecurityConfig` 中的 `/v1/noauth/**`
這些端點無需 Access Token 即可存取，主要用於登入流程與密碼恢復：
* **`GET /v1/noauth/turnstile/config`**：回傳 Cloudflare Turnstile (人機驗證) 的設定，讓前端決定要顯示哪種驗證碼。
* **`POST /v1/noauth/captcha`**：產生傳統圖片驗證碼（限流：20次/分鐘）。
* **`POST /v1/noauth/login`**：核心登入 API。驗證帳密後，若為單租戶直接登入，會將 Refresh Token 寫入 Cookie。
* **`POST /v1/noauth/token/refresh`**：刷新 Access Token。從 Cookie 中讀取 Refresh Token 進行驗證並發放新 Token（限流：30次/分鐘）。
* **`POST /v1/noauth/user/forgot-password`**：發送忘記密碼郵件（限流：5次/5分鐘）。
* **`PUT /v1/noauth/user/reset-password`**：透過郵件中的連結重設密碼。
* **`POST /v1/noauth/user/force-change-password`**：處理「首次登入」或「密碼已過期」的強制改密邏輯。此處巧妙地使用了一個**短期的 Bearer Token**（而非正式 Access Token）來驗證身分。

#### B. 需認證端點 (Auth) - 對應 `SecurityConfig` 中的需驗證規則
這些端點必須攜帶有效的 Access Token 才能存取：
* **租戶管理**：
  * `POST /v1/auth/select-tenant`：初次登入多租戶帳號時，選擇要進入的租戶。
  * `POST /v1/auth/switch-tenant`：在系統內切換到另一個有權限的租戶。
* **登出管理**：
  * `POST /v1/auth/logout`：主動登出，清除 Refresh Token Cookie，並將 Token 加入黑名單。
  * `POST /v1/auth/idle-logout`：前端偵測到使用者閒置過久時觸發的被動登出。
* **使用者資訊**：
  * `GET /v1/auth/user/info`：取得當前登入使用者的詳細資訊與權限。
* **Session 與裝置管理 (N-7 需求)**：
  * `GET /v1/auth/sessions`：列出當前使用者的所有有效 Session（登入裝置清單），並透過比對 Cookie 中的 `jti` 標記出「當前裝置」。
  * `DELETE /v1/auth/sessions/{sessionId}`：強制登出指定裝置（撤銷該裝置的 Refresh Token）。

---

### 3. 關鍵私有輔助方法 (Private Helpers)
* **`getCurrentUserId()` / `getCurrentTenantId()`**：
  * 從 Spring Security 的 `SecurityContextHolder` 中安全地提取當前認證使用者的 ID 和租戶 ID。若未認證則拋出 `ACCESS_TOKEN_INVALID` 異常。
* **`setRefreshTokenCookie()` / `clearRefreshTokenCookie()`**：
  * 封裝了 Cookie 的寫入與清除邏輯。嚴格設定了 `HttpOnly`, `Secure`, `SameSite`, `Path`, `MaxAge` 等安全屬性，確保 Refresh Token 不會被前端 JS 讀取，且只能在 HTTPS 環境下傳輸。
* **`extractJtiSafely()`**：
  * 安全地解析 Refresh Token 中的 `jti` (JWT ID)。即使 Token 格式錯誤或過期，也只會回傳 `null` 而不會拋出異常導致系統崩潰，主要用於前端標記「當前裝置」。

### 總結
`AuthController` 不僅僅是一個簡單的登入/登出接口，它完整實作了**現代 Web 應用的進階安全認證流程**，包含：雙 Token 架構、多租戶隔離、嚴格的速率限制、安全的 Cookie 管理，以及完整的 Session/裝置控制能力。它與 `SecurityConfig` 中的 URL 放行規則完美配合，構成了系統的第一道防線。