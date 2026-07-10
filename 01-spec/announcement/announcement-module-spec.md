

# 公告管理模組（Announcement）需求規格書

---

## 模組範圍

本規格書涵蓋 `com.taipei.iot.announcement` 套件下的完整功能，依職責分為以下六個子功能群：

| # | 子功能群 | 核心類別 |
|---|---|---|
| F1 | 前台公告查詢與詳情 | `AnnouncementService.listVisible`, `getById` |
| F2 | 管理端公告 CRUD | `AnnouncementService.listAdmin`, `create`, `update`, `delete` |
| F3 | 未讀計數與已讀標記 | `AnnouncementReadService.getUnreadCount`, `markAsRead`, `markAllAsRead` |
| F4 | 已讀統計與未讀名單 | `AnnouncementReadService.getReadStats`, `getUnreadUsers` |
| F5 | 置頂排序 | `AnnouncementService.listPinned`, `reorderPins` |
| F6 | 附件管理 | `AnnouncementAttachmentService.upload`, `delete` |

---

## F1 — 前台公告查詢與詳情

### 1. 需求概述 (High-Level Goal)

提供已登入使用者依照自身所屬部門查詢「目前可見」公告（已發佈、未過期、且受眾範圍符合）的分頁列表，以及取得單筆公告的完整詳情，並支援多語言（zh-TW / zh-CN / en）內容回傳。

### 2. Use Case

| 項目 | 說明 |
| :--- | :--- |
| **Actor** | 已登入的一般使用者（任何角色） |
| **Precondition** | JWT 合法，`TenantContext` 已設定，`SecurityContext` 含有效的 `UserInfo`（userId, deptId） |
| **正常流程 (Happy Path)** | 1. 使用者呼叫 `GET /v1/auth/announcements?category=SYSTEM&page=0&size=10`<br>2. Service 從 `SecurityContextUtils` 取得 userId、deptId（null 時以 -1 代替）<br>3. 查詢 `status=PUBLISHED`、`publishAt ≤ now`、`expireAt > now` 或為 null、且 `scope=ALL` 或 deptId 在 `announcement_depts` 中的公告<br>4. 對每筆公告查詢當前使用者是否已讀（`isRead` 欄位），回傳分頁結果 |
| **替代流程 (Alternate Flow)** | 若 category 未傳入，則不做分類過濾，回傳全部可見公告 |
| **例外流程 (Exception Flow)** | 查詢單筆（`GET /v1/auth/announcements/{id}`）時，若公告不存在或使用者無法見到（已過期 / 未發佈 / scope 不符），回傳 `ErrorCode.ANNOUNCEMENT_NOT_FOUND` |

### 3. 功能完成條件（測試合約 / Acceptance Criteria）

#### 3.1 快樂路徑 (Happy Path)

- [x] **AC-F1-1**：使用者所屬部門有 3 筆可見公告（含 1 筆 scope=ALL、2 筆 scope=DEPT 且 deptId 符合），呼叫 `listVisible` 應回傳 3 筆，且每筆含正確的 `isRead` 欄位。
- [x] **AC-F1-2**：使用者已讀其中 1 筆，`isRead=true`；其餘 2 筆 `isRead=false`。
- [x] **AC-F1-3**：傳入 `lang=en` 時，回傳的 title / content 應優先使用 `announcement_translations` 中 lang_code=en 的翻譯；若無 en 翻譯則 fallback 到主表（zh-TW）並於 `resolvedLang` 欄位標示 `zh-TW`。
- [x] **AC-F1-4**：`getById` 正常情境：回傳指定 id 的公告，`isRead` 正確，且具 `ANNOUNCEMENT_MANAGE` 權限者額外回傳 `editable=true`。

#### 3.2 商業規則 / 邊界值

- [x] **AC-F1-5**：使用者 `deptId=null` 時，Service 應以 `-1L` 作為 deptId 參數傳入 Repository，且只有 `scope=ALL` 的公告對此使用者可見。
- [x] **AC-F1-6**：`publishAt` 大於 now 的公告（排程中），不出現在前台列表。
- [x] **AC-F1-7**：`expireAt` 小於 now 的公告（已過期），不出現在前台列表。
- [x] **AC-F1-8**：`getById` 對已過期 / 未發佈 / scope 不符的公告，拋出 `BusinessException(ANNOUNCEMENT_NOT_FOUND)`。
- [x] **AC-F1-9**：`category` 過濾只接受白名單（`GENERAL/SYSTEM/POLICY/EVENT/MAINTENANCE`）；傳入非法值由 Service 的 `normalizeCategoryFilter` 轉換為 null（等同不過濾）。

