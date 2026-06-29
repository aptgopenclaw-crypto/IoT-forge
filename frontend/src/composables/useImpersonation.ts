import { computed, onBeforeUnmount, ref, type Ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { revokeImpersonation } from '@/api/impersonation'

/**
 * [Phase 4 / 4.1.6 / ADR-002] Shape of the `impersonation` JWT claim issued by
 * `ImpersonationService.create()`. Backend reference:
 * `com.taipei.iot.common.util.JwtClaimKeys.IMPERSONATION`.
 */
export interface ImpersonationClaim {
  originalUserId: string | null
  sessionId: string
  /** Epoch seconds — same unit as standard `exp`. */
  expiresAt: number
}

interface JwtPayloadLike {
  scope?: unknown
  impersonation?: unknown
}

function decodeJwtPayload(token: string): JwtPayloadLike | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const json = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))
    const parsed = JSON.parse(json)
    return typeof parsed === 'object' && parsed !== null ? (parsed as JwtPayloadLike) : null
  } catch {
    return null
  }
}

/**
 * Returns the `impersonation` claim from an IMPERSONATION-scope JWT, or `null`
 * if the token is not an impersonation token / the claim is malformed.
 *
 * Exported so the composable's unit tests can drive it directly without having
 * to instantiate the whole pinia store + axios chain.
 */
export function getImpersonationClaim(
  token: string | null | undefined,
): ImpersonationClaim | null {
  if (!token) return null
  const payload = decodeJwtPayload(token)
  if (!payload) return null
  if (typeof payload.scope !== 'string' || payload.scope.toUpperCase() !== 'IMPERSONATION') {
    return null
  }
  const raw = payload.impersonation
  if (!raw || typeof raw !== 'object') return null
  const claim = raw as Record<string, unknown>
  const sessionId = claim.sessionId
  const expiresAt = claim.expiresAt
  if (typeof sessionId !== 'string' || typeof expiresAt !== 'number') return null
  const originalUserId =
    typeof claim.originalUserId === 'string' ? claim.originalUserId : null
  return { originalUserId, sessionId, expiresAt }
}

function formatMmSs(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds))
  const mm = String(Math.floor(safe / 60)).padStart(2, '0')
  const ss = String(safe % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

/**
 * [Phase 4 / 4.1.6] Reactive impersonation session state for the banner.
 *
 * Drives off `authStore.accessToken` so callers don't need to thread tokens
 * through props — the same source of truth used by the rest of the auth
 * pipeline. A 1-second `setInterval` keeps `remainingSeconds` /
 * `formattedRemaining` ticking; the interval is cleared on component unmount
 * so banners on transient pages don't leak timers.
 *
 * Tests can advance time deterministically by calling `_tick(epochSeconds)`.
 */
export function useImpersonation() {
  const authStore = useAuthStore()
  const ending = ref(false)
  const now: Ref<number> = ref(Math.floor(Date.now() / 1000))
  let timer: ReturnType<typeof setInterval> | null = null

  const claim = computed<ImpersonationClaim | null>(() =>
    getImpersonationClaim(authStore.accessToken),
  )
  const isImpersonating = computed(() => claim.value !== null)
  const sessionId = computed(() => claim.value?.sessionId ?? null)
  const originalUserId = computed(() => claim.value?.originalUserId ?? null)
  const remainingSeconds = computed(() => {
    if (!claim.value) return 0
    return Math.max(0, claim.value.expiresAt - now.value)
  })
  const formattedRemaining = computed(() => formatMmSs(remainingSeconds.value))
  const isExpired = computed(() => isImpersonating.value && remainingSeconds.value === 0)

  if (typeof window !== 'undefined') {
    timer = setInterval(() => {
      now.value = Math.floor(Date.now() / 1000)
    }, 1000)
  }

  onBeforeUnmount(() => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  })

  /**
   * Revoke the current impersonation session on the backend then clear the
   * local auth state and bounce the user to /login. Network failures are
   * swallowed because revoke is idempotent — the worst case is a session that
   * expires naturally a few minutes later.
   */
  async function endImpersonation() {
    const id = sessionId.value
    if (!id || ending.value) return
    ending.value = true
    try {
      try {
        await revokeImpersonation(id)
      } catch {
        // ignore — DELETE is idempotent server-side
      }
      await authStore.doLogout()
    } finally {
      ending.value = false
    }
  }

  return {
    // ── State ──
    isImpersonating,
    sessionId,
    originalUserId,
    remainingSeconds,
    formattedRemaining,
    isExpired,
    ending,
    // ── Actions ──
    endImpersonation,
    // ── Test hook ──
    /** Force-set the internal `now` reference (epoch seconds). Tests only. */
    _tick: (epochSeconds: number) => {
      now.value = epochSeconds
    },
  }
}
