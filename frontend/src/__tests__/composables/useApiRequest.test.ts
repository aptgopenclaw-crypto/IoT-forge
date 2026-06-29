import { describe, it, expect, vi, beforeEach } from 'vitest'
import { effectScope, nextTick } from 'vue'
import { useApiRequest } from '@/composables/useApiRequest'
import type { BaseResponse } from '@/types/auth'

// Mock useApiError
const handleErrorMock = vi.fn()
vi.mock('@/composables/useApiError', () => ({
  useApiError: () => ({ handleError: handleErrorMock }),
}))

function makeResponse<T>(body: T, errorCode = '00000'): BaseResponse<T> {
  return { errorCode, errorMsg: '', errorDetail: '', timestamp: '', body }
}

function makeErrorResponse(errorCode: string, errorMsg = ''): BaseResponse<null> {
  return { errorCode, errorMsg, errorDetail: '', timestamp: '', body: null }
}

describe('useApiRequest', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    vi.clearAllMocks()
    scope = effectScope()
  })

  it('sets loading=true during execution and false after', async () => {
    let resolveFn: (v: BaseResponse<string>) => void
    const apiFn = vi.fn(() => new Promise<BaseResponse<string>>((r) => { resolveFn = r }))

    const { loading, execute } = scope.run(() => useApiRequest(apiFn))!

    expect(loading.value).toBe(false)

    const promise = execute()
    expect(loading.value).toBe(true)

    resolveFn!(makeResponse('hello'))
    await promise

    expect(loading.value).toBe(false)
    scope.stop()
  })

  it('returns body and sets data on success (errorCode 00000)', async () => {
    const apiFn = vi.fn(() => Promise.resolve(makeResponse({ id: 1, name: 'test' })))

    const { data, error, execute } = scope.run(() => useApiRequest(apiFn))!

    const result = await execute()

    expect(result).toEqual({ id: 1, name: 'test' })
    expect(data.value).toEqual({ id: 1, name: 'test' })
    expect(error.value).toBeNull()
    scope.stop()
  })

  it('handles business error (non-00000 errorCode)', async () => {
    const apiFn = vi.fn(() => Promise.resolve(makeErrorResponse('20005', 'User not found')))

    const { data, error, execute } = scope.run(() =>
      useApiRequest(apiFn, { codeMessages: { '20005': 'Custom: user not found' } }),
    )!

    const result = await execute()

    expect(result).toBeUndefined()
    expect(data.value).toBeNull()
    expect(error.value).toBe('Custom: user not found')
    expect(handleErrorMock).toHaveBeenCalled()
    scope.stop()
  })

  it('uses errorMsg as fallback when no codeMessages match', async () => {
    const apiFn = vi.fn(() => Promise.resolve(makeErrorResponse('99999', 'Server error')))

    const { error, execute } = scope.run(() => useApiRequest(apiFn))!

    await execute()

    expect(error.value).toBe('Server error')
    scope.stop()
  })

  it('handles network/exception errors', async () => {
    const apiFn = vi.fn(() => Promise.reject(new Error('Network Error')))

    const { loading, error, execute } = scope.run(() =>
      useApiRequest(apiFn, { fallbackMessage: 'Request failed' }),
    )!

    const result = await execute()

    expect(result).toBeUndefined()
    expect(error.value).toBe('Request failed')
    expect(loading.value).toBe(false)
    expect(handleErrorMock).toHaveBeenCalled()
    scope.stop()
  })

  it('discards stale results when discardStale=true (default)', async () => {
    let callCount = 0
    const apiFn = vi.fn(() => {
      const count = ++callCount
      // First call resolves slowly, second resolves immediately
      if (count === 1) {
        return new Promise<BaseResponse<number>>((r) =>
          setTimeout(() => r(makeResponse(1)), 50),
        )
      }
      return Promise.resolve(makeResponse(2))
    })

    const { data, execute } = scope.run(() => useApiRequest(apiFn))!

    // Fire two calls rapidly — first should be discarded
    const p1 = execute()
    const p2 = execute()

    const r1 = await p1
    const r2 = await p2

    // First call's result is discarded (stale)
    expect(r1).toBeUndefined()
    // Second call wins
    expect(r2).toBe(2)
    expect(data.value).toBe(2)
    scope.stop()
  })

  it('keeps all results when discardStale=false', async () => {
    let callCount = 0
    const apiFn = vi.fn(() => {
      const count = ++callCount
      return Promise.resolve(makeResponse(count))
    })

    const { data, execute } = scope.run(() =>
      useApiRequest(apiFn, { discardStale: false }),
    )!

    await execute()
    expect(data.value).toBe(1)
    await execute()
    expect(data.value).toBe(2)
    scope.stop()
  })

  it('does not show error when showError=false', async () => {
    const apiFn = vi.fn(() => Promise.reject(new Error('fail')))

    const { execute } = scope.run(() =>
      useApiRequest(apiFn, { showError: false, fallbackMessage: 'oops' }),
    )!

    await execute()

    expect(handleErrorMock).not.toHaveBeenCalled()
    scope.stop()
  })

  it('passes arguments through to the api function', async () => {
    const apiFn = vi.fn((_a: string, _b: number) => Promise.resolve(makeResponse('ok')))

    const { execute } = scope.run(() => useApiRequest(apiFn))!

    await (execute as (...args: unknown[]) => Promise<unknown>)('hello', 42)

    expect(apiFn).toHaveBeenCalledWith('hello', 42)
    scope.stop()
  })

  it('clears previous error on new execute call', async () => {
    let shouldFail = true
    const apiFn = vi.fn(() => {
      if (shouldFail) return Promise.reject(new Error('fail'))
      return Promise.resolve(makeResponse('ok'))
    })

    const { error, execute } = scope.run(() =>
      useApiRequest(apiFn, { fallbackMessage: 'failed' }),
    )!

    await execute()
    expect(error.value).toBe('failed')

    shouldFail = false
    await execute()
    expect(error.value).toBeNull()
    scope.stop()
  })
})
