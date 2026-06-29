# listAdmin（後台管理視角）vs listVisible（前台視角）

你的理解**部分正確，但有一個重要區分**：

### `listAdmin`（後台管理視角）vs `listVisible`（前台視角）

| | `listVisible`（前台） | `listAdmin`（後台管理） |
|---|---|---|
| DEPT_ADMIN 看到 scope=ALL | ✓ 全部 | 只有**自己建立的** |
| DEPT_ADMIN 看到 scope=DEPT 含自己部門 | ✓ | ✓（含他人建立的） |

### 原因

`listAdmin` 的目的是「我能管理哪些公告」，不是「我能看見哪些公告」。

- 他人建立的 `scope=ALL` 公告 → DEPT_ADMIN 無法編輯/刪除（SA 3.7 限制：只能編輯自己建立的），所以在管理後台不顯示是合理的。
- 自己建立的 `scope=ALL` 公告 → 透過 `createdBy = :userId` 條件仍會出現。

```sql
WHERE (a.createdBy = :userId                    ← 自己建立的（任何 scope）
       OR (a.scope = 'DEPT' AND EXISTS (         ← 含自己部門的 DEPT 公告（含他人建立）
           SELECT 1 FROM AnnouncementDept ad
           WHERE ad.announcementId = a.id AND ad.deptId = :userDeptId)))
```

### 結論

SA 文件的描述是**正確的**。`scope=ALL` 在前台前是全可見的，但在後台管理列表中，DEPT_ADMIN 只看得到「自己建立的」那些（包含 `scope=ALL`），以及「受眾含自己部門的 DEPT 公告」。這樣設計是為了避免 DEPT_ADMIN 在後台看到大量無法管理的全體公告。