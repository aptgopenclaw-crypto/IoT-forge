-- =============================================
-- V29_1: Announcement seed data (test / demo)
-- =============================================
-- 依賴 V29 建表完成後執行
-- tenant_id = 'TENANT_A' (台北市路燈)
-- dept_id: 5=公燈處, 6=第一分隊(北區), 7=第二分隊(南區), 8=工程股, 9=行政股, 10=智慧路燈管理中心

-- ① 系統維護通知（全體、置頂、已發佈）
INSERT INTO announcements (tenant_id, title, content, status, scope, pinned, publish_at, expire_at, created_by, created_by_name, created_at, updated_at)
VALUES (
    'TENANT_A',
    '系統維護通知：4/25（六）22:00 ~ 4/26（日）02:00',
    '各位同仁好：

為提升系統效能與安全性，路燈管理平台將於 115 年 4 月 25 日（星期六）22:00 至 4 月 26 日（星期日）02:00 進行例行性系統維護作業。

維護期間系統將暫停服務，届時將無法登入或使用各項功能。請各單位提前完成當日報修案件登錄作業。

如有緊急路燈故障通報需求，維護期間請撥打 1999 市民專線。

造成不便敬請見諒。

公燈處 系統管理員',
    'PUBLISHED', 'ALL', true,
    '2026-04-20 09:00:00',
    '2026-05-20 09:00:00',
    'u-tpe-admin',
    '系統管理員',
    '2026-04-20 09:00:00',
    '2026-04-20 09:00:00'
);

-- ② 路燈管理系統功能更新公告（全體、已發佈）
INSERT INTO announcements (tenant_id, title, content, status, scope, pinned, publish_at, expire_at, created_by, created_by_name, created_at, updated_at)
VALUES (
    'TENANT_A',
    '路燈管理平台 v2.1 功能更新公告',
    '各位同仁好：

路燈管理平台已於 4/18 完成版本更新（v2.1），本次更新內容如下：

1. 新增「閒置逾時自動登出」功能，系統閒置超過設定時間將自動登出，提升資安防護。
2. 新增「系統公告」功能，管理員可發佈公告通知所有使用者或指定部門。
3. 優化地圖頁面載入效能，改善大量路燈標記點的繪製速度。
4. 修正部分瀏覽器下匯出 Excel 檔案名稱亂碼問題。

如使用上有任何問題，請聯繫系統管理員。

公燈處 系統管理員',
    'PUBLISHED', 'ALL', false,
    '2026-04-18 14:00:00',
    '2026-05-18 14:00:00',
    'u-tpe-admin',
    '系統管理員',
    '2026-04-18 14:00:00',
    '2026-04-18 14:00:00'
);

-- ③ 北區巡檢路線調整（部門公告 → 第一分隊、已發佈）
INSERT INTO announcements (tenant_id, title, content, status, scope, pinned, publish_at, expire_at, created_by, created_by_name, created_at, updated_at)
VALUES (
    'TENANT_A',
    '五月份北區夜間巡檢路線調整通知',
    '第一分隊同仁：

因應中山北路三段至五段路面重鋪工程（施工期間 5/1 ~ 5/31），五月份夜間巡檢路線調整如下：

原路線 A（中山北路段）：暫停巡檢，改由施工單位負責臨時照明。
原路線 B（承德路段）：巡檢範圍向東擴大，涵蓋中山北路周邊替代照明區域。
原路線 C（民權西路段）：維持不變。

調整後巡檢路線圖已上傳至共用資料夾，請各位同仁自行下載查閱。如有疑問請洽分隊長。

北區分隊長 李明華',
    'PUBLISHED', 'DEPT', false,
    '2026-04-22 08:30:00',
    '2026-05-22 08:30:00',
    'u-squad1-mgr',
    '北區分隊長 李明華',
    '2026-04-22 08:30:00',
    '2026-04-22 08:30:00'
);

-- ④ 南區設備更換通知（部門公告 → 第二分隊、已發佈）
INSERT INTO announcements (tenant_id, title, content, status, scope, pinned, publish_at, expire_at, created_by, created_by_name, created_at, updated_at)
VALUES (
    'TENANT_A',
    '南區 LED 路燈燈具批次更換作業通知',
    '第二分隊同仁：

配合 115 年度節能改善計畫，南區轄內以下路段將進行 LED 燈具批次更換作業：

- 羅斯福路四段至六段（預計 4/28 ~ 5/5）
- 基隆路三段（預計 5/6 ~ 5/10）
- 辛亥路一段（預計 5/12 ~ 5/16）

作業期間請配合廠商施工時程，協助現場交通維護及舊燈具清點作業。每日作業完成後請於系統登錄更換數量。

詳細施工排程表請參閱附件。

南區分隊長 陳國強',
    'PUBLISHED', 'DEPT', false,
    '2026-04-21 10:00:00',
    '2026-05-21 10:00:00',
    'u-squad2-mgr',
    '南區分隊長 陳國強',
    '2026-04-21 10:00:00',
    '2026-04-21 10:00:00'
);

