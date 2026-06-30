-- Phase A: Multi-Auth Provider — users table add auth_type / external_id columns

ALTER TABLE users
    ADD COLUMN auth_type    VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN external_id  VARCHAR(255);

CREATE INDEX idx_users_external_id ON users(external_id) WHERE external_id IS NOT NULL;

COMMENT ON COLUMN users.auth_type IS '此帳號的認證來源：LOCAL / LDAP / OIDC / SAML';
COMMENT ON COLUMN users.external_id IS '外部 IdP 的唯一識別（LDAP DN / OIDC sub / SAML nameId）';
