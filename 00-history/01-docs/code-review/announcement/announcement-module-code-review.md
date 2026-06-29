# Announcement 模組 Code Review & Security Review

> 審查日期：2026-05-20
> 審查範圍：`backend/src/main/java/com/taipei/iot/announcement/` + `frontend/src/views/announcement/` + `frontend/src/views/admin/announcement/` + 相關 API / Store / 元件

---

## 模組結構總覽

```
backend/announcement/
├── controller/
│   └── AnnouncementController.java       # REST API（前台+管理端統一入口）
├── service/
│   ├── AnnouncementService.java          # CRUD + 可見性查詢 + DataScope 控制
│   └── AnnouncementReadService.java      # 已讀追蹤（mark read / unread count）
├── dto/
│   ├── AnnouncementRequest.java          # 新增/編輯 DTO（@Valid）
│   ├── AnnouncementResponse.java         # 回傳 DTO
│   └── UnreadCountResponse.java          # 未讀數量
├── entity/
│   ├── Announcement.java                 # 主表 Entity（TenantAware + @Filter）
│   ├── AnnouncementDept.java             # 部門目標 junction table
│   ├── AnnouncementDeptId.java           # 複合主鍵
│   ├── AnnouncementRead.java             # 已讀紀錄
│   ├── AnnouncementScope.java            # Enum: ALL / DEPT
│   └── AnnouncementStatus.java           # Enum: DRAFT / PUBLISHED
└── repository/
    ├── AnnouncementRepository.java       # JPQL + TenantScopedRepository
    ├── AnnouncementDeptRepository.java   # junction table CRUD
    └── AnnouncementReadRepository.java   # Native SQL upsert (ON CONFLICT)

frontend/
├── api/announcement/index.ts             # API 封裝
├── types/announcement.ts                 # TypeScript 型別定義
├── stores/announcementStore.ts           # Pinia store（未讀 + 輪詢）
├── views/announcement/
│   └── AnnouncementListView.vue          # 前台公告列表
├── views/admin/announcement/
│   └── AnnouncementManagementView.vue    # 管理端 CRUD
└── components/NotificationBell.vue       # 鈴鐺元件（含公告 tab）

DB Migration:
└── V29__announcement__create_tables.sql  # 3 tables + indexes + menus + permissions
```

---

## 總體評價

公告模組設計**良好**，關鍵優點：

- **租戶隔離**完整：Entity 實作 `TenantAware`、Repository 繼承 `TenantScopedRepository`、Hibernate `@Filter` 自動注入
- **DataScope 分層**：ADMIN 看全部、DEPT_ADMIN 只能操作自己建立的或受眾含自己部門的公告
- **JPQL 查詢精確**：前台可見性檢查涵蓋 status + publishAt + expireAt + scope + 部門匹配
- **已讀追蹤高效**：使用 PostgreSQL `ON CONFLICT DO NOTHING` upsert，避免重複 INSERT 例外
- **前端無 XSS 風險**：content 欄位使用 `<pre>` 和 `{{ }}` 文字插值，未使用 `v-html`
- **審計追蹤**：CREATE / UPDATE / DELETE 均有 `@AuditEvent` 註解
- **前端輪詢適當**：5 分鐘間隔查詢未讀數，避免過度請求

---

## 安全審查（Security Review）

### ✅ 防護到位的項目

| 防護類別 | 實作方式 | 評估 |
|----------|----------|------|
| 租戶隔離 | `@Filter(name = "tenantFilter")` + `TenantEntityListener` 自動設 tenantId | ✅ |
| 寫入授權 | `@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")` on CUD 端點 | ✅ |
| DataScope 控制 | DEPT_ADMIN 只能 update/delete 自己建立的公告 | ✅ |
| XSS 防護 | 前端使用文字插值（`{{ }}`），不渲染 HTML | ✅ |
| Input Validation | `@NotBlank`, `@NotNull`, `@Size(max=200)` on AnnouncementRequest | ✅ |
| CSRF | JWT Bearer Token（系統級防護） | ✅ |
| 已讀重複防護 | `ON CONFLICT (announcement_id, user_id) DO NOTHING` | ✅ |
| DB CASCADE | `ON DELETE CASCADE` 確保刪除公告時自動清除 junction + reads | ✅ |
| 權限 seed | ADMIN + DEPT_ADMIN 預設綁定 `ANNOUNCEMENT_VIEW` + `ANNOUNCEMENT_MANAGE` | ✅ |

