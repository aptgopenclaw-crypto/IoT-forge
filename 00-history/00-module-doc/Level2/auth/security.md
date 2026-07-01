.這五個 Class 共同構成了該系統的 **「JWT 認證、多租戶隔離與安全防護核心引擎」**。它們緊密協作，解決了現代企業級後台系統中最複雜的幾個安全與架構問題：**無狀態認證、平台/租戶權限隔離 (ADR-007)、代操審計、以及 Cookie 型 Refresh Token 的 CSRF 防護**。

以下為您逐一拆解這五個 Class 的功能與設計目的：

---

### 1. `JwtUtil.java`：JWT 密碼學與生命週期管理中心
**核心定位**：封裝底層 JJWT 庫，提供統一、安全且符合複雜業務場景的 Token 生成與解析能力。

**主要功能**：
*   **密碼學強度把關**：在初始化時強制檢查 `jwt.secret` 長度必須 $\ge$ 32 bytes (256 bits)，確保 HMAC-SHA256 簽章不被暴力破解。
*   **多場景 Token 發行**：
    *   **Access Token**：標準業務 Token，封裝了 `userId`, `tenantId`, `roles`, `permissions`, `dataScope` 以及關鍵的 **`scope` (TokenScope)**。
    *   **Impersonation Token (代操 Token)**：超級管理員代操租戶時使用，額外夾帶 `impersonation` claim (記錄原管理者 ID 與 Session)，供後續審計追蹤。
    *   **Temporary Token (臨時 Token)**：用於登入後「選擇租戶」的過渡階段，標記為 `temporary: true`。
    *   **Password Change Token**：強制修改密碼的短期 Token，綁定 `purpose: password_change`，防止被挪作他用。
    *   **Refresh Token**：帶有唯一的 `jti` (JWT ID)，用於與 Redis 撤銷清單及資料庫 `user_session` 表精確對齊，支援單點登出或單 Token 撤銷。

**設計目的**：將 Token 的發行邏輯集中管理，確保所有發出的 Token 都帶有足夠的上下文 (如租戶 ID、權限碼、作用域)，並滿足不同安全級別（如短期強制改密、長期刷新）的需求。

---

### 2. `CsrfCookieFilter.java`：Cookie 型 Refresh Token 的 CSRF 防護盾
**核心定位**：針對「攜帶 HttpOnly Cookie (Refresh Token) 的變更端點」進行跨站請求偽造 (CSRF) 防護。

**主要功能**：
*   **精準攔截**：只針對特定的 POST 端點 (`/v1/noauth/token/refresh`, `/v1/auth/logout`, `/v1/auth/idle-logout`) 進行檢查，不影響一般 API 效能。
*   **條件式防護**：先檢查請求是否帶有 `refresh_token` Cookie。如果沒有，直接放行（沒有 Cookie 就沒有 CSRF 風險）。
*   **Origin/Referer 白名單校驗**：若帶有 Cookie，則嚴格檢查 HTTP Header 中的 `Origin` 或 `Referer` 是否來自 `CorsProperties` 設定的合法前端網域。若驗證失敗，直接回傳 403 並記錄 `CSRF_ATTEMPT` 安全日誌。

**設計目的**：系統採用 Stateless JWT (Access Token 放 Header)，但為了安全將 Refresh Token 放在 HttpOnly Cookie 中。瀏覽器會自動在跨站請求中附帶 Cookie，因此必須透過檢查 Origin/Referer 來防禦 CSRF，確保「刷新 Token」和「登出」動作確實是由合法的前端頁面發起的。

---

### 3. `JwtAuthenticationFilter.java`：認證上下文初始化與多租戶路由核心
**核心定位**：Spring Security 過濾器鏈的核心，負責將無狀態的 JWT 轉換為系統可理解的「認證上下文 (Authentication)」與「多租戶上下文 (TenantContext)」。

**主要功能**：
*   **JWT 解析與權限映射**：提取 JWT 中的 `roles` (加上 `ROLE_` 前綴) 與 `permissions`，構建 Spring Security 的 `GrantedAuthority`，並設定到 `SecurityContextHolder`。
*   **上下文傳遞**：將 `tenantId`, `deptId`, `dataScope`, `scope` 等額外資訊存入 Authentication 的 `details` 中，供後續過濾器（如 `ScopeEnforcementFilter`）使用。
*   **多租戶與場域即時控制**：
    *   透過 `TenantEnabledCache` 即時檢查該租戶（場域）是否被停用。若停用，**不等 Token 自然過期，直接返回 403 阻擋**。
    *   設定 `TenantContext`，確保後續的 MyBatis/JPA 查詢能自動帶入正確的 `tenantId` (資料隔離)。
    *   若為 `SUPER_ADMIN`，則標記為「代操 (Impersonator)」或「系統上下文」。
