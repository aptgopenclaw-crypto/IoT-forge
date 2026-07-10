
## 1. `findVisibleAnnouncements` — 前台可見公告查詢

**業務場景**：一般使用者登入後，在公告列表看到的「可閱讀」公告。

**SQL 邏輯**：
```sql
WHERE a.status = 'PUBLISHED'
  AND a.publishAt <= :now
  AND (a.expireAt IS NULL OR a.expireAt > :now)
  AND (:category IS NULL OR a.category = :category)
  AND (a.scope = 'ALL'
       OR EXISTS (SELECT 1 FROM AnnouncementDept ad
                  WHERE ad.announcementId = a.id AND ad.deptId = :userDeptId))
```

**關鍵條件拆解**：

| 條件 | 說明 |
|------|------|
| `status = 'PUBLISHED'` | 只顯示已發布的公告 |
| `publishAt <= now` | 已到達發布時間（排除定時發布但尚未生效的） |
| `expireAt IS NULL OR expireAt > now` | 未過期（無期限或期限在未來） |
| `:category IS NULL OR category = :category` | 可選分類過濾（傳 null 表示全部） |
| `scope = 'ALL' OR EXISTS(...deptId...)` | 可見範圍：全體可見 **或** 使用者所屬部門在發送清單中 |

**設計亮點**：透過 `EXISTS` 子查詢來檢查部門權限，而非 `JOIN`，避免 `dept` 表查詢干擾主結果集的分頁（Pageable）正確性。

---

## 2. `findAdminAnnouncements` — 超級管理員後台查詢

**業務場景**：系統管理員（ADMIN）查看所有公告，包含草稿、排程中等所有狀態。

**SQL 邏輯**：
```sql
WHERE (:statusFilter = 'ALL' OR
       (:statusFilter = 'DRAFT' AND a.status = 'DRAFT') OR
       (:statusFilter = 'SCHEDULED' AND a.status = 'PUBLISHED' AND a.publishAt > :now) OR
       (:statusFilter = 'PUBLISHED' AND a.status = 'PUBLISHED'
           AND a.publishAt <= :now
           AND (a.expireAt IS NULL OR a.expireAt > :now)) OR
       (:statusFilter = 'EXPIRED' AND a.status = 'PUBLISHED' AND a.expireAt IS NOT NULL AND a.expireAt < :now))
  AND (:category IS NULL OR a.category = :category)
  AND (:keyword IS NULL
       OR a.title LIKE :keyword ESCAPE '\\'
       OR a.contentText LIKE :keyword ESCAPE '\\')
```

**關鍵條件拆解**：

| `statusFilter` 值 | 實際查詢條件 | 說明 |
|-------------------|-------------|------|
| `'ALL'` | 無狀態限制 | 顯示所有公告（包含草稿） |
| `'DRAFT'` | `status = 'DRAFT'` | 僅草稿 |
| `'SCHEDULED'` | `status = 'PUBLISHED' AND publishAt > now` | **計算狀態**：已設定發布但尚未到期的「排程中」公告 |
| `'PUBLISHED'` | `status = 'PUBLISHED' AND publishAt <= now AND (expireAt IS NULL OR expireAt > now)` | 真正已發布且未過期 |
| `'EXPIRED'` | `status = 'PUBLISHED' AND expireAt IS NOT NULL AND expireAt < now` | 已過期（且狀態必須為 PUBLISHED） |

**關鍵字搜尋**：搜尋 `title` 或 `contentText`（純文字版本），避免比對到 HTML 標籤本身。`ESCAPE '\\'` 是為了轉義 `%` 和 `_` 等特殊字元（雖然這串 JPQL 沒有拼接 `%`，但若 Service 層有加 `%keyword%` 則需要）。

**無權限限制**：管理員能看到所有租戶下的所有公告（配合 `tenantFilter` 自動隔離租戶）。

---

## 3. `findDeptAdminAnnouncements` — 部門管理員後台查詢

**業務場景**：部門管理員（DEPT_ADMIN）只能看到「自己建立的」或「發送對象包含自己部門」的公告。

**SQL 邏輯**：
```sql
WHERE (a.createdBy = :userId
       OR (a.scope = 'DEPT' AND EXISTS (
           SELECT 1 FROM AnnouncementDept ad
           WHERE ad.announcementId = a.id AND ad.deptId = :userDeptId)))
  AND (:statusFilter = 'ALL' OR ...)  -- 狀態條件與上述相同
  AND (:category IS NULL OR a.category = :category)
  AND (:keyword IS NULL OR ...)
```

**關鍵差異**：相較於 `findAdminAnnouncements`，多了一個**權限過濾**：

| 權限條件 | 說明 |
|---------|------|
| `createdBy = :userId` | 自己建立的公告（無論 scope 是什麼） |
| `scope = 'DEPT' AND EXISTS(ad.deptId = :userDeptId)` | 發送範圍為「部門」，且該部門包含使用者 |

