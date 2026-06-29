import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getCaptcha, getUserInfo, login, logout, refreshTokenApi, selectTenant, switchTenant } from '@/api/auth'
import { setAxiosToken, setAxiosRefreshHandler, setAxiosLogoutHandler } from '@/api/axios/axiosIns'
import { useTenantStore } from '@/stores/tenantStore'
import { useMenuStore } from '@/stores/menuStore'
import { useDeptStore } from '@/stores/deptStore'
import router from '@/router'
import { resolveLandingPath, getTokenScope, type TokenScope } from '@/router/guards'
import { getImpersonationClaim, type ImpersonationClaim } from '@/composables/useImpersonation'
import type { LoginRequest, UserInfoDto, TenantOption } from '@/types/auth'
import { getJwtRemainingMs } from '@/utils/jwt'

const REFRESH_BEFORE_EXPIRY_MS = 60_000 // refresh 60s before token expires

let _refreshTimer: ReturnType<typeof setTimeout> | null = null

export const useAuthStore = defineStore('auth', () => {
  // ── State ──
  const accessToken = ref<string | null>(null)
  const userInfo = ref<UserInfoDto | null>(null)
  const temporaryToken = ref<string | null>(null)
  /** [Phase 3] Short-lived `password_change` token from login */
  const passwordChangeToken = ref<string | null>(null)

  // ── Getters ──
  const isAuthenticated = computed(() => !!sessionStorage.getItem('passExam'))

  /**
   * [Phase 4 / 4.1.7 / ADR-004 / ADR-007] Derived `scope` claim from the
   * current access token. Components/guards should treat `null` as the legacy
   * fallback (= TENANT) — same convention as `router/guards.ts`.
   */
  const scope = computed<TokenScope | null>(() => getTokenScope(accessToken.value))

  /**
   * [Phase 4 / 4.1.7 / ADR-002] Derived `impersonation` claim. Non-null only
   * when the access token is an IMPERSONATION-scope JWT. Single source of
   * truth for the banner + future `/platform/impersonations` page.
   */
  const impersonation = computed<ImpersonationClaim | null>(() =>
    getImpersonationClaim(accessToken.value),
  )

  /** True when the current session is a super_admin impersonating a tenant user. */
  const isImpersonating = computed(() => impersonation.value !== null)

  // ── Internal ──
  function _scheduleTokenRefresh(token: string) {
    if (_refreshTimer) {
      clearTimeout(_refreshTimer)
      _refreshTimer = null
    }
    const remainingMs = getJwtRemainingMs(token)
    if (remainingMs === null) return
    const delay = Math.max(remainingMs - REFRESH_BEFORE_EXPIRY_MS, 0)
    _refreshTimer = setTimeout(async () => {
      try {
        const res = await refreshTokenApi()
        const newToken = res.body.accessToken
        accessToken.value = newToken
        setAxiosToken(newToken)
        _scheduleTokenRefresh(newToken)
      } catch {
        // If refresh fails, the 401 interceptor will handle it
      }
    }, delay)
  }

  function _initAxiosCallbacks() {
    setAxiosRefreshHandler(async () => {
      const res = await refreshTokenApi()
      const newToken = res.body.accessToken
      accessToken.value = newToken
      setAxiosToken(newToken)
      _scheduleTokenRefresh(newToken)
      return newToken
    })
    setAxiosLogoutHandler(() => {
      clearAuth()
      router.push('/login')
    })
    if (accessToken.value) {
      setAxiosToken(accessToken.value)
      _scheduleTokenRefresh(accessToken.value)
    }
  }

  // ── Actions ──
  async function doGetCaptcha() {
    const res = await getCaptcha()
    return res.body
  }

  async function doLogin(payload: LoginRequest) {
    const res = await login(payload)
    const data = res.body
    if (data.passwordChangeRequired) {
      passwordChangeToken.value = data.accessToken
      await router.push('/force-change-password')
      return
    }
    if (!data.needsSelection) {
      accessToken.value = data.accessToken
      setAxiosToken(data.accessToken)
      _scheduleTokenRefresh(data.accessToken)
      sessionStorage.setItem('passExam', 'true')
      // [Phase 4 / 4.1.4] Scope-based landing: PLATFORM → /platform/tenants,
      // IMPERSONATION → /?impersonating=1, TENANT → /.
      await router.push(resolveLandingPath(data.accessToken))
    } else {
      // [Phase 5 / ADR-007] needsSelection=true is now only reached by
      // non-super-admin users mapped to multiple tenants. super_admin always
      // takes the !needsSelection branch above and lands on /platform/tenants.
      temporaryToken.value = data.accessToken
      setAxiosToken(data.accessToken)
      const tenantStore = useTenantStore()
      tenantStore.setTenantList(data.tenants as TenantOption[])
      await router.push('/select-tenant')
    }
  }

  async function applyPostForceChangeLogin(data: import('@/types/auth').LoginResult) {
    passwordChangeToken.value = null
    if (!data.needsSelection) {
      accessToken.value = data.accessToken
      setAxiosToken(data.accessToken)
      _scheduleTokenRefresh(data.accessToken)
      sessionStorage.setItem('passExam', 'true')
      // [Phase 4 / 4.1.4] Same scope-based landing as doLogin.
      await router.push(resolveLandingPath(data.accessToken))
    } else {
      // [Phase 5 / ADR-007] Same simplification as doLogin: super_admin no
      // longer goes through select-tenant.
      temporaryToken.value = data.accessToken
      setAxiosToken(data.accessToken)
      const tenantStore = useTenantStore()
      tenantStore.setTenantList(data.tenants as TenantOption[])
      await router.push('/select-tenant')
    }
  }

  async function doSelectTenant(tenantId: string) {
    const res = await selectTenant({ tenantId })
    const data = res.body
    accessToken.value = data.accessToken
    setAxiosToken(data.accessToken)
    _scheduleTokenRefresh(data.accessToken)
    temporaryToken.value = null
    localStorage.setItem('lastTenantId', tenantId)
    sessionStorage.setItem('passExam', 'true')
    // [Phase 4 / 4.1.4] After selecting a tenant the new access token carries
    // the resolved scope (PLATFORM for super_admin, TENANT otherwise) — honour
    // it so super_admin lands on the platform shell instead of the green one.
    await router.push(resolveLandingPath(data.accessToken))
  }

  async function doSwitchTenant(tenantId: string) {
    const res = await switchTenant({ tenantId })
    const data = res.body
    accessToken.value = data.accessToken
    setAxiosToken(data.accessToken)
    _scheduleTokenRefresh(data.accessToken)
    localStorage.setItem('lastTenantId', tenantId)
    window.location.reload()
  }

  /**
   * [Phase 4 / 4.1.9 / ADR-002] Swap the current PLATFORM access token for a
   * freshly-minted IMPERSONATION token returned by
   * `POST /v1/platform/impersonations`. We re-use the `doSwitchTenant`
   * mechanic of "store new token + full reload" so menus / userInfo / dept
   * caches all rebuild against the impersonated tenant context.
   */
  function applyImpersonationToken(token: string, landingPath?: string) {
    accessToken.value = token
    setAxiosToken(token)
    _scheduleTokenRefresh(token)
    sessionStorage.setItem('passExam', 'true')
    // Hard reload so every pinia store + cached query refreshes against the
    // impersonated tenant.
    if (typeof window !== 'undefined') {
      const target = landingPath ?? resolveLandingPath(token)
      window.location.assign(target)
    }
  }

  async function getPermission() {
    const res = await getUserInfo()
    userInfo.value = res.body
    const tenantStore = useTenantStore()
    tenantStore.setTenantList(userInfo.value.availableTenants)
  }

  async function restoreSession() {
    if (accessToken.value) return
    const res = await refreshTokenApi()
    const newToken = res.body.accessToken
    accessToken.value = newToken
    setAxiosToken(newToken)
    _scheduleTokenRefresh(newToken)
  }

  async function doLogout() {
    try { await logout() } catch { /* ignore */ }
    clearAuth()
    await router.push('/login')
  }

  function clearAuth() {
    accessToken.value = null
    userInfo.value = null
    temporaryToken.value = null
    passwordChangeToken.value = null
    setAxiosToken(null)
    sessionStorage.removeItem('passExam')
    if (_refreshTimer) {
      clearTimeout(_refreshTimer)
      _refreshTimer = null
    }

    useMenuStore().$reset()

    const deptStore = useDeptStore()
    deptStore.deptOptions = []
    deptStore.deptFlatMap = new Map()
    deptStore.initialized = false
  }

  function $reset() {
    accessToken.value = null
    userInfo.value = null
    temporaryToken.value = null
    passwordChangeToken.value = null
  }

  return {
    // State
    accessToken,
    userInfo,
    temporaryToken,
    passwordChangeToken,
    // Getters
    isAuthenticated,
    scope,
    impersonation,
    isImpersonating,
    // Actions
    _initAxiosCallbacks,
    _scheduleTokenRefresh,
    doGetCaptcha,
    doLogin,
    applyPostForceChangeLogin,
    doSelectTenant,
    doSwitchTenant,
    applyImpersonationToken,
    getPermission,
    restoreSession,
    doLogout,
    clearAuth,
    $reset,
  }
})
