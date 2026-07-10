
---

## 1. `findVisibleAnnouncements` — 前台使用者可見公告查詢

**業務目的**  
一般使用者登入後，在公告列表看到的「可閱讀」公告（已發布、未過期、且在使用者權限範圍內）。

**核心 JPQL 條件**  
```sql
WHERE a.status = 'PUBLISHED'
  AND a.publishAt <= :now
  AND (a.expireAt IS NULL OR a.expireAt > :now)
  AND (:category IS NULL OR a.category = :category)
  AND (a.scope = 'ALL'
       OR EXISTS (SELECT 1 FROM AnnouncementDept ad
                  WHERE ad.announcementId = a.id AND ad.deptId = :userDeptId))
```

**邏輯拆解**  
| 條件 | 說明 |
|------|------|
| `status = 'PUBLISHED'` | 排除草稿或歸檔 |
| `publishAt <= now` | 已到發布時間（排程中但尚未到時間的不顯示） |
| `expireAt IS NULL OR expireAt > now` | 無期限或期限未到 |
| `:category IS NULL OR category = :category` | 前端可選分類過濾 |
| `scope = 'ALL'` 或子查詢 `EXISTS (announcement_depts)` | 全體可見，或使用者所屬部門在發送清單中 |

**關鍵點**  
- 使用 `EXISTS` 而非 `JOIN`，避免因多對多關聯導致分頁（`Pageable`）時總數計算錯誤（因 `JOIN` 會產生重複資料）。
- 分頁由 Spring Data 的 `Pageable` 自動處理，效能依賴於資料庫索引（建議在 `status`, `publishAt`, `expireAt`, `category`, `scope` 上建立適當索引）。

---

## 2. `findAdminAnnouncements` — 超級管理員後台列表查詢

**業務目的**  
系統管理員（ADMIN）可查看所有公告（含草稿、排程、過期），並支援狀態、分類、關鍵字搜尋。

**核心 JPQL 條件（狀態過濾）**  
```sql
WHERE (:statusFilter = 'ALL' OR
       (:statusFilter = 'DRAFT' AND a.status = 'DRAFT') OR
       (:statusFilter = 'SCHEDULED' AND a.status = 'PUBLISHED' AND a.publishAt > :now) OR
       (:statusFilter = 'PUBLISHED' AND a.status = 'PUBLISHED'
           AND a.publishAt <= :now
           AND (a.expireAt IS NULL OR a.expireAt > :now)) OR
       (:statusFilter = 'EXPIRED' AND a.status = 'PUBLISHED' AND a.expireAt IS NOT NULL AND a.expireAt < :now))
```

**狀態篩選對照表**  
| `statusFilter` | 實際查詢邏輯 |
|----------------|-------------|
| `'ALL'` | 無任何狀態限制（顯示所有） |
| `'DRAFT'` | `a.status = 'DRAFT'` |
| `'SCHEDULED'` | `a.status = 'PUBLISHED'` 且 `a.publishAt > now`（排程中，尚未公開） |
| `'PUBLISHED'` | `a.status = 'PUBLISHED'` 且 `publishAt <= now` 且未過期 |
| `'EXPIRED'` | `a.status = 'PUBLISHED'` 且 `expireAt IS NOT NULL` 且 `expireAt < now` |

**關鍵字搜尋**  
```sql
(:keyword IS NULL
 OR a.title LIKE :keyword ESCAPE '\\'
 OR a.contentText LIKE :keyword ESCAPE '\\')
```
- 搜尋 `title` 與 `contentText`（純文字版），避免比對 HTML 標籤。
- `ESCAPE '\\'` 用於轉義使用者輸入中的特殊字元（如 `%`、`_`），但一般 Service 層會在關鍵字前後加上 `%`，此處需確保 Escape 正確處理。

**無權限限制**  
管理員無需過濾創建人與部門，可直接看所有公告（租戶隔離由 `@Filter` 自動處理）。

---

## 3. `findDeptAdminAnnouncements` — 部門管理員後台列表查詢

**業務目的**  
部門管理員（DEPT_ADMIN）只能查看「自己建立的」或「發送範圍包含自己部門」的公告。

**核心權限條件**  
```sql
WHERE (a.createdBy = :userId
       OR (a.scope = 'DEPT' AND EXISTS (
           SELECT 1 FROM AnnouncementDept ad
           WHERE ad.announcementId = a.id AND ad.deptId = :userDeptId)))
```
- `createdBy = :userId`：自己建立的公告（無論 scope 為何）。
- `scope = 'DEPT' AND EXISTS(...)`：公告發送範圍為「部門」且該部門包含當前使用者。

**狀態與關鍵字過濾**  
與 `findAdminAnnouncements` 完全相同，複用了相同的狀態邏輯與關鍵字搜尋。

**設計意圖**  
部門管理員**不能**編輯或查看全體（`scope = 'ALL'`）但非自己創建的公告，因為這類公告通常由更高權限（如總經理或系統管理員）發布。

---

## 4. `countUnread` — 計算使用者未讀公告數量

**業務目的**  
顯示使用者首頁或導航列上的未讀公告小紅點數量。

