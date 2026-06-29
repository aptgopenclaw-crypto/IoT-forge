import { ref, shallowRef, type Ref, type ShallowRef } from 'vue'
import { useApiError } from './useApiError'
import type { BaseResponse } from '@/types/auth'

export interface UseApiRequestOptions {
  /** Error code → message map forwarded to useApiError.handleError */
  codeMessages?: Record<string, string>
  /** Fallback error message when no code mapping matches */
  fallbackMessage?: string
  /** If true, discard results from stale (superseded) calls (default: true) */
  discardStale?: boolean
  /** If true, show error via ElMessage (default: true) */
  showError?: boolean
}

export interface UseApiRequestReturn<T> {
  /** Reactive loading state */
  loading: Ref<boolean>
  /** Reactive error message from the last failed call (null when ok) */
  error: Ref<string | null>
  /** Reactive data from the last successful response body */
  data: ShallowRef<T | null>
  /** Execute the API call. Returns the body on success, or undefined on error/stale. */
  execute: (...args: never[]) => Promise<T | undefined>
}

/**
 * Composable that wraps an API function with unified loading / error / data handling.
 *
 * Features:
 * - Reactive `loading`, `error`, `data` refs
 * - Automatic success check (`errorCode === '00000'`)
 * - Stale-request discard (rapid consecutive calls only keep the latest result)
 * - Error display via `useApiError` integration
 *
 * @example Query usage
 * const { loading, data, error, execute } = useApiRequest(
 *   (params: UserListQuery) => listUsers(params),
 *   { fallbackMessage: t('user.list.loadFailed') }
 * )
 * await execute({ page: 0, size: 20 })
 *
 * @example Mutation usage
 * const { loading, execute } = useApiRequest(
 *   (id: string) => disableUser(id),
 *   { codeMessages: { '20005': t('user.list.userNotFound') } }
 * )
 * const result = await execute(userId)
 * if (result) showSuccess()
 */
export function useApiRequest<T, TArgs extends unknown[] = unknown[]>(
  apiFn: (...args: TArgs) => Promise<BaseResponse<T>>,
  options: UseApiRequestOptions = {},
): UseApiRequestReturn<T> {
  const { codeMessages, fallbackMessage, discardStale = true, showError = true } = options

  const loading = ref(false)
  const error = ref<string | null>(null)
  const data = shallowRef<T | null>(null)

  const { handleError } = useApiError()

  let callId = 0

  async function execute(...args: unknown[]): Promise<T | undefined> {
    const thisCallId = ++callId
    loading.value = true
    error.value = null

    try {
      const res = await apiFn(...(args as unknown as TArgs))

      // Discard stale: a newer call has been issued while we were awaiting
      if (discardStale && thisCallId !== callId) {
        return undefined
      }

      if (res.errorCode === '00000') {
        data.value = res.body
        return res.body
      }

      // Business-level error (non-00000 code but HTTP 200)
      const msg = codeMessages?.[res.errorCode] || res.errorMsg || fallbackMessage || res.errorCode
      error.value = msg
      if (showError) {
        handleError({ response: { data: res } }, codeMessages, fallbackMessage)
      }
      return undefined
    } catch (err: unknown) {
      // Discard stale
      if (discardStale && thisCallId !== callId) {
        return undefined
      }

      error.value = fallbackMessage || 'Unknown error'
      if (showError) {
        handleError(err, codeMessages, fallbackMessage)
      }
      return undefined
    } finally {
      // Only flip loading off if this is the latest call
      if (!discardStale || thisCallId === callId) {
        loading.value = false
      }
    }
  }

  return {
    loading,
    error,
    data,
    execute: execute as (...args: never[]) => Promise<T | undefined>,
  }
}

