

### 🎯 業務目的
當使用者點擊「我已閱讀」或「確認已讀」按鈕時，系統呼叫此方法，將閱讀紀錄寫入資料庫。這個行為必須滿足：
- **只記錄第一次閱讀時間**（後續重複點擊不覆蓋、不報錯）。
- **支援高併發**（避免先查後存的競爭條件，Race Condition）。

---

### ⚙️ 技術機制逐項拆解

```sql
	@Modifying
	@Query(value = """
			INSERT INTO announcement_reads (announcement_id, user_id, read_at)
			VALUES (:announcementId, :userId, now())
			ON CONFLICT (announcement_id, user_id) DO NOTHING
			""", nativeQuery = true)
	void markAsRead(@Param("announcementId") Long announcementId, @Param("userId") String userId);
```			

#### 1. `@Modifying` 的作用
- 告訴 Spring Data JPA：這是一個**資料異動操作**（INSERT / UPDATE / DELETE），而非查詢（SELECT）。
- 若缺少此註解，Spring 會嘗試將該操作視為查詢，導致執行期錯誤。
- 執行此方法時，會直接在資料庫執行原生 SQL。

#### 2. `nativeQuery = true` 的作用
- 標示這串 SQL 是**純粹的 PostgreSQL 方言**，而非 JPQL。
- 因為 `ON CONFLICT` 是 PostgreSQL 專有語法，必須使用原生模式才能執行。

#### 3. 參數綁定 `:announcementId` 與 `:userId`
- 使用 `@Param` 將方法參數安全的綁定到 SQL 中，防止 SQL Injection。
- 傳入的 `userId` 通常是 String（例如員工編號或 UUID）。

#### 4. `now()` 與 `read_at`
- 直接使用資料庫伺服器的當前時間，而非應用伺服器的 `LocalDateTime.now()`。
- 優點：確保時間一致性（分散式環境中特別重要），減少因應用伺服器時區或系統時間誤差導致的問題。

---

### 🧠 完整執行邏輯（流程圖）

| 步驟 | 情境 A：使用者「首次」閱讀 | 情境 B：使用者「重複」點擊 |
|------|--------------------------|--------------------------|
| 1. 執行 SQL | 嘗試插入 `(announcementId, userId, now())` | 嘗試插入相同組合 |
| 2. 檢查衝突 | 無此組合 → 插入成功，影響 1 筆 | 命中 UNIQUE 約束 → 觸發衝突 |
| 3. 衝突處理 | 不適用 | 執行 `DO NOTHING`，直接忽略 |
| 4. 資料庫回應 | 影響列數：1 | 影響列數：0 |
| 5. 對 Spring 的影響 | 視為成功（Success） | 視為成功（Success） |
| 6. 方法返回值 | `void`，無回傳 | `void`，無回傳 |
| 7. 例外拋出 | ❌ 無 | ❌ 無 |

---

### 🛡️ 冪等性（Idempotency）的實踐
- **冪等性定義**：無論執行 1 次還是 10 次，最終資料狀態完全一致。
- 此方法完美實現冪等性：
  - 第 1 次執行 → 資料庫多一筆閱讀記錄。
  - 第 2~10 次執行 → 資料庫維持原狀。
  - 永遠不會因為使用者快速連點而產生重複記錄或錯誤訊息。

---

### ⚠️ 重要使用注意事項（必讀）

#### 1. 必須有對應的唯一約束（否則報錯）
此方法依賴於 `announcement_reads` 表上存在 `UNIQUE (announcement_id, user_id)` 約束或唯一索引。若缺少，執行會直接拋出：
```
ERROR: there is no unique or exclusion constraint matching the ON CONFLICT specification
```
務必先由 DDL 建立該約束：
```sql
ALTER TABLE announcement_reads ADD CONSTRAINT uk_reads_unique UNIQUE (announcement_id, user_id);
```

#### 2. 必須在事務（Transactional）中執行
- `@Modifying` 方法預設會在**事務**中執行。一般情況下有 `@Transactional` 修飾業務層（Service）方法，Repository 方法會加入該事務。
- 若無事務，某些 JPA 實作可能拋出異常。建議在 Service 方法加上 `@Transactional`。

#### 3. 關於 Persistence Context 的快取問題（可能踩坑）
- 由於使用 `nativeQuery`，這筆 INSERT 操作**不會更新** Hibernate 的 Persistence Context（一級快取）。
- 如果在同一個事務中，呼叫 `markAsRead()` 之後又查詢 `AnnouncementRead` 且希望看到剛插入的資料，可能因為 L1 Cache 未同步而讀到舊狀態。
- **解決方式**：
  - 在 `@Modifying` 上加上 `flushAutomatically = true` 和 `clearAutomatically = true`：
    ```java
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    ```
    這會強制在執行前 `flush`（寫入 DB），並在執行後 `clear` 清除 Persistence Context，確保後續查詢直接從資料庫重新讀取。

#### 4. 返回值的設計選擇
- 此方法設計為 `void`，代表呼叫者不關心這次操作實際是「插入成功」還是「衝突忽略」。
- 若有業務需求需要知道是否「首次閱讀」，可改為 `int` 返回值，檢查影響列數（1 或 0）。

---

### 📝 總結一句話
這是一個**利用 PostgreSQL 獨有的 `ON CONFLICT` 語法，實現高效、冪等且無感的「記錄使用者閱讀行為」方法**，完全避免了傳統「先查詢是否存在 → 再決定插入」的競態條件問題，是實務中處理重複請求的標準示範。