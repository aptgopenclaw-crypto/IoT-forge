-- ============================================================
-- Flyway V16: 新增 users 軟刪除欄位
-- ============================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