#### 3.3 安全性與副作用

- [x] **AC-F1-10**：租戶隔離 — `AnnouncementRepository` 的 `@Filter(tenantFilter)` 必須在執行前已啟用，確保 A 租戶使用者看不到 B 租戶的公告。
- [x] **AC-F1-11**：`GET /v1/auth/announcements` 需 `isAuthenticated()`；未登入請求應被 Spring Security 攔截，不進入 Service。

### 4. 相依元件與對外介面

| 元件 | 方法 | 預期行為 |
| :--- | :--- | :--- |
| `AnnouncementRepository` | `findVisibleAnnouncements(deptId, category, now, pageable)` | 回傳 `Page<Announcement>`，含置頂在前排序 |
| `AnnouncementReadRepository` | `findByAnnouncementIdInAndUserId(ids, userId)` | 回傳已讀記錄，供組裝 `isRead` |
| `AnnouncementTranslationRepository` | `findByAnnouncementIdIn(ids)` | 回傳所有翻譯記錄，供語言 fallback |
| `SecurityContextUtils` | `getUserInfo()` | 回傳含 userId, deptId 的 `UserInfo` |
| `HtmlSanitizerService` | （查詢時不調用，僅 create/update 時調用） | — |

---

## F2 — 管理端公告 CRUD

### 1. 需求概述

提供具 `ANNOUNCEMENT_MANAGE` 權限的管理員（ADMIN / DEPT_ADMIN）對公告進行完整的生命週期管理（建立、編輯、刪除），並支援 HTML 富文字內文（XSS 清洗）、多語言翻譯、部門受眾指定、排程發佈與失效設定。

### 2. Use Case

| 項目 | 說明 |
| :--- | :--- |
| **Actor** | 具 `ANNOUNCEMENT_MANAGE` 權限的管理員 |
| **Precondition** | JWT 合法，具有 `ANNOUNCEMENT_MANAGE` 權限，`DataScope` 為 `ALL`（ADMIN）或非 ALL（DEPT_ADMIN） |
| **正常流程 — 新增** | 1. 管理員呼叫 `POST /v1/auth/announcements`，帶入標題、HTML 內文、status、scope 等欄位<br>2. Service 對 content 執行 XSS sanitize，並萃取純文字寫入 `contentText`<br>3. 建立 `Announcement` entity，若 scope=DEPT 且有 targetDeptIds，同步寫入 `announcement_depts`<br>4. 若有 translations，寫入 `announcement_translations`（zh-TW 以主表為準不重複寫入）<br>5. 回傳新建公告的 `AnnouncementResponse` |
| **正常流程 — 編輯** | 1. 管理員呼叫 `PUT /v1/auth/announcements/{id}`，帶入含 `version` 的 request<br>2. Service 載入 entity，比對 version（樂觀鎖）<br>3. 通過後更新欄位，重建 `announcement_depts` 與 `announcement_translations`<br>4. 以 `saveAndFlush` 觸發 Hibernate `@Version` 自動遞增<br>5. 回傳更新後的 `AnnouncementResponse` |
| **正常流程 — 刪除** | 1. 管理員呼叫 `DELETE /v1/auth/announcements/{id}`<br>2. Service 確認公告存在，DEPT_ADMIN 確認為自建公告<br>3. 刪除 entity，DB CASCADE 自動刪除 `announcement_depts` / `announcement_reads` |
| **替代流程** | DEPT_ADMIN 新增時：scope 強制改為 `DEPT`，targetDeptIds 強制為自身部門，無法指定其他部門 |
| **例外流程** | 編輯時 version 不符 → `ANNOUNCEMENT_VERSION_CONFLICT`；DEPT_ADMIN 嘗試刪除他人公告 → `PERMISSION_DENIED`；scope=DEPT 但 targetDeptIds 為空 → `VALIDATION_ERROR` |

### 3. 功能完成條件（測試合約 / Acceptance Criteria）

#### 3.1 快樂路徑 (Happy Path)