**設計考量**：
- 部門管理員**不能**看到 `scope = 'ALL'` 但非自己建立的公告（因為那是全公司公告，應由更高權限的人管理）。
- 狀態過濾邏輯與 ADMIN 完全一致，保持統一。

---

## 4. `countUnread` — 計算未讀公告數量

**業務場景**：使用者登入後，首頁或導航列顯示「你有 N 則未讀公告」的小紅點。

**SQL 邏輯**：
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

**核心邏輯**：與 `findVisibleAnnouncements` 的條件**完全一致**，再多加一個條件：

| 條件 | 說明 |
|------|------|
| `NOT EXISTS (SELECT ... FROM AnnouncementRead ...)` | 該使用者尚未閱讀過此公告 |

**注意**：`AnnouncementRead` 表記錄的是閱讀行為（無論 `requires_ack` 為何），所以此處查的是「所有未讀」，而非「未確認」。

---

## 5. `findMaxPinOrder` — 取得最大置頂順序

**業務場景**：當使用者要將某公告設為置頂時，系統自動分配一個新的 `pinOrder`（數字越大越靠後）。

**SQL 邏輯**：
```sql
SELECT COALESCE(MAX(a.pinOrder), 0) FROM Announcement a WHERE a.pinned = true
```

**說明**：
- 查詢所有 `pinned = true` 的公告中，最大的 `pinOrder` 值。
- 若沒有任何置頂公告，則回傳 `0`。
- Service 層可將新公告的 `pinOrder` 設為 `max + 1`，自動排在最後。
- **受租戶過濾器影響**：只統計當前租戶的置頂公告（透過 `@Filter(name = "tenantFilter")`）。

---

## 6. `findPinnedForDeptAdmin` — 部門管理員的置頂公告清單

**業務場景**：部門管理員在「置頂排序管理」頁面，拖曳調整置頂公告的順序。

**SQL 邏輯**：
```sql
SELECT a FROM Announcement a
WHERE a.pinned = true
  AND (a.createdBy = :userId
       OR (a.scope = 'DEPT' AND EXISTS (
           SELECT 1 FROM AnnouncementDept ad
           WHERE ad.announcementId = a.id AND ad.deptId = :userDeptId)))
ORDER BY a.pinOrder ASC NULLS LAST, a.publishAt DESC
```

**關鍵點**：
- **權限限制**：同 `findDeptAdminAnnouncements`，只能看到自己建立或發送給自己部門的置頂公告。
- **排序規則**：
  - `pinOrder ASC`：數字越小越前面。
  - `NULLS LAST`：沒有 `pinOrder` 的（理論上不該出現在此，因為 `pinned=true` 應有值）排最後。
  - `publishAt DESC`：同順位時，新發布的在前。
- **用途**：供 UI 拖曳排序時，取得當前所有置頂公告的順序清單。

---

## 7. `findAllPinned` — 超級管理員的置頂公告清單

**業務場景**：超級管理員查看並管理所有租戶下的置頂公告（同樣受 `tenantFilter` 隔離）。

**SQL 邏輯**：
```sql
SELECT a FROM Announcement a
WHERE a.pinned = true
ORDER BY a.pinOrder ASC NULLS LAST, a.publishAt DESC
```

**與 `findPinnedForDeptAdmin` 差異**：
- 無權限限制（ADMIN 可看到所有置頂公告）。
- 排序邏輯完全相同。

---

## 總結：查詢設計模式

| 方法 | 角色 | 核心過濾 | 狀態處理 | 排序 |
|------|------|---------|---------|------|
| `findVisibleAnnouncements` | 一般使用者 | 可見性（scope + dept） | PUBLISHED + 時間範圍 | 分頁 |
| `findAdminAnnouncements` | ADMIN | 無（全看） | 五種狀態篩選 | 分頁 |
| `findDeptAdminAnnouncements` | DEPT_ADMIN | 自己建立或部門相關 | 五種狀態篩選 | 分頁 |
| `countUnread` | 一般使用者 | 可見性 + 未讀 | PUBLISHED + 時間範圍 | 純計數 |
| `findMaxPinOrder` | 任意 | `pinned=true` | - | 聚合函數 |
| `findPinnedForDeptAdmin` | DEPT_ADMIN | 自己建立或部門相關 | `pinned=true` | `pin_order` ASC |
| `findAllPinned` | ADMIN | `pinned=true` | - | `pin_order` ASC |

**共通設計原則**：
1. **計算狀態**（SCHEDULED / EXPIRED）由 JPQL 運算，不在資料庫存欄位，避免資料不一致。
2. **分頁查詢**使用 `EXISTS` 而非 `JOIN`，避免因多對多關聯導致分頁數量錯誤。
3. **全文檢索**使用 `contentText` 純文字欄位，避開 HTML 標籤干擾。
4. **租戶隔離**透過 Hibernate `@Filter` 自動追加 `tenant_id` 條件，Repository 層無需手動處理。