# 在role的設計中，已經有data scope的作法，這樣的native sql的作法是合理的嗎？

---

## 為什麼不能套用 `DataPermissionAspect`？

`DataPermissionAspect` 的設計目的是對「有單一 `dept_id` 欄位的資源」套用部門階層過濾，邏輯是：

```
dept_id = :userDeptId          (THIS_LEVEL)
hierarchy_path LIKE :prefix%   (THIS_LEVEL_AND_BELOW)
```

但公告的 admin 可見性規則本質上是**業務邏輯**，不是純部門過濾：

```
createdBy = :userId
OR (scope = 'DEPT' AND EXISTS announcement_depts WHERE deptId = :userDeptId)
```

公告沒有單一 `dept_id` 欄位，而是透過 `announcement_depts` 多對多關聯表。硬套 Aspect 會非常彆扭。

---

## 現有設計的合理性

| 面向 | 評估 |
|------|------|
| tenant 隔離 | ✓ 透過 `TenantScopedRepository` + Hibernate Filter 自動處理 |
| ADMIN（ALL scope） | ✓ `findAdminAnnouncements`，僅 statusFilter/keyword 篩選 |
| DEPT_ADMIN | ✓ `findDeptAdminAnnouncements`，自己建立的 + dept 受眾符合 |
| `publishAt` 排程 | ✓ admin 查詢不強制 `publishAt <= now`（可看到未來排程的 PUBLISHED） |

---

## 一個值得注意的邊角情況

`DataScopeEnum` 有三個值，但 `AnnouncementService` 只分兩支：

```java
if (scope == DataScopeEnum.ALL) {
    // findAdminAnnouncements
} else {
    // findDeptAdminAnnouncements  ← THIS_LEVEL 和 THIS_LEVEL_AND_BELOW 都走這裡
}
```

`THIS_LEVEL_AND_BELOW` 在公告情境下被靜默對待為 `THIS_LEVEL`（只看自己部門，不看下級部門的公告）。這在公告模組中通常沒問題，因為 DEPT_ADMIN 的受眾設計就是「指定部門」而非「部門樹」，但若未來有需要可以補上這個分支。

---

**結論**：公告模組自訂 JPQL 是正確選擇。`DataPermissionAspect` 是通用部門資料過濾機制，公告的 admin 可見性是帶有業務語意的查詢邏輯，兩者職責不同，不應強行整合。