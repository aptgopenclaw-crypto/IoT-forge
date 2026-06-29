import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/authStore'

vi.mock('@/api/auth')
vi.mock('@/api/axios/axiosIns', () => ({
  default: {},
  setAxiosToken: vi.fn(),
  setAxiosRefreshHandler: vi.fn(),
  setAxiosLogoutHandler: vi.fn(),
}))
vi.mock('@/router', () => ({ default: { push: vi.fn() } }))

function makeJwt(payload: Record<string, unknown>): string {
  const enc = (obj: unknown) =>
    btoa(JSON.stringify(obj))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '')
  return `${enc({ alg: 'none' })}.${enc(payload)}.sig`
}

describe('authStore scope/impersonation derived state [Phase 4 / 4.1.7]', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('exposes null scope/impersonation when there is no token', () => {
    const store = useAuthStore()
    expect(store.scope).toBeNull()
    expect(store.impersonation).toBeNull()
    expect(store.isImpersonating).toBe(false)
  })

  it('derives scope=PLATFORM from a PLATFORM-scope token', () => {
    const store = useAuthStore()
    store.accessToken = makeJwt({ scope: 'PLATFORM' })
    expect(store.scope).toBe('PLATFORM')
    expect(store.impersonation).toBeNull()
    expect(store.isImpersonating).toBe(false)
  })

  it('derives scope=TENANT from a TENANT-scope token', () => {
    const store = useAuthStore()
    store.accessToken = makeJwt({ scope: 'TENANT' })
    expect(store.scope).toBe('TENANT')
    expect(store.impersonation).toBeNull()
    expect(store.isImpersonating).toBe(false)
  })

  it('derives scope=IMPERSONATION and surfaces the impersonation claim', () => {
    const store = useAuthStore()
    store.accessToken = makeJwt({
      scope: 'IMPERSONATION',
      impersonation: {
        originalUserId: 'super-admin-1',
        sessionId: 'sess-XYZ',
        expiresAt: 1_800_000_000,
      },
    })
    expect(store.scope).toBe('IMPERSONATION')
    expect(store.isImpersonating).toBe(true)
    expect(store.impersonation).toEqual({
      originalUserId: 'super-admin-1',
      sessionId: 'sess-XYZ',
      expiresAt: 1_800_000_000,
    })
  })

  it('returns null scope for a legacy token without a `scope` claim', () => {
    const store = useAuthStore()
    store.accessToken = makeJwt({ uid: 'legacy' })
    expect(store.scope).toBeNull()
    expect(store.impersonation).toBeNull()
  })

  it('returns null impersonation when scope=IMPERSONATION but the claim is malformed', () => {
    const store = useAuthStore()
    store.accessToken = makeJwt({
      scope: 'IMPERSONATION',
      impersonation: { sessionId: 42, expiresAt: 'bad' },
    })
    expect(store.scope).toBe('IMPERSONATION')
    expect(store.impersonation).toBeNull()
    expect(store.isImpersonating).toBe(false)
  })

  it('reactively recomputes scope/impersonation when accessToken changes', () => {
    const store = useAuthStore()
    expect(store.scope).toBeNull()

    store.accessToken = makeJwt({ scope: 'PLATFORM' })
    expect(store.scope).toBe('PLATFORM')

    store.accessToken = makeJwt({
      scope: 'IMPERSONATION',
      impersonation: { sessionId: 's1', expiresAt: 100 },
    })
    expect(store.scope).toBe('IMPERSONATION')
    expect(store.impersonation?.sessionId).toBe('s1')

    store.accessToken = null
    expect(store.scope).toBeNull()
    expect(store.impersonation).toBeNull()
  })

  it('clearAuth resets scope/impersonation back to null', () => {
    const store = useAuthStore()
    store.accessToken = makeJwt({
      scope: 'IMPERSONATION',
      impersonation: { sessionId: 's', expiresAt: 1 },
    })
    expect(store.isImpersonating).toBe(true)

    store.clearAuth()
    expect(store.accessToken).toBeNull()
    expect(store.scope).toBeNull()
    expect(store.impersonation).toBeNull()
    expect(store.isImpersonating).toBe(false)
  })

  it('applyImpersonationToken stores the new token and navigates via window.location [Phase 4 / 4.1.9]', () => {
    const store = useAuthStore()
    const newToken = makeJwt({
      scope: 'IMPERSONATION',
      impersonation: {
        sessionId: 'sess-new',
        originalUserId: 'super-1',
        expiresAt: 1_800_000_000,
      },
    })
    const assignSpy = vi.fn()
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { assign: assignSpy, href: '' },
    })

    store.applyImpersonationToken(newToken)

    expect(store.accessToken).toBe(newToken)
    expect(store.scope).toBe('IMPERSONATION')
    expect(sessionStorage.getItem('passExam')).toBe('true')
    // Default landing for IMPERSONATION scope is `/?impersonating=1` (see
    // router/guards.ts `resolveLandingPath`).
    expect(assignSpy).toHaveBeenCalledWith('/?impersonating=1')
  })

  it('applyImpersonationToken honours an explicit landingPath override', () => {
    const store = useAuthStore()
    const newToken = makeJwt({
      scope: 'IMPERSONATION',
      impersonation: { sessionId: 's', expiresAt: 1 },
    })
    const assignSpy = vi.fn()
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { assign: assignSpy, href: '' },
    })

    store.applyImpersonationToken(newToken, '/custom-landing')

    expect(assignSpy).toHaveBeenCalledWith('/custom-landing')
  })
})
