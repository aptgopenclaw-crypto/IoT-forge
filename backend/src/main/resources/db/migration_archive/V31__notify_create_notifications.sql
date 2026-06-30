

CREATE TABLE notifications (
	id bigserial NOT NULL,
	tenant_id varchar(50) NOT NULL,
	user_id varchar(50) NOT NULL,
	"type" varchar(20) NOT NULL,
	title varchar(200) NOT NULL,
	"content" varchar(2000) NULL,
	ref_type varchar(50) NULL,
	ref_id varchar(50) NULL,
	"read" bool DEFAULT false NOT NULL,
	read_at timestamptz NULL,
	created_at timestamptz DEFAULT now() NOT NULL,
	updated_at timestamptz NULL,
	CONSTRAINT notifications_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_notifications_tenant ON notifications USING btree (tenant_id);
CREATE INDEX idx_notifications_user_read ON notifications USING btree (user_id, read, created_at DESC);