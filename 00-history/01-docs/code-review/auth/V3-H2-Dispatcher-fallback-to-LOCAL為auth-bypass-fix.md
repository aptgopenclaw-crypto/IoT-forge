**V3-H2 Dispatcher fallback-to-LOCAL auth bypass — fixed.** Here's the summary:

### Problem
`AuthenticationDispatcher.dispatch()` caught *any* `BusinessException` from external auth providers (including credential errors like wrong password) and fell back to LOCAL password check. An attacker could bypass LDAP by sending bad credentials to trigger the fallback, then log in with a known local password.

### Fix (3 changes matching the code review's recommendations)

#### (a) `fallbackLocal` default changed `true` → `false`
**File:** `TenantAuthConfigEntity.java` — `@Builder.Default private Boolean fallbackLocal = false`

Admins must *explicitly* enable fallback, acknowledging the risk. The previous default silently allowed the bypass.

#### (b) Only infrastructure errors trigger fallback
**File:** `AuthenticationDispatcher.java`

New method `isInfrastructureError()` returns true only for `LDAP_SERVICE_UNAVAILABLE` — credential errors (`LDAP_AUTH_FAILED`, `LOGIN_FAIL`, `USER_NOT_FOUND`, `ACCOUNT_DISABLED/LOCKED`) are unconditionally re-thrown.

```java
private static boolean isInfrastructureError(BusinessException e) {
    ErrorCode code = e.getErrorCode();
    return code == ErrorCode.LDAP_SERVICE_UNAVAILABLE;
}
```

#### (c) Security event logging
**File:** `AuthenticationDispatcher.java` + `SecurityEvent.java`

Added `SecurityEvent.AUTH_FALLBACK` — recorded via `SecurityLogger.warn()` on each fallback (with auth type, tenant, and error code), making it visible to SIEM/SOC systems. Rough `log.warn` replaced with structured event.

### Tests
- `dispatch_externalProviderFails_fallbackLocal_enabled` — now uses `LDAP_SERVICE_UNAVAILABLE` (infra error → fallback allowed)
- **New:** `dispatch_externalProviderFails_credentialError_doesNotFallback` — verifies `LOGIN_FAIL` is re-thrown, never falls back