-- 為 countUnread() 查詢加 partial index
-- 查詢條件：status='PUBLISHED' AND publish_at <= now AND (expire_at IS NULL OR expire_at > now)
-- partial index 只索引 status='PUBLISHED' 的 row，大幅縮小索引大小並提升計數效能
-- scope 放入鍵集合用以支援 (scope='ALL' OR EXISTS dept) 的分支判斷
-- 註：announcement_reads(announcement_id, user_id) 已由 V29 的 UNIQUE 約束建立隱式索引，無需重複
CREATE INDEX IF NOT EXISTS idx_announcements_published_active
    ON announcements (tenant_id, scope, publish_at, expire_at)
    WHERE status = 'PUBLISHED';
