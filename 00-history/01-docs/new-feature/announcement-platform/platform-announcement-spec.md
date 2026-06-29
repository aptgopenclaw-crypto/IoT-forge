# 平台公告（Platform Announcement）系統分析與設計

> 版本：v1.0 · 2026-06-01  
> 範圍：後端 + 資料庫 + REST API + 前端管理頁  
> 適用模組：`platform.announcement`  
> 前置條件：租戶公告模組已完成（V29–V41）、Platform/Tenant Separation（ADR-007）已上線

---

## 1. 目標與設計原則

讓 **super_admin** 在平台管理介面發布跨場域公告（如系統維護通知），所有場域使用者皆可看到。

### 1.1 核心設計決策

| # | 決策 | 說明 |
|---|------|------|
| D-1 | **獨立表（方案 B）** | 使用 `platform_announcements` 新表，不修改現有 `announcements` 的 tenant scoping 機制，避免影響已穩定的租戶公告系統 |
| D-2 | **簡化模型** | 平台公告不需要部門範圍、已讀追蹤、置頂、附件、多語系等租戶公告的進階功能，符合 YAGNI 原則 |
| D-3 | **Scope 分離** | 管理端走 `/v1/platform/` 路徑（PLATFORM scope），租戶端唯讀走 `/v1/auth/` 路徑（TENANT scope），遵循 ADR-007 |
| D-4 | **HTML 內容安全** | 內容經 `HtmlSanitizerService` 過濾 XSS，純文字版存入 `content_text` 供關鍵字搜尋 |

### 1.2 方案比較（A: 共用表 vs B: 獨立表）

| 面向 | A: 重用 `announcements` 表 | B: 新建 `platform_announcements` 表 |
|------|---------------------------|--------------------------------------|
| 附件/翻譯/已讀追蹤/置頂 | 立即可用 | 需要時再加 |
| 程式碼量 | 較少（但修改散佈多處） | 較多（但集中獨立） |
| 對現有功能的風險 | **中高** — 需改 tenant filter、entity listener、每支現有 query | **零** — 完全不動現有程式碼 |
| Query 複雜度 | 每支 query 需加 `OR tenant_id IS NULL` | 前台展示時需合併兩個來源 |
| 未來維護 | 耦合 — 改租戶公告邏輯可能影響平台公告 | 獨立演進 |

**結論：選 B。** 平台公告本質簡單、數量少，風險隔離優先。

### 1.3 非目標（Out of Scope）

- 平台公告的已讀追蹤（可未來擴充）
- 平台公告的附件上傳（可未來擴充）
- 平台公告的多語系翻譯（可未來擴充）
- 租戶端公告欄（`AnnouncementListView`）整合顯示平台公告（後續迭代）

---

## 2. 資料庫設計

### 2.1 新增表：`platform_announcements`

```sql
CREATE TABLE platform_announcements (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title            VARCHAR(200)  NOT NULL,
    content          TEXT          NOT NULL,       -- sanitized HTML
    content_text     TEXT,                         -- 純文字版（供搜尋）
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',   -- DRAFT / PUBLISHED
    category         VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',  -- SYSTEM / MAINTENANCE / GENERAL
    publish_at       TIMESTAMP,                    -- 排程發佈；null = 立即
    expire_at        TIMESTAMP,                    -- 失效時間；null = 永不過期
    created_by       VARCHAR(50),
    created_by_name  VARCHAR(100),
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP
);
```

**與 `announcements` 表的差異：**
- 無 `tenant_id` — 跨場域
- 無 `scope` / `pinned` / `pin_order` / `requires_ack` / `version` — 簡化
- 無關聯表（`announcement_depts`、`announcement_reads`、`announcement_translations`、`announcement_attachments`）

### 2.2 權限

| permission_id | code | name | group_name |
|---------------|------|------|------------|
| `PERM_PLATFORM_ANNOUNCEMENT_MANAGE` | `PLATFORM_ANNOUNCEMENT_MANAGE` | 管理平台公告 | 平台管理 |

自動綁定至 `ROLE_SUPER_ADMIN`。

### 2.3 選單

| menu_id | parent_id | name | route_path | scope |
|---------|-----------|------|-----------|-------|
| 105 | 102（系統管理） | 公告管理 | `/platform/announcements` | PLATFORM |

Platform sidebar 結構更新為：
```
場域管理 (100, sort=10)
  └─ 場域管理 (101)
系統管理 (102, sort=20)
  ├─ 選單管理 (103)
  ├─ 系統設定 (104)
  └─ 公告管理 (105, sort=30)  ← NEW
公告欄 (34, sort=90)
```

### 2.4 Migration

檔案：`V68__platform__announcement.sql`

---

## 3. REST API 設計

### 3.1 管理端（PLATFORM scope）

Base path: `/v1/platform/announcements`  
權限：`PLATFORM_ANNOUNCEMENT_MANAGE`

| Method | Path | 說明 |
|--------|------|------|
| `GET` | `/` | 列表（分頁 + statusFilter / category / keyword） |
| `GET` | `/{id}` | 單筆詳情 |
| `POST` | `/` | 新增 |
| `PUT` | `/{id}` | 編輯 |
| `DELETE` | `/{id}` | 刪除 |

### 3.2 租戶端唯讀（TENANT / IMPERSONATION scope）

Base path: `/v1/auth/platform-announcements`  
權限：`isAuthenticated()`

| Method | Path | 說明 |
|--------|------|------|
| `GET` | `/` | 已發佈且未過期的平台公告（分頁 + category） |

### 3.3 Request / Response DTO

**PlatformAnnouncementRequest**
```json
{
  "title": "系統維護通知",
  "content": "<p>本系統將於 2026-06-01 凌晨 2:00 進行維護。</p>",
  "status": "PUBLISHED",
  "category": "MAINTENANCE",
  "publishAt": "2026-06-01T02:00:00",
  "expireAt": "2026-06-02T06:00:00"
}
```

**PlatformAnnouncementResponse**
```json
{
  "id": 1,
  "title": "系統維護通知",
  "content": "<p>本系統將於 2026-06-01 凌晨 2:00 進行維護。</p>",
  "status": "PUBLISHED",
  "category": "MAINTENANCE",
  "publishAt": "2026-06-01T02:00:00",
  "expireAt": "2026-06-02T06:00:00",
  "createdBy": "user-001",
  "createdByName": "系統管理員",
  "createdAt": "2026-06-01T00:00:00",
  "updatedAt": "2026-06-01T00:00:00"
}
```

---

## 4. 後端架構

### 4.1 Package 結構

```
com.taipei.iot.platform.announcement/
├── controller/
│   ├── PlatformAnnouncementController.java      ← /v1/platform/announcements
│   └── PlatformAnnouncementReadController.java   ← /v1/auth/platform-announcements
├── dto/
│   ├── PlatformAnnouncementRequest.java
│   └── PlatformAnnouncementResponse.java
├── entity/
│   └── PlatformAnnouncement.java
├── repository/
│   └── PlatformAnnouncementRepository.java
└── service/
    └── PlatformAnnouncementService.java
```

### 4.2 核心邏輯

- **Entity**: 不實作 `TenantAware`，無 Hibernate `@Filter`，無 `TenantEntityListener`
- **Repository**: 繼承 `JpaRepository`（非 `TenantScopedRepository`），使用 JPQL 自訂查詢
- **Service**: 透過 `HtmlSanitizerService` 清洗 HTML + 萃取純文字；`PUBLISHED` 狀態且 `publishAt` 為 null 時自動填入 `now()`
- **Controller**: class-level `@PreAuthorize("hasAuthority('PLATFORM_ANNOUNCEMENT_MANAGE')")`；`@AuditEvent` 記錄 CUD 操作

### 4.3 Audit 事件

| AuditEventType | 觸發時機 |
|----------------|----------|
| `CREATE_PLATFORM_ANNOUNCEMENT` | 新增平台公告 |
| `UPDATE_PLATFORM_ANNOUNCEMENT` | 編輯平台公告 |
| `DELETE_PLATFORM_ANNOUNCEMENT` | 刪除平台公告 |

---

## 5. 前端架構

### 5.1 新增檔案

| 檔案 | 說明 |
|------|------|
| `types/platformAnnouncement.ts` | 型別定義 |
| `api/platformAnnouncement/index.ts` | API client（管理端 + 租戶端唯讀） |
| `views/platform/PlatformAnnouncementManageView.vue` | 管理頁面 |

### 5.2 路由

```typescript
// router/index.ts — platformChildren
{
  path: '/platform/announcements',
  name: 'PlatformAnnouncementManage',
  component: () => import('@/views/platform/PlatformAnnouncementManageView.vue'),
  meta: { requiresScope: 'PLATFORM' },
}
```

### 5.3 管理頁面功能

- **列表**: 表格顯示標題、分類（tag）、狀態（tag）、發佈/失效時間、建立者、操作按鈕
- **篩選**: 狀態（全部/草稿/已發佈/已過期）、分類、關鍵字搜尋
- **新增/編輯**: Dialog 表單 — 標題、內容（textarea）、分類、狀態、發佈時間、失效時間
- **刪除**: 確認對話框
- **分頁**: 支援頁碼 + 每頁筆數切換
- **Dark theme**: 配合 PlatformLayout 的深色主題 CSS variables

### 5.4 分類標籤

| category | 中文 | tag type |
|----------|------|----------|
| `SYSTEM` | 系統 | warning |
| `MAINTENANCE` | 維護 | danger |
| `GENERAL` | 一般 | info |

---

## 6. 安全性

| 面向 | 措施 |
|------|------|
| 路徑保護 | `ScopeEnforcementFilter` 確保 `/v1/platform/**` 僅 PLATFORM token 可存取 |
| 權限控制 | `@PreAuthorize("hasAuthority('PLATFORM_ANNOUNCEMENT_MANAGE')")` |
| XSS 防護 | `HtmlSanitizerService` 白名單過濾 HTML，剝離 `<script>`、事件屬性等 |
| 輸入驗證 | `@NotBlank`、`@Size(max=200)`、`@Pattern` 約束 DTO 欄位 |
| 租戶端唯讀 | `PlatformAnnouncementReadController` 只暴露 `GET`，`@PreAuthorize("isAuthenticated()")` |

---

## 7. 後續擴充方向

| 項目 | 優先級 | 說明 |
|------|--------|------|
| 租戶端公告欄整合 | P1 | 修改 `AnnouncementListView` / `announcementStore` 合併顯示平台公告 |
| `NotificationBell` 整合 | P2 | 平台公告出現在通知鈴鐺的公告 tab |
| 富文本編輯器 | P2 | 將 textarea 替換為租戶公告相同的 rich text editor |
| 已讀追蹤 | P3 | 新增 `platform_announcement_reads` 表 |
| 附件上傳 | P3 | 複用 `AnnouncementAttachmentService` 模式 |
| 多語系翻譯 | P4 | 複用 `announcement_translations` 模式 |
