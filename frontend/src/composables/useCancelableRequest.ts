import { onScopeDispose } from 'vue'

/**
 * Composable that prevents request race conditions by aborting stale requests.
 *
 * When a new request is made via `withCancel`, any previously pending request
 * from the same instance is automatically cancelled via AbortController.
 * The AbortController is also cleaned up when the component/scope is disposed.
 *
 * @example
 * const { withCancel } = useCancelableRequest()
 *
 * async function loadPage(page: number) {
 *   const res = await withCancel((signal) =>
 *     axiosIns.get('/users', { params: { page }, signal })
 *   )
 *   // only reaches here if NOT aborted
 *   items.value = res.data.body.content
 * }
 */
export function useCancelableRequest() {
  let controller: AbortController | null = null

  /**
   * Execute an async function with an AbortSignal.
   * Aborts any previous in-flight request from this instance.
   *
   * @param fn - Receives an AbortSignal to pass to axios (or fetch).
   * @returns The resolved value of `fn`, or throws if aborted.
   */
  async function withCancel<T>(fn: (signal: AbortSignal) => Promise<T>): Promise<T> {
    // Abort previous request if still pending
    if (controller) {
      controller.abort()
    }
    controller = new AbortController()
    const { signal } = controller

    try {
      const result = await fn(signal)
      return result
    } catch (err: unknown) {
      // Re-throw non-cancel errors; swallow cancellations silently
      if (err instanceof DOMException && err.name === 'AbortError') {
        throw err
      }
      // Axios wraps AbortError in its own error with code 'ERR_CANCELED'
      if (
        typeof err === 'object' && err !== null &&
        'code' in err && (err as { code: string }).code === 'ERR_CANCELED'
      ) {
        throw err
      }
      throw err
    }
  }

  /**
   * Check if an error is a cancellation (AbortError or axios ERR_CANCELED).
   */
  function isCanceled(err: unknown): boolean {
    if (err instanceof DOMException && err.name === 'AbortError') return true
    if (
      typeof err === 'object' && err !== null &&
      'code' in err && (err as { code: string }).code === 'ERR_CANCELED'
    ) return true
    return false
  }

  /** Abort the current in-flight request, if any. */
  function cancel() {
    if (controller) {
      controller.abort()
      controller = null
    }
  }

  // Auto-cancel on scope dispose (component unmount / watcher cleanup)
  onScopeDispose(cancel)

  return { withCancel, isCanceled, cancel }
}