- [x] **AC-F2-1**：ADMIN 新增 `scope=ALL` 的公告，Service 應正確呼叫 `announcementRepository.save()`，且 entity.createdBy = 當前 userId。
- [x] **AC-F2-2**：ADMIN 新增 `scope=DEPT`，targetDeptIds=[10,20]，應同步呼叫 `announcementDeptRepository.saveAll()`，寫入 2 筆 junction 記錄。
- [x] **AC-F2-3**：新增帶有 translations（zh-CN, en）的公告，`announcementTranslationRepository.saveAll()` 應被呼叫，且 zh-TW 不重複寫入 translations 子表。
- [x] **AC-F2-4**：ADMIN 以正確 version 編輯公告，更新成功並回傳新 response；`saveAndFlush` 被呼叫一次。
- [x] **AC-F2-5**：ADMIN 刪除公告，`announcementRepository.delete(entity)` 被呼叫；db CASCADE 處理子表（單元測試中以 verify 驗證呼叫，不實際查子表）。

#### 3.2 商業規則 / 邊界值

- [x] **AC-F2-6**：DEPT_ADMIN 新增公告時，無論 request 傳入的 scope 為何，entity.scope 強制為 `DEPT`，targetDeptIds 強制為 `[user.deptId]`。
- [x] **AC-F2-7**：scope=DEPT 但 request.targetDeptIds 為 null 或空時，拋出 `BusinessException(VALIDATION_ERROR)`。
- [x] **AC-F2-8**：編輯時 request.version=null，拋出 `BusinessException(VALIDATION_ERROR, "缺少 version 欄位")`。
- [x] **AC-F2-9**：編輯時 request.version 與 entity.version 不符，拋出 `BusinessException(ANNOUNCEMENT_VERSION_CONFLICT)`。
- [x] **AC-F2-10**：HTML 內文含 `<script>alert(1)</script>`，`HtmlSanitizerService.sanitize()` 執行後 `<script>` 標籤應被移除；`contentText` 為純文字（無任何 HTML 標籤）。
- [x] **AC-F2-11**：新增公告 `publishAt=null` 時，Service 應將 `publishAt` 設為 `LocalDateTime.now()`（≈建立當下時間），確保立即可見。
- [x] **AC-F2-12**：置頂且未提供 `pinOrder` 時，Service 應自動計算 `max(pinOrder)+1` 並寫入。
- [x] **AC-F2-13**：取消置頂（`pinned=false`）時，`pinOrder` 應被設為 null。
- [x] **AC-F2-14**：`expireAt` 早於 `publishAt` 時，Bean Validation 的 `@AssertTrue` 應觸發驗證失敗（在 Controller 層 `@Valid` 攔截；Service 層單元測試可略）。

#### 3.3 安全性與副作用

- [x] **AC-F2-15**：DEPT_ADMIN 嘗試編輯他人（createdBy ≠ userId）的公告，拋出 `BusinessException(PERMISSION_DENIED)`。
- [x] **AC-F2-16**：DEPT_ADMIN 嘗試刪除他人公告，拋出 `BusinessException(PERMISSION_DENIED)`。
- [x] **AC-F2-17**：`POST/PUT/DELETE` 均需 `hasAuthority('ANNOUNCEMENT_MANAGE')`；缺少此權限由 Spring Security 在進入 Service 前攔截（不進行 Service 單元測試，可在 Controller 整合測試驗證）。
- [x] **AC-F2-18**：所有寫入操作帶有 `@AuditEvent`（CREATE / UPDATE / DELETE），確保審計記錄被觸發（可透過 Mock AuditEventPublisher 或 `@SpyBean` 驗證）。

### 4. 相依元件與對外介面

| 元件 | 方法 | 預期行為 |
| :--- | :--- | :--- |
| `AnnouncementRepository` | `save(entity)` | 持久化公告，auto-increment id |
| `AnnouncementRepository` | `saveAndFlush(entity)` | 含樂觀鎖更新，`@Version` 自動遞增 |
| `AnnouncementRepository` | `delete(entity)` | CASCADE 刪除子表記錄 |
| `AnnouncementDeptRepository` | `saveAll(List<AnnouncementDept>)` | 批次插入 junction table |
| `AnnouncementDeptRepository` | `deleteByAnnouncementId(id)` | 編輯前清空舊 junction 記錄 |
| `AnnouncementTranslationRepository` | `deleteByAnnouncementId(id)` | 編輯前清空舊翻譯 |
| `AnnouncementTranslationRepository` | `saveAll(List)` | 批次插入翻譯記錄 |
| `HtmlSanitizerService` | `sanitize(html)` | XSS 清洗後回傳安全 HTML |
| `HtmlSanitizerService` | `extractText(html)` | 剝離所有 HTML 標籤，回傳純文字 |
| `SecurityContextUtils` | `getUserInfo()` | 回傳 userId, deptId, dataScope |

