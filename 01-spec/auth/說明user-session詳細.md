結合您提供的 `UserSessionServiceImpl.java` 實作，以及先前討論的 `UserSessionEntity` 與 `AuthController`，這個系統的 **User Session 機制**展現了非常成熟的**雙重儲存架構 (Dual-Storage Architecture)** 與**高容錯設計**。

以下為您深入剖析 `UserSessionServiceImpl` 的核心邏輯與架構亮點：

---

### 1. 核心架構：DB 與 Redis 的職責分離

在無狀態 JWT 架構下，要實現「列出登入裝置」與「強制登出」功能，必須引入服務端狀態。此 Service 巧妙地將狀態拆分為兩層：

| 儲存層 | 技術 | 職責 | 優勢 |
| :--- | :--- | :--- | :--- |
| **持久層 (Source of Truth)** | **PostgreSQL / MySQL** (`UserSessionEntity`) | 儲存 Session 的完整元資料（IP、UserAgent、發行時間、所屬租戶等），並作為「裝置清單」的查詢來源。 | 支援複雜查詢、關聯性分析、審計日誌，且不受記憶體限制。 |
| **快取/黑名單層 (Fast Path)** | **Redis** (`auth:revoked_refresh:{sessionId}`) | 儲存被撤銷的 `jti` (Session ID)，並設定精確的 TTL（剩餘有效時間）。 | 提供 O(1) 的高性能查詢，讓 `JwtAuthenticationFilter` 在每次請求時能快速判斷 Token 是否已失效。 |

---

### 2. 關鍵方法與業務邏輯詳解

#### A. `listMine`：裝置清單與「當前裝置」標記
```java
public List<SessionDto> listMine(String userId, String currentJti) { ... }
```
* **邏輯**：從 DB 查詢該使用者所有**未撤銷且未過期** (`findActiveByUserId`) 的 Session。
* **亮點 (當前裝置標記)**：透過比對前端傳入的 `currentJti`（從 Refresh Token Cookie 中解析出來）與 DB 中的 `sessionId`，在回傳的 DTO 中動態設定 `current = true`。這讓前端 UI 能在裝置清單中打勾或高亮顯示「您目前正在使用的裝置」。

#### B. `revoke`：單一裝置強制登出 (核心安全流程)
```java
public void revoke(String userId, String sessionId) { ... }
```
這是實現「踢出特定裝置」的核心方法，其設計包含了三個極佳的安全與工程實踐：

1. **縱深防禦的雙重定位**：
   查詢時使用 `findBySessionIdAndUserId(sessionId, userId)`。即使攻擊者猜到了別人的 `sessionId` (雖然 128-bit 隨機字串極難猜測)，也因為沒有對應的 `userId` 而無法撤銷，完美呼應了 `UserSessionEntity` 中的 [Tenant v2 T-2] 縱深防禦策略。
2. **冪等性設計 (Idempotency) 防資訊洩漏**：
   ```java
   if (Boolean.TRUE.equals(session.getRevoked()) || session.getExpiresAt().isBefore(now)) {
       return; // 視為成功，直接回傳
   }
   ```
   如果目標 Session 已經被撤銷或已過期，系統**不會拋出異常**，而是視為操作成功。這防止了攻擊者透過觀察 HTTP 狀態碼或錯誤訊息，來探測「某個 sessionId 是否存在」或「是否屬於當前使用者」。
3. **DB 與 Redis 的雙寫與 TTL 計算**：
   * 先在 DB 中將 `revoked` 設為 `true`。
   * 接著計算 Redis 黑名單的 TTL：`Duration.between(now, session.getExpiresAt()).toMillis()`。這確保了 Redis 中的黑名單 Key 會在 Token 自然過期時**自動清理**，避免 Redis 記憶體無限膨脹。

#### C. `revokeAllExceptCurrent`：一鍵踢出其他裝置
```java
public void revokeAllExceptCurrent(String userId, String excludeJti) { ... }
```
* **場景**：使用者發現帳號異常，點擊「登出所有其他裝置」。
* **邏輯**：
  1. 查出所有活躍 Session，過濾掉 `excludeJti`（當前裝置）。
  2. 呼叫 Repository 的批次更新方法 (`revokeAllByUserIdExcept`) 在 DB 層面一次性撤銷。
  3. 遍歷被撤銷的列表，逐一寫入 Redis 黑名單。
* **效能考量**：DB 層面使用批次 SQL 更新，避免了 N+1 問題；Redis 層面雖然需要迴圈寫入，但因為是背景非同步/快速操作，且通常一個使用者的活躍裝置數不會太多（通常 < 10），效能完全可接受。

---

### 3. 高容錯設計：Best-Effort Redis 寫入

在 `revoke` 和 `revokeAllExceptCurrent` 中，Redis 的寫入都被包裹在 `try-catch` 區塊中：

```java
try {
    stringRedisTemplate.opsForValue().set(...);
} catch (Exception e) {
    log.warn("revoke session: Redis blacklist write failed...");
}
```

**為什麼這樣設計？**
* **DB 是 Source of Truth**：只要 DB 中 `revoked = true`，即使 Redis 寫入失敗，當該裝置下次嘗試使用 Refresh Token 時，`AuthService` 在查詢 DB 階段就會發現其已撤銷，從而拒絕刷新。
* **避免可用性災難**：如果因為 Redis 短暫宕機或網路抖動導致撤銷失敗並拋出 500 錯誤，使用者會以為「踢出裝置」功能壞了，產生恐慌。採用 **Best-Effort (盡力而為)** 策略，確保 DB 撤銷必定成功，並回傳 200 OK，將 Redis 的失敗降級為 Warn 日誌，由維運人員後續排查，保證了核心業務的可用性。

---

### 4. 總結：User Session 機制的企業級價值

`UserSessionServiceImpl` 不僅僅是一個 CRUD 服務，它解決了現代 JWT 架構中最棘手的幾個問題：

1. **彌補 JWT 的無狀態缺陷**：透過 DB+Redis，實現了精細化的 Token 撤銷與裝置管理。
2. **極致的安全防禦**：透過 `userId + sessionId` 雙重校驗、冪等性設計，杜绝了越權操作與資訊洩漏 (Enumeration)。
3. **優雅的多租戶支援**：配合 Entity 層放棄 ORM 過濾器的設計，在 Service 層用嚴謹的程式碼邏輯，支援了超級管理員跨租戶切換時，Session 狀態不中斷的流暢體驗。
4. **高可用性與自我清理**：Best-Effort 雙寫保證了操作不阻塞，而精確的 Redis TTL 計算確保了系統不會產生記憶體垃圾。

這是一個可以作為**業界 JWT 會話管理最佳實踐 (Best Practice)** 的優秀實作。