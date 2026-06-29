# Announcement 模組 Code Review & Security Review (第二輪)

> 審查日期：2026-05-26
> 上一輪：[announcement-module-code-review.md](announcement-module-code-review.md)（已全數修正）
> 審查範圍：
> - `backend/src/main/java/com/taipei/iot/announcement/`
> - `backend/src/main/resources/db/migration/V29__announcement__create_tables.sql`
> - `frontend/src/views/announcement/AnnouncementListView.vue`
> - `frontend/src/views/admin/announcement/AnnouncementManagementView.vue`
> - `frontend/src/stores/announcementStore.ts`、`frontend/src/api/announcement/index.ts`
> - `frontend/src/components/NotificationBell.vue`

---

## 模組現況快照

| 項目 | 現況 |
|---|---|
| 後端檔案 | controller×1、service×2、entity×6、repository×3、dto×3 |
| 前端檔案 | view×2、store×1、api×1、type×1、bell 整合 ×1 |
| 測試覆蓋 | ✅ 3 個測試類別（`AnnouncementServiceTest`、`AnnouncementReadServiceTest`、`AnnouncementControllerTest`） |
| DB | 3 tables (`announcements` / `announcement_depts` / `announcement_reads`) + 4 indexes + 2 permissions |
| 上一輪 P2–P4 缺陷 | **全部已修正**（含 list 拆分、`@Size(50000)`、`@Pattern`、`@Min/@Max`、LIKE 轉義、批次 `findByDeptIdIn`、`getById` 可見性、`hasManagePermission()` 簡化、單元測試補齊） |

---

## 總體評價

第二輪審查以**第一輪修正後的版本**為基準。整體結構紮實，重點優化已落地。本輪聚焦：

1. 上一輪未涵蓋的**深層安全議題**（特別是租戶隔離的覆蓋盲區）
2. **資料一致性／併發**面向
3. **效能微觀問題**
4. **架構與 DX**（前端狀態管理、API 設計）
5. **功能優化建議**（產品向）

---

## 一、安全審查（Security Review）

### ✅ 已驗證到位

| 防護 | 機制 | 結果 |
|---|---|---|
| Multi-tenant filter on `Announcement` | `@Filter` + `TenantScopedRepository` + `TenantFilterAspect` | ✅ |
| Permission gate (寫入) | `@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")` | ✅ |
| Permission gate (admin 列表) | `@PreAuthorize` on `GET /admin` | ✅ |
| getById 可見性 | service 區分 `hasManagePermission` 旗標，未授權者驗證 status/publishAt/expireAt/scope | ✅ |
| DataScope (DEPT_ADMIN) | 寫入端強制覆蓋 `scope`/`targetDeptIds`、update/delete 限制 `createdBy == self` | ✅ |
| Input validation | `@NotBlank` / `@Size` / `@Pattern` / `@Min` / `@Max` 完整 | ✅ |
| LIKE escape | `replace("%","\\%")` + `ESCAPE '\\'` | ✅ |
| XSS | 前端 `<pre>{{ }}` 文字插值，無 `v-html` | ✅ |
| 已讀 idempotent | `ON CONFLICT DO NOTHING` | ✅ |
| Cascade delete | `ON DELETE CASCADE` 清除 junction + reads | ✅ |

---

### 🔴 [P1 — Critical] `AnnouncementReadRepository` 未受租戶 Filter 保護　✅ 已修正（2026-05-26）

> **修正摘要**：`AnnouncementReadService.markAsRead()` 在執行 native INSERT 前，先呼叫 `announcementRepository.existsById(id)`（受 `@Filter(tenantFilter)` 保護）驗證該公告屬於當前租戶；查無則拋 `ANNOUNCEMENT_NOT_FOUND`。新增測試 `markAsRead_crossTenantAnnouncementId_throwsNotFound` 驗證跨租戶 ID 被拒絕且 native INSERT 不會被呼叫。`markAllAsRead` 既有 native SQL 已含 `WHERE a.tenant_id = :tenantId`，無需更動。