---

### 需要改進的安全問題

#### 1. [中等] `hasManagePermission()` 仍使用硬編碼角色名稱

```java
private boolean hasManagePermission() {
    return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                    || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                    || a.getAuthority().equals("ROLE_DEPT_ADMIN")
                    || a.getAuthority().equals("ANNOUNCEMENT_MANAGE"));
}
```

**風險**：與剛完成的 permission-based 重構不一致。自定義角色即使被賦予 `ANNOUNCEMENT_MANAGE` 權限，仍需滿足 `ANNOUNCEMENT_MANAGE` authority 才能通過（第四個條件），所以功能上不會壞——但前三個 `ROLE_*` 條件是冗餘的，增加維護負擔。

**建議**：簡化為只檢查 permission code：
```java
private boolean hasManagePermission() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return false;
    return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ANNOUNCEMENT_MANAGE"));
}
```

#### 2. [中等] `getById()` 端點缺少可見性檢查

```java
@GetMapping("/{id}")
public BaseResponse<AnnouncementResponse> getById(@PathVariable Long id) {
    return BaseResponse.success(announcementService.getById(id));
}
```

**風險**：任何已認證使用者都可以透過 ID 直接存取**任何**公告（包括 DRAFT 狀態、已過期、或不在自己部門範圍內的）。雖然租戶隔離有效（跨場域不會洩漏），但同場域內一般使用者不應看到 DRAFT 公告。

**建議**：在 `getById()` service 中加入可見性驗證：
- 管理員（有 ANNOUNCEMENT_MANAGE）→ 可看全部
- 一般使用者 → 只能看 PUBLISHED + 未過期 + scope 符合

#### 3. [低] `content` 欄位無長度限制（TEXT 類型）

```java
@NotBlank
private String content;  // 無 @Size 限制
```

**風險**：攻擊者可提交超大 content（如 10MB 文字），造成：
- 資料庫儲存空間耗盡
- API 回應過大佔用頻寬
- 前端渲染大量文字導致瀏覽器卡頓

**建議**：加入合理的長度限制，如 `@Size(max = 50000)`（約 50KB 純文字足夠一般公告）。

#### 4. [低] `keyword` 查詢未轉義 LIKE 特殊字元

```java
String safeKeyword = (keyword != null && !keyword.isBlank()) ? "%" + keyword.trim() + "%" : null;
```

**風險**：如果使用者輸入含 `%` 或 `_` 的關鍵字（如搜尋 "50%"），會被當作 LIKE 萬用字元，導致非預期的查詢結果（非安全漏洞，但行為不正確）。

**建議**：轉義特殊字元：
```java
String escaped = keyword.trim().replace("%", "\\%").replace("_", "\\_");
String safeKeyword = "%" + escaped + "%";
```
並在 JPQL 中加入 `ESCAPE '\\'`。

#### 5. [低] SecurityConfig 無公告端點的 URL 層保護

公告端點 `/v1/auth/announcements/**` 落入 SecurityConfig 的 `.anyRequest().authenticated()` 規則，這是正確的（所有端點都需認證）。但前台的 GET 端點和管理端的 CUD 端點共用同一個 base path，URL 層無法區分。

**評估**：這不是問題——方法層的 `@PreAuthorize` 和 `hasManagePermission()` 已提供足夠的細粒度控制。只是記錄此設計決策。

---

## 程式碼品質審查（Code Review）

### ✅ 優點

#### 1. 批次查詢避免 N+1

`toResponseList()` 方法展現了良好的效能意識：

```java
// 批次載入 junction table
Map<Long, List<AnnouncementDept>> deptMap = announcementDeptRepository.findByAnnouncementIdIn(ids)...

// 批次載入已讀狀態
Set<Long> readIds = getReadAnnouncementIds(ids, currentUserId);

// 收集所有部門 ID 一次查詢名稱
Map<Long, String> deptNameMap = resolveDeptNameMap(allDeptIds);
```

