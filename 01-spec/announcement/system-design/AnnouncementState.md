這個 `AnnouncementStatsRepository` 是一個專門處理**公告已讀統計與未讀清單**的資料訪問層，全部使用 **Native SQL**，因為它跨多個表格進行複雜的統計與分頁查詢，JPQL 難以高效表達。以下逐一拆解每個方法的 SQL 邏輯與業務意圖。

---

## 1. `countAudienceAll` — 計算「全體可見」公告的目標受眾總人數

**業務目的**  
當公告的 `scope = 'ALL'` 時，計算當前租戶內**有多少有效使用者應該看到這則公告**（即分母），用於計算已讀比例。

**SQL 邏輯**  
```sql
SELECT count(*) FROM user_tenant_mapping m
JOIN users u ON u.user_id = m.user_id
WHERE m.tenant_id = :tenantId AND m.enabled = true AND u.deleted = false
```

**關鍵條件**  
| 條件 | 說明 |
|------|------|
| `m.tenant_id = :tenantId` | 只統計指定租戶 |
| `m.enabled = true` | 該使用者在該租戶下的啟用狀態為啟用 |
| `u.deleted = false` | 使用者未軟刪除（排除已離職或註銷帳號） |

**業務意義**  
- 不考慮部門（因為是 `ALL`），直接統計整個租戶的有效人數。
- 離職或停用者不計入，避免已讀比例失準。

---

## 2. `countAudienceDept` — 計算「部門限定」公告的目標受眾總人數

**業務目的**  
當公告的 `scope = 'DEPT'` 時，計算**指定部門內**有多少有效使用者應該看到這則公告。

**SQL 邏輯**  
```sql
SELECT count(*) FROM user_tenant_mapping m
JOIN users u ON u.user_id = m.user_id
WHERE m.tenant_id = :tenantId AND m.enabled = true AND u.deleted = false
  AND m.dept_id IN (:deptIds)
```

**關鍵條件**  
- 與 `countAudienceAll` 相同，但多了一個 **`m.dept_id IN (:deptIds)`**，只統計該公告關聯的部門清單中的使用者。
- `:deptIds` 來自 `announcement_depts` 表，由 Service 層傳入。

**業務意義**  
- 精確計算該公告的「目標族群」總數（分母）。
- 因為 `scope = 'DEPT'` 的公告可能發送給多個部門，需彙總這些部門內的有效使用者。

---

## 3. `countReadAll` — 計算「全體可見」公告的已讀人數

**業務目的**  
針對 `scope = 'ALL'` 的公告，計算**在有效使用者範圍內**有多少人已經讀過（分子）。

**SQL 邏輯**  
```sql
SELECT count(*) FROM announcement_reads r
JOIN user_tenant_mapping m ON m.user_id = r.user_id AND m.tenant_id = :tenantId
JOIN users u ON u.user_id = m.user_id
WHERE r.announcement_id = :announcementId
  AND m.enabled = true AND u.deleted = false
```

**關鍵條件**  
| 條件 | 說明 |
|------|------|
| `r.announcement_id = :announcementId` | 限該公告的閱讀記錄 |
| `JOIN user_tenant_mapping ... AND m.tenant_id = :tenantId` | 確保閱讀記錄中的使用者在當前租戶內 |
| `m.enabled = true AND u.deleted = false` | 只計入仍有效（未離職或停用）的使用者 |

**業務意義**  
- 排除已離職或停用者的閱讀記錄，避免已讀比例虛高。
- 與 `countAudienceAll` 的條件完全一致，確保分子與分母的範圍相同。

---

## 4. `countReadDept` — 計算「部門限定」公告的已讀人數

**業務目的**  
針對 `scope = 'DEPT'` 的公告，計算**在指定部門有效使用者範圍內**的已讀人數。

**SQL 邏輯**  
```sql
SELECT count(*) FROM announcement_reads r
JOIN user_tenant_mapping m ON m.user_id = r.user_id AND m.tenant_id = :tenantId
JOIN users u ON u.user_id = m.user_id
WHERE r.announcement_id = :announcementId
  AND m.enabled = true AND u.deleted = false
  AND m.dept_id IN (:deptIds)
```

**關鍵條件**  
- 與 `countReadAll` 相同，但多了一個 **`m.dept_id IN (:deptIds)`**，只計算該公告關聯部門內的已讀人數。

**業務意義**  
- 確保分子（已讀人數）的範圍與分母（目標受眾）完全一致，比例計算正確。

---

## 5. `findUnreadUsersAll` — 取得「全體可見」公告的未讀使用者清單（分頁）

**業務目的**  
管理後台顯示「未讀名單」，供管理者查看「誰還沒讀過這則公告」，**僅適用於 `scope = 'ALL'` 的公告**。

**SQL 邏輯（主要查詢）**  
```sql
SELECT u.user_id, u.display_name, u.email, m.dept_id, d.dept_name
FROM user_tenant_mapping m
JOIN users u ON u.user_id = m.user_id
LEFT JOIN dept_info d ON d.dept_id = m.dept_id
WHERE m.tenant_id = :tenantId AND m.enabled = true AND u.deleted = false
  AND NOT EXISTS (
      SELECT 1 FROM announcement_reads r
      WHERE r.announcement_id = :announcementId AND r.user_id = m.user_id
  )
  AND (CAST(:keyword AS text) IS NULL
       OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))
       OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')))
ORDER BY u.display_name, u.user_id
```