### 5. 附加說明

- **樂觀鎖**：`Announcement` entity 具 `@Version Long version` 欄位；編輯 API 必須帶上從 GET 取回的原始 version，否則拒絕請求。
- **SQL Injection 防護**：keyword 搜尋前由 `SqlLikeEscaper.contains()` 轉義 `%` / `_` 特殊字元，再傳入 JPQL 的 named parameter。

---

## F3 — 未讀計數與已讀標記

### 1. 需求概述

提供使用者查詢當前未讀公告數量（用於導覽列小紅點），以及對單筆公告或全部公告進行已讀標記；標記操作須為冪等（重複呼叫不得產生重複資料或錯誤）。

### 2. Use Case

| 項目 | 說明 |
| :--- | :--- |
| **Actor** | 已登入的任何使用者 |
| **Precondition** | JWT 合法，`TenantContext` 已設定 |
| **正常流程 — 未讀計數** | 1. 前端呼叫 `GET /v1/auth/announcements/unread-count`<br>2. Service 取得 userId、deptId（null 以 -1 代替）<br>3. 查詢已發佈、未過期、且受眾符合、且使用者尚未讀的公告數量<br>4. 回傳 `UnreadCountResponse { count: N }` |
| **正常流程 — 標記單筆已讀** | 1. 使用者呼叫 `POST /v1/auth/announcements/{id}/read`<br>2. Service 驗證公告存在（`announcementRepository.existsById`，含租戶過濾）<br>3. 執行 `markAsRead(announcementId, userId)`（upsert ON CONFLICT DO NOTHING）<br>4. 回傳 200 OK |
| **正常流程 — 全部標為已讀** | 1. 使用者呼叫 `POST /v1/auth/announcements/read-all`<br>2. Service 取得 userId、tenantId、deptId<br>3. 執行 `markAllAsRead(userId, tenantId, deptId)` 批次 INSERT（僅插入未讀記錄，ON CONFLICT DO NOTHING）<br>4. 回傳 200 OK |
| **替代流程** | 全部標為已讀時，若使用者已無任何未讀公告，DB INSERT 的 SELECT 子句結果為空，不插入任何記錄，不拋錯 |
| **例外流程** | 標記單筆時，公告不存在或不屬於當前租戶，拋出 `BusinessException(ANNOUNCEMENT_NOT_FOUND)`，阻止跨租戶寫入 |

### 3. 功能完成條件（測試合約 / Acceptance Criteria）

#### 3.1 快樂路徑

- [x] **AC-F3-1**：使用者有 3 筆未讀公告，`getUnreadCount()` 回傳 `UnreadCountResponse { count: 3 }`。
- [x] **AC-F3-2**：`markAsRead(announcementId=5)` 正常情境：`announcementRepository.existsById(5)` 回傳 true，`markAsRead(5, userId)` 被呼叫一次。
- [x] **AC-F3-3**：`markAllAsRead()` 正常情境：`announcementReadRepository.markAllAsRead(userId, tenantId, deptId)` 被呼叫，且 tenantId 來自 `TenantContext.getCurrentTenantId()`。

#### 3.2 商業規則 / 邊界值

- [x] **AC-F3-4**：使用者 `deptId=null` 時，傳入 `countUnread` 的 deptId 應為 `-1L`（以 `ArgumentCaptor` 驗證）。
- [x] **AC-F3-5**：使用者已無未讀公告，`countUnread` 回傳 0，不拋例外。
- [x] **AC-F3-6**：重複呼叫 `markAsRead(sameId)` 兩次，第二次仍不拋錯（ON CONFLICT DO NOTHING 保證冪等）。

#### 3.3 安全性與副作用

- [x] **AC-F3-7**：租戶隔離 — `markAsRead` 在呼叫 native SQL INSERT 前，先透過 `announcementRepository.existsById()`（受 `@Filter(tenantFilter)` 保護）驗證公告屬於當前租戶；若不屬於當前租戶，拋出 `ANNOUNCEMENT_NOT_FOUND`，防止跨租戶寫入。
- [x] **AC-F3-8**：`markAllAsRead` 的 native SQL 中 `WHERE a.tenant_id = :tenantId` 條件確保只標記當前租戶的公告，`tenantId` 來自 `TenantContext.getCurrentTenantId()`（以 `ArgumentCaptor` 驗證此參數）。

