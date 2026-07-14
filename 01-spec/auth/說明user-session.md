結合您提供的 `UserSessionEntity.java` 與 `AuthService.java`，以及先前討論的 `AuthController` 與 `SecurityConfig`，這個系統中的 **User Session（使用者會話）** 設計非常精妙。

它並非傳統 Spring MVC 中的 `HttpSession`（因為系統設定為 `STATELESS` 無狀態），而是**基於 JWT `jti` (JWT ID) 與 Refresh Token 生命週期所建立的「服務端會話追蹤機制」**。

以下為該 User Session 機制的詳細架構說明與設計亮點：

---

### 1. 核心定位：無狀態 JWT 與有狀態設備管理的橋樑
由於系統採用 JWT 進行認證（無狀態），後端無法直接知道某個 Token 是否被登出，也無法列出使用者目前登入了哪些設備。
`UserSessionEntity` 的存在，就是為了在資料庫中**持久化追蹤每一個 Refresh Token 的狀態**。它將「無狀態的 Token」與「有狀態的裝置/設備清單」完美結合，實現了 N-7 需求（登入裝置清單／強制登出）。

---

### 2. 實體欄位解析與生命週期 (`UserSessionEntity`)

| 欄位名稱 | 類型 | 說明與業務意義 |
| :--- | :--- | :--- |
| **`sessionId`** | String (PK) | **核心靈魂**：等同於 JWT 中的 `jti` claim（128-bit 隨機字串）。這使得前端傳入的 Token 能直接對應到資料庫中的這筆 Session 紀錄。 |
| `userId` | String | 關聯的使用者 ID。 |
| `tenantId` | String | 發行此 Token 時的租戶 ID。超級管理員切換租戶時，此欄位會更新。 |
| `ipAddress` / `userAgent`| String | 記錄登入時的來源 IP 與瀏覽器/設備資訊，用於前端展示「裝置清單」。 |
| `issuedAt` / `expiresAt` | LocalDateTime | Token 的簽發時間與過期時間。 |
| **`lastSeenAt`** | LocalDateTime | **活躍度追蹤**：每次前端呼叫 `/token/refresh` 時更新，代表該裝置的「最後活動時間」。 |
| **`revoked`** / `revokedAt`| Boolean / Time | **撤銷標記**：當使用者主動登出、閒置登出，或管理員強制踢人時，設為 `true`。後端在解析 Refresh Token 時會比對此狀態。 |

---

### 3. 核心架構決策：多租戶隔離設計 (Spec T-2)

`UserSessionEntity` 的註解中有一段非常精彩的架構決策說明 **[Tenant v2 T-2]**，解釋了**為什麼刻意不加入 Hibernate 的多租戶過濾器 (`@Filter`)**：

1. **主鍵全域唯一，無跨租戶猜測風險**：
   `sessionId` (即 `jti`) 是高強度的隨機字串，攻擊者無法透過列舉 ID 來跨租戶讀取別人的 Session。
2. **支援超級管理員的「跨租戶無縫切換」**：
   超級管理員 (SUPER_ADMIN) 擁有多個租戶的權限。當他們從 Tenant A 切換到 Tenant B 時，Refresh Token 會進行 Rotation（輪轉）。如果加上了 `@Filter`，在 Tenant B 的 Context 下，系統會找不到原本在 Tenant A 建立的 Session，導致 Refresh Token 機制崩潰。
3. **避免 Context 缺失導致的「誤殺」**：
   Session 的查詢與撤銷，經常發生在 `TenantContext` 尚未設定（如：登入前）或即將被清除（如：登出時）的邊緣時刻。強制加上租戶過濾器會導致這些合法請求失敗。

**🛡️ 縱深防禦 (Defense in Depth)**：
既然放棄了 ORM 層級的自動隔離，系統在 Service 層 (`AuthServiceImpl`) 採取了嚴格的補償措施：
* 查詢/撤銷 Session 時，必須使用 **`sessionId` + `userId` 雙重條件** 定位。
* 嚴禁使用 `findAll()` 列出全表。
* 禁止僅憑 `sessionId` 查詢後，就直接信任該筆資料的 `tenantId` 欄位（防止越權）。

---

### 4. 與 `AuthService` 的聯動：Session 的生命週期管理

`AuthService` 介面定義了 Session 在各個關鍵節點的狀態變更：

#### A. 建立 (Creation)
* **`login()`**：使用者成功登入後，產生 Refresh Token，並在 `user_session` 表中**新增一筆紀錄**（寫入 `jti`, `userId`, `ip`, `userAgent` 等）。
* **`selectTenant()` / `switchTenant()`**：多租戶使用者選擇租戶後，會更新當前 Session 的 `tenantId`，或觸發 Token Rotation。

#### B. 續期與輪轉 (Refresh & Rotation)
* **`refreshToken()`**：
  1. 前端帶上舊的 Refresh Token 請求刷新。
  2. 後端驗證舊 Token 簽名，並查詢 `user_session` 表確認 `revoked = false`。
  3. **更新 `lastSeenAt`**（記錄裝置活躍度）。
  4. *(若啟用 Rotation)* 將舊 Session 標記為 `revoked = true`，並產生新的 `jti` 與新 Session 紀錄。

#### C. 銷毀 (Revocation / Logout)
* **`logout()`**：
  1. 從 Cookie 中讀取 Refresh Token，解析出 `jti`。
  2. 在 `user_session` 表中將對應的 `sessionId` 標記為 **`revoked = true`**，並記錄 `revokedAt`。
  3. 清除前端 Cookie。
  *(這也解釋了為什麼 JWT 是無狀態的，但我們仍能實現「登出後 Token 立即失效」的功能)*。

---

### 5. 總結：這套設計帶來的業務價值

1. **完整的裝置管理 (Device Management)**：
   前端可以呼叫 `GET /v1/auth/sessions`，後端透過查詢 `user_session` 表，回傳使用者目前登入的所有設備清單（包含 IP、瀏覽器、最後上線時間），並透過比對 Cookie 中的 `jti` 標記出「當前裝置」。
2. **精準的安全審計與強制下線**：
   管理員或使用者本人可以透過 `DELETE /v1/auth/sessions/{sessionId}` 強制踢出特定設備。後端只需將該 `sessionId` 設為 `revoked=true`，該設備的 Refresh Token 就會立即失效。
3. **優雅的多租戶體驗**：
   透過放棄 ORM 層的租戶過濾，換取了超級管理員在多租戶間切換時，Session 狀態不會中斷的流暢體驗，同時在 Service 層用嚴謹的雙重校驗守住了安全底線。

這是一個**將「無狀態認證 (JWT)」與「有狀態業務需求 (設備管理/強制登出)」完美融合的企業級最佳實踐**。