**問題**
`AnnouncementReadRepository` 與 `AnnouncementDeptRepository` 均**未實作 `TenantScopedRepository`**，且 `AnnouncementRead` / `AnnouncementDept` Entity 上**未掛 `@Filter`**。

更關鍵的是 `markAsRead` 是**原生 SQL INSERT**：

```java
@Modifying
@Query(value = """
    INSERT INTO announcement_reads (announcement_id, user_id, read_at)
    VALUES (:announcementId, :userId, now())
    ON CONFLICT (announcement_id, user_id) DO NOTHING
    """, nativeQuery = true)
void markAsRead(@Param("announcementId") Long announcementId, @Param("userId") String userId);
```

並且 controller 端點是：

```java
@PostMapping("/{id}/read")
public BaseResponse<Void> markAsRead(@PathVariable Long id) { ... }
```

**攻擊情境**
1. 攻擊者 A（屬於 tenant T1）取得 tenant T2 的公告 ID（透過列舉 1..N 或側通道）
2. 呼叫 `POST /v1/auth/announcements/{id}/read`
3. service → native INSERT 直接寫入，未檢查公告是否屬於 T1
4. 在 T2 的 `announcement_reads` 中產生跨租戶污染資料

**影響**
- 跨租戶資料污染（寫入）
- 雖然不直接洩漏資料內容，但破壞租戶隔離原則（OWASP A01 Broken Access Control）
- 可能造成 T2 的「未讀數」統計失準（雖然 countUnread 只查特定 user_id，影響範圍有限）

**建議修正**
在 `AnnouncementReadService.markAsRead()` 先驗證公告屬於當前租戶：

```java
@Transactional
public void markAsRead(Long announcementId) {
    String userId = SecurityContextUtils.getCurrentUserId();
    // 透過 tenant-filtered repository 驗證該公告存在於當前租戶
    if (!announcementRepository.existsById(announcementId)) {
        throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
    }
    announcementReadRepository.markAsRead(announcementId, userId);
}
```

或在 native SQL 中加入 tenant 子查詢：

```sql
INSERT INTO announcement_reads (announcement_id, user_id, read_at)
SELECT :announcementId, :userId, now()
WHERE EXISTS (
    SELECT 1 FROM announcements
    WHERE id = :announcementId AND tenant_id = :tenantId
)
ON CONFLICT (announcement_id, user_id) DO NOTHING
```

---

### 🟡 [P2] `getById` 對「已認證但無權限的他租戶 ID」的回應碼

`getById()` 使用 `announcementRepository.findById(id)`，由於 `Announcement` 有 `@Filter` 與 `TenantScopedRepository`，**跨租戶查不到** → 拋 `ANNOUNCEMENT_NOT_FOUND`。✅ 安全。

但 service 內可見性檢查也統一拋 `ANNOUNCEMENT_NOT_FOUND`：

```java
if (!published || !notExpired || !started || !scopeMatch) {
    throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
}
```

**評估**：✅ **這是正確的設計**（避免透過錯誤碼區分「不存在 vs 無權限」造成資訊洩漏 / IDOR 偵測）。**保留現狀**。

---

### 🟡 [P2] `markAllAsRead` 的 tenant 來源風險低但值得標記

```java
String tenantId = TenantContext.getCurrentTenantId();
announcementReadRepository.markAllAsRead(user.getUserId(), tenantId, deptId);
```

從 `TenantContext` 取得當前租戶，且 native SQL 用 `WHERE a.tenant_id = :tenantId` 過濾來源公告，**設計正確**。

但若未來 `TenantContext` 在某些路徑被汙染（例如 async 任務），此處會跟著漂移。

**建議**：加入 unit test 斷言「在 T1 context 下執行 markAllAsRead，T2 公告不會被插入」，防止迴歸。