**關鍵拆解**  
| 區塊 | 說明 |
|------|------|
| **SELECT 欄位** | 回傳 `user_id`、`display_name`、`email`、`dept_id`、`dept_name`，供前端展示未讀人員列表 |
| **JOIN 與 LEFT JOIN** | `LEFT JOIN dept_info` 容許使用者未綁定部門（仍可列出） |
| **有效使用者過濾** | 與 `countAudienceAll` 相同（啟用、未刪除） |
| **`NOT EXISTS`** | 排除已在 `announcement_reads` 有閱讀記錄的使用者 |
| **關鍵字搜尋** | 支援 `display_name` 或 `email` 模糊比對（不分大小寫） |
| **排序** | 依姓名與 ID 排序，便於閱讀 |

**分頁查詢（`countQuery`）**  
- 因使用分頁（`Pageable`），需同時提供 `countQuery` 計算總未讀人數（條件與主查詢相同，僅 SELECT 改為 `count(*)`）。

**業務意義**  
- 管理員可查看「誰還沒讀」，進行催讀或通報。
- 只計有效使用者（離職者不應出現在未讀清單中）。

---

## 6. `findUnreadUsersDept` — 取得「部門限定」公告的未讀使用者清單（分頁）

**業務目的**  
針對 `scope = 'DEPT'` 的公告，顯示**指定部門內**的未讀使用者清單。

**SQL 邏輯**  
```sql
SELECT u.user_id, u.display_name, u.email, m.dept_id, d.dept_name
FROM user_tenant_mapping m
JOIN users u ON u.user_id = m.user_id
LEFT JOIN dept_info d ON d.dept_id = m.dept_id
WHERE m.tenant_id = :tenantId AND m.enabled = true AND u.deleted = false
  AND m.dept_id IN (:deptIds)
  AND NOT EXISTS (
      SELECT 1 FROM announcement_reads r
      WHERE r.announcement_id = :announcementId AND r.user_id = m.user_id
  )
  AND (CAST(:keyword AS text) IS NULL
       OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))
       OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')))
ORDER BY u.display_name, u.user_id
```

**與 `findUnreadUsersAll` 的差異**  
| 差異點 | `findUnreadUsersAll` | `findUnreadUsersDept` |
|--------|----------------------|----------------------|
| 部門過濾 | 無（全租戶） | `m.dept_id IN (:deptIds)` |
| 適用場景 | `scope = 'ALL'` 公告 | `scope = 'DEPT'` 公告 |

**業務意義**  
- 部門管理員只能看到自己部門內的未讀名單（權限控制）。
- 與 `countAudienceDept` 條件一致，確保清單範圍正確。

---

## 📊 整體設計模式總結

| 方法 | 計算目標 | 適用公告類型 | 關鍵過濾條件 |
|------|---------|-------------|-------------|
| `countAudienceAll` | 目標受眾總數（分母） | `scope = 'ALL'` | 租戶啟用且未刪除 |
| `countAudienceDept` | 目標受眾總數（分母） | `scope = 'DEPT'` | 租戶啟用且未刪除 + 部門清單 |
| `countReadAll` | 已讀人數（分子） | `scope = 'ALL'` | 租戶啟用且未刪除 + 閱讀記錄 |
| `countReadDept` | 已讀人數（分子） | `scope = 'DEPT'` | 租戶啟用且未刪除 + 部門清單 + 閱讀記錄 |
| `findUnreadUsersAll` | 未讀使用者清單（分頁） | `scope = 'ALL'` | 租戶啟用且未刪除 + 無閱讀記錄 |
| `findUnreadUsersDept` | 未讀使用者清單（分頁） | `scope = 'DEPT'` | 租戶啟用且未刪除 + 部門清單 + 無閱讀記錄 |

---

## 🧠 為什麼這個 Repository 使用 Native SQL？

根據檔案註解與實際 SQL 複雜度，原因如下：

1. **跨多表複雜關聯**：涉及 `announcement_reads`、`user_tenant_mapping`、`users`、`dept_info` 四個表，且需 LEFT JOIN 處理部門資訊缺失情境。
2. **分頁與計數分離**：需要同時提供 `countQuery` 與主查詢，JPQL 在處理多表 JOIN 與子查詢時語法較繁瑣。
3. **動態關鍵字搜尋**：使用 `LOWER()` 與 `CONCAT('%', :keyword, '%')` 實作不分大小寫模糊比對，JPQL 雖可做到但語法更受限制。
4. **不受 Hibernate `@Filter` 影響**：由於 Native SQL 不經過 Hibernate 的過濾器，租戶隔離必須**手動以參數帶入**（如 `:tenantId`），確保資料安全。
5. **效能最佳化**：這些統計查詢對效能要求高，Native SQL 可讓開發者完全控制執行計畫（如使用 `EXISTS` 子查詢而非 `LEFT JOIN ... WHERE IS NULL`，避免資料膨脹）。

---

## ⚠️ 使用注意事項

| 風險點 | 建議 |
|--------|------|
| **SQL Injection** | 全數使用 `:param` 參數綁定，安全無虞。 |
| **租戶隔離** | 必須在 Service 層傳入正確的 `tenantId`，否則可能洩漏跨租戶資料。 |
| **`dept_info` 表** | 假設存在 `dept_info` 表提供部門名稱，若不存在需調整 LEFT JOIN。 |
| **關鍵字搜尋效能** | `LOWER(display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))` 無法使用一般 B-tree 索引，若資料量增大應考慮 PostgreSQL 的 `pg_trgm` 或全文檢索。 |
| **分頁排序欄位** | 排序固定為 `display_name, user_id`，若前端需要其他排序方式需擴充參數。 |
| **結果映射** | 回傳 `Page<Object[]>`，需要手動將 `Object[]` 轉換為 DTO 或使用 `@SqlResultSetMapping` 定義映射規則。 |

---

