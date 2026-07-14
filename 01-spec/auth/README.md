基於您提供的所有程式碼檔案與先前的架構討論，以下為您整理出一份具備**架構師視角**的 **Auth（認證與授權）功能模組完整說明文件**。

這份說明不僅涵蓋了 API 與業務邏輯，更深入剖析了該模組在**安全防禦、多租戶隔離、跨模組解耦**等企業級設計上的核心決策。

---

# 🛡️ 企業級 Auth 認證與授權模組架構說明

## 一、 模組定位與核心價值
`auth` 模組是整個多租戶 IoT 平台的**安全心臟與信任錨點**。它不僅負責傳統的使用者登入與權限控管，更肩負了**多租戶隔離 (Multi-Tenancy)**、**可插拔身份認證 (Pluggable Authentication)**、**裝置會話管理 (Device Session)** 以及**全域密碼安全底線 (Password Policy Floor)** 的重任。

模組設計嚴格遵循 **Zero Trust (零信任)** 與 **Defense in Depth (縱深防禦)** 原則，在保持 JWT 無狀態高效能的同時，透過 DB+Redis 雙寫機制彌補了無狀態架構在「強制登出」與「裝置管理」上的缺陷。

---

## 二、 核心安全防線：自定義過濾器鏈 (Filter Chain)
系統摒棄了 Spring Security 繁重的預設 Session 機制，採用無狀態 JWT，並由 `SecurityConfig` 編排了三道自定義安全防線，形成嚴密的請求過濾漏斗：

1. **防線 1：`CsrfCookieFilter` (來源與狀態防護)**
   * **職責**：保護依賴 `refresh_token` Cookie 的敏感端點（如 `/refresh`, `/logout`）。
   * **機制**：雖然 Cookie 設定了 `SameSite=Lax`，此 Filter 仍強制校驗 `Origin` / `Referer` 白名單，防禦同源子網域 XSS 或舊版瀏覽器的 CSRF 攻擊。若無 Cookie 則短路放行，兼顧安全與效能。
2. **防線 2：`JwtAuthenticationFilter` (身份確立與上下文建立)**
   * **職責**：解析 Bearer Token，驗證簽名與有效期。
   * **機制**：提取 Claims (包含 `scope`, `tenantId`, `permissions`)，建立 `Authentication` 物件注入 `SecurityContextHolder`，並同步設定 `TenantContext` (ThreadLocal)。若發現場域已停用，會即時攔截 (403)。
3. **防線 3：`ScopeEnforcementFilter` (平台/租戶隔離守門員)**
   * **職責**：實作 **ADR-007**，確保 Token 的 `scope` 與請求的 URL 前綴嚴格匹配。
   * **機制**：`PLATFORM` scope 只能存取 `/v1/platform/**`；`TENANT` / `IMPERSONATION` 只能存取 `/v1/auth/**`。防止租戶 Token 越權呼叫平台管理 API。

---

## 三、 核心業務領域 (Core Domains)

### 1. 雙 Token 機制與生命週期管理
* **Access Token**：存放於 HTTP Header，生命週期短，承載使用者身份、權限與 `TokenScope`。
* **Refresh Token**：存放於 **HttpOnly + Secure + SameSite Cookie**，生命週期長，有效防禦 XSS 竊取。
* **`JwtUtil` 引擎**：支援簽發多種用途的 Token，包含標準 Access/Refresh、多租戶選擇用的 Temporary Token、以及**強制改密專用的 PasswordChange Token** (綁定 `purpose=password_change`，防止 Token 被挪作他用)。

