import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios'

interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
  _retryCount?: number
}

/** Max retry attempts for idempotent GET requests */
export const MAX_RETRIES = 2
/** Base delay in ms (exponential: 1000, 2000) */
export const RETRY_BASE_DELAY = 1000
/** HTTP status codes eligible for retry */
export const RETRYABLE_STATUS = new Set([503, 504])
/** Axios error codes eligible for retry */
export const RETRYABLE_CODES = new Set(['ECONNABORTED', 'ERR_NETWORK', 'ETIMEDOUT'])

const axiosIns: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/v1',
  timeout: 15000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    'X-Requested-With': 'XMLHttpRequest',
  },
})

// --- Callback injection (avoid circular import with authStore) ---
let _refreshHandler: (() => Promise<string>) | null = null
let _logoutHandler: (() => void) | null = null

export function setAxiosToken(token: string | null) {
  if (token) {
    axiosIns.defaults.headers.common['Authorization'] = `Bearer ${token}`
  } else {
    delete axiosIns.defaults.headers.common['Authorization']
  }
}

export function setAxiosRefreshHandler(handler: () => Promise<string>) {
  _refreshHandler = handler
}

export function setAxiosLogoutHandler(handler: () => void) {
  _logoutHandler = handler
}

// Request interceptor
axiosIns.interceptors.request.use((config) => {
  // 多語系：將前端目前 locale 一律帶入 Accept-Language；後端會在白名單外 fallback
  try {
    const lang = localStorage.getItem('locale')
    if (lang) {
      config.headers.set('Accept-Language', lang)
    }
  } catch {
    // localStorage 在 SSR / 隱私模式可能失效；忽略
  }

  // N-10: Extend timeout for download / export requests (blob or matching URL patterns)
  if (
    config.responseType === 'blob' ||
    /\/(export|download)\b/.test(config.url ?? '')
  ) {
    config.timeout = 60_000
  }

  return config
})

// Response interceptor — unwrap res.data
// Singleton refresh: prevent multiple 401s from triggering multiple refreshes
let refreshPromise: Promise<string> | null = null

axiosIns.interceptors.response.use(
  (res) => res.data,
  async (err) => {
    const originalRequest = err.config as RetryConfig
    // --- 401 token refresh logic ---
    if (
      err.response?.status === 401 &&
      !originalRequest._retry &&
      _refreshHandler &&
      !originalRequest.url?.includes('/noauth/token/refresh')
    ) {
      originalRequest._retry = true
      if (!refreshPromise) {
        refreshPromise = _refreshHandler()
          .catch(() => {
            if (_logoutHandler) _logoutHandler()
            return Promise.reject(err)
          })
          .finally(() => {
            refreshPromise = null
          })
      }
      try {
        const newToken = await refreshPromise
        originalRequest.headers['Authorization'] = `Bearer ${newToken}`
        return axiosIns(originalRequest)
      } catch {
        return Promise.reject(err)
      }
    }

    // --- N-11: GET retry with exponential backoff ---
    const method = (originalRequest.method ?? '').toUpperCase()
    const retryCount = originalRequest._retryCount ?? 0
    const isRetryableMethod = method === 'GET' || method === 'HEAD'
    const isRetryableError =
      RETRYABLE_CODES.has(err.code ?? '') ||
      RETRYABLE_STATUS.has(err.response?.status ?? 0)

    if (isRetryableMethod && isRetryableError && retryCount < MAX_RETRIES) {
      originalRequest._retryCount = retryCount + 1
      const delay = RETRY_BASE_DELAY * Math.pow(2, retryCount) // 1000, 2000
      await new Promise(resolve => setTimeout(resolve, delay))
      return axiosIns(originalRequest)
    }

    return Promise.reject(err)
  },
)

export default axiosIns
