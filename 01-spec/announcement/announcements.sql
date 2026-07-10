-- announcements definition

-- Drop table

-- DROP TABLE announcements;

CREATE TABLE announcements (
	id bigserial NOT NULL,
	tenant_id varchar(50) NOT NULL,
	title varchar(200) NOT NULL,
	"content" text NOT NULL,
	status varchar(20) DEFAULT 'DRAFT'::character varying NOT NULL,
	"scope" varchar(20) DEFAULT 'ALL'::character varying NOT NULL,
	pinned bool DEFAULT false NOT NULL,
	publish_at timestamp NULL,
	expire_at timestamp NULL,
	created_by varchar(50) NULL,
	created_by_name varchar(100) NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	updated_at timestamp DEFAULT now() NOT NULL,
	"version" int8 DEFAULT 0 NOT NULL,
	content_text text NULL,
	category varchar(20) DEFAULT 'GENERAL'::character varying NOT NULL,
	requires_ack bool DEFAULT false NOT NULL,
	pin_order int4 NULL,
	CONSTRAINT announcements_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_announcements_category_publish ON announcements USING btree (category, publish_at DESC) WHERE ((status)::text = 'PUBLISHED'::text);
CREATE INDEX idx_announcements_pin_order ON announcements USING btree (tenant_id, pin_order) WHERE ((pinned = true) AND (pin_order IS NOT NULL));
CREATE INDEX idx_announcements_published_active ON announcements USING btree (tenant_id, scope, publish_at, expire_at) WHERE ((status)::text = 'PUBLISHED'::text);
CREATE INDEX idx_announcements_requires_ack ON announcements USING btree (tenant_id, publish_at DESC) WHERE ((requires_ack = true) AND ((status)::text = 'PUBLISHED'::text));
CREATE INDEX idx_announcements_status ON announcements USING btree (tenant_id, status, publish_at DESC);
CREATE INDEX idx_announcements_tenant_id ON announcements USING btree (tenant_id);