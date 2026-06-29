/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API base path (e.g. /v1 or http://localhost:8080/v1) */
  readonly VITE_API_BASE_URL: string
  /** Sentry DSN — omit or leave empty to disable */
  readonly VITE_SENTRY_DSN?: string
  /** Enable IoT module feature flag ('true' | 'false') */
  readonly VITE_ENABLE_IOT?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
