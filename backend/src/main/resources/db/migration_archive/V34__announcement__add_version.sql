-- =============================================
-- V34: announcements 加入 @Version 樂觀鎖欄位
-- 防止多管理員併發編輯互相覆蓋
-- =============================================

ALTER TABLE announcements
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
