-- ════════════════════════════════════════════════════════════════════════════
-- V37: 公告分類（category）
--
-- 新增 category 欄位以支援前台分流與顏色標籤呈現。
-- 允許值：GENERAL（一般，預設）、SYSTEM（系統）、POLICY（政策）、
--         EVENT（活動）、MAINTENANCE（維修）。
-- ════════════════════════════════════════════════════════════════════════════

ALTER TABLE announcements
    ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'GENERAL';

-- 既有資料回填為 GENERAL（DEFAULT 已處理新插入，這裡明確顯示意圖）
UPDATE announcements SET category = 'GENERAL' WHERE category IS NULL;

-- 前台 list 常用 (category, publish_at DESC) 過濾，加 partial index 加速
CREATE INDEX IF NOT EXISTS idx_announcements_category_publish
    ON announcements (category, publish_at DESC)
    WHERE status = 'PUBLISHED';
