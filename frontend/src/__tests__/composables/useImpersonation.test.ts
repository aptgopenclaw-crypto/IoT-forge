import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { getImpersonationClaim, useImpersonation } from '@/composables/useImpersonation'

// ── Mocks ────────────────────────────────────────────────────────────────────
const accessTokenRef = { value: null as string | null }
const doLogoutMock = vi.fn()
vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    get accessToken() {
      return accessTokenRef.value
    },
    doLogout: doLogoutMock,
  }),
}))

const revokeMock = vi.fn()
vi.mock('@/api/impersonation', () => ({
  revokeImpersonation: (id: string) => revokeMock(id),
}))

// ── JWT factory ──────────────────────────────────────────────────────────────
function makeJwt(payload: Record<string, unknown>): string {
  const enc = (obj: unknown) =>
    btoa(JSON.stringify(obj))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '')
  return `${enc({ alg: 'none' })}.${enc(payload)}.sig`
}

function makeImpersonationToken(opts: {
  sessionId?: string
  originalUserId?: string | null
  expiresAt?: number
}) {
  return makeJwt({
    scope: 'IMPERSONATION',
    impersonation: {
      sessionId: opts.sessionId ?? 'sess-123',
      originalUserId: opts.originalUserId ?? 'super-admin-1',
      expiresAt: opts.expiresAt ?? Math.floor(Date.now() / 1000) + 600,
    },
  })
}

// ── getImpersonationClaim ────────────────────────────────────────────────────
describe('getImpersonationClaim [Phase 4 / 4.1.6]', () => {
  it('parses a well-formed IMPERSONATION token', () => {
    const t = makeImpersonationToken({ sessionId: 'sess-A', expiresAt: 1717000000 })
    expect(getImpersonationClaim(t)).toEqual({
      sessionId: 'sess-A',
      originalUserId: 'super-admin-1',
      expiresAt: 1717000000,
    })
  })

  it('returns null for non-IMPERSONATION scopes', () => {
    expect(getImpersonationClaim(makeJwt({ scope: 'PLATFORM' }))).toBeNull()
    expect(getImpersonationClaim(makeJwt({ scope: 'TENANT' }))).toBeNull()
  })

  it('returns null when scope claim is missing', () => {
    expect(getImpersonationClaim(makeJwt({ uid: 'x' }))).toBeNull()
  })

  it('returns null when impersonation claim is missing or malformed', () => {
    expect(getImpersonationClaim(makeJwt({ scope: 'IMPERSONATION' }))).toBeNull()
    expect(
      getImpersonationClaim(
        makeJwt({ scope: 'IMPERSONATION', impersonation: 'not-an-object' }),
      ),
    ).toBeNull()
    expect(
      getImpersonationClaim(
        makeJwt({ scope: 'IMPERSONATION', impersonation: { sessionId: 'x' } }),
      ),
    ).toBeNull()
    expect(
      getImpersonationClaim(
        makeJwt({ scope: 'IMPERSONATION', impersonation: { expiresAt: 1 } }),
      ),
    ).toBeNull()
  })

  it('returns null for null / empty / malformed tokens', () => {
    expect(getImpersonationClaim(null)).toBeNull()
    expect(getImpersonationClaim(undefined)).toBeNull()
    expect(getImpersonationClaim('')).toBeNull()
    expect(getImpersonationClaim('not.a.jwt')).toBeNull()
  })

  it('upper-cases lower-case scope values for forward-compat', () => {
    const t = makeJwt({
      scope: 'impersonation',
      impersonation: { sessionId: 's', expiresAt: 1 },
    })
    expect(getImpersonationClaim(t)?.sessionId).toBe('s')
  })
})

// ── useImpersonation composable ──────────────────────────────────────────────
/**
 * Helper that mounts a tiny harness component so the composable runs inside a
 * real Vue lifecycle (so `onBeforeUnmount` etc. work).
 */
