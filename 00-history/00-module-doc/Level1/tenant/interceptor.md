這個 `TenantInterceptor` 類別是一個標準的 **Spring MVC `HandlerInterceptor`**。

它的核心目的是：**在 HTTP 請求進入 Controller 之前，根據系統的「部署模式（Single/Multi）」，負責初始化、校驗或清理 `TenantContext`（租戶上下文），並作為防止跨租戶資料污染的安全防線。**

以下為其詳細功能與設計目的解析：

---

### 一、 核心功能解析

#### 1. 依據部署模式進行「上下文分流」
它依賴前面提到的 `TenantProperties`，在 `preHandle` 階段根據 `tenant.mode` 採取不同策略：
*   **`multi` (多租戶模式)**：**直接放行 (`return true;`)**。因為在多租戶模式下，`tenantId` 已經由更外層的 `JwtAuthenticationFilter` 從 JWT Token 中解析並設定到 `TenantContext` 了，這裡不需要重複處理。
*   **`single` (單一租戶模式)**：**強制覆寫**。將 `TenantContext` 強制設定為 `application.yml` 中配置的 `tenant.default-id`（預設為 "DEFAULT"）。這讓底層依賴 `TenantContext` 的 Repository 或 Service 無需修改程式碼，即可在單一租戶環境下正常運作。

#### 2. 防止「資料污染」的安全交叉檢查 (T-5 修復)
這是此類別**最關鍵的安全設計**。當系統處於 `single` 模式時，它會進行嚴格的校驗：
*   **痛點場景**：假設系統原本是多租戶（Multi），某天運維人員手滑，將生產環境的配置誤改為 `single` 模式。如果沒有防護，其他租戶（例如租戶 B）的合法 JWT 請求進入系統後，會被靜默歸併到 `DEFAULT` 租戶，導致**租戶 B 的請求讀取或寫入了 DEFAULT 租戶的資料庫，造成嚴重的跨租戶資料污染**。
*   **防護機制**：如果當前是 `single` 模式，且 JWT 解析出來的 `jwtTenant` 不為空，且**不等於**配置的 `defaultId`，系統會：
    1.  記錄安全警告日誌 (`SecurityLogger.warn`)。
    2.  清空 `TenantContext`。
    3.  **直接拋出 403 例外 (`TENANT_MODE_MISMATCH`)**，拒絕該請求。
*   *例外放行*：如果是內部排程（System Context）或尚未經過 JWT 認證的公開端點（如 `/login`），則允許放行。

#### 3. 請求生命週期管理 (防止 ThreadLocal 洩漏)
*   **實作方式**：在 `afterCompletion` 方法中呼叫 `TenantContext.clear()`。
*   **目的**：`TenantContext` 底層使用的是 `ThreadLocal`。在 Tomcat 等 Web 容器中，Thread 是被池化複用的。如果請求結束後不清理，下一個分配到該 Thread 的請求可能會「繼承」上一個請求的租戶身分，導致災難性的資料錯亂。此方法確保了每次請求結束後的絕對乾淨。

#### 4. 啟動期觀測 (運維友善設計)
*   **實作方式**：使用 `@PostConstruct` 標記 `logMode()` 方法。
*   **目的**：在 Spring Boot 啟動日誌中，明確印出當前生效的 `tenant.mode` 與 `tenant.default-id`。這讓運維人員在部署後，無需進入系統內部，只需看 Console Log 就能確認多租戶開關是否正確設定。

---

### 二、 在架構中的協同運作 (全局視圖)

將 `TenantInterceptor` 放入您前面提供的架構中，它的角色非常清晰：

1.  **請求進入** $\rightarrow$ `JwtAuthenticationFilter` (解析 JWT，若是 multi 模式則設定 `TenantContext`)。
2.  **進入 Spring MVC** $\rightarrow$ **`TenantInterceptor.preHandle()`**：
    *   檢查 `TenantProperties`。
    *   若是 `single`，檢查 JWT 身分是否與配置的 `defaultId` 衝突（防資料污染），然後強制覆寫 `TenantContext`。
3.  **進入 Controller / Service** $\rightarrow$ 業務邏輯執行。
4.  **呼叫 Repository** $\rightarrow$ `TenantFilterAspect` 攔截，讀取 `TenantContext` 並注入 Hibernate Filter。
5.  **請求結束** $\rightarrow$ **`TenantInterceptor.afterCompletion()`** 清理 `ThreadLocal`。

### 總結

`TenantInterceptor` 是連接 **「外部 HTTP 請求」** 與 **「內部多租戶上下文 (`TenantContext`)」** 的橋樑。它不僅實現了單一/多租戶模式的無縫切換，更透過 **T-5 修復** 堵住了因配置錯誤導致跨租戶資料污染的潛在安全漏洞，是一個兼具彈性與嚴謹性的基礎設施元件。