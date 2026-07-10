-- announcement_reads definition

-- Drop table

-- DROP TABLE announcement_reads;

CREATE TABLE announcement_reads (
	id bigserial NOT NULL, -- 主鍵 ID，自增序列 (BIGINT)
	announcement_id int8 NOT NULL, -- 公告 ID，關聯 announcements 表的主鍵 (BIGINT，非空)
	user_id varchar(50) NOT NULL, -- 用戶 ID，標識閱讀該公告的用戶 (VARCHAR(50)，非空)
	read_at timestamp DEFAULT now() NOT NULL, -- 閱讀時間，記錄用戶首次閱讀該公告的時間點 (TIMESTAMP，非空)
	CONSTRAINT announcement_reads_announcement_id_user_id_key UNIQUE (announcement_id, user_id),
	CONSTRAINT announcement_reads_pkey PRIMARY KEY (id),
	CONSTRAINT announcement_reads_announcement_id_fkey FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE
);
CREATE INDEX idx_announcement_reads_user ON announcement_reads USING btree (user_id);
COMMENT ON TABLE announcement_reads IS '公告閱讀記錄表，記錄用戶對公告的閱讀行為。用於追蹤哪些用戶已讀過哪些公告，配合 announcements 表的 requires_ack 字段，可實現重要公告的已讀/未讀統計及未讀名單查詢。';

-- Column comments

COMMENT ON COLUMN announcement_reads.id IS '主鍵 ID，自增序列 (BIGINT)';
COMMENT ON COLUMN announcement_reads.announcement_id IS '公告 ID，關聯 announcements 表的主鍵 (BIGINT，非空)';
COMMENT ON COLUMN announcement_reads.user_id IS '用戶 ID，標識閱讀該公告的用戶 (VARCHAR(50)，非空)';
COMMENT ON COLUMN announcement_reads.read_at IS '閱讀時間，記錄用戶首次閱讀該公告的時間點 (TIMESTAMP，非空)';