-- ============================================================
-- AUTH Flyway V1: Create AUTH tables
-- ============================================================

-- 1. roles (global, no tenant_id)
CREATE TABLE roles (
    role_id     VARCHAR(50)     PRIMARY KEY,
    code        VARCHAR(50)     NOT NULL UNIQUE,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(500),
    built_in    BOOLEAN         NOT NULL DEFAULT true,
    enabled     BOOLEAN         NOT NULL DEFAULT true,
    create_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO roles (role_id, code, name, description) VALUES
('ROLE_SUPER_ADMIN', 'SUPER_ADMIN', '超級管理員', '跨 tenant 最高權限'),
('ROLE_ADMIN',       'ADMIN',       '場域管理者', '單一 tenant 最高權限'),
('ROLE_OPERATOR',    'OPERATOR',    '維運人員',   '設備管理、工單處理'),
('ROLE_VIEWER',      'VIEWER',      '檢視者',     '唯讀'),
('ROLE_FIELD_USER',  'FIELD_USER',  '現場人員',   'APP 端操作'),
('ROLE_MONITOR',     'MONITOR',     '監造人員',   '監造/稽核');

-- 2. permissions (global, RBAC manages seed)
CREATE TABLE permissions (
    permission_id   VARCHAR(50)     PRIMARY KEY,
    code            VARCHAR(100)    NOT NULL UNIQUE,
    name            VARCHAR(200)    NOT NULL,
    group_name      VARCHAR(100),
    sort_order      INT             DEFAULT 0
);

-- 3. role_permissions (tenant_id nullable = future override)
CREATE TABLE role_permissions (
    role_id         VARCHAR(50)     NOT NULL REFERENCES roles(role_id),
    permission_id   VARCHAR(50)     NOT NULL REFERENCES permissions(permission_id),
    tenant_id       VARCHAR(50)     REFERENCES tenant(tenant_id),
    UNIQUE(role_id, permission_id, tenant_id)
);

-- 4. depts (per tenant, DEPT capability manages CRUD)
CREATE TABLE depts (
    dept_id     VARCHAR(50)     PRIMARY KEY,
    tenant_id   VARCHAR(50)     NOT NULL REFERENCES tenant(tenant_id),
    parent_id   VARCHAR(50)     REFERENCES depts(dept_id),
    name        VARCHAR(200)    NOT NULL,
    code        VARCHAR(100)    NOT NULL,
    sort_order  INT             DEFAULT 0,
    leader      VARCHAR(100),
    phone       VARCHAR(50),
    enabled     BOOLEAN         NOT NULL DEFAULT true,
    create_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code)
);

-- 5. users (global, no tenant_id)
CREATE TABLE users (
    user_id         VARCHAR(50)     PRIMARY KEY,
    email           VARCHAR(200)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    display_name    VARCHAR(200)    NOT NULL,
    phone           VARCHAR(50),
    enabled         BOOLEAN         NOT NULL DEFAULT true,
    locked          BOOLEAN         NOT NULL DEFAULT false,
    locked_at       TIMESTAMP WITHOUT TIME ZONE,
    login_fail_count INT            NOT NULL DEFAULT 0,
    is_super_admin  BOOLEAN         NOT NULL DEFAULT false,
    last_login_at   TIMESTAMP WITHOUT TIME ZONE,
    create_time     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- 6. user_tenant_mapping
CREATE TABLE user_tenant_mapping (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             VARCHAR(50)     NOT NULL REFERENCES users(user_id),
    tenant_id           VARCHAR(50)     NOT NULL REFERENCES tenant(tenant_id),
    role_id             VARCHAR(50)     NOT NULL REFERENCES roles(role_id),
    dept_id             VARCHAR(50)     REFERENCES depts(dept_id),
    default_project_id  VARCHAR(50),
    enabled             BOOLEAN         NOT NULL DEFAULT true,
    create_time         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, tenant_id)
);

CREATE INDEX idx_utm_user ON user_tenant_mapping(user_id);
CREATE INDEX idx_utm_tenant ON user_tenant_mapping(tenant_id);

-- 7. user_login_log
CREATE TABLE user_login_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     VARCHAR(50)     NOT NULL,
    tenant_id   VARCHAR(50),
    email       VARCHAR(200),
    ip_address  VARCHAR(50),
    user_agent  VARCHAR(500),
    event_type  VARCHAR(50)     NOT NULL,
    detail      VARCHAR(500),
    create_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_login_log_user ON user_login_log(user_id);

-- 8. change_password_log
CREATE TABLE change_password_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     VARCHAR(50)     NOT NULL,
    change_type VARCHAR(50)     NOT NULL,
    ip_address  VARCHAR(50),
    create_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 9. user_reset_password_token
CREATE TABLE user_reset_password_token (
    token_id    VARCHAR(100)    PRIMARY KEY,
    user_id     VARCHAR(50)     NOT NULL REFERENCES users(user_id),
    token       VARCHAR(255)    NOT NULL UNIQUE,
    expired_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used        BOOLEAN         NOT NULL DEFAULT false,
    create_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);