三次批次查詢取代 N × 3 次迴圈查詢，對列表頁效能至關重要。

#### 2. DEPT_ADMIN 強制 scope 設計精巧

```java
if (dataScope != DataScopeEnum.ALL) {
    entity.setScope(AnnouncementScope.DEPT.getValue());
    targetDeptIds = List.of(user.getDeptId());
}
```

DEPT_ADMIN 建立公告時，後端**強制覆蓋** scope 和 targetDeptIds，即使前端被篡改也無法繞過。前端也配合鎖定 UI。

#### 3. `markAllAsRead` 使用單一原生 SQL

```sql
INSERT INTO announcement_reads (announcement_id, user_id, read_at)
SELECT a.id, :userId, now()
FROM announcements a
WHERE ... AND NOT EXISTS (SELECT 1 FROM announcement_reads r ...)
ON CONFLICT DO NOTHING
```

一次 SQL 完成「全部標為已讀」，無需逐一 INSERT，在公告數量多時效能優異。

#### 4. 前端 Store 輪詢設計合理

- 5 分鐘間隔（不頻繁）
- `startPolling` / `stopPolling` 生命週期管理
- 本地狀態即時更新（`markRead` 後直接修改 state，無需重新 fetch）

#### 5. 前端 Dialog 的 expire_at 自動計算

```typescript
watch(() => form.publishAt, (newVal) => {
  if (!expireManuallyEdited.value && !form.neverExpire && newVal) {
    const d = new Date(newVal)
    d.setDate(d.getDate() + 30)
    form.expireAt = toDateTimeString(d)
  }
})
```

使用者設定 publishAt 時自動填入 +30 天的 expireAt，減少操作步驟；手動修改後停止自動計算，UX 細膩。

---

### 需要改進的問題

#### 6. [中等] `resolveDeptNameMap()` 迴圈中逐一查詢

```java
private Map<Long, String> resolveDeptNameMap(Set<Long> deptIds) {
    return deptIds.stream()
            .collect(Collectors.toMap(
                    id -> id,
                    id -> deptInfoRepository.findByDeptId(id)  // ← 每個 deptId 一次 SQL
                            .map(DeptInfoEntity::getDeptName)
                            .orElse(String.valueOf(id))
            ));
}
```

**問題**：雖然在 `toResponseList` 中已收集所有 deptIds，但 `resolveDeptNameMap` 仍然逐一查詢。如果有 10 個不同部門，就是 10 次 SQL。

**建議**：使用 `findAllById` 或自定義 `findByDeptIdIn(Collection<Long> ids)` 一次查回：
```java
private Map<Long, String> resolveDeptNameMap(Set<Long> deptIds) {
    if (deptIds == null || deptIds.isEmpty()) return Collections.emptyMap();
    return deptInfoRepository.findByDeptIdIn(deptIds).stream()
            .collect(Collectors.toMap(DeptInfoEntity::getDeptId, DeptInfoEntity::getDeptName));
}
```

#### 7. [中等] 無單元測試

**問題**：`backend/src/test/java/com/taipei/iot/announcement/` 目錄不存在，整個模組**零測試覆蓋**。

**建議**：至少覆蓋以下場景：
- `AnnouncementService`: create / update / delete 權限驗證、DEPT_ADMIN 強制 scope、PUBLISHED 可見性邏輯
- `AnnouncementReadService`: markAsRead idempotent、countUnread 正確性
- `AnnouncementController` (WebMvcTest): 權限拒絕、admin=true 無權限回 403

#### 8. [低] `status` 和 `scope` 使用 String 而非 Enum 驗證

```java
@NotNull
private String status;  // 接受任意字串如 "INVALID_STATUS"

@NotNull
private String scope;   // 接受任意字串如 "XSS_PAYLOAD"
```

**問題**：前端可以傳入不在 `DRAFT`/`PUBLISHED` 或 `ALL`/`DEPT` 範圍內的值，後端不會拒絕（只是資料異常）。

