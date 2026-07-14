-- V26__create_vms_server.sql
CREATE TABLE vms_server (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    name          VARCHAR(100) NOT NULL,
    vms_type      VARCHAR(20) NOT NULL DEFAULT 'NX_WITNESS',
    base_url      VARCHAR(255) NOT NULL,
    auth_type     VARCHAR(20) NOT NULL DEFAULT 'BASIC',
    auth_username VARCHAR(100),
    auth_password VARCHAR(255),      -- AES encrypted
    api_token     VARCHAR(500),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
