-- announcements definition

-- Drop table

-- DROP TABLE announcements;

CREATE TABLE announcements (
	id bigserial NOT NULL, -- 主鍵 ID，自增序列 (BIGINT)
	tenant_id varchar(50) NOT NULL, -- 租戶 ID，多租戶隔離標識 (VARCHAR(50)，非空)
	title varchar(200) NOT NULL, -- 公告標題 (VARCHAR(200)，非空)
	"content" text NOT NULL, -- 公告正文內容 (TEXT，非空，存儲 HTML 或富文本)
	status varchar(20) DEFAULT 'DRAFT'::character varying NOT NULL, -- 公告狀態，枚舉值：DRAFT(草稿)、PUBLISHED(已發佈)、ARCHIVED(已歸檔) 等 (VARCHAR(20)，非空)
	"scope" varchar(20) DEFAULT 'ALL'::character varying NOT NULL, -- 可見範圍，枚舉值：PUBLIC(全體可見)、DEPARTMENT(部門可見) 等 (VARCHAR(20)，非空)
	pinned bool DEFAULT false NOT NULL, -- 是否置頂 (BOOLEAN，非空，true 表示置頂)
	publish_at timestamp NULL, -- 發佈時間 (TIMESTAMP，發佈後生效)
	expire_at timestamp NULL, -- 過期時間 (TIMESTAMP，過期後可能不再顯示)
	created_by varchar(50) NULL, -- 創建人 ID (VARCHAR(50))
	created_by_name varchar(100) NULL, -- 創建人姓名 (VARCHAR(100))
	created_at timestamp DEFAULT now() NOT NULL, -- 創建時間，自動生成，不可更新 (TIMESTAMP，非空)
	updated_at timestamp DEFAULT now() NOT NULL, -- 最後更新時間，自動維護 (TIMESTAMP)
	"version" int8 DEFAULT 0 NOT NULL, -- 樂觀鎖版本號，Hibernate 自動管理，更新時自增並用於併發控制 (BIGINT，非空)
	content_text text NULL, -- 純文本版本，由 HTML 剝離後生成，用於關鍵詞搜索，避免匹配 HTML 標籤 (TEXT)
	category varchar(20) DEFAULT 'GENERAL'::character varying NOT NULL, -- 公告分類，默認 GENERAL，對應 AnnouncementCategory 枚舉 (VARCHAR(20)，非空，默認值 'GENERAL')
	requires_ack bool DEFAULT false NOT NULL, -- 是否需要用戶確認已讀；true 時需用戶點擊“我已閱讀”才記錄閱讀狀態，管理端顯示已讀比例與未讀名單 (BOOLEAN，非空，默認 false)
	pin_order int4 NULL, -- 置頂排序序號，數字越小越靠前；僅在 pinned = true 時有意義，取消置頂時自動設為 NULL (INTEGER)
	CONSTRAINT announcements_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_announcements_category_publish ON announcements USING btree (category, publish_at DESC) WHERE ((status)::text = 'PUBLISHED'::text);
CREATE INDEX idx_announcements_pin_order ON announcements USING btree (tenant_id, pin_order) WHERE ((pinned = true) AND (pin_order IS NOT NULL));
CREATE INDEX idx_announcements_published_active ON announcements USING btree (tenant_id, scope, publish_at, expire_at) WHERE ((status)::text = 'PUBLISHED'::text);
CREATE INDEX idx_announcements_requires_ack ON announcements USING btree (tenant_id, publish_at DESC) WHERE ((requires_ack = true) AND ((status)::text = 'PUBLISHED'::text));
CREATE INDEX idx_announcements_status ON announcements USING btree (tenant_id, status, publish_at DESC);
CREATE INDEX idx_announcements_tenant_id ON announcements USING btree (tenant_id);
COMMENT ON TABLE announcements IS '公告主表，用於儲存多租戶環境下的公告內容與元資料。支援公告完整生命週期管理，包含標題、HTML 正文、純文字檢索欄位、狀態(草稿/已發布/已歸檔)、可見範圍、分類(GENERAL等)、置頂排序、強制已讀確認旗標、生效與過期時間，並自動記錄建立者、審計時間及樂觀鎖版本控制。';

-- Column comments

COMMENT ON COLUMN announcements.id IS '主鍵 ID，自增序列 (BIGINT)';
COMMENT ON COLUMN announcements.tenant_id IS '租戶 ID，多租戶隔離標識 (VARCHAR(50)，非空)';
COMMENT ON COLUMN announcements.title IS '公告標題 (VARCHAR(200)，非空)';
COMMENT ON COLUMN announcements."content" IS '公告正文內容 (TEXT，非空，存儲 HTML 或富文本)';
COMMENT ON COLUMN announcements.status IS '公告狀態，枚舉值：DRAFT(草稿)、PUBLISHED(已發佈)、ARCHIVED(已歸檔) 等 (VARCHAR(20)，非空)';
COMMENT ON COLUMN announcements."scope" IS '可見範圍，枚舉值：PUBLIC(全體可見)、DEPARTMENT(部門可見) 等 (VARCHAR(20)，非空)';
COMMENT ON COLUMN announcements.pinned IS '是否置頂 (BOOLEAN，非空，true 表示置頂)';
COMMENT ON COLUMN announcements.publish_at IS '發佈時間 (TIMESTAMP，發佈後生效)';
COMMENT ON COLUMN announcements.expire_at IS '過期時間 (TIMESTAMP，過期後可能不再顯示)';
COMMENT ON COLUMN announcements.created_by IS '創建人 ID (VARCHAR(50))';
COMMENT ON COLUMN announcements.created_by_name IS '創建人姓名 (VARCHAR(100))';
COMMENT ON COLUMN announcements.created_at IS '創建時間，自動生成，不可更新 (TIMESTAMP，非空)';
COMMENT ON COLUMN announcements.updated_at IS '最後更新時間，自動維護 (TIMESTAMP)';
COMMENT ON COLUMN announcements."version" IS '樂觀鎖版本號，Hibernate 自動管理，更新時自增並用於併發控制 (BIGINT，非空)';
COMMENT ON COLUMN announcements.content_text IS '純文本版本，由 HTML 剝離後生成，用於關鍵詞搜索，避免匹配 HTML 標籤 (TEXT)';
COMMENT ON COLUMN announcements.category IS '公告分類，默認 GENERAL，對應 AnnouncementCategory 枚舉 (VARCHAR(20)，非空，默認值 ''GENERAL'')';
COMMENT ON COLUMN announcements.requires_ack IS '是否需要用戶確認已讀；true 時需用戶點擊“我已閱讀”才記錄閱讀狀態，管理端顯示已讀比例與未讀名單 (BOOLEAN，非空，默認 false)';
COMMENT ON COLUMN announcements.pin_order IS '置頂排序序號，數字越小越靠前；僅在 pinned = true 時有意義，取消置頂時自動設為 NULL (INTEGER)';