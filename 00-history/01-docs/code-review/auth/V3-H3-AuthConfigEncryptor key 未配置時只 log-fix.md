**V3-H3 AuthConfigEncryptor key fail-fast — fixed.**

### Problem
`init()` only logged `log.warn` when `app.auth.config-secret-key` was missing — production could start without the encryption key, leaving LDAP bind passwords and OIDC client secrets stored as plaintext in the database.

### Fix
**`AuthConfigEncryptor.java`** — Changed `init()` from `log.warn` + `return` to `throw new IllegalStateException(...)` when the key is blank. The application now fails to start if the encryption key is missing, preventing silent data exposure.

### Tests updated
- `encrypt_withoutKey_throwsException` → renamed to `init_withoutKey_throwsException`, now asserts `init()` throws (instead of `encrypt()`)
- `isKeyConfigured_withoutKey_returnsFalse` — skips calling `init()` (which would now throw) and relies on the constructor's default state