### 4. 相依元件與對外介面

| 元件 | 方法 | 預期行為（Mock 回傳值） |
| :--- | :--- | :--- |
| `AnnouncementRepository` | `countUnread(deptId, userId, now)` | 回傳 `long`（未讀公告總數） |
| `AnnouncementRepository` | `existsById(announcementId)` | `true` = 公告存在且屬於當前租戶 |
| `AnnouncementReadRepository` | `markAsRead(announcementId, userId)` | void（upsert，ON CONFLICT DO NOTHING） |
| `AnnouncementReadRepository` | `markAllAsRead(userId, tenantId, deptId)` | void（批次 INSERT，ON CONFLICT DO NOTHING） |
| `TenantContext` | `getCurrentTenantId()` | 回傳當前租戶 ID（String） |
| `SecurityContextUtils` | `getUserInfo()` | 回傳含 userId, deptId 的 `UserInfo` |
| `SecurityContextUtils` | `getCurrentUserId()` | 回傳當前使用者 ID（String） |

---

## F4 — 已讀統計與未讀名單

### 1. 需求概述

為管理端提供指定公告的受眾傳達率統計（已讀人數 / 受眾總數 / 已讀比例），以及未讀使用者的分頁名單，特別服務於「需確認（requiresAck=true）」類公告的傳達追蹤。

### 2. Use Case

| 項目 | 說明 |
| :--- | :--- |
| **Actor** | 具 `ANNOUNCEMENT_MANAGE` 權限的管理員 |
| **Precondition** | 公告已存在，管理員已登入 |
| **正常流程 — scope=ALL** | 1. 呼叫 `GET /v1/auth/announcements/{id}/read-stats`<br>2. 計算受眾：`countAudienceAll(tenantId)`（該租戶所有有效使用者）<br>3. 計算已讀：`countReadAll(announcementId, tenantId)`<br>4. 計算 `readRatio = readCount / totalAudience`（`BigDecimal`, 4 位小數，HALF_UP）<br>5. 回傳 `AnnouncementReadStatsResponse` |
| **正常流程 — scope=DEPT** | 1. 從 `announcement_depts` 取得 deptIds<br>2. 計算受眾：`countAudienceDept(tenantId, deptIds)`<br>3. 計算已讀：`countReadDept(announcementId, tenantId, deptIds)`<br>4. 回傳統計 |
| **替代流程** | scope=DEPT 但 junction table 為空（資料異常），`totalAudience=0, readCount=0, readRatio=0` |
| **例外流程** | DEPT_ADMIN 查詢他人公告統計 → `PERMISSION_DENIED` |

### 3. 功能完成條件（測試合約 / Acceptance Criteria）

#### 3.1 快樂路徑

- [x] **AC-F4-1**：scope=ALL 公告，受眾 100 人，已讀 37 人，`getReadStats` 應回傳 `totalAudience=100, readCount=37, unreadCount=63, readRatio=0.3700`。
- [x] **AC-F4-2**：scope=DEPT 公告，deptIds=[10,20]，受眾 50 人，已讀 50 人，`readRatio=1.0000`，`unreadCount=0`。
- [x] **AC-F4-3**：`getUnreadUsers` scope=ALL，回傳含 userId, displayName, email, deptId, deptName 的分頁結果。

#### 3.2 商業規則 / 邊界值

- [x] **AC-F4-4**：`totalAudience=0` 時（空 junction table 異常情境），`readRatio=BigDecimal.ZERO`，不拋出除零例外。
- [x] **AC-F4-5**：scope=DEPT 但 deptIds 為空，`getUnreadUsers` 應直接回傳空 `PageResponse`，不呼叫 `announcementStatsRepository`。
- [x] **AC-F4-6**：`getUnreadUsers` 支援 keyword 模糊搜尋（name / email），keyword 空白時 safeKeyword=null（不過濾）。

#### 3.3 安全性與副作用

- [x] **AC-F4-7**：DEPT_ADMIN（dataScope ≠ ALL）查詢非自建公告統計，`loadAndCheckManage` 應拋出 `BusinessException(PERMISSION_DENIED)`。
- [x] **AC-F4-8**：需 `hasAuthority('ANNOUNCEMENT_MANAGE')`（Controller 層控制）。

### 4. 相依元件與對外介面

