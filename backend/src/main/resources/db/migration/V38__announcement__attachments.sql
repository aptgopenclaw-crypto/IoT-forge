-- ════════════════════════════════════════════════════════════════════════════
-- V38: 公告附件（announcement_attachments）
--
-- 為公告提供檔案附件能力（政策文件、活動報名表等）。
-- 沿用 common.FileStorageService（./uploads/announcement/{announcementId}/...）。
-- 檔案校驗（副檔名白名單 / Magic bytes / 大小）由 FileValidationService 處理。
--
-- 設計重點：
--   * 多租戶：tenant_id 配合 @Filter("tenantFilter") 隔離
--   * 軟刪除不採用（容量考量），刪除時連同實體檔案一併移除
--   * 公告刪除時 CASCADE 移除附件記錄（實體檔在 Service 層刪除）
-- ════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS announcement_attachments (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(50)  NOT NULL,
    announcement_id BIGINT       NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_size       BIGINT       NOT NULL,
    mime_type       VARCHAR(150) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    created_by      VARCHAR(50),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_announcement_attachments_announcement
        FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_announcement_attachments_announcement
    ON announcement_attachments (announcement_id);

CREATE INDEX IF NOT EXISTS idx_announcement_attachments_tenant
    ON announcement_attachments (tenant_id);
