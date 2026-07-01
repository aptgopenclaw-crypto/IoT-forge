這個 `SecurityConfig.java` 類別是整個後端系統的**安全中樞（Security Hub）**，基於 Spring Security 框架編寫。

它的**核心目的**是：為系統建立一套嚴格的**認證（Authentication）**、**授權（Authorization）**與**Web 安全防護機制**，確保 API 免受未經授權的存取與常見的 Web 攻擊（如 XSS、CSRF、中間人攻擊等），同時滿足企業級的安全合規要求（從註解中的 N-3, N-4, N-7, ADR-007 等代號可以看出，這套配置是經過嚴格的安全審查或資安規範約束的）。

以下是該類別的**主要功能拆解**：

### 1. 定義多條安全過濾鏈 (SecurityFilterChain)
系統根據請求路徑與環境變數，將請求分流到不同的安全鏈：
*   **Swagger/OpenAPI 專用鏈 (`swaggerFilterChain`)**：
    *   **目的**：確保 API 文件（Swagger UI）僅在開發/測試環境（`springdoc.api-docs.enabled=true`）開放，避免生產環境意外暴露 API 結構。此鏈對文件路徑完全放行並關閉 CSRF。
*   **主安全鏈 (`filterChain`)**：
    *   **目的**：處理所有核心業務 API 的安全驗證。採用 **Stateless（無狀態）** 會話管理，完全依賴 JWT 進行認證。

### 2. 嚴格的 HTTP 安全標頭防護 (Security Headers)
為了防禦各種 Web 攻擊，配置了極其詳細的 HTTP Response Headers（符合 OWASP 最佳實踐）：
*   **HSTS**：強制使用 HTTPS，防止中間人攻擊（MITM）降級竊聽。
*   **CSP (Content Security Policy)**：嚴格限制外部資源載入，有效防禦 **XSS（跨站腳本攻擊）**。
*   **Referrer-Policy**：防止跳轉時將帶有 Token 或敏感資訊的完整 URL 洩漏給第三方。
*   **Permissions-Policy**：禁用瀏覽器敏感 API（如攝影機、麥克風、定位），防止惡意腳本濫用裝置硬體。
*   **X-XSS-Protection**：顯式關閉舊版瀏覽器有缺陷的 XSS 審計器。

### 3. 自定義過濾器鏈 (Custom Filter Chain)
在 Spring Security 標準流程中插入了三個自定義過濾器，以滿足特定業務與安全需求：
1.  `CsrfCookieFilter`：在 JWT 認證前執行，針對攜帶 Cookie 的特定請求（如登出、刷新 Token）進行 CSRF 防護（檢查 Origin/Referer）。
2.  `JwtAuthenticationFilter`：核心過濾器，負責解析 JWT、驗證簽名並建立 Spring Security 的 `Authentication` 物件。
3.  `ScopeEnforcementFilter`：在 JWT 認證後執行，用於校驗 JWT 中的 `scope` 聲明與請求的 URL 路徑前綴是否匹配（防止 Token 越權使用）。

### 4. 細粒度的 URL 級別授權 (RBAC 權限控制)
透過 `requestMatchers` 對系統的所有 API 端點進行了**基於權限碼（Authority）的細粒度存取控制**：
*   **公開端點**：如 `/v1/noauth/**`、WebSocket 握手、健康檢查 (`/actuator/health`)、登出 (`logout`)。
*   **系統維運端點**：如 `/actuator/**` 限制僅 `SYSTEM_OPS` 角色可存取。
*   **業務模組權限隔離**：
    *   使用者管理、角色管理 (RBAC)、選單管理、審計日誌、部門管理等，皆嚴格對應到具體的權限碼（如 `USER_LIST`, `ROLE_CREATE`, `MENU_UPDATE` 等）。
    *   平台/租戶管理 (`/v1/platform/tenants/**`) 限制為最高權限 `PLATFORM_TENANT_MANAGE`。
*   **方法級別安全**：透過 `@EnableMethodSecurity` 啟用，允許在 Controller 層使用 `@PreAuthorize` 進行更細部的資料級別或邏輯級別權限控制。

### 5. 統一的安全異常處理與審計日誌
*   **401 未認證 (AuthenticationEntryPoint)**：當 Token 無效或過期時，攔截並返回統一的 JSON 格式 (`BaseResponse.fail(ErrorCode.ACCESS_TOKEN_INVALID)`)，避免洩漏系統底層資訊。
*   **403 拒絕存取 (AccessDeniedHandler)**：當權限不足時，除了返回統一的 JSON 錯誤碼 (`PERMISSION_DENIED`)，還會**觸發 `SecurityLogger` 記錄安全警告日誌**，記錄 IP、路徑與使用者資訊，便於後續資安審計與入侵偵測。

### 6. 密碼加密策略
*   提供 `BCryptPasswordEncoder` Bean，確保系統中所有使用者密碼皆使用帶有 Salt 的 BCrypt 雜湊演算法進行單向加密儲存。

---

**總結來說：**
這個 Class 的目的是**打造一個符合高標準資安規範（如 ISO 27001 或政府/金融級資安健檢要求）的後端防護網**。它不僅解決了「誰能存取什麼 API」的問題（JWT + RBAC），更主動防禦了「駭客可能利用的 Web 漏洞」（CSP, HSTS, CSRF 防護），並將安全事件標準化輸出以便於監控。