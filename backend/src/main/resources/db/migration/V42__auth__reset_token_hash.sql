-- V42__auth__reset_token_hash.sql
--
-- Security fix: stop persisting the plaintext password-reset token. The token
-- is now hashed (SHA-256 hex) before being written to the database. The
-- plaintext value continues to be delivered to the user via email only.
--
-- Any pending (un-used, un-expired) reset tokens are wiped because their
-- plaintext form is unknown and therefore unverifiable against the new
-- hashed column. Affected users must re-trigger "forgot password".

-- 1) Invalidate any in-flight tokens before changing the schema.
DELETE FROM user_reset_password_token WHERE used = false;

-- 2) Drop the unique constraint on the old plaintext column.
ALTER TABLE user_reset_password_token
    DROP CONSTRAINT IF EXISTS user_reset_password_token_token_key;

-- 3) Rename token -> token_hash and shrink to the SHA-256 hex length (64 chars).
ALTER TABLE user_reset_password_token
    RENAME COLUMN token TO token_hash;

ALTER TABLE user_reset_password_token
    ALTER COLUMN token_hash TYPE VARCHAR(64);

-- 4) Re-create the uniqueness guarantee on the hash column.
ALTER TABLE user_reset_password_token
    ADD CONSTRAINT user_reset_password_token_token_hash_key UNIQUE (token_hash);