*   **ThreadLocal 記憶體洩漏防護**：在 `finally` 區塊中強制呼叫 `TenantContext.clear()`，防止 Tomcat 執行緒池重用時，殘留的 ThreadLocal 資料污染下一個請求。
*   **安全審計**：捕獲 JWT 過期、格式錯誤等異常，統一透過 `SecurityLogger` 記錄安全警告。

**設計目的**：這是請求進入業務邏輯前的「總開關」。它不僅解決了「你是誰、你有什麼權限」的問題，更解決了「你屬於哪個租戶、你的資料隔離邊界在哪裡」的核心多租戶架構問題。

---

### 4. `TokenScope.java`：平台/租戶分離的權限邊界定義 (ADR-007)
**核心定位**：一個列舉 (Enum)，定義 JWT Token 的「作用域 (Scope)」，從根本上隔離平台管理與租戶業務。

**主要功能 (定義三種 Scope)**：
*   **`PLATFORM`**：超級管理員 (super_admin) 的 Token。沒有 `tenantId`，只能存取平台級 API (`/v1/platform/`)。
*   **`TENANT`**：一般租戶使用者的 Token。有 `tenantId`，只能存取租戶級 API (`/v1/auth/` 及對應業務)。
*   **`IMPERSONATION`**：超管代操租戶時發放的 Token。路由規則同 `TENANT`，但會觸發特殊的審計日誌。

**設計目的**：實作架構決策記錄 **ADR-007 (Platform/Tenant Separation)**。防止超級管理員的 Token 被盜用後，拿去呼叫租戶級的業務 API（反之亦然），在 Token 層級就劃清「平台維運」與「租戶業務」的界線。

---

### 5. `ScopeEnforcementFilter.java`：URL 路由與 Token Scope 的強制校驗器
**核心定位**：在 `JwtAuthenticationFilter` *之後* 執行的第二道防線，負責校驗「Token 的 Scope」是否與「當前請求的 URL 路徑」匹配。

**主要功能**：
*   **路由與 Scope 匹配規則**：
    *   請求 `/v1/platform/**` $\rightarrow$ 必須是 `PLATFORM` Scope。
    *   請求 `/v1/auth/**` (非豁免路徑) $\rightarrow$ 必須是 `TENANT` 或 `IMPERSONATION` Scope。
*   **跨域/啟動端點豁免**：定義了 `AUTH_SCOPE_AGNOSTIC_EXACT/PREFIXES` (如 `select-tenant`, `logout`, `user/info`, `menus/my`)。這些端點無論是平台超管還是租戶用戶都會用到，因此豁免 Scope 檢查。
*   **平滑升級/滾退機制**：支援 `enforce` (預設，不匹配直接回傳 403) 和 `warning` (僅記錄日誌，不阻擋) 兩種模式。
*   **舊 Token 相容**：若 JWT 中沒有 `scope` claim (舊版 Token)，預設視為 `TENANT`。

**設計目的**：落實 ADR-007 的實體路由隔離。即使 `JwtAuthenticationFilter` 驗證 Token 簽章合法，`ScopeEnforcementFilter` 也會確保 **「平台級的鑰匙不能開租戶的門」**。其 `warning` 模式設計展現了成熟的工程思維，允許在生產環境平滑過渡新安全策略而不中斷服務。

---

### 總結：這五個 Class 如何協同運作？

當一個請求進入系統時，它們的生命週期如下：

1.  **`CsrfCookieFilter`** 先檢查：如果是刷新/登出請求且帶有 Cookie，驗證 Origin/Referer 防 CSRF。
2.  **`JwtAuthenticationFilter`** 接手：解析 JWT，驗證簽章，建立 Spring Security 認證上下文，並**初始化多租戶上下文 (TenantContext)**。
3.  **`ScopeEnforcementFilter`** 把關：讀取 JWT 中的 `scope` (由 **`TokenScope`** 定義)，比對當前請求的 URL 前綴，確保平台 Token 不打租戶 API，租戶 Token 不打平台 API。
4.  最後，請求順利進入 Controller，而底層的 Token 發行與解析邏輯，皆由 **`JwtUtil`** 在背後安全地支撐著。