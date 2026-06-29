-- V45: Phase 3 password expiry support — adds password_changed_at + force_change_password
-- columns to the users table. D-3 (avoid mass expiry) is implemented by populating
-- password_changed_at = NOW() for every existing row BEFORE applying the NOT NULL constraint.

ALTER TABLE users
    ADD COLUMN password_changed_at  TIMESTAMP,
    ADD COLUMN force_change_password BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users SET password_changed_at = NOW() WHERE password_changed_at IS NULL;

ALTER TABLE users ALTER COLUMN password_changed_at SET NOT NULL;

CREATE INDEX idx_users_password_changed_at ON users (password_changed_at);
