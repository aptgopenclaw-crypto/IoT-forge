-- ============================================================
-- DEPT Flyway V5: Create dept_info table + support columns
-- ============================================================

-- 1. Drop old placeholder depts table (created in V1 as placeholder)
ALTER TABLE user_tenant_mapping DROP CONSTRAINT IF EXISTS user_tenant_mapping_dept_id_fkey;
DROP TABLE IF EXISTS depts;

-- 2. Create dept_info table (per-tenant)
CREATE TABLE dept_info (
    dept_id         BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(50)     NOT NULL,
    pid             BIGINT          REFERENCES dept_info(dept_id),
    dept_name       VARCHAR(100)    NOT NULL,
    dept_sort       INTEGER         DEFAULT 0,
    status          SMALLINT        DEFAULT 1,
    hierarchy_path  VARCHAR(500),
    create_by       VARCHAR(50),
    update_by       VARCHAR(50),
    create_time     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP WITH TIME ZONE,

    UNIQUE (tenant_id, dept_name, pid)
);

CREATE INDEX idx_dept_tenant_id ON dept_info(tenant_id);
CREATE INDEX idx_dept_pid       ON dept_info(pid);

-- 3. Alter user_tenant_mapping.dept_id from VARCHAR(50) to BIGINT
UPDATE user_tenant_mapping SET dept_id = NULL WHERE dept_id IS NOT NULL;
ALTER TABLE user_tenant_mapping ALTER COLUMN dept_id TYPE BIGINT USING dept_id::BIGINT;
ALTER TABLE user_tenant_mapping
    ADD CONSTRAINT fk_utm_dept_info FOREIGN KEY (dept_id) REFERENCES dept_info(dept_id);

-- 4. Add data_scope column to roles (for DataScope mechanism)
ALTER TABLE roles ADD COLUMN data_scope VARCHAR(30) DEFAULT 'ALL';
