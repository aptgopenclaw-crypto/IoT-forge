import { describe, it, expect, vi, beforeEach } from 'vitest'
import { effectScope } from 'vue'
import { useCancelableRequest } from '@/composables/useCancelableRequest'

describe('useCancelableRequest', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    scope = effectScope()
  })

  it('should resolve with the result of fn', async () => {
    const { withCancel } = scope.run(() => useCancelableRequest())!
    const result = await withCancel((_signal) => Promise.resolve('data'))
    expect(result).toBe('data')
    scope.stop()
  })

  it('should pass an AbortSignal to fn', async () => {
    const { withCancel } = scope.run(() => useCancelableRequest())!
    let receivedSignal: AbortSignal | null = null

    await withCancel((signal) => {
      receivedSignal = signal
      return Promise.resolve()
    })

    expect(receivedSignal).toBeInstanceOf(AbortSignal)
    expect(receivedSignal!.aborted).toBe(false)
    scope.stop()
  })

  it('should abort the previous request when a new one starts', async () => {
    const { withCancel } = scope.run(() => useCancelableRequest())!
    let firstSignal: AbortSignal | null = null

    // Start a request that never resolves
    const firstPromise = withCancel((signal) => {
      firstSignal = signal
      return new Promise(() => {}) // never resolves
    })

    // Start a second request — should abort the first
    const secondResult = await withCancel((_signal) => Promise.resolve('second'))

    expect(firstSignal!.aborted).toBe(true)
    expect(secondResult).toBe('second')
    scope.stop()
  })

  it('should abort on scope dispose (component unmount)', async () => {
    let capturedSignal: AbortSignal | null = null
    const innerScope = effectScope()

    const { withCancel } = innerScope.run(() => useCancelableRequest())!

    // Start a request
    const promise = withCancel((signal) => {
      capturedSignal = signal
      return new Promise(() => {}) // never resolves
    })

    // Dispose the scope (simulates component unmount)
    innerScope.stop()

    expect(capturedSignal!.aborted).toBe(true)
  })

  it('isCanceled should detect AbortError', () => {
    const { isCanceled } = scope.run(() => useCancelableRequest())!

    const abortError = new DOMException('The operation was aborted.', 'AbortError')
    expect(isCanceled(abortError)).toBe(true)
    scope.stop()
  })

  it('isCanceled should detect axios ERR_CANCELED', () => {
    const { isCanceled } = scope.run(() => useCancelableRequest())!

    const axiosError = { code: 'ERR_CANCELED', message: 'canceled' }
    expect(isCanceled(axiosError)).toBe(true)
    scope.stop()
  })

  it('isCanceled should return false for other errors', () => {
    const { isCanceled } = scope.run(() => useCancelableRequest())!

    expect(isCanceled(new Error('network error'))).toBe(false)
    expect(isCanceled(null)).toBe(false)
    expect(isCanceled(undefined)).toBe(false)
    scope.stop()
  })

  it('cancel() should explicitly abort the current request', async () => {
    const { withCancel, cancel } = scope.run(() => useCancelableRequest())!
    let capturedSignal: AbortSignal | null = null

    const promise = withCancel((signal) => {
      capturedSignal = signal
      return new Promise(() => {})
    })

    cancel()
    expect(capturedSignal!.aborted).toBe(true)
    scope.stop()
  })

  it('should propagate non-cancel errors normally', async () => {
    const { withCancel } = scope.run(() => useCancelableRequest())!

    await expect(
      withCancel((_signal) => Promise.reject(new Error('server error')))
    ).rejects.toThrow('server error')
    scope.stop()
  })
})