**核心 JPQL**  
```sql
SELECT COUNT(a) FROM Announcement a
WHERE a.status = 'PUBLISHED'
  AND a.publishAt <= :now
  AND (a.expireAt IS NULL OR a.expireAt > :now)
  AND (a.scope = 'ALL'
       OR EXISTS (SELECT 1 FROM AnnouncementDept ad
                  WHERE ad.announcementId = a.id AND ad.deptId = :userDeptId))
  AND NOT EXISTS (SELECT 1 FROM AnnouncementRead r
                  WHERE r.announcementId = a.id AND r.userId = :userId)
```

**邏輯**  
- 先計算所有「可見」公告（與 `findVisibleAnnouncements` 條件相同）。
- 再排除已讀（`NOT EXISTS` 在 `announcement_reads` 表中該使用者有記錄的）。

**注意**  
這裡的「已讀」是廣義的閱讀記錄，不論 `requires_ack` 為何，只要記錄存在即視為已讀。若需區分「需確認」的強制已讀，可再擴充條件（例如加上 `a.requiresAck = true` 等）。

---

## 5. `findMaxPinOrder` — 取得當前最大置頂順序值

**業務目的**  
當使用者將某公告設為置頂時，自動分配一個新的 `pinOrder`（數字越大排越後面）。

**JPQL**  
```sql
SELECT COALESCE(MAX(a.pinOrder), 0) FROM Announcement a WHERE a.pinned = true
```

- 查詢所有 `pinned = true` 的公告中，最大的 `pinOrder`。
- `COALESCE(..., 0)`：若無任何置頂公告，回傳 `0`。
- Service 層可設新公告的 `pinOrder = max + 1`。

**租戶隔離**  
此方法受 `@Filter(name = "tenantFilter")` 影響，僅統計當前租戶的置頂公告。

---

## 6. `findPinnedForDeptAdmin` — 部門管理員的置頂公告列表（排序用）

**業務目的**  
部門管理員在「置頂排序管理」功能中，取得所有自己可管理的置頂公告，以便拖曳調整順序。

**JPQL**  
```sql
SELECT a FROM Announcement a
WHERE a.pinned = true
  AND (a.createdBy = :userId
       OR (a.scope = 'DEPT' AND EXISTS (
           SELECT 1 FROM AnnouncementDept ad
           WHERE ad.announcementId = a.id AND ad.deptId = :userDeptId)))
ORDER BY a.pinOrder ASC NULLS LAST, a.publishAt DESC
```

- **權限條件**：與 `findDeptAdminAnnouncements` 相同（自己建立或部門相關）。
- **排序**：`pinOrder ASC`（數字小優先），`NULLS LA如果需要進一步探討某個查詢的執行計畫或潛在效能優化策略，歡迎提出！ST`（防止 null 值干擾），同順位時 `publishAt DESC`（最新發布在前）。

**用途**  
前端拖曳後，會將新的順序清單回傳，Service 層再批次更新各公告的 `pinOrder`。

---

## 7. `findAllPinned` — 超級管理員的置頂公告列表

**業務目的**  
超級管理員查看所有置頂公告（無權限限制），供管理或排序使用。

**JPQL**  
```sql
SELECT a FROM Announcement a
WHERE a.pinned = true
ORDER BY a.pinOrder ASC NULLS LAST, a.publishAt DESC
```

- 與 `findPinnedForDeptAdmin` 僅差在無 `WHERE` 權限條件。
- 同樣受 `@Filter` 租戶隔離，僅顯示當前租戶的置頂公告。

---

## 📌 綜合注意事項與潛在風險

| 議題 | 說明與建議 |
|------|-----------|
| **索引建議** | `status`, `publishAt`, `expireAt`, `category`, `scope`, `pinned`, `pinOrder` 等欄位應建立適當索引，尤其 `findVisibleAnnouncements` 和 `countUnread` 查詢量大。 |
| **`LIKE` 搜尋效能** | 使用 `LIKE '%keyword%'` 可能無法使用 B-tree 索引，若資料量增大，應考慮改用 PostgreSQL 的全文檢索（`tsvector`）或 Elasticsearch。 |
| **`ESCAPE` 的實作** | Service 層在拼接 `%` 時需正確處理使用者輸入的特殊字元，此處的 `ESCAPE '\\'` 已提供轉義機制。 |
| **`EXISTS` 子查詢** | 在 `findVisibleAnnouncements` 與 `findDeptAdminAnnouncements` 中使用，執行計畫通常為 Semi-Join，效能尚可，但需注意 `announcement_depts` 表的索引（建議在 `(announcement_id, dept_id)` 建立複合索引）。 |
| **`findMaxPinOrder` 的並發問題** | 若同時多人新增置頂公告，可能取得相同 `max` 值，導致 `pinOrder` 衝突。應在 Service 層使用樂觀鎖或資料庫層唯一約束來處理，或使用 `SELECT ... FOR UPDATE` 在交易中鎖定。 |
| **分頁注意** | 所有回傳 `Page<Announcement>` 的方法會自動執行兩次查詢（一次計數、一次列表），若 `EXISTS` 子查詢複雜，可能影響效能，可考慮改用 `@Query(countQuery = "...")` 自訂計數查詢。 |

---

