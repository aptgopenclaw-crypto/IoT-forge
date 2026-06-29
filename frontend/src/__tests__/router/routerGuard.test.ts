import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { type RouteLocationNormalized } from 'vue-router'
import { setActivePinia, createPinia } from 'pinia'

// We test the guard logic by importing and replaying it against a minimal router setup.
// The actual router uses dynamic imports in beforeEach; we mock the stores directly.

const mockAuthStore = {
  accessToken: null as string | null,
  userInfo: null as { isSuperAdmin?: boolean } | null,
  _initAxiosCallbacks: vi.fn(),
  restoreSession: vi.fn(),
  getPermission: vi.fn(),
  clearAuth: vi.fn(),
}

const mockMenuStore = {
  initialized: false,
  fetchMyMenus: vi.fn(),
  hasRouteAccess: vi.fn().mockReturnValue(true),
}

const mockDeptStore = {
  initialized: false,
  fetchDeptOptions: vi.fn(),
}

vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => mockAuthStore,
}))
vi.mock('@/stores/menuStore', () => ({
  useMenuStore: () => mockMenuStore,
}))
vi.mock('@/stores/deptStore', () => ({
  useDeptStore: () => mockDeptStore,
}))

describe('Router Guard Logic', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    mockAuthStore.accessToken = null
    mockAuthStore.userInfo = null
    mockMenuStore.initialized = false
    mockMenuStore.hasRouteAccess.mockReturnValue(true)
    mockDeptStore.initialized = false
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  /**
   * Helper: simulates the guard logic from router/index.ts
   * Returns the navigation result (path or 'pass')
   */
  async function runGuard(to: Partial<RouteLocationNormalized>): Promise<string> {
    const route = {
      path: to.path ?? '/',
      name: to.name ?? undefined,
      meta: to.meta ?? {},
    } as RouteLocationNormalized

    // 1. noauth routes
    if (route.meta.requiresAuth === false) {
      return 'pass'
    }

    // 2. Not logged in
    const passExam = sessionStorage.getItem('passExam')
    if (!passExam) {
      return '/login'
    }

    // 3. Bootstrap
    mockAuthStore._initAxiosCallbacks()

    if (!mockMenuStore.initialized) {
      if (!mockAuthStore.accessToken) {
        try {
          await mockAuthStore.restoreSession()
        } catch {
          mockAuthStore.clearAuth()
          return '/login'
        }
      }
      try {
        if (!mockAuthStore.userInfo) {
          await mockAuthStore.getPermission()
        }
        await mockMenuStore.fetchMyMenus()
        if (!mockDeptStore.initialized) {
          try {
            await mockDeptStore.fetchDeptOptions()
          } catch { /* degrade */ }
        }
      } catch {
        if (!mockMenuStore.initialized) {
          mockMenuStore.initialized = true
        }
      }
      // re-enter
      mockMenuStore.initialized = true
      return 're-enter'
    }

    // 4. publicRoutes
    if (route.meta.isPublic) {
      return 'pass'
    }

    // 5. [Phase 4 / 4.1.8] superAdminOnly removed — scope checks live in
    //    guards.test.ts via `resolveScopeRedirect`. This simulation only
    //    exercises bootstrap + menu-based access here.

    // 6. menu-based access
    if (!mockMenuStore.hasRouteAccess(route.name as string)) {
      return '/'
    }

    return 'pass'
  }

  describe('noauth routes', () => {
    it('should pass through login route', async () => {
      const result = await runGuard({ path: '/login', meta: { requiresAuth: false } })
      expect(result).toBe('pass')
    })

    it('should pass through forgot-password route', async () => {
      const result = await runGuard({ path: '/forgot-password', meta: { requiresAuth: false } })
      expect(result).toBe('pass')
    })
  })

  describe('unauthenticated access', () => {
    it('should redirect to /login when no passExam', async () => {
      const result = await runGuard({ path: '/admin/users', name: 'UserList', meta: {} })
      expect(result).toBe('/login')
    })
  })

  describe('session bootstrap', () => {
    it('should restore session and fetch menus on first access', async () => {
      sessionStorage.setItem('passExam', 'true')
      mockAuthStore.restoreSession.mockResolvedValue(undefined)
      mockAuthStore.getPermission.mockResolvedValue(undefined)
      mockMenuStore.fetchMyMenus.mockResolvedValue(undefined)
      mockDeptStore.fetchDeptOptions.mockResolvedValue(undefined)

      const result = await runGuard({ path: '/admin/users', name: 'UserList', meta: {} })

      expect(result).toBe('re-enter')
      expect(mockAuthStore.restoreSession).toHaveBeenCalled()
      expect(mockAuthStore.getPermission).toHaveBeenCalled()
      expect(mockMenuStore.fetchMyMenus).toHaveBeenCalled()
      expect(mockDeptStore.fetchDeptOptions).toHaveBeenCalled()
    })

    it('should redirect to /login if restoreSession fails', async () => {
      sessionStorage.setItem('passExam', 'true')
      mockAuthStore.restoreSession.mockRejectedValue(new Error('401'))

      const result = await runGuard({ path: '/admin/users', name: 'UserList', meta: {} })

      expect(result).toBe('/login')
      expect(mockAuthStore.clearAuth).toHaveBeenCalled()
    })

    it('should skip restoreSession if token already exists', async () => {
      sessionStorage.setItem('passExam', 'true')
      mockAuthStore.accessToken = 'existing-token'
      mockAuthStore.getPermission.mockResolvedValue(undefined)
      mockMenuStore.fetchMyMenus.mockResolvedValue(undefined)

      await runGuard({ path: '/admin/users', name: 'UserList', meta: {} })

      expect(mockAuthStore.restoreSession).not.toHaveBeenCalled()
    })
  })

  describe('public routes (logged in)', () => {
    it('should pass through for public routes when authenticated', async () => {
      sessionStorage.setItem('passExam', 'true')
      mockMenuStore.initialized = true

      const result = await runGuard({ path: '/', name: 'Home', meta: { isPublic: true } })
      expect(result).toBe('pass')
    })
  })

  describe('legacy superAdminOnly meta [Phase 4 / 4.1.8 — retired]', () => {
    it('is no longer honoured by the guard — menu access alone decides', async () => {
      // After 4.1.8 the only gates left in this simplified simulation are
      // bootstrap + menu-based access. A non-super-admin user whose menu
      // permits the route can now reach it; scope enforcement is delegated
      // to `resolveScopeRedirect` (covered by guards.test.ts).
      sessionStorage.setItem('passExam', 'true')
      mockMenuStore.initialized = true
      mockAuthStore.userInfo = { isSuperAdmin: false }
      mockMenuStore.hasRouteAccess.mockReturnValue(true)

      const result = await runGuard({
        path: '/admin/system/tenants',
        name: 'TenantManage',
        meta: { superAdminOnly: true },
      })
      expect(result).toBe('pass')
    })

    it('falls back to menu denial when the route is not in the user\u2019s menus', async () => {
      sessionStorage.setItem('passExam', 'true')
      mockMenuStore.initialized = true
      mockAuthStore.userInfo = { isSuperAdmin: false }
      mockMenuStore.hasRouteAccess.mockReturnValue(false)

      const result = await runGuard({
        path: '/admin/system/tenants',
        name: 'TenantManage',
        meta: { superAdminOnly: true },
      })
      expect(result).toBe('/')
    })
  })

  describe('menu-based access control', () => {
    it('should allow access when route is in menu', async () => {
      sessionStorage.setItem('passExam', 'true')
      mockMenuStore.initialized = true
      mockMenuStore.hasRouteAccess.mockReturnValue(true)

      const result = await runGuard({ path: '/admin/users', name: 'UserList', meta: {} })
      expect(result).toBe('pass')
    })

    it('should deny access when route is not in menu', async () => {
      sessionStorage.setItem('passExam', 'true')
      mockMenuStore.initialized = true
      mockMenuStore.hasRouteAccess.mockReturnValue(false)

      const result = await runGuard({ path: '/admin/users', name: 'UserList', meta: {} })
      expect(result).toBe('/')
    })
  })
})
