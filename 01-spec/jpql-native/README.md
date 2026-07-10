這是一個非常經典的架構抉擇問題。核心原則很簡單：

> **預設使用 JPQL（或 HQL），只在 JPQL「做不到」或「做得極差」時，才使用 Native SQL。**

為了幫你在實戰中快速下決定，我把兩者的適用場景、優缺點，以及最重要的**隱藏地雷（Persistence Context 問題）**整理如下：

---

### 1. 什麼時候「優先」使用 JPQL？

JPQL 是 JPA 的標準查詢語言，它**不直接操作資料表，而是操作 Entity（實體物件）**。

| 適用場景 | 原因與說明 |
| :--- | :--- |
| **標準 CRUD 查詢** | 例如 `WHERE`、`JOIN`、`GROUP BY`、分頁（`Pageable`）。 |
| **需要自動關聯 Entity** | 如果查詢結果要直接回傳 `Announcement` 物件，JPQL 會自動處理映射。 |
| **希望保持資料庫無關性** | 例如專案後續可能從 PostgreSQL 換成 MySQL，JPQL 會自動翻譯成對應的方言。 |
| **需要利用 Hibernate 快取（1st / 2nd Level Cache）** | JPQL 查詢出的 Entity 會放入 Persistence Context（一級快取），後續對該物件的修改會自動髒檢查（Dirty Checking）並在交易結束時寫入 DB。 |

---

### 2. 什麼時候「必須」使用 Native SQL？

Native SQL 直接傳送給資料庫執行，不經 JPA 解析。通常在以下情境**不得不**使用：

| 適用場景 | 具體範例（在你的專案中） |
| :--- | :--- |
| **使用資料庫獨有的語法（最常見）** | **PostgreSQL 的 `ON CONFLICT` (UPSERT)**、MySQL 的 `REPLACE INTO`、`INSERT IGNORE`。 |
| **使用 PostgreSQL 進階功能** | CTE（`WITH` 語句）、窗口函數（`ROW_NUMBER() OVER`）、`RETURNING` 子句（插入後回傳 ID）。 |
| **複雜的統計報表或大量資料更新** | 需要多表聯合大批量 `UPDATE` 或 `DELETE`，用 JPQL 產生低效 SQL 時。 |
| **呼叫資料庫預存程序（Stored Procedure）** | JPA 對預存程序的支援較弱，通常直接用原生呼叫。 |

---

### 3. 使用 Native SQL 的「巨大代價」（務必注意）

你現在看到的 `@Modifying` + `nativeQuery = true` 雖然好用，但它**完全繞過 Hibernate 的一級快取（Persistence Context）**。

- **情境**：你用 Native SQL 插入了 `announcement_reads`。
- **地雷**：如果在同一個 Transaction（同一支 Service 方法）中，你緊接著執行 `findById` 去撈這筆 `Announcement`，Hibernate 的 Session 因為不知道你做了 Native 更新，可能會直接從快取吐出「舊的」資料，導致查詢結果與 DB 不同步。

**解決對策（最佳實務）**：
在你的 `@Modifying` 方法上務必加上這兩個參數，強制清空快取：
```java
@Modifying(flushAutomatically = true, clearAutomatically = true)
```
- `flushAutomatically`：執行前先把當前 Session 的變更寫入 DB。
- `clearAutomatically`：執行後清空 Persistence Context，強迫後續查詢去資料庫重新撈取。

---

### 4. 你的專案中的決策對照表

| 你寫過的方法 | 使用語言 | 決策原因 |
| :--- | :--- | :--- |
| `findVisibleAnnouncements`<br>（複雜多條件 + 子查詢） | **JPQL** | 需要分頁（Pageable），且要自動映射成 `Announcement` 物件，利用 `@Filter` 做多租戶隔離。 |
| `markAsRead`<br>（防重複插入） | **Native SQL** | 因為 `ON CONFLICT` 是 PostgreSQL 專屬語法，JPQL **不支援**，只能用 Native。 |
| `countUnread`<br>（計數統計） | **JPQL** | 邏輯單純，回傳 `Long`，希望統一管理 Entity 欄位名稱（避免硬編碼資料庫欄位）。 |

---

### 總結一句話建議

1. **寫查詢（SELECT）**：如果結果是 `Entity` 或 `Page<Entity>`，**預設先用 JPQL**。如果 JPQL 產生出來的 SQL 效能太差（看執行計畫），再重構成 Native Query + `@SqlResultSetMapping` 轉為 DTO。
2. **寫異動（INSERT/UPDATE/DELETE）**：
   - 單純的單筆改狀態：用 JPQL 或 Spring Data JPA 的 `save()`。
   - **涉及資料庫特殊語法（如 UPSERT、CTE）**：直接用 Native SQL，但記得加上 `clearAutomatically = true` 確保快取刷新。
