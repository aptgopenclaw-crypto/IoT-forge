-- announcement_depts definition

-- Drop table

-- DROP TABLE announcement_depts;

CREATE TABLE announcement_depts (
	announcement_id int8 NOT NULL, -- 公告 ID，複合主鍵的一部分，關聯 announcements 表的主鍵 (BIGINT，非空)
	dept_id int8 NOT NULL, -- 部門 ID，複合主鍵的一部分，關聯部門表的主鍵 (BIGINT，非空)
	CONSTRAINT announcement_depts_pkey PRIMARY KEY (announcement_id, dept_id),
	CONSTRAINT announcement_depts_announcement_id_fkey FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE,
	CONSTRAINT announcement_depts_dept_id_fkey FOREIGN KEY (dept_id) REFERENCES dept_info(dept_id)
);
CREATE INDEX idx_announcement_depts_dept ON announcement_depts USING btree (dept_id);
COMMENT ON TABLE announcement_depts IS '公告與部門的關聯表，用於記錄公告的定向發送範圍（指定哪些部門可見）。採用複合主鍵（announcement_id, dept_id），表示一個公告可發送給多個部門，一個部門可接收多個公告。';

-- Column comments

COMMENT ON COLUMN announcement_depts.announcement_id IS '公告 ID，複合主鍵的一部分，關聯 announcements 表的主鍵 (BIGINT，非空)';
COMMENT ON COLUMN announcement_depts.dept_id IS '部門 ID，複合主鍵的一部分，關聯部門表的主鍵 (BIGINT，非空)';