---

### 🟡 [P3] 前台 `list` 端點完全無權限註解

```java
@GetMapping
public BaseResponse<PageResponse<AnnouncementResponse>> list(...) {
    return BaseResponse.success(announcementService.listVisible(page, size));
}
```

落入 SecurityConfig 的 `.anyRequest().authenticated()`，**任何登入使用者皆可呼叫**。  
這是符合需求的（公告就是給所有員工看），但建議加上明示的 `@PreAuthorize("isAuthenticated()")` 以提升可讀性與審計檢驗效率。

---

### 🟢 [P4] 未加入 `expireAt > publishAt` 業務驗證

`AnnouncementRequest` 只驗證型別，沒有跨欄位驗證。前端會自動填 +30 天，但**惡意客戶端可以送出 `expireAt < publishAt`**，產生「永遠不可見」的殭屍資料（無安全風險，僅資料正確性）。

**建議**：自訂 `@AssertTrue` 或 class-level validator：

```java
@AssertTrue(message = "expireAt 必須晚於 publishAt")
public boolean isExpireAfterPublish() {
    return expireAt == null || publishAt == null || expireAt.isAfter(publishAt);
}
```

---

### 🟢 [P4] `markAsRead` 端點無 rate limit

任何使用者可任意頻率呼叫 `POST /{id}/read`（即使是已讀），雖然 `ON CONFLICT` 不會產生例外，但每次仍會走一次 native INSERT。  
**評估**：可接受。前端會在標為已讀後設 `item.isRead = true` 避免重複呼叫；若有 abuse 疑慮可加 client-side guard 即可。

---

## 二、程式碼品質審查（Code Review）

### ✅ 優點

1. **list 端點拆分清楚**（`/` 前台 vs `/admin` 管理端），權限以 `@PreAuthorize` 宣告，比上一輪 `hasManagePermission()` 寫法乾淨。
2. **批次查詢設計完整**：`toResponseList` 已用 `findByDeptIdIn` 完成 dept 名稱批次解析，三段批次（depts / reads / deptNames）取代 N+1。
3. **DEPT_ADMIN 寫入安全雙保險**：後端強制 + 前端 UI 鎖定。
4. **測試覆蓋健全**：覆蓋 create / update / delete 權限、DEPT_ADMIN 強制 scope、getById 可見性、controller 403。
5. **markAllAsRead 單一 SQL** 仍是優異設計。
6. **Pagination 已加邊界**（`@Min(0)` / `@Min(1) @Max(100)`）。

---

### 🟡 [中] `toResponse` 與 `toResponseList` 邏輯重複

單筆 `toResponse` 又呼叫 `announcementDeptRepository.findByAnnouncementId()` 與 `resolveDeptNames()`（逐一查 dept 名稱）。批次版已優化，但**單筆版仍是 N 次 dept 查詢**。

**建議**：`toResponse` 內也改用 `findByDeptIdIn`：

```java
private AnnouncementResponse toResponse(Announcement entity, String currentUserId, boolean includeEditable) {
    List<AnnouncementDept> depts = announcementDeptRepository.findByAnnouncementId(entity.getId());
    Set<Long> deptIdSet = depts.stream().map(AnnouncementDept::getDeptId).collect(Collectors.toSet());
    Map<Long, String> nameMap = resolveDeptNameMap(deptIdSet);
    List<Long> deptIds = depts.stream().map(AnnouncementDept::getDeptId).toList();
    List<String> deptNames = deptIds.stream()
            .map(id -> nameMap.getOrDefault(id, String.valueOf(id))).toList();
    // ...
}
```

順手刪除 `resolveDeptNames(List<Long>)` 這個 N+1 helper（無其他呼叫者）。

---

### 🟡 [中] 缺乏樂觀鎖（`@Version`），併發編輯會 last-write-wins

