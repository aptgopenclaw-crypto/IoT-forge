import { describe, it, expect } from 'vitest'
import { getTokenScope, resolveLandingPath, resolveScopeRedirect, type TokenScope } from '@/router/guards'

/**
 * [Phase 4 / 4.1.4] Scope-aware post-login landing rules:
 *   PLATFORM      → /platform/tenants
 *   TENANT        → /
 *   IMPERSONATION → /?impersonating=1
 * Legacy tokens (no `scope` claim) and malformed tokens both fall back to `/`.
 */

function makeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
  const body = btoa(JSON.stringify(payload))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
  // Signature segment is irrelevant for the scope helpers.
  return `${header}.${body}.sig`
}

describe('router/guards: getTokenScope [Phase 4 / 4.1.4]', () => {
  it('extracts a PLATFORM scope claim', () => {
    expect(getTokenScope(makeJwt({ scope: 'PLATFORM' }))).toBe<TokenScope>('PLATFORM')
  })

  it('extracts a TENANT scope claim', () => {
    expect(getTokenScope(makeJwt({ scope: 'TENANT' }))).toBe<TokenScope>('TENANT')
  })

  it('extracts an IMPERSONATION scope claim', () => {
    expect(getTokenScope(makeJwt({ scope: 'IMPERSONATION' }))).toBe<TokenScope>('IMPERSONATION')
  })

  it('upper-cases lower-case scope values for forward-compat', () => {
    expect(getTokenScope(makeJwt({ scope: 'platform' }))).toBe<TokenScope>('PLATFORM')
  })

  it('returns null for tokens without a scope claim (legacy)', () => {
    expect(getTokenScope(makeJwt({ uid: 'u-1' }))).toBeNull()
  })

  it('returns null for an unknown scope value', () => {
    expect(getTokenScope(makeJwt({ scope: 'OPERATOR' }))).toBeNull()
  })

  it('returns null for malformed / empty / non-JWT input', () => {
    expect(getTokenScope(null)).toBeNull()
    expect(getTokenScope(undefined)).toBeNull()
    expect(getTokenScope('')).toBeNull()
    expect(getTokenScope('not-a-jwt')).toBeNull()
    expect(getTokenScope('a.b')).toBeNull()
    expect(getTokenScope('a.@@@.c')).toBeNull()
  })
})

describe('router/guards: resolveLandingPath [Phase 4 / 4.1.4]', () => {
  it('routes PLATFORM tokens to /platform/tenants', () => {
    expect(resolveLandingPath(makeJwt({ scope: 'PLATFORM' }))).toBe('/platform/tenants')
  })

  it('routes TENANT tokens to /', () => {
    expect(resolveLandingPath(makeJwt({ scope: 'TENANT' }))).toBe('/')
  })

  it('routes IMPERSONATION tokens to /?impersonating=1 (banner flag)', () => {
    expect(resolveLandingPath(makeJwt({ scope: 'IMPERSONATION' }))).toBe('/?impersonating=1')
  })

  it('falls back to / for legacy tokens (no scope claim)', () => {
    expect(resolveLandingPath(makeJwt({ uid: 'u-1' }))).toBe('/')
  })

  it('falls back to / for malformed / empty input', () => {
    expect(resolveLandingPath(null)).toBe('/')
    expect(resolveLandingPath(undefined)).toBe('/')
    expect(resolveLandingPath('garbage')).toBe('/')
  })
})

describe('router/guards: resolveScopeRedirect [Phase 4 / 4.1.5]', () => {
  const platformToken = makeJwt({ scope: 'PLATFORM' })
  const tenantToken = makeJwt({ scope: 'TENANT' })
  const impersonationToken = makeJwt({ scope: 'IMPERSONATION' })
  const legacyToken = makeJwt({ uid: 'u-1' })

  function route(path: string, requiresScope?: TokenScope) {
    return { path, meta: requiresScope ? { requiresScope } : {} }
  }

  // ── PLATFORM token ────────────────────────────────────────────────────────
  it('PLATFORM token + tenant route → /platform/tenants', () => {
    expect(resolveScopeRedirect(platformToken, route('/admin/users'))).toBe('/platform/tenants')
    expect(resolveScopeRedirect(platformToken, route('/'))).toBe('/platform/tenants')
    expect(resolveScopeRedirect(platformToken, route('/profile'))).toBe('/platform/tenants')
  })

  it('PLATFORM token + platform route → allowed (null)', () => {
    expect(
      resolveScopeRedirect(platformToken, route('/platform/tenants', 'PLATFORM')),
    ).toBeNull()
    expect(
      resolveScopeRedirect(
        platformToken,
        route('/platform/tenants/abc/auth-config', 'PLATFORM'),
      ),
    ).toBeNull()
  })

  it('PLATFORM token already at /platform/tenants without requiresScope meta → no loop', () => {
    // Belt-and-braces: even if a route record forgot the meta tag, avoid a
    // redirect loop when we are already on the platform landing.
    expect(resolveScopeRedirect(platformToken, route('/platform/tenants'))).toBeNull()
  })

  // ── TENANT token ──────────────────────────────────────────────────────────
  it('TENANT token + tenant route → allowed (null)', () => {
    expect(resolveScopeRedirect(tenantToken, route('/admin/users'))).toBeNull()
    expect(resolveScopeRedirect(tenantToken, route('/'))).toBeNull()
  })

  it('TENANT token + platform route → /', () => {
    expect(
      resolveScopeRedirect(tenantToken, route('/platform/tenants', 'PLATFORM')),
    ).toBe('/')
  })

  // ── IMPERSONATION token ──────────────────────────────────────────────────
  it('IMPERSONATION token + tenant route → allowed (null)', () => {
    expect(resolveScopeRedirect(impersonationToken, route('/admin/users'))).toBeNull()
    expect(resolveScopeRedirect(impersonationToken, route('/'))).toBeNull()
  })

  it('IMPERSONATION token + platform route → /?impersonating=1', () => {
    expect(
      resolveScopeRedirect(impersonationToken, route('/platform/tenants', 'PLATFORM')),
    ).toBe('/?impersonating=1')
  })

  // ── Legacy / missing token (treated as TENANT) ───────────────────────────
  it('legacy token (no scope claim) behaves like TENANT', () => {
    expect(resolveScopeRedirect(legacyToken, route('/admin/users'))).toBeNull()
    expect(
      resolveScopeRedirect(legacyToken, route('/platform/tenants', 'PLATFORM')),
    ).toBe('/')
  })

  it('null / undefined token behaves like TENANT', () => {
    expect(resolveScopeRedirect(null, route('/admin/users'))).toBeNull()
    expect(
      resolveScopeRedirect(undefined, route('/platform/tenants', 'PLATFORM')),
    ).toBe('/')
  })

  it('case-insensitive requiresScope meta (forward-compat)', () => {
    expect(
      resolveScopeRedirect(tenantToken, {
        path: '/platform/tenants',
        meta: { requiresScope: 'platform' },
      }),
    ).toBe('/')
  })
})