**建議**：使用 `@Pattern` 或自定義 validator：
```java
@NotNull
@Pattern(regexp = "^(DRAFT|PUBLISHED)$", message = "status 必須為 DRAFT 或 PUBLISHED")
private String status;

@NotNull
@Pattern(regexp = "^(ALL|DEPT)$", message = "scope 必須為 ALL 或 DEPT")
private String scope;
```

#### 9. [低] `page` 和 `size` 參數無邊界驗證

```java
@RequestParam(defaultValue = "0") int page,
@RequestParam(defaultValue = "10") int size
```

**問題**：攻擊者可傳 `size=999999` 一次撈出大量資料，或傳 `page=-1` 導致異常。

**建議**：加入 `@Min(0)` / `@Max(100)` 限制：
```java
@RequestParam(defaultValue = "0") @Min(0) int page,
@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
```

#### 10. [低] 前端 `announcementStore` 錯誤靜默吞掉

```typescript
async fetchUnreadCount() {
  try { ... } catch { /* silently fail */ }
},
```

**問題**：所有錯誤（包括 401 token expired）被靜默忽略，使用者不知道鈴鐺的數字可能過期。

**建議**：可接受（背景輪詢不應打擾使用者），但至少在 console 記一行 debug 日誌，方便排錯。

#### 11. [建議] `AnnouncementController.list()` 方法過長

一個 GET 端點承擔了前台和管理端兩種查詢邏輯，且包含手動權限檢查 `hasManagePermission()`。

**建議**：拆為兩個端點更清晰：
- `GET /v1/auth/announcements` → 前台可見公告（所有已認證使用者）
- `GET /v1/auth/announcements/admin` → 管理端查詢（需 `ANNOUNCEMENT_MANAGE` 權限）

這也可避免 `hasManagePermission()` 手動檢查，改用 `@PreAuthorize`。

---

## 架構總評

| 維度 | 評分 | 說明 |
|------|------|------|
| 安全性 | **8.5/10** | 租戶隔離完整、寫入權限由 @PreAuthorize 保護、DataScope 控制正確、無 XSS。扣分：getById 缺可見性檢查、content 無長度限制、status/scope 無 enum 驗證。 |
| 正確性 | **9/10** | JPQL 查詢邏輯正確（visible / admin / deptAdmin 三種分層）、已讀 upsert 冪等、CASCADE 刪除完整。LIKE 特殊字元未轉義是唯一正確性問題。 |
| 效能 | **8.5/10** | 列表批次查詢設計優秀、markAllAsRead 單一 SQL。扣分：`resolveDeptNameMap` 迴圈查詢。 |
| 可維護性 | **8/10** | 結構清晰、DTO 分離完整。扣分：零測試覆蓋、controller list() 承擔雙重職責、hasManagePermission 硬編碼。 |
| UX | **9/10** | 前端輪詢 + 本地狀態即時更新、Dialog expire_at 自動計算、DEPT_ADMIN UI 鎖定、pagination 完整。 |

---

## 建議優先級摘要

| 優先級 | 項目 | 類型 | 狀態 |
|--------|------|------|------|
| **P2** | `getById()` 加入可見性驗證 | Security | ✅ 已修正 |
| **P2** | `hasManagePermission()` 改為純 permission-based | Security / Maintainability | ✅ 已修正 |
| **P3** | `content` 欄位加 `@Size(max=50000)` | Security | ✅ 已修正 |
| **P3** | `status` / `scope` 加 `@Pattern` 驗證 | Correctness | ✅ 已修正 |
| **P3** | `resolveDeptNameMap()` 改為批次查詢 | Performance | ✅ 已修正 |
| **P3** | 加入單元測試 | Maintainability | ✅ 已補充（48 tests） |
| **P4** | `page` / `size` 加邊界驗證 | Security | ✅ 已修正 |
| **P4** | `keyword` LIKE 特殊字元轉義 | Correctness | ✅ 已修正 |
| **P4** | 考慮拆分 list() 為前台/管理端兩個端點 | Maintainability | ✅ 已拆分 |