function mountWithComposable() {
  let api!: ReturnType<typeof useImpersonation>
  const Harness = defineComponent({
    setup() {
      api = useImpersonation()
      return () => h('div')
    },
  })
  const wrapper = mount(Harness)
  return { wrapper, api: () => api }
}

describe('useImpersonation [Phase 4 / 4.1.6]', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    accessTokenRef.value = null
    revokeMock.mockResolvedValue({ body: null })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('reports isImpersonating=false when no token / non-IMPERSONATION token', () => {
    accessTokenRef.value = null
    const { wrapper, api } = mountWithComposable()
    expect(api().isImpersonating.value).toBe(false)
    expect(api().sessionId.value).toBeNull()
    expect(api().remainingSeconds.value).toBe(0)
    wrapper.unmount()

    accessTokenRef.value = makeJwt({ scope: 'TENANT' })
    const second = mountWithComposable()
    expect(second.api().isImpersonating.value).toBe(false)
    second.wrapper.unmount()
  })

  it('exposes sessionId, originalUserId and a live MM:SS countdown', () => {
    const nowSeconds = 1_717_000_000
    accessTokenRef.value = makeImpersonationToken({
      sessionId: 'sess-X',
      originalUserId: 'super-1',
      expiresAt: nowSeconds + 125, // 2 minutes 5 seconds
    })

    const { wrapper, api } = mountWithComposable()
    api()._tick(nowSeconds)

    expect(api().isImpersonating.value).toBe(true)
    expect(api().sessionId.value).toBe('sess-X')
    expect(api().originalUserId.value).toBe('super-1')
    expect(api().remainingSeconds.value).toBe(125)
    expect(api().formattedRemaining.value).toBe('02:05')

    api()._tick(nowSeconds + 60)
    expect(api().remainingSeconds.value).toBe(65)
    expect(api().formattedRemaining.value).toBe('01:05')

    wrapper.unmount()
  })

  it('clamps remainingSeconds to 0 and exposes isExpired when past expiry', () => {
    const nowSeconds = 1_717_000_000
    accessTokenRef.value = makeImpersonationToken({ expiresAt: nowSeconds - 30 })
    const { wrapper, api } = mountWithComposable()
    api()._tick(nowSeconds)

    expect(api().remainingSeconds.value).toBe(0)
    expect(api().formattedRemaining.value).toBe('00:00')
    expect(api().isExpired.value).toBe(true)

    wrapper.unmount()
  })

  it('endImpersonation calls revoke then doLogout', async () => {
    accessTokenRef.value = makeImpersonationToken({ sessionId: 'sess-END' })
    const { wrapper, api } = mountWithComposable()

    await api().endImpersonation()

    expect(revokeMock).toHaveBeenCalledWith('sess-END')
    expect(doLogoutMock).toHaveBeenCalledOnce()
    expect(api().ending.value).toBe(false)
    wrapper.unmount()
  })

  it('endImpersonation still logs out when revoke API fails (idempotent)', async () => {
    accessTokenRef.value = makeImpersonationToken({ sessionId: 'sess-FAIL' })
    revokeMock.mockRejectedValueOnce(new Error('500'))
    const { wrapper, api } = mountWithComposable()

    await api().endImpersonation()

    expect(revokeMock).toHaveBeenCalledOnce()
    expect(doLogoutMock).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('endImpersonation is a no-op when there is no session', async () => {
    accessTokenRef.value = null
    const { wrapper, api } = mountWithComposable()
    await api().endImpersonation()
    expect(revokeMock).not.toHaveBeenCalled()
    expect(doLogoutMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('clears the internal timer on unmount (no leak)', async () => {
    const clearSpy = vi.spyOn(globalThis, 'clearInterval')
    accessTokenRef.value = makeImpersonationToken({})
    const { wrapper } = mountWithComposable()
    wrapper.unmount()
    await flushPromises()
    expect(clearSpy).toHaveBeenCalled()
    clearSpy.mockRestore()
  })
})