| 元件 | 方法 | 預期行為 |
| :--- | :--- | :--- |
| `AnnouncementRepository` | `findById(id)` | 找不到時拋 `ANNOUNCEMENT_NOT_FOUND` |
| `AnnouncementDeptRepository` | `findByAnnouncementId(id)` | 回傳 deptId 清單 |
| `AnnouncementStatsRepository` | `countAudienceAll(tenantId)` | 回傳 `long`（全租戶有效使用者數） |
| `AnnouncementStatsRepository` | `countReadAll(announcementId, tenantId)` | 回傳 `long`（已讀人數） |
| `AnnouncementStatsRepository` | `countAudienceDept(tenantId, deptIds)` | 回傳 `long`（指定部門受眾數） |
| `AnnouncementStatsRepository` | `countReadDept(announcementId, tenantId, deptIds)` | 回傳 `long`（指定部門已讀人數） |
| `AnnouncementStatsRepository` | `findUnreadUsersAll(id, tenantId, keyword, pageable)` | 回傳 `Page<Object[]>`（userId, displayName, email, deptId, deptName） |
| `AnnouncementStatsRepository` | `findUnreadUsersDept(id, tenantId, deptIds, keyword, pageable)` | 同上，但限定部門 |

---

## F5 — 置頂排序

### 1. 需求概述

提供管理端對置頂公告的列表查詢與拖曳排序功能；置頂公告在前台與管理端列表中依 `pinOrder` 升序排列於最前方。

### 2. Use Case

| 項目 | 說明 |
| :--- | :--- |
| **Actor** | 具 `ANNOUNCEMENT_MANAGE` 權限的管理員 |
| **Precondition** | 已有若干置頂公告存在 |
| **正常流程** | 1. 管理員拖曳排序，前端產生新的 orderedIds 陣列<br>2. 呼叫 `PUT /v1/auth/announcements/pin-order`<br>3. Service 依陣列順序（index+1）批次更新各公告的 `pinOrder` 欄位<br>4. 下次查詢列表時公告依新順序排列 |
| **例外流程** | orderedIds 中包含非置頂公告 id，Service 仍更新 `pinOrder` 但不強制改變 `pinned` 欄位（前端負責過濾） |

### 3. 功能完成條件（測試合約 / Acceptance Criteria）

- [x] **AC-F5-1**：`reorderPins([3,1,2])` 應令 id=3 的公告 pinOrder=1、id=1 的 pinOrder=2、id=2 的 pinOrder=3。
- [x] **AC-F5-2**：`listPinned()` ADMIN 回傳全部 pinned=true 的公告；DEPT_ADMIN 僅回傳自建或受眾包含自己部門的置頂公告。
- [x] **AC-F5-3**：需 `hasAuthority('ANNOUNCEMENT_MANAGE')`。

### 4. 相依元件與對外介面

| 元件 | 方法 | 預期行為 |
| :--- | :--- | :--- |
| `AnnouncementRepository` | `findAllPinned()` | 回傳所有 `pinned=true` 公告（依 pinOrder 排序） |
| `AnnouncementRepository` | `findPinnedForDeptAdmin(userId, deptId)` | 回傳 DEPT_ADMIN 可見的置頂公告 |
| `AnnouncementRepository` | `findById(id)` + `save(entity)` | 依 id 逐一更新 pinOrder |

---

## F6 — 附件管理

### 1. 需求概述

提供管理員對公告附件的上傳與刪除，支援白名單副檔名控制（預設僅允許 PDF），每則公告最多 10 個附件，並透過 virus scan 與 MIME type 驗證防止惡意檔案上傳。

### 2. Use Case

| 項目 | 說明 |
| :--- | :--- |
| **Actor** | 具 `ANNOUNCEMENT_MANAGE` 權限的管理員，且為公告建立者（DEPT_ADMIN）或任意管理員（ADMIN） |
| **Precondition** | 公告已存在，尚未達到附件上限（10 個） |
| **正常流程 — 上傳** | 1. 管理員呼叫 `POST /v1/auth/announcements/{id}/attachments`，帶入 Multipart 檔案<br>2. Service 驗證公告存在且有權限操作<br>3. 確認當前附件數 < 10<br>4. 執行副檔名白名單驗證（`allowedExtensions`）<br>5. 呼叫 `FileValidationService` 進行 MIME type 與 virus scan<br>6. 呼叫 `FileStorageService.store()` 落地檔案，取得 storagePath<br>7. 寫入 `announcement_attachments` 記錄<br>8. 回傳 `AnnouncementAttachmentResponse` |
| **正常流程 — 刪除** | 1. 管理員呼叫 `DELETE /v1/auth/announcements/{annId}/attachments/{attachId}`<br>2. Service 驗證附件屬於指定公告且有刪除權限<br>3. 從 storage 刪除實體檔案，刪除 DB 記錄 |
| **例外流程** | 附件數已達 10 個 → `BusinessException(ANNOUNCEMENT_ATTACHMENT_LIMIT_EXCEEDED)`；副檔名不在白名單 → `BusinessException(FILE_TYPE_NOT_ALLOWED)`；DEPT_ADMIN 操作他人公告附件 → `PERMISSION_DENIED` |

