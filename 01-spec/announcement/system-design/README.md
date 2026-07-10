## 公告（Announcement）功能模組說明

### 一、模組概述

公告模組提供完整的多租戶公告發布、閱讀追蹤、多語系支援與附件管理功能。涵蓋前台使用者瀏覽與已讀標記，以及後台管理端的完整 CRUD、發布狀態管理、置頂排序、已讀統計與未讀名單追蹤。

- **技術架構**：Spring Boot + JPA / Hibernate，PostgreSQL 資料庫，多租戶隔離（`tenant_id`），權限控制（Spring Security）。
- **核心實體**：`Announcement`（主表）、`AnnouncementTranslation`（多語言翻譯）、`AnnouncementAttachment`（附件）、`AnnouncementRead`（閱讀記錄）、`AnnouncementDept`（部門關聯）。

---

### 二、前台使用者功能（無需管理權限）

| 端點 | 方法 | 功能說明 |
|------|------|----------|
| `/v1/auth/announcements` | GET | 查詢可見公告列表（已發布、未過期、且受眾為「全體」或包含使用者部門），支援分類過濾與分頁。回傳內容依 `lang` 參數或 `Accept-Language` 標頭進行多語系呈現。 |
| `/v1/auth/announcements/{id}` | GET | 取得單筆公告詳情。若當前使用者具備 `ANNOUNCEMENT_MANAGE` 權限，則額外回傳 `editable` 欄位（標示可否編輯）。 |
| `/v1/auth/announcements/unread-count` | GET | 查詢未讀公告數量（用於首頁小紅點提示）。 |
| `/v1/auth/announcements/{id}/read` | POST | 將指定公告標記為已讀（冪等操作，重複請求不產生錯誤）。 |
| `/v1/auth/announcements/read-all` | POST | 將當前使用者所有可見且未讀的公告一次全部標記為已讀。 |
| `/v1/auth/announcements/{id}/attachments` | GET | 列出該公告的附件清單（僅限已發布且使用者有權限查看的公告）。 |
| `/v1/auth/announcements/{id}/attachments/{attachmentId}/download` | GET | 下載指定附件（會進行權限校驗，確保使用者可查看該公告）。 |
| `/v1/auth/announcements/attachments/config` | GET | 取得附件上傳政策（允許的副檔名白名單），供前端 UI 校驗提示。 |

---

### 三、管理後台功能（需 `ANNOUNCEMENT_MANAGE` 權限）

#### 3.1 公告管理

| 端點 | 方法 | 功能說明 |
|------|------|----------|
| `/v1/auth/announcements/admin` | GET | 管理端公告列表，支援 `statusFilter`（DRAFT / SCHEDULED / PUBLISHED / EXPIRED / ALL）、`category`、`keyword`（標題或純文字內容）過濾，分頁回傳。系統管理員可查看全部；部門管理員僅能看到自己建立或發送範圍包含其部門的公告。 |
| `/v1/auth/announcements` | POST | 新增公告（需傳入標題、內容、發布/過期時間、範圍、分類、是否置頂、是否需確認等完整資訊）。 |
| `/v1/auth/announcements/{id}` | PUT | 編輯公告（帶入 `version` 欄位進行樂觀鎖並發控制，衝突時回傳 409）。 |
| `/v1/auth/announcements/{id}` | DELETE | 刪除公告（串聯刪除關聯的部門關係與閱讀記錄，由資料庫 ON DELETE CASCADE 處理）。 |

#### 3.2 置頂排序管理

| 端點 | 方法 | 功能說明 |
|------|------|----------|
| `/v1/auth/announcements/pinned` | GET | 列出目前所有置頂公告（依 `pin_order` 升序排列），供前端拖曳排序 UI 使用。系統管理員看全部；部門管理員只看自己可管理的置頂公告。 |
| `/v1/auth/announcements/pin-order` | PUT | 接收前端拖曳後的公告 ID 順序清單，批次更新各公告的 `pin_order`（覆蓋式更新）。 |

#### 3.3 已讀統計與未讀名單

| 端點 | 方法 | 功能說明 |
|------|------|----------|
| `/v1/auth/announcements/{id}/read-stats` | GET | 取得指定公告的已讀統計：受眾總人數、已讀人數、未讀人數、已讀比例（排除已離職或停用使用者，確保比例真實）。 |
| `/v1/auth/announcements/{id}/unread-users` | GET | 分頁查詢未讀使用者清單，支援 `keyword` 模糊搜尋（名稱或 email）。管理員可據此進行催讀通知。 |

#### 3.4 附件管理（管理端專屬）

| 端點 | 方法 | 功能說明 |
|------|------|----------|
| `/v1/auth/announcements/{id}/attachments` | POST | 上傳附件（需 `multipart/form-data`），執行副檔名、Magic Bytes、檔案大小等完整性校驗，並儲存於 `./uploads/announcement/{id}/`。 |
| `/v1/auth/announcements/{id}/attachments/{attachmentId}` | DELETE | 刪除指定附件（同時刪除實體檔案）。 |

---

### 四、跨功能機制

- **多語系支援**  
  公告標題與內容支援多語言翻譯（儲存於 `announcement_translations` 子表）。語言解析順序：查詢參數 `?lang=` → `Accept-Language` 標頭 → Service 層預設語系（fallback）。Service 層會驗證語言代碼是否在允許清單內。

- **租戶隔離**  
  所有查詢與寫入操作皆自動套用 `tenant_id` 過濾（Hibernate `@Filter` 或手動參數傳遞），確保多租戶資料安全。

- **權限控制**  
  一般使用者僅能讀取與標記已讀；管理操作需 `ANNOUNCEMENT_MANAGE` 權限。後台列表與置頂排序會根據使用者角色（ADMIN / DEPT_ADMIN）自動篩選可管理的資料範圍。

- **操作審計**  
  新增、編輯、刪除、置頂順序變更等操作皆標記 `@AuditEvent`，會自動記錄操作者、時間與動作類型。

- **樂觀鎖並發控制**  
  編輯公告時須提供 `version` 欄位，防止同時編輯導致資料覆蓋。

---

### 五、附件安全性設計

- **檔案驗證**：副檔名、Magic Bytes（檔案頭）、檔案大小均透過 `FileValidationService` 進行嚴格校驗。
- **下載防護**：Response 標頭加入 `X-Content-Type-Options: nosniff`，避免瀏覽器 MIME 嗅探攻擊；檔案名稱使用 `filename*=UTF-8''` 編碼，支援中文檔名。
- **儲存路徑**：實體檔案儲存於應用伺服器 `./uploads/announcement/{announcementId}/`，資料庫僅記錄相對路徑與中繼資料。

---

### 六、技術特點總結

- **資料庫查詢最佳化**：前台列表與統計使用原生 SQL 或 JPQL 子查詢（`EXISTS`）避免 N+1 問題。
- **冪等設計**：標記已讀與置頂順序更新均為冪等操作，避免重複請求產生副作用。
- **分頁統一**：所有列表查詢皆採用 `PageQuery` 封裝分頁參數，並透過 `@PaginationParams` 自動解析。
- **錯誤處理**：樂觀鎖衝突回傳 409，權限不足回傳 403，業務異常統一由全域例外處理器轉為標準格式。

---

此模組為企業內部公告系統的核心，支援從發布、傳播、追蹤到報表的一條龍服務，適合多租戶、多語言、多部門的複雜組織架構。