兩個管理員同時編輯同一則公告，**後存的會覆蓋先存的**，無提示。對「重要的全公司公告」可能造成資訊遺失。

**建議**：
```java
@Version
@Column(name = "version", nullable = false)
private Long version;
```
搭配 migration `ALTER TABLE announcements ADD COLUMN version BIGINT NOT NULL DEFAULT 0;` 與前端送回 version。  
**優先度中**，看實際多管理員協作頻率決定。

---

### 🟡 [中] 前端 `AnnouncementListView` 直接操作 store state

```typescript
async function toggleExpand(item) {
  // ...
  if (!item.isRead) {
    await markAsRead(item.id)
    item.isRead = true
    announcementStore.unreadCount = Math.max(0, announcementStore.unreadCount - 1)  // ⚠️
  }
}
```

直接設定 `announcementStore.unreadCount`，繞過 store action，違反 Pinia 封裝原則。  
若改用 strict mode 或日後加入 audit hook 會出問題。

**建議**：`announcementStore.ts` 加入 `decrementUnread()` action 或讓 view 統一呼叫 `announcementStore.markRead(id)`（已存在），並把 popoverItems 與 list view items 同步邏輯下沉到 store。

---

### 🟡 [中] 前端兩處 `markAsRead` 路徑分歧

- `AnnouncementListView` 直接呼叫 `api/announcement.markAsRead`
- `announcementStore.markRead()` 也呼叫同一支 API

兩條路徑都會修改未讀計數，但**僅有後者會同時同步 `popoverItems`**。若使用者在前台 list 標為已讀，鈴鐺彈出後仍顯示為未讀（直到下次輪詢）。

**建議**：list view 改呼叫 `announcementStore.markRead(id)`，並讓 store 內部維護「跨 view 已讀同步」。

---

### 🟢 [低] `keyword` 僅查詢 `title`，不查詢 `content`

管理端 keyword filter 只 LIKE `title`：

```jpql
AND (:keyword IS NULL OR a.title LIKE :keyword ESCAPE '\\')
```

**評估**：對短公告通常足夠，但「找關鍵字在內文」是常見需求。  
**建議**：擴充為 `(a.title LIKE :kw OR a.content LIKE :kw)`，或日後考慮 PostgreSQL FTS。

---

### 🟢 [低] DB index 對 unread count 查詢可優化

現有 index：
```sql
CREATE INDEX idx_announcements_status ON announcements(tenant_id, status, publish_at DESC);
```

`countUnread` 查詢條件包含 `expire_at`，當公告很多時 `expire_at` 過濾仍需回表。  
**建議**（非緊急）：考慮 partial index：
```sql
CREATE INDEX idx_announcements_active ON announcements(tenant_id, publish_at DESC)
WHERE status = 'PUBLISHED';
```

---

### 🟢 [低] `Announcement` 缺乏 `createdBy` 與 `users` 的關聯約束

`created_by VARCHAR(50)` 無 FK，使用者被刪除後 `created_by_name` 仍可顯示（已是 snapshot），但 DEPT_ADMIN 的「自己建立」檢查若 user_id 重用會誤判（系統未支援 user_id 重用，目前安全）。

**建議**：不必加 FK（會與軟刪設計衝突），但加註解說明 `created_by_name` 為 snapshot 設計。

---

### 🟢 [低] DTO 沒有 OpenAPI 註解

無 `@Schema` / `@Operation`，Swagger 文件不夠完整（若有用）。可選改善。

---

### 🟢 [低] `AnnouncementController.hasManagePermission()` 仍存在但設計合理

僅給 `getById` 使用，用以決定是否套用可見性檢查。**設計正確**，只是若日後新增其他端點要參考管理員身分，應抽到 `SecurityContextUtils` 共用。

---

## 三、效能評估

