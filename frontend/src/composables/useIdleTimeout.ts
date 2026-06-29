import { ref, onUnmounted, watch } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { getIdleTimeout, idleLogout } from '@/api/setting'
import router from '@/router'

const DEFAULT_IDLE_TIMEOUT_MINUTES = 15
const CHECK_INTERVAL_MS = 30_000
const THROTTLE_MS = 30_000
const WARNING_BEFORE_MS = 2 * 60 * 1000 // 2 minutes

const CHANNEL_NAME = 'idle-timeout-sync'

export function useIdleTimeout() {
  const authStore = useAuthStore()

  const timeoutMinutes = ref(DEFAULT_IDLE_TIMEOUT_MINUTES)
  const showWarning = ref(false)
  const remainingSeconds = ref(0)

  let lastActivityTime = Date.now()
  let checkTimer: ReturnType<typeof setInterval> | null = null
  let countdownTimer: ReturnType<typeof setInterval> | null = null
  let throttleTimer: ReturnType<typeof setTimeout> | null = null
  let channel: BroadcastChannel | null = null
  let active = false

  const timeoutMs = () => timeoutMinutes.value * 60 * 1000

  function resetActivity() {
    lastActivityTime = Date.now()
    if (showWarning.value) {
      showWarning.value = false
      stopCountdown()
    }
    // Broadcast to other tabs
    channel?.postMessage({ type: 'activity', time: lastActivityTime })
  }

  function onUserActivity() {
    if (!active) return
    if (throttleTimer) return
    throttleTimer = setTimeout(() => {
      throttleTimer = null
    }, THROTTLE_MS)
    resetActivity()
  }

  function checkIdle() {
    if (!active) return
    const elapsed = Date.now() - lastActivityTime
    const remaining = timeoutMs() - elapsed

    if (remaining <= 0) {
      doIdleLogout()
    } else if (remaining <= WARNING_BEFORE_MS && !showWarning.value) {
      showWarning.value = true
      startCountdown(remaining)
    }
  }

  function startCountdown(remainingMs: number) {
    remainingSeconds.value = Math.ceil(remainingMs / 1000)
    stopCountdown()
    countdownTimer = setInterval(() => {
      remainingSeconds.value--
      if (remainingSeconds.value <= 0) {
        doIdleLogout()
      }
    }, 1000)
  }

  function stopCountdown() {
    if (countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }

  async function doIdleLogout() {
    if (!active) return
    stop()
    // Notify other tabs to logout locally (without calling API)
    channel?.postMessage({ type: 'idle_logout' })
    try {
      await idleLogout()
    } catch {
      // fire-and-forget: still clear local state
    }
    authStore.clearAuth()
    await router.push('/login')
  }

  function handleBroadcast(event: MessageEvent) {
    if (event.data?.type === 'activity') {
      lastActivityTime = event.data.time
      if (showWarning.value) {
        showWarning.value = false
        stopCountdown()
      }
    } else if (event.data?.type === 'idle_logout') {
      // Another tab triggered idle logout — just clear local state
      stop()
      authStore.clearAuth()
      router.push('/login')
    }
  }

  function handleVisibilityChange() {
    if (document.visibilityState === 'visible' && active) {
      // Re-check immediately when tab becomes visible (setInterval may have been throttled)
      checkIdle()
    }
  }

  const EVENTS = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart'] as const

  async function start() {
    if (active) return

    try {
      const res = await getIdleTimeout()
      if (res.body && res.body > 0) {
        timeoutMinutes.value = res.body
      }
    } catch (err: unknown) {
      timeoutMinutes.value = DEFAULT_IDLE_TIMEOUT_MINUTES
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status !== 401 && status !== 403) {
        console.warn('[useIdleTimeout] getIdleTimeout failed, using default', err)
      }
    }

    active = true
    lastActivityTime = Date.now()

    EVENTS.forEach((e) => document.addEventListener(e, onUserActivity, { passive: true }))
    document.addEventListener('visibilitychange', handleVisibilityChange)

    checkTimer = setInterval(checkIdle, CHECK_INTERVAL_MS)

    channel = new BroadcastChannel(CHANNEL_NAME)
    channel.onmessage = handleBroadcast
  }

  function stop() {
    active = false
    EVENTS.forEach((e) => document.removeEventListener(e, onUserActivity))
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    if (checkTimer) {
      clearInterval(checkTimer)
      checkTimer = null
    }
    stopCountdown()
    if (throttleTimer) {
      clearTimeout(throttleTimer)
      throttleTimer = null
    }
    if (channel) {
      channel.close()
      channel = null
    }
    showWarning.value = false
  }

  function continueSession() {
    resetActivity()
  }

  // Auto-start when authenticated, auto-stop when not
  const stopWatcher = watch(
    () => authStore.isAuthenticated,
    (authenticated) => {
      if (authenticated) {
        start()
      } else {
        stop()
      }
    },
    { immediate: true },
  )

  onUnmounted(() => {
    stop()
    stopWatcher()
  })

  return {
    showWarning,
    remainingSeconds,
    continueSession,
  }
}
