這份文件已經是 Markdown 格式，但有些部分（例如表格）可能需要進一步調整以符合 Markdown 的語法。以下是經過調整的 Markdown 格式版本：

```markdown
# Announcement 功能模組分析

這是一個台北市物聯網（路燈管理）平台中的「系統公告」功能模組，採用前後端分離架構（Spring Boot 3 後端 + Vue 3 前端），提供管理員發佈公告、一般使用者瀏覽公告與已讀追蹤的完整功能。

---

## 整體架構

```
backend/src/main/java/com/taipei/iot/announcement/
├── controller/AnnouncementController.java   # REST API 層
├── service/
│   ├── AnnouncementService.java             # 公告 CRUD 核心邏輯
│   └── AnnouncementReadService.java         # 已讀/未讀邏輯
├── repository/
│   ├── AnnouncementRepository.java          # 公告查詢（含複雜 JPQL）
│   ├── AnnouncementDeptRepository.java      # 公告-部門關聯表
│   └── AnnouncementReadRepository.java      # 已讀記錄表
├── entity/
│   ├── Announcement.java                    # 公告主實體
│   ├── AnnouncementDept.java / AnnouncementDeptId.java  # 多對多關聯
│   ├── AnnouncementRead.java                # 已讀記錄
│   ├── AnnouncementStatus.java              # DRAFT / PUBLISHED 枚舉
│   └── AnnouncementScope.java               # ALL / DEPT 枚舉
└── dto/
    ├── AnnouncementRequest.java             # 新增/編輯請求
    ├── AnnouncementResponse.java            # 回應（含 isRead、editable）
    └── UnreadCountResponse.java             # 未讀數量
