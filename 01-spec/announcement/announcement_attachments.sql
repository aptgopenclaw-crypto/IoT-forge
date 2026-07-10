-- announcement_attachments definition

-- Drop table

-- DROP TABLE announcement_attachments;

CREATE TABLE announcement_attachments (
	id bigserial NOT NULL, -- 主鍵 ID，自增序列 (BIGINT)
	tenant_id varchar(50) NOT NULL, -- 租戶 ID，用於多租戶數據隔離 (VARCHAR(50)，非空)
	announcement_id int8 NOT NULL, -- 所屬公告 ID，關聯 announcements 表的主鍵 (BIGINT，非空)
	file_name varchar(255) NOT NULL, -- 附件原始文件名，用於前端展示 (VARCHAR(255)，非空)
	file_size int8 NOT NULL, -- 附件大小，單位：字節 (BIGINT，非空)
	mime_type varchar(150) NOT NULL, -- 附件 MIME 類型，如 image/png、application/pdf 等 (VARCHAR(150)，非空)
	file_path varchar(500) NOT NULL, -- 文件相對存儲路徑，由 FileStorageService 生成，僅供內部使用，不對外暴露 (VARCHAR(500)，非空)
	created_by varchar(50) NULL, -- 創建人 ID (VARCHAR(50))
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 創建時間，自動生成，不可更新 (TIMESTAMP，非空)
	CONSTRAINT announcement_attachments_pkey PRIMARY KEY (id),
	CONSTRAINT fk_announcement_attachments_announcement FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE
);
CREATE INDEX idx_announcement_attachments_announcement ON announcement_attachments USING btree (announcement_id);
CREATE INDEX idx_announcement_attachments_tenant ON announcement_attachments USING btree (tenant_id);
COMMENT ON TABLE announcement_attachments IS '公告附件表，存儲公告附件的元數據。實際文件存放於 ./uploads/announcement/{announcementId}/ 目錄下，由 FileStorageService 管理。本表僅保存文件名、大小、MIME 類型及相對路徑等信息，支持多租戶隔離。';

-- Column comments

COMMENT ON COLUMN announcement_attachments.id IS '主鍵 ID，自增序列 (BIGINT)';
COMMENT ON COLUMN announcement_attachments.tenant_id IS '租戶 ID，用於多租戶數據隔離 (VARCHAR(50)，非空)';
COMMENT ON COLUMN announcement_attachments.announcement_id IS '所屬公告 ID，關聯 announcements 表的主鍵 (BIGINT，非空)';
COMMENT ON COLUMN announcement_attachments.file_name IS '附件原始文件名，用於前端展示 (VARCHAR(255)，非空)';
COMMENT ON COLUMN announcement_attachments.file_size IS '附件大小，單位：字節 (BIGINT，非空)';
COMMENT ON COLUMN announcement_attachments.mime_type IS '附件 MIME 類型，如 image/png、application/pdf 等 (VARCHAR(150)，非空)';
COMMENT ON COLUMN announcement_attachments.file_path IS '文件相對存儲路徑，由 FileStorageService 生成，僅供內部使用，不對外暴露 (VARCHAR(500)，非空)';
COMMENT ON COLUMN announcement_attachments.created_by IS '創建人 ID (VARCHAR(50))';
COMMENT ON COLUMN announcement_attachments.created_at IS '創建時間，自動生成，不可更新 (TIMESTAMP，非空)';