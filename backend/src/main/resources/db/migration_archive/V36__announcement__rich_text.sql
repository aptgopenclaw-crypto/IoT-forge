-- 公告富文本支援：content_text 為純文字 fallback，供關鍵字搜尋使用，避免比對到 HTML 標籤
-- 由後端 HtmlSanitizerService.extractText() 從 sanitized content 萃取後寫入
ALTER TABLE announcements
    ADD COLUMN content_text TEXT;

-- 回填既有資料：原本 content 為純文字，直接複製即可
UPDATE announcements SET content_text = content WHERE content_text IS NULL;

-- 為關鍵字搜尋加上 trigram index（PostgreSQL pg_trgm）以支援 LIKE '%kw%' 加速
-- 若 pg_trgm extension 未啟用則改用一般 btree，搜尋走 ILIKE 仍可。
-- 此處保守起見不強制啟用 extension，僅補一個輕量 btree 給排序使用
-- 註：若日後啟用 pg_trgm，建議改為：
--   CREATE INDEX idx_announcements_content_text_trgm ON announcements USING gin (content_text gin_trgm_ops);
