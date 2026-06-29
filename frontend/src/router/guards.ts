/**
 * [Phase 4 / 4.1.4 / ADR-004 / ADR-007] Router guard helpers — scope-aware
 * post-login landing & (4.1.5) cross-scope navigation guard.
 *
 * The backend issues a JWT with a `scope` claim (see backend
 * `JwtClaimKeys.SCOPE`):
 *   - "PLATFORM"      → super_admin acting on platform-level resources
 *   - "TENANT"        → ordinary tenant user
 *   - "IMPERSONATION" → super_admin acting as a tenant user
 *   - absent          → legacy token, treated as TENANT for backward compat
 *
 * Landing rules per the platform-tenant-separation phased plan §4.1.4:
 *   PLATFORM      → /platform/tenants
 *   TENANT        → /
 *   IMPERSONATION → /?impersonating=1   (TenantLayout + banner, see 4.1.6)
 *
 * Exporting this as a thin module rather than inlining in `authStore` keeps
 * 4.1.5's `scope-mismatch` redirect helper (and its unit tests) co-located.
 */

export type TokenScope = 'PLATFORM' | 'TENANT' | 'IMPERSONATION'

/**
 * Decode a JWT payload without verification (purely for scope-based UI
 * routing). The same token is re-validated by the backend on every request,
 * so a tampered client-side scope cannot bypass authorization.
 */
function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const json = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))
    const parsed = JSON.parse(json)
    return typeof parsed === 'object' && parsed !== null ? (parsed as Record<string, unknown>) : null
  } catch {
    return null
  }
}

/**
 * Returns the `scope` claim of the given JWT, or `null` if the token is
 * malformed / the claim is absent. The caller is responsible for applying the
 * legacy-token fallback (treat `null` as TENANT).
 */
export function getTokenScope(token: string | null | undefined): TokenScope | null {
  if (!token) return null
  const payload = decodeJwtPayload(token)
  const raw = payload?.scope
  if (typeof raw !== 'string') return null
  const upper = raw.toUpperCase()
  if (upper === 'PLATFORM' || upper === 'TENANT' || upper === 'IMPERSONATION') {
    return upper
  }
  return null
}

/**
 * Returns the post-login landing path for the given access token, honouring
 * the §4.1.4 scope-routing rules. Legacy tokens (no `scope` claim) are routed
 * as TENANT to preserve pre-Phase-1 behaviour.
 */
export function resolveLandingPath(token: string | null | undefined): string {
  const scope = getTokenScope(token)
  switch (scope) {
    case 'PLATFORM':
      return '/platform/tenants'
    case 'IMPERSONATION':
      return '/?impersonating=1'
    case 'TENANT':
    default:
      return '/'
  }
}

/**
 * [Phase 4 / 4.1.5] Returns a redirect path when the given token's scope is
 * incompatible with the destination route, or `null` when navigation is
 * allowed. The decision is purely UI-level — the backend
 * `ScopeEnforcementFilter` (Phase 1.1.2) is the authoritative gate; this
 * helper just keeps users from landing on a page that would 403 every API
 * call and looks half-rendered.
 *
 * Rules:
 *   - PLATFORM token + destination missing `meta.requiresScope`
 *     → redirect to `/platform/tenants` (super_admin must stay on platform shell)
 *   - non-PLATFORM token + destination `meta.requiresScope === 'PLATFORM'`
 *     → redirect to the caller's natural landing
 *     (`/?impersonating=1` for IMPERSONATION, `/` for TENANT)
 *
 * Legacy tokens (no `scope` claim) are treated as TENANT.
 */
export function resolveScopeRedirect(
  token: string | null | undefined,
  to: { path: string; meta?: { requiresScope?: unknown } },
): string | null {
  const scope: TokenScope = getTokenScope(token) ?? 'TENANT'
  const required =
    typeof to.meta?.requiresScope === 'string'
      ? (to.meta.requiresScope.toUpperCase() as TokenScope)
      : // Dynamic routes under /platform/* implicitly require PLATFORM scope
        // even without explicit meta (they are added by menuStore at runtime).
        to.path.startsWith('/platform')
        ? 'PLATFORM'
        : undefined

  // Already on the correct shell → nothing to do.
  if (required === scope) return null

  // Tenant / impersonation token hitting a PLATFORM-scoped route.
  if (required === 'PLATFORM' && scope !== 'PLATFORM') {
    return scope === 'IMPERSONATION' ? '/?impersonating=1' : '/'
  }

  // Platform-scoped token hitting a non-PLATFORM route (tenant UI, public
  // post-login pages, etc.). Force them back to the platform shell.
  if (required !== 'PLATFORM' && scope === 'PLATFORM') {
    // Avoid a redirect loop if the destination is already the platform landing.
    if (to.path === '/platform/tenants') return null
    return '/platform/tenants'
  }

  return null
}