### 3. 功能完成條件（測試合約 / Acceptance Criteria）

#### 3.1 快樂路徑

- [x] **AC-F6-1**：公告有 5 個附件，上傳 PDF 檔案，`attachmentRepository.save()` 被呼叫，回傳含 id、originalFilename、fileSize 的 response。
- [x] **AC-F6-2**：上傳成功後，`AnnouncementAttachment.storagePath` 由 `FileStorageService.store()` 的回傳值填入。

#### 3.2 商業規則 / 邊界值

- [x] **AC-F6-3**：當前附件數已達 `MAX_ATTACHMENTS_PER_ANNOUNCEMENT=10`，再次上傳應拋出 `BusinessException(ANNOUNCEMENT_ATTACHMENT_LIMIT_EXCEEDED)`，且 `FileStorageService` 不被呼叫。
- [x] **AC-F6-4**：上傳 `.exe` 或其他非白名單副檔名，在呼叫 `FileValidationService` 之前即拋出例外。
- [x] **AC-F6-5**：allowedExtensions 由設定 `announcement.attachments.allowed-extensions` 控制，預設為 `pdf`；可透過 config 擴充（如 `pdf,docx`）。

#### 3.3 安全性與副作用

- [x] **AC-F6-6**：DEPT_ADMIN 嘗試對他人公告上傳附件，`checkManagePermission` 應拋出 `PERMISSION_DENIED`。
- [x] **AC-F6-7**：`FileValidationService` 負責 MIME type 與 virus scan；單元測試中以 Mock 模擬 virus scan 通過與失敗兩種情境。

### 4. 相依元件與對外介面

| 元件 | 方法 | 預期行為 |
| :--- | :--- | :--- |
| `AnnouncementAttachmentRepository` | `countByAnnouncementId(id)` | 回傳 `int`（當前附件數量） |
| `AnnouncementAttachmentRepository` | `save(attachment)` | 持久化附件記錄 |
| `AnnouncementAttachmentRepository` | `findById(id)` | 找不到時拋例外 |
| `AnnouncementAttachmentRepository` | `delete(attachment)` | 刪除記錄 |
| `FileValidationService` | `validate(file, allowedExts)` | 驗證 MIME type、virus scan；不合格時拋例外 |
| `FileStorageService` | `store(file, subDir)` | 落地檔案，回傳 storagePath（String） |
| `FileStorageService` | `delete(storagePath)` | 刪除實體檔案 |

---

## 附錄 A：資料模型摘要

### 核心 Entity 欄位

| Entity | 關鍵欄位 | 說明 |
|---|---|---|
| `Announcement` | `id, tenantId, title, content, contentText, status, scope, category, pinned, pinOrder, requiresAck, publishAt, expireAt, createdBy, version` | 主表；`@Version` 樂觀鎖 |
| `AnnouncementDept` | `announcementId, deptId` | 複合主鍵 junction table；scope=DEPT 時使用 |
| `AnnouncementRead` | `announcementId, userId, readAt` | 複合 UNIQUE(announcementId, userId)；ON CONFLICT DO NOTHING 保證冪等 |
| `AnnouncementTranslation` | `id, announcementId, langCode, title, content` | 多語言翻譯子表；zh-TW 以主表為準 |
| `AnnouncementAttachment` | `id, announcementId, originalFilename, storagePath, fileSize, contentType` | 附件中繼資料 |

### 枚舉值

| 枚舉 | 允許值 | 說明 |
|---|---|---|
| `status` | `DRAFT`, `PUBLISHED` | 草稿 / 發佈 |
| `scope` | `ALL`, `DEPT` | 全公司 / 指定部門 |
| `category` | `GENERAL`, `SYSTEM`, `POLICY`, `EVENT`, `MAINTENANCE` | 公告分類 |
| `statusFilter`（管理端查詢） | `ALL`, `DRAFT`, `SCHEDULED`, `PUBLISHED`, `EXPIRED` | 其中 `SCHEDULED` 為計算狀態 |

