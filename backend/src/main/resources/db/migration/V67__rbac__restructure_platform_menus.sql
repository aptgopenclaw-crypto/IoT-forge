-- =============================================================================
-- V67: 重構平台選單結構 — 場域管理 + 系統管理 各為頂層
--
-- 使用者預期 sidebar 顯示：
--   1. 場域管理 > 場域管理
--   2. 系統管理 > 選單管理, 系統設定
--   3. 公告欄
--
-- 變更：
--   1. menu_id=100：'平台管理' → '場域管理', icon → 'building'
--   2. menu_id=101：'租戶管理' → '場域管理', route_name → 'PlatformTenantManage'
--      (與前端靜態路由名稱對齊)
--   3. menu_id=102：parent_id 100 → NULL (升為頂層目錄)
--   4. menu_id=37：visible=false (認證方式設定需 tenant context，暫隱藏)
--   5. sort_order 調整確保順序：場域管理(10), 系統管理(20), 公告欄(90)
-- =============================================================================

-- ── 1. 場域管理 DIRECTORY (was 平台管理) ────────────────────────────────────
UPDATE menus
   SET name = '場域管理',
       icon = 'building',
       sort_order = 10
 WHERE menu_id = 100;

-- ── 2. 場域管理 PAGE (was 租戶管理) ─────────────────────────────────────────
UPDATE menus
   SET name = '場域管理',
       route_name = 'PlatformTenantManage'
 WHERE menu_id = 101;

-- ── 3. 系統管理升為頂層 ─────────────────────────────────────────────────────
UPDATE menus
   SET parent_id = NULL,
       sort_order = 20
 WHERE menu_id = 102;

-- ── 4. 隱藏認證方式設定（需 tenant context，不適合 platform sidebar） ────────
UPDATE menus
   SET visible = false
 WHERE menu_id = 37;

-- ── 5. 公告欄排序放最後 ─────────────────────────────────────────────────────
UPDATE menus
   SET sort_order = 90
 WHERE menu_id = 34;
