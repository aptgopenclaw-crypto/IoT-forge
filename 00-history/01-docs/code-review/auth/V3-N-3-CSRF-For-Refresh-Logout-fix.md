**N-3 CSRF for refresh / logout — fixed.** Here's the summary:

### Problem
`csrf.disable()` was set in `SecurityConfig`, leaving cookie-bearing endpoints (`refresh`, `logout`, `idle-logout`) relying solely on `SameSite=Lax` for CSRF protection. Though SameSite provides good baseline protection, defense-in-depth was missing — the code review flagged this as 🔴 high-risk.

### Solution: `CsrfCookieFilter` (Origin/Referer validation)

**New file:** `backend/src/main/java/com/taipei/iot/auth/security/CsrfCookieFilter.java`

A `OncePerRequestFilter` that:
1. **Matches only** `POST /v1/noauth/token/refresh`, `POST /v1/auth/logout`, `POST /v1/auth/idle-logout`
2. **Skips if no `refresh_token` cookie** — no cookie, no CSRF risk
3. **Validates `Origin`** against `cors.allowed-origins` (from `CorsProperties`)
4. **Falls back to `Referer`** if Origin is absent (extracts the origin part from the URL)
5. **Rejects with `403 + CSRF_VALIDATION_FAILED`** if neither header matches
6. Logs `SecurityEvent.CSRF_ATTEMPT` via `SecurityLogger` on failure

**Registration:** Placed before `JwtAuthenticationFilter` in `SecurityConfig` — runs before auth, since the CSRF check only needs headers.

### Other changes
- **`ErrorCode.java`** — Added `CSRF_VALIDATION_FAILED("10035", 403, ...)`
- **`SecurityEvent.java`** — Added `CSRF_ATTEMPT`
- **`SecurityConfig.java`** — Imported and registered `CsrfCookieFilter`
- **`AuthControllerTest.java`** — Updated `refreshToken_success` and `logout_success` to include `Origin: http://localhost` header (matches test's `cors.allowed-origins` config)