### 支援語言

| langCode | 說明 |
|---|---|
| `zh-TW` | 預設語言，主表 title/content 所代表的語言 |
| `zh-CN` | 簡體中文翻譯（存於 announcement_translations） |
| `en` | 英文翻譯（存於 announcement_translations） |

---

## 附錄 B：API 端點摘要

| Method | Path | 權限 | 對應功能 |
|---|---|---|---|
| `GET` | `/v1/auth/announcements` | `isAuthenticated()` | F1：前台公告列表 |
| `GET` | `/v1/auth/announcements/{id}` | `isAuthenticated()` | F1：公告詳情 |
| `GET` | `/v1/auth/announcements/unread-count` | `isAuthenticated()` | F3：未讀計數 |
| `POST` | `/v1/auth/announcements/{id}/read` | `isAuthenticated()` | F3：標記單筆已讀 |
| `POST` | `/v1/auth/announcements/read-all` | `isAuthenticated()` | F3：全部標為已讀 |
| `GET` | `/v1/auth/announcements/admin` | `ANNOUNCEMENT_MANAGE` | F2：管理端列表 |
| `POST` | `/v1/auth/announcements` | `ANNOUNCEMENT_MANAGE` | F2：新增公告 |
| `PUT` | `/v1/auth/announcements/{id}` | `ANNOUNCEMENT_MANAGE` | F2：編輯公告 |
| `DELETE` | `/v1/auth/announcements/{id}` | `ANNOUNCEMENT_MANAGE` | F2：刪除公告 |
| `GET` | `/v1/auth/announcements/{id}/read-stats` | `ANNOUNCEMENT_MANAGE` | F4：已讀統計 |
| `GET` | `/v1/auth/announcements/{id}/unread-users` | `ANNOUNCEMENT_MANAGE` | F4：未讀名單 |
| `GET` | `/v1/auth/announcements/pinned` | `ANNOUNCEMENT_MANAGE` | F5：置頂列表 |
| `PUT` | `/v1/auth/announcements/pin-order` | `ANNOUNCEMENT_MANAGE` | F5：更新置頂順序 |
| `POST` | `/v1/auth/announcements/{id}/attachments` | `ANNOUNCEMENT_MANAGE` | F6：上傳附件 |
| `DELETE` | `/v1/auth/announcements/{id}/attachments/{attachId}` | `ANNOUNCEMENT_MANAGE` | F6：刪除附件 |

---

### 📌 使用建議

1. **章節 3（測試合約）最重要**：每一條標記 `[x]` 的 AC 都必須對應一個 `@Test` 方法（含 Mockito Mock / `ArgumentCaptor` 驗證）。
2. 將此文件提供給 AI Agent 時，附加以下指令：
   > "請依據此需求文件實作單元測試，確保所有標記為 `[x]` 的 Acceptance Criteria 都有對應的 `@Test` 方法覆蓋。使用 JUnit 5 + Mockito，測試類別放在 `com.taipei.iot.announcement` 對應的 test package 下。"
3. **測試命名強制規範**：所有 `@Test` 方法名稱**必須**以對應的 AC 編號開頭，格式為：

   ```
   [AC編號]_[方法名]_[情境]_[預期結果]
   ```

   | 範例類型 | 方法名稱 |
   |---|---|
   | ✅ 正確 | `AC_F3_7_markAsRead_crossTenant_throwsNotFound` |
   | ✅ 正確 | `AC_F2_6_create_deptAdmin_forcesDeptscopeAndOwnDept` |
   | ❌ 錯誤 | `testMarkAsRead_throwsException`（缺少 AC ID） |
   | ❌ 錯誤 | `markAllAsRead_noUnreadAnnouncements_ok`（缺少 AC ID） |

4. **可追溯性檢查清單**（Code Review 時人工核查）：
   - [ ] 每一條標記為 `[x]` 的 AC 在測試程式中是否都有對應的 `@Test` 方法（名稱含 AC ID）？
   - [ ] 是否有任何 `@Test` 方法沒有對應到任何 AC？（若有，可能為冗餘測試，應刪除或補充對應 AC）

5. **安全測試優先**：AC-F3-7（跨租戶防護）、AC-F2-15/16（DEPT_ADMIN 越權）應為第一批撰寫的測試。