### 2. 會話與裝置管理 (Session & Device)
為解決 JWT 無法主動失效的問題，引入了 `UserSessionEntity` 與 `UserSessionServiceImpl`：
* **雙層儲存架構**：DB (`user_session` 表) 作為 Source of Truth 儲存裝置元資料 (IP, UserAgent)；Redis 作為黑名單快取，提供 O(1) 的撤銷查詢。
* **縱深防禦與多租戶決策 (Tenant v2 T-2)**：`UserSessionEntity` **刻意不加入** Hibernate 的 `@Filter` 租戶隔離。因為 Super Admin 跨租戶切換時需保持 Session 連續性，且 `jti` (128-bit 隨機) 具備全域唯一性。隔離邏輯下沉至 Service 層，強制使用 `sessionId + userId` 雙重校驗，防止越權撤銷。
* **冪等性與 Best-Effort**：撤銷 Session 時，若目標已失效則視為成功 (防資訊洩漏)；Redis 寫入失敗不阻斷 DB 撤銷，確保核心業務高可用。

### 3. 多租戶密碼政策 (Password Policy)
實作 **Spec D-4 (平台設定下限，租戶只能加強)** 的三層策略體系：
* **平台層 (`PlatformPasswordPolicyController`)**：Super Admin 設定全域安全底線 (Floor)。
* **租戶層 (`TenantPasswordPolicyController`)**：Tenant Admin 設定覆寫值。`PasswordPolicyService` 會嚴格攔截低於平台底線的設定。
* **連鎖清理機制 (Post-write scan)**：當平台**提高**底線 (如最小長度 8->12) 時，系統會自動觸發 `dao.deleteBelowFloor()`，強制清除所有低於 12 的租戶覆寫值，確保安全規範即時全局生效。
* **公開查詢 (`NoauthPasswordPolicyController`)**：提供未登入狀態下的前端表單即時驗證規則。

### 4. 可插拔認證引擎 (Pluggable Auth Engine)
透過 **策略模式 (Strategy Pattern)** 實現認證來源的解耦：
* **`AuthenticationDispatcher`**：核心路由器。根據 Tenant 配置或 User 個別設定，決定路由至 LOCAL、LDAP 或 OIDC Provider。
* **安全 Fallback 機制 (V3-H2)**：只有當外部認證發生**基礎設施錯誤** (如 LDAP 伺服器斷線) 且開啟 `fallbackLocal` 時，才會降級至本地認證；若是**憑證錯誤** (密碼錯誤)，絕對禁止 Fallback，防止攻擊者繞過外部認證。
* **Provider-Agnostic DTO**：`AuthenticationRequest` 與 `AuthenticationResult` 屏蔽了底層協定差異，確保核心邏輯純潔。

---

## 四、 跨模組解耦：端口與適配器 (Ports & Adapters)

在多模組架構中，為了避免 `asset` (資產) 或 `audit` (審計) 等模組直接依賴 `auth` 模組導致**循環依賴**或**高耦合**，系統在 `common` 模組定義了抽象 Port，並在 `auth.port` 提供實作 (Adapter)：

| Port (定義於 common) | Adapter (實作於 auth.port) | 委派給 auth 內部服務 | 業務意義 |
| :--- | :--- | :--- | :--- |
| `SessionRevoker` | `AuthSessionRevoker` | `UserSessionService` | 其他模組可觸發強制登出 (如：帳號被鎖定時踢出所有裝置) |
| `PasswordPolicyProvider`| `AuthPasswordPolicyProvider`| `PasswordPolicyResolver` | 其他模組在建立/修改使用者時，可驗證密碼是否符合當前租戶政策 |
| `TokenJtiReader` | `AuthTokenJtiReader` | `JwtUtil` | 其他模組需要解析 Token 識別碼時使用 |

這種設計讓 `auth` 模組成為一個**高內聚的基礎設施黑盒子**，對外只暴露標準插座 (Port)，完美遵循依賴反轉原則 (DIP)。

---

## 五、 總結

這個 `auth` 模組展現了極高的工程成熟度。它沒有盲目套用框架的預設行為，而是針對**多租戶 SaaS 平台**的痛點（如：跨租戶 Session 管理、平台安全底線強制、多來源認證 Fallback 漏洞）提出了精確的程式碼級解決方案。

透過 **Filter Chain 的縱深防禦**、**DB+Redis 的雙寫會話管理**、**嚴格的下限強制策略**，以及 **Ports & Adapters 的優雅解耦**，該模組為整個 IoT 平台築起了一道堅不可摧且具備高擴展性的安全防線。