-- ⑤ 排程發佈（未來時間、全體、已發佈但尚未到 publish_at）
INSERT INTO announcements (tenant_id, title, content, status, scope, pinned, publish_at, expire_at, created_by, created_by_name, created_at, updated_at)
VALUES (
    'TENANT_A',
    '115 年度路燈節能改善計畫全面啟動',
    '各位同仁好：

依據臺北市政府 115 年度節能減碳推動方案，公燈處將自 5 月 1 日起全面啟動路燈節能改善計畫，重點工作項目如下：

一、傳統水銀燈汰換為 LED 燈具，預計年度更換 3,000 盞。
二、導入智慧調光控制系統，於深夜時段（00:00~05:00）降低亮度至 70%。
三、建置路燈用電監測平台，即時掌握各區域用電狀況。

各分隊及工程股請依分工表展開前置作業，5 月份起按月回報進度。

詳細計畫書及分工表將另行發送。

公燈處 系統管理員',
    'PUBLISHED', 'ALL', true,
    '2026-05-01 00:00:00',
    '2026-05-31 00:00:00',
    'u-tpe-admin',
    '系統管理員',
    '2026-04-22 16:00:00',
    '2026-04-22 16:00:00'
);

-- ⑥ 草稿（尚未發佈）
INSERT INTO announcements (tenant_id, title, content, status, scope, pinned, publish_at, expire_at, created_by, created_by_name, created_at, updated_at)
VALUES (
    'TENANT_A',
    '公燈處 115 年度上半年考核作業說明',
    '各位同仁好：

依據本府人事處函示，115 年度上半年考核作業即將展開，相關期程如下：

一、自評期間：6/1 ~ 6/10
二、初核期間：6/11 ~ 6/20
三、考核會議：6/25

請各單位主管提醒所屬同仁於期限內完成自評作業。

（草稿，待人事確認後發佈）',
    'DRAFT', 'ALL', false,
    NULL,
    NULL,
    'u-tpe-admin',
    '系統管理員',
    '2026-04-22 11:00:00',
    '2026-04-22 11:00:00'
);

-- ⑦ 已過期公告（expire_at 已過）
INSERT INTO announcements (tenant_id, title, content, status, scope, pinned, publish_at, expire_at, created_by, created_by_name, created_at, updated_at)
VALUES (
    'TENANT_A',
    '三月份路燈故障報修統計報表已上傳',
    '各位同仁好：

115 年 3 月份路燈故障報修統計報表已彙整完成並上傳至系統，各單位可至「報表查詢」功能下載查閱。

本月統計摘要：
- 報修案件總數：287 件
- 已完修：271 件（完修率 94.4%）
- 處理中：16 件
- 平均修復時間：2.3 個工作天

各分隊完修率均達 90% 以上，感謝各位同仁辛勞。未完修案件請持續追蹤處理。

公燈處 系統管理員',
    'PUBLISHED', 'ALL', false,
    '2026-04-01 09:00:00',
    '2026-04-15 09:00:00',
    'u-tpe-admin',
    '系統管理員',
    '2026-04-01 09:00:00',
    '2026-04-01 09:00:00'
);

-- ⑧ 永不過期的重要政策公告（expire_at = NULL）
INSERT INTO announcements (tenant_id, title, content, status, scope, pinned, publish_at, expire_at, created_by, created_by_name, created_at, updated_at)
VALUES (
    'TENANT_A',
    '路燈管理平台資訊安全注意事項',
    '各位同仁好：

為落實資訊安全管理政策，使用路燈管理平台時請遵守以下規定：

一、帳號密碼管理
  - 密碼長度須 12 碼以上，包含大小寫英文、數字及特殊符號。
  - 禁止與他人共用帳號或將密碼告知他人。
  - 系統將定期要求更換密碼。

二、使用規範
  - 離開座位時請登出系統或鎖定電腦螢幕。
  - 系統閒置超過 15 分鐘將自動登出。
  - 禁止於公用電腦上勾選「記住密碼」。

三、資料保護
  - 匯出之報表資料應妥善保管，不得任意轉傳至外部。
  - 發現帳號異常或疑似遭盜用，請立即通報系統管理員。

以上規定即日起生效，請各單位配合辦理。

公燈處 系統管理員',
    'PUBLISHED', 'ALL', true,
    '2026-03-01 09:00:00',
    NULL,
    'u-tpe-admin',
    '系統管理員',
    '2026-03-01 09:00:00',
    '2026-03-01 09:00:00'
);

-- =============================================
-- announcement_depts: DEPT-scoped 公告的目標部門
-- =============================================
-- ③ 北區巡檢路線調整 → 第一分隊 (dept_id=6)
INSERT INTO announcement_depts (announcement_id, dept_id)
SELECT a.id, 6 FROM announcements a WHERE a.title = '五月份北區夜間巡檢路線調整通知' AND a.tenant_id = 'TENANT_A';

-- ④ 南區 LED 燈具更換 → 第二分隊 (dept_id=7)
INSERT INTO announcement_depts (announcement_id, dept_id)
SELECT a.id, 7 FROM announcements a WHERE a.title = '南區 LED 路燈燈具批次更換作業通知' AND a.tenant_id = 'TENANT_A';