```

---

## 資料庫設計（3 張表）

| 表名                | 用途           | 關鍵欄位                                                                 |
|---------------------|----------------|--------------------------------------------------------------------------|
| announcements       | 公告主表      | tenant_id, title, content, status(DRAFT/PUBLISHED), scope(ALL/DEPT), pinned, publish_at, expire_at |
| announcement_depts  | 公告-部門多對多 | announcement_id + dept_id 複合主鍵，CASCADE 刪除                          |
| announcement_reads  | 已讀追蹤      | announcement_id + user_id UNIQUE，記錄 read_at                            |

資料庫遷移（Flyway V29）同時建立選單（menu_id=33 公告管理、menu_id=34 公告欄）與權限（ANNOUNCEMENT_VIEW、ANNOUNCEMENT_MANAGE），並將權限綁定到 ADMIN 和 DEPT_ADMIN 角色。

---

## 核心業務邏輯

### 1. 公告可見性規則（AnnouncementRepository.findVisibleAnnouncements）

一則公告對使用者可見需同時滿足：
- `status = 'PUBLISHED'`
- `publish_at <= now`（已到發佈時間）
- `expire_at IS NULL OR expire_at > now`（尚未過期）
- 受眾符合：`scope = 'ALL'` 或 使用者所屬部門在 `announcement_depts` 中

### 2. 角色權限模型

| 角色               | 前台          | 管理頁面                                                             |
|--------------------|---------------|----------------------------------------------------------------------|
| 一般使用者         | 看可見公告    | 無權限（403）                                                       |
| DEPT_ADMIN         | 看可見公告    | 只能看/編輯/刪除自己建立的公告，強制 `scope=DEPT`、目標為自己部門    |
| ADMIN / SUPER_ADMIN | 看可見公告   | 看全部公告，可自由選擇 `ALL/DEPT`，可指定多個目標部門                |

### 3. 狀態過濾邏輯（管理頁面的 statusFilter）

- **ALL** — 全部
- **DRAFT** — 草稿
- **PUBLISHED** — 已發佈且未過期
- **EXPIRED** — 已發佈但已過期（`expire_at < now`）

這三種有效狀態的判斷寫在 JPQL 的 CASE 邏輯中，而非資料庫欄位。

### 4. 已讀追蹤

- `markAsRead(id)`: `INSERT ... ON CONFLICT DO NOTHING`（幂等 upsert）
- `markAllAsRead()`: 一次性將使用者所有可見但未讀的公告批量 INSERT，使用與可見性相同的條件查詢
- 未讀數量：`COUNT` 符合可見性條件 + 不存在於 `announcement_reads` 的公告

---

## API 端點摘要

- `GET    /v1/auth/announcements`             # 列表（`?admin=true` 管理頁面 / `false` 前台）
- `GET    /v1/auth/announcements/{id}`        # 單筆詳情
- `GET    /v1/auth/announcements/unread-count` # 未讀數量
- `POST   /v1/auth/announcements/{id}/read`   # 標記單筆已讀
- `POST   /v1/auth/announcements/read-all`    # 全部已讀
- `POST   /v1/auth/announcements`             # 新增（需 `ADMIN/DEPT_ADMIN/ANNOUNCEMENT_MANAGE` 權限）
- `PUT    /v1/auth/announcements/{id}`        # 編輯
- `DELETE /v1/auth/announcements/{id}`        # 刪除

增刪改操作皆使用 `@AuditEvent` 註解記錄審計日誌。

---

## 前端設計

### 前台 - 公告欄 (AnnouncementListView.vue)
- 卡片式列表，每張卡顯示標題、發佈者、日期、範圍標籤
- 未讀公告標題加粗 + 藍色圓點指示器
- 點擊展開內文，同時觸發標記已讀
- 置頂公告顯示 📌 圖示
- 支援分頁

### 管理頁面 (AnnouncementManagementView.vue)
- 表格列表 + 狀態篩選（全部/草稿/已發佈/已過期）+ 標題關鍵字搜尋
- 新增/編輯 Dialog，含：
  - 標題（max 200 字）、內文（textarea）
  - 範圍選擇：`ADMIN` 可選 `ALL/DEPT` + 多選部門，`DEPT_ADMIN` 鎖定自己部門
  - 狀態：草稿 / 發佈
  - 發佈時間：立即 / 排程（datetime picker）
  - 置頂 checkbox
  - 過期時間：預設 `publish_at + 30 天`，可手動修改，支援「永不過期」
- 僅 `editable=true` 的列顯示編輯/刪除按鈕

### Pinia Store (announcementStore.ts)
- 管理未讀數量與 popover 列表
- 每 5 分鐘輪詢未讀數量
- 樂觀更新：標記已讀時直接扣減本地 `unreadCount`

---

## 種子資料

`V29_1__announcement__seed_data.sql` 內含 8 筆測試資料，涵蓋所有案例：

| #   | 標題               | 狀態       | 範圍          | 特點                              |
|-----|--------------------|------------|---------------|-----------------------------------|
| 1   | 系統維護通知       | PUBLISHED  | ALL           | 置頂、有期限                     |
| 2   | v2.1 功能更新公告  | PUBLISHED  | ALL           | 一般公告                         |
| 3   | 北區巡檢路線調整   | PUBLISHED  | DEPT→第一分隊 | 部門公告                         |
| 4   | 南區 LED 燈具更換  | PUBLISHED  | DEPT→第二分隊 | 部門公告                         |
| 5   | 節能改善計畫       | PUBLISHED  | ALL           | 排程發佈（未來 `publish_at`）    |
| 6   | 上半年考核作業     | DRAFT      | ALL           | 草稿（無 `publish_at`）          |
| 7   | 三月份統計報表     | PUBLISHED  | ALL           | 已過期（`expire_at` 在過去）     |
| 8   | 資訊安全注意事項   | PUBLISHED  | ALL           | 永不過期（`expire_at=NULL`）+ 置頂 |

---

## 設計亮點

- **多租戶隔離**：`Announcement` 實作 `TenantAware`，透過 Hibernate `@Filter` 自動過濾 `tenant_id`
- **JPQL 複雜查詢**：可見性、管理頁篩選、未讀計數等邏輯都在 Repository 層用一條 SQL 完成，避免 N+1 查詢
- **批次載入優化**：`toResponseList` 使用 `findByAnnouncementIdIn` 一次載入所有公告的部門關聯和已讀狀態，再在記憶體中組合
- **幂等已讀**：`ON CONFLICT DO NOTHING` 確保重複標記已讀不會拋例外
- **DEP_ADMIN 權限收斂**：後端強制部門管理員只能發 `DEPT` 公告給自己部門，且只能編輯/刪除自己的公告，前端僅做 UI 輔助
```

這是調整後的 Markdown 格式版本，表格和代碼區塊已經正確轉換。如果需要進一步修改，請告訴我！