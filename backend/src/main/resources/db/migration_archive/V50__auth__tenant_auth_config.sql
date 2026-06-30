-- Phase A: Multi-Auth Provider — tenant_auth_config table
-- Stores per-tenant authentication provider configuration

CREATE TABLE tenant_auth_config (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(50)     NOT NULL UNIQUE,
    auth_type       VARCHAR(20)     NOT NULL DEFAULT 'LOCAL',
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    config_json     TEXT,
    fallback_local  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tenant_auth_config_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id)
);

CREATE INDEX idx_tenant_auth_config_tenant ON tenant_auth_config(tenant_id);

COMMENT ON TABLE tenant_auth_config IS '租戶認證方式配置（每個租戶最多一筆）';
COMMENT ON COLUMN tenant_auth_config.auth_type IS 'LOCAL / LDAP / OIDC / SAML';
COMMENT ON COLUMN tenant_auth_config.config_json IS '加密後的 provider 設定 JSON（AES-256-GCM）';
COMMENT ON COLUMN tenant_auth_config.fallback_local IS '外部 IdP 失敗時是否允許退回本地帳密';
