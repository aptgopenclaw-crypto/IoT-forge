這五個 Class 加上 `SecurityConfig`，共同構成了一個**精密協作、職責分明且具備縱深防禦（Defense in Depth）的 Spring Security 過濾器鏈（Filter Chain）**。

如果要一句話總結它們的關係：
**`SecurityConfig` 是總指揮，負責編排執行順序；`JwtUtil` 與 `TokenScope` 是底層引擎與契約；而三個 Filter 則是按照嚴格順序執行的三道安全防線，共同完成「來源驗證 ➔ 身份認證 ➔ 權限隔離」的完整流程。**

以下為您詳細剖析它們的彼此關係與協作流程：

---

### 一、 核心角色與依賴關係定位

在進入執行流程前，我們先釐清它們在架構中的定位：

1. **總指揮 / 編排者：`SecurityConfig`**
   * **角色**：定義整個安全架構的藍圖。
   * **關係**：它**依賴並注入**了三個自定義 Filter，並透過 `addFilterBefore` / `addFilterAfter` 明確規定它們在 Spring Security 過濾器鏈中的**相對執行順序**。
2. **底層引擎 / 密碼學核心：`JwtUtil`**
   * **角色**：負責 JWT 的「簽發（生成）」與「解析（驗證）」。
   * **關係**：它是無狀態的基礎設施工具類。被 `JwtAuthenticationFilter` 依賴用來**解析**請求中的 Token；同時也被 `AuthController` / `AuthService` 依賴用來**簽發** Token。
3. **跨模組契約 / 領域列舉：`TokenScope`**
   * **角色**：定義 JWT 中 `scope` 欄位的合法值（`PLATFORM`, `TENANT`, `IMPERSONATION`）。
   * **關係**：它是**生產端**（`JwtUtil` 簽發 Token 時寫入）與**消費端**（`ScopeEnforcementFilter` 驗證時讀取）之間的**資料契約**。
4. **三道防線 / 執行者：三個 Filter**
   * **角色**：攔截 HTTP 請求，執行特定的安全檢查。
   * **關係**：它們彼此**不直接依賴**，而是透過 `SecurityConfig` 串聯，並透過 `HttpServletRequest` / `SecurityContextHolder` 傳遞上下文。

---

### 二、 過濾器鏈（Filter Chain）的執行順序與協作流程

當一個 HTTP 請求進入系統時，這三個 Filter 會按照以下**嚴格的順序**接力執行。這個順序是經過精心設計的，不可隨意調換：

#### 🛡️ 第一道防線：`CsrfCookieFilter` (來源與狀態防護)
* **執行時機**：**最早執行**（在 JWT 解析之前）。
* **協作邏輯**：
  * 它不關心使用者是誰（不需要 JWT），它只關心「這個請求是不是來自合法的前端網域」。
  * 它精準鎖定依賴 `refresh_token` Cookie 的 POST 端點（如 `/v1/auth/logout`）。
  * 如果請求帶有 Cookie 但 `Origin`/`Referer` 不合法，**直接阻擋（403）**，請求根本不會往下走，節省了後續 JWT 解析的效能開銷。
* **為什麼在最前面？** 因為 CSRF 攻擊本質上是利用瀏覽器的 Cookie 自動攜帶機制，這與 JWT 無關。必須在解析 JWT 之前，先確保這個「帶有 Cookie 的請求」不是跨站偽造的。

#### 🛡️ 第二道防線：`JwtAuthenticationFilter` (身份認證與上下文建立)
* **執行時機**：**中間執行**（在 CSRF 之後，Scope 之前）。
* **協作邏輯**：
  * 從 Header 提取 `Bearer <token>`，呼叫 **`JwtUtil.parseToken()`** 驗證簽名與有效期。
  * 提取 Claims（userId, tenantId, roles, permissions, **scope**）。
  * **關鍵交接**：將解析出來的 `scope`（其值由 **`TokenScope`** 定義）放入 `Authentication` 物件的 `details` 中，並設定 `TenantContext`。
  * 將 `Authentication` 物件存入 `SecurityContextHolder`，讓後續的 Filter 和 Controller 都能知道「當前使用者是誰」。
* **為什麼在中間？** 因為它負責「建立身份」。後面的 `ScopeEnforcementFilter` 和 Spring Security 內建的 `AuthorizationFilter` 都必須依賴它建立好的 `Authentication` 物件才能進行權限判斷。