| 場景 | 現況 | 評估 |
|---|---|---|
| 列表頁（管理端）20 筆 | 4 次 SQL（list / depts / reads / dept names） | ✅ 優秀 |
| 列表頁（前台）10 筆 | 同上 | ✅ 優秀 |
| getById | 4–5 次 SQL（含逐一 dept 查詢） | ⚠️ 可優化（見上方建議） |
| markAllAsRead | 1 次 native SQL | ✅ 優秀 |
| countUnread 輪詢（每 5 分鐘 / 使用者） | 1 次 SQL with `NOT EXISTS` | ✅ 可接受 |
| 部門名稱解析 | `findByDeptIdIn` 已實作 | ✅ |

---

## 四、功能優化建議（產品向）

以下為**新功能**或**UX 強化**建議，非缺陷：

### 高價值（建議優先評估）

1. **🌟 富文本支援（含安全防護）**
   - 目前 `content` 為純文字，公告無法插入連結、粗體、條列。
   - 建議：前端引入 TipTap / Quill，後端引入 OWASP Java HTML Sanitizer（白名單策略），允許 `<a> <b> <ul> <li> <p> <br>`，禁止 `<script> <iframe> on*` 屬性。
   - 同時保留純文字 fallback（搜尋仍以純文字版本）。

2. **🌟 公告分類 / 標籤（category）**
   - 新增 `category VARCHAR(20)` 欄位（如 `SYSTEM`/`POLICY`/`EVENT`/`MAINTENANCE`），前台 list 可篩選，UI 以不同顏色標籤呈現。
   - 對「重要公告」「維修通告」分流，提升閱讀效率。

3. **🌟 排程發佈與下架**
   - 目前 `publishAt` 已支援排程（未到時間查不到），但**沒有 admin 視覺指示**「此公告尚未到發佈時間」。
   - 建議新增 `SCHEDULED` 計算狀態（status=PUBLISHED 但 publishAt > now），讓 admin 列表標示「排程中」。

4. **🌟 公告附件**
   - 新增 `announcement_attachments` 表（檔名 / size / mime / 路徑），整合既有 upload 機制。
   - 多用於政策文件、活動報名表等。

5. **🌟 已讀回條 / 統計**
   - 對「需確認」類公告（如員工守則修訂）加入 `requires_ack BOOLEAN`，使用者需明確點「我已閱讀並了解」。
   - 管理端可看「已讀比例」與「未讀名單」（已有 `announcement_reads` 表，只需 service + UI）。

### 中等價值

6. **預覽 / 草稿自動儲存**
   - 編輯時每 30 秒自動存草稿（status=DRAFT），避免關閉視窗遺失。
   - 「預覽」按鈕模擬前台呈現。

7. **批次操作**
   - 管理端 list 支援多選 → 批次刪除 / 批次下架（status 改 DRAFT）。

8. **公告置頂順序**
   - 目前 `pinned` 為 boolean，多個置頂只能用 `publish_at` 排序。
   - 建議改 `pin_order INT NULL`，可拖曳調整置頂順序。

9. **即時推播（WebSocket / SSE）**
   - 取代 5 分鐘輪詢，新公告即時跳鈴鐺。
   - 系統已有 notification module 可能已有 WebSocket 基礎，可整合。

10. **多語系公告**
    - `announcements` 加 `lang_code`，或新增 `announcement_translations` 子表。
    - 目前後台僅單一語言；既然前端已有 i18n，公告也應跟著切換語言顯示。

### 低價值（看實際需求）

11. **公告留言 / Q&A**：對員工互動類公告開放留言。
12. **Email / 推播通知**：發佈時透過 notification module 同步推送。
13. **公告版本歷史**：保留每次編輯前的快照，可比對差異。
14. **公告匯出**：admin 可匯出 CSV/PDF 作為存檔。
15. **首次登入強制閱讀**：`requires_ack` + 未確認時 modal 阻擋，適合法遵類公告。

---

## 五、優先級總表

| 優先級 | 項目 | 類型 | 預估工時 | 狀態 |
|---|---|---|---|---|
| **P1** | `markAsRead` native SQL 加入 tenant 驗證 | Security | S | ✅ 已修正（2026-05-26） |
| **P2** | `markAllAsRead` 加 tenant 隔離迴歸測試 | Security | S | ✅ 已修正（2026-05-26） |
| **P2** | 加入 `@Version` 樂觀鎖 | Correctness | M | ✅ 已修正（2026-05-26） |
| **P2** | 前端 `AnnouncementListView` 改走 store action（同步 popoverItems） | Code Quality | S | ✅ 已修正（2026-05-26） |
| **P3** | 前台 list 端點加 `@PreAuthorize("isAuthenticated()")` 明示 | Readability | XS | ✅ 已修正（2026-05-26） |
| **P3** | `toResponse` 改用批次 `findByDeptIdIn` 並刪除 `resolveDeptNames` | Performance | S | ✅ 已修正（2026-05-26） |
| **P3** | `AnnouncementRequest` 加 `@AssertTrue` 跨欄位驗證 | Validation | S | ✅ 已修正（2026-05-26） |
| **P3** | keyword 搜尋擴及 content | UX | S | ✅ 已修正（2026-05-26） |
| **P4** | 為 `countUnread` 加 partial index | Performance | XS | ✅ 已修正（2026-05-26） |
| **P4** | DTO 補 OpenAPI 註解 | DX | M | ✅ 已修正（2026-05-26） |
| **🌟 Feature** | 富文本（含 sanitizer） | Product | L | ✅ 已修正（2026-05-26） |
| **🌟 Feature** | 公告分類 / category | Product | M | ✅ 已修正（2026-05-26） |
| **🌟 Feature** | 已讀回條（requires_ack）+ 已讀統計 | Product | M | ✅ 已修正（2026-05-26） |
| **🌟 Feature** | 排程狀態視覺化（SCHEDULED） | Product | S | ✅ 已修正（2026-05-26） |
| **🌟 Feature** | 附件支援 | Product | L | ✅ 已修正（2026-05-26） |
| **🌟 Feature** | 預覽 / 草稿自動儲存（30s autosave + 預覽 dialog） | Product | M | ✅ 已修正（2026-05-26） |
| **🌟 Feature** | 公告置頂順序（pin_order + 拖曳排序） | Product | M | ✅ 已修正（2026-05-26） |
| **🌟 Feature** | 多語系公告（announcement_translations 子表 + Accept-Language） | Product | M | ✅ 已修正（2026-05-26） |

---

## 架構評分

| 維度 | 第一輪後 | 本輪 | 變化說明 |
|---|---|---|---|
| 安全性 | 8.5 | **8.5** | list 拆分、permission 收斂提升；但發現 `markAsRead` 跨租戶寫入問題，扣回 |
| 正確性 | 9 | **9** | LIKE 已轉義、validator 完整；缺乏 `expireAt > publishAt` 與 `@Version` |
| 效能 | 8.5 | **9** | `findByDeptIdIn` 已套用；單筆 `toResponse` 仍可優化 |
| 可維護性 | 8 | **9** | controller 拆分、測試補齊 → 顯著提升 |
| UX | 9 | **9** | UX 細節維持；列表 store 同步可再進化 |

**綜合：8.8 / 10**（上一輪 8.6）

---

## 結論

第一輪所列的所有 P2–P4 缺陷皆已完整修正並有測試輔助，模組成熟度顯著提升。本輪的 P1（`markAsRead` 跨租戶寫入）與所有 P2–P4 品質性建議**亦已全數修正**；🌟 Feature 區段中除中等價值與低價值的部分項目外（批次操作、即時推播 WebSocket、留言、Email 推播、版本歷史、匯出、首次登入強制閱讀），其餘建議均已落地（富文本、分類、已讀回條/統計、排程視覺化、附件、預覽/草稿自動儲存、置頂順序、多語系公告）。