#### 🛡️ 第三道防線：`ScopeEnforcementFilter` (平台/租戶隔離守門員)
* **執行時機**：**最後執行**（在 JWT 解析之後，Spring Security URL 授權之前）。
* **協作邏輯**：
  * 從 `SecurityContextHolder` 中取出上一步 `JwtAuthenticationFilter` 留下的 `Authentication` 物件。
  * 讀取 `details` 中的 `scope` claim（再次依賴 **`TokenScope`** 的定義）。
  * 比對請求的 URL 前綴：如果是 `/v1/platform/**`，則 `scope` 必須是 `PLATFORM`；如果是 `/v1/auth/**`，則 `scope` 必須是 `TENANT` 或 `IMPERSONATION`。
  * 如果不匹配（例如：租戶使用者拿著 TENANT Token 試圖存取平台管理 API），**直接阻擋（403 SCOPE_FORBIDDEN）**。
* **為什麼在最後（JWT 之後）？** 因為它必須先知道這個 Token 的 `scope` 是什麼（這需要 JWT 先被解析），才能進行路徑匹配。它是實現 ADR-007（平台/租戶分離）的核心機制。

---

### 三、 資料流與依賴關係圖解

為了更直觀地理解，我們可以將它們的關係抽象化為以下資料流：

```text
[HTTP Request]
      │
      ▼
┌─────────────────────────────────────────────────────────┐
│ 1. SecurityConfig (總指揮)                              │
│    - 決定走哪條 FilterChain                             │
│    - 注入並排序三個 Filter                              │
└─────────────────────────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────────────────────────┐
│ 2. CsrfCookieFilter (防線 1)                            │
│    - 檢查 Cookie + Origin/Referer                       │
│    - 失敗 ➔ 403 CSRF_VALIDATION_FAILED                  │
└─────────────────────────────────────────────────────────┘
      │ (通過)
      ▼
┌─────────────────────────────────────────────────────────┐
│ 3. JwtAuthenticationFilter (防線 2)                     │
│    - 呼叫 【JwtUtil】 解析 Bearer Token                 │
│    - 提取 Claims (包含 【TokenScope】)                  │
│    - 建立 Authentication 並放入 SecurityContext         │
│    - 失敗 ➔ 401 ACCESS_TOKEN_INVALID (由 Config 處理)   │
└─────────────────────────────────────────────────────────┘
      │ (通過，身份已確立)
      ▼
┌─────────────────────────────────────────────────────────┐
│ 4. ScopeEnforcementFilter (防線 3)                      │
│    - 讀取 SecurityContext 中的 scope                    │
│    - 比對 URL 前綴與 【TokenScope】 是否匹配            │
│    - 失敗 ➔ 403 SCOPE_FORBIDDEN                         │
└─────────────────────────────────────────────────────────┘
      │ (通過，權限隔離已確認)
      ▼
[Spring Security 內建 AuthorizationFilter] (檢查 URL 級別 RBAC)
      │
      ▼
[Controller] + [@PreAuthorize] (檢查方法級別 RBAC)
```

---

### 四、 總結：這個設計的精妙之處

1. **職責單一與解耦**：
   * `JwtUtil` 只懂密碼學，不懂 HTTP 請求。
   * `TokenScope` 只定義資料結構，不懂業務邏輯。
   * 三個 Filter 各自只負責一件事（CSRF、認證、Scope 隔離），透過 `SecurityConfig` 像積木一樣組合起來。
2. **效能優化（短路機制）**：
   * 把不需要解析 JWT 就能判斷的 `CsrfCookieFilter` 放在最前面。如果 CSRF 檢查失敗，直接返回，避免了後續昂貴的 JWT 簽名驗證運算。
3. **完美的上下文傳遞**：
   * `JwtAuthenticationFilter` 解析完 JWT 後，沒有把 `scope` 藏在自己內部，而是透過 `Authentication.setDetails()` 傳遞給 `SecurityContextHolder`。
   * 這使得 `ScopeEnforcementFilter` 可以優雅地讀取這個上下文，而不需要「再次解析一次 JWT」，保持了過濾器的輕量與高效。

這是一個非常標準且優秀的**企業級 Spring Security 自定義過濾器鏈設計**，完美平衡了安全性、效能與程式碼的可維護性。