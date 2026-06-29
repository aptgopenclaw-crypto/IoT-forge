import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/authStore'
import * as authApi from '@/api/auth'
import * as axiosModule from '@/api/axios/axiosIns'

// Mock modules
vi.mock('@/api/auth')
vi.mock('@/api/axios/axiosIns', () => ({
  default: {},
  setAxiosToken: vi.fn(),
  setAxiosRefreshHandler: vi.fn(),
  setAxiosLogoutHandler: vi.fn(),
}))
vi.mock('@/router', () => ({
  default: { push: vi.fn() },
}))

describe('authStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('initial state', () => {
    it('should start with null token and userInfo', () => {
      const store = useAuthStore()
      expect(store.accessToken).toBeNull()
      expect(store.userInfo).toBeNull()
      expect(store.temporaryToken).toBeNull()
    })

    it('should report not authenticated when no passExam', () => {
      const store = useAuthStore()
      expect(store.isAuthenticated).toBe(false)
    })

    it('should report authenticated when passExam is set', () => {
      sessionStorage.setItem('passExam', 'true')
      const store = useAuthStore()
      expect(store.isAuthenticated).toBe(true)
    })
  })

  describe('doLogin', () => {
    it('should set token and passExam on successful login (no tenant selection)', async () => {
      const store = useAuthStore()
      vi.mocked(authApi.login).mockResolvedValue({
        errorCode: '00000',
        errorMsg: '',
        errorDetail: '',
        timestamp: '',
        body: {
          accessToken: 'test-token-123',
          refreshToken: null,
          needsSelection: false,
          isSuperAdmin: false,
          tenants: null,
        },
      })

      await store.doLogin({
        email: 'test@test.com',
        password: 'pass',
        captcha: '1234',
        captchaKey: 'key',
      })

      expect(store.accessToken).toBe('test-token-123')
      expect(sessionStorage.getItem('passExam')).toBe('true')
      expect(axiosModule.setAxiosToken).toHaveBeenCalledWith('test-token-123')
    })

    it('should redirect to tenant selection when needsSelection is true', async () => {
      const store = useAuthStore()
      const router = await import('@/router')

      vi.mocked(authApi.login).mockResolvedValue({
        errorCode: '00000',
        errorMsg: '',
        errorDetail: '',
        timestamp: '',
        body: {
          accessToken: 'temp-token',
          refreshToken: null,
          needsSelection: true,
          isSuperAdmin: false,
          tenants: [
            { tenantId: 't1', tenantCode: 'T1', tenantName: 'Tenant 1', roleName: 'Admin', deptName: null },
          ],
        },
      })

      await store.doLogin({
        email: 'test@test.com',
        password: 'pass',
        captcha: '1234',
        captchaKey: 'key',
      })

      expect(store.temporaryToken).toBe('temp-token')
      expect(store.accessToken).toBeNull()
      expect(sessionStorage.getItem('passExam')).toBeNull()
      expect(router.default.push).toHaveBeenCalledWith('/select-tenant')
    })
  })

  describe('doSelectTenant', () => {
    it('should set final token and passExam after tenant selection', async () => {
      const store = useAuthStore()
      store.temporaryToken = 'temp-token'

      vi.mocked(authApi.selectTenant).mockResolvedValue({
        errorCode: '00000',
        errorMsg: '',
        errorDetail: '',
        timestamp: '',
        body: { accessToken: 'final-token', refreshToken: 'final-refresh' },
      })

      await store.doSelectTenant('tenant-1')

      expect(store.accessToken).toBe('final-token')
      expect(store.temporaryToken).toBeNull()
      expect(sessionStorage.getItem('passExam')).toBe('true')
      expect(localStorage.getItem('lastTenantId')).toBe('tenant-1')
      expect(axiosModule.setAxiosToken).toHaveBeenCalledWith('final-token')
    })
  })

  describe('restoreSession', () => {
    it('should restore token via refresh API', async () => {
      const store = useAuthStore()

      vi.mocked(authApi.refreshTokenApi).mockResolvedValue({
        errorCode: '00000',
        errorMsg: '',
        errorDetail: '',
        timestamp: '',
        body: { accessToken: 'refreshed-token', refreshToken: 'refreshed-refresh' },
      })

      await store.restoreSession()

      expect(store.accessToken).toBe('refreshed-token')
      expect(axiosModule.setAxiosToken).toHaveBeenCalledWith('refreshed-token')
    })

    it('should not call API if token already exists', async () => {
      const store = useAuthStore()
      store.accessToken = 'existing-token'

      vi.mocked(authApi.refreshTokenApi).mockClear()
      await store.restoreSession()

      expect(authApi.refreshTokenApi).not.toHaveBeenCalled()
    })
  })

  describe('clearAuth', () => {
    it('should reset all auth state', () => {
      const store = useAuthStore()
      store.accessToken = 'some-token'
      store.userInfo = {
        userId: 'u1',
        email: 'test@example.com',
        displayName: 'Test',
        tenantId: 't1',
        tenantName: 'Tenant',
        roles: [],
        deptId: null,
        deptName: null,
        permissions: [],
        isSuperAdmin: false,
        availableTenants: [],
      }
      store.temporaryToken = 'temp'
      sessionStorage.setItem('passExam', 'true')

      store.clearAuth()

      expect(store.accessToken).toBeNull()
      expect(store.userInfo).toBeNull()
      expect(store.temporaryToken).toBeNull()
      expect(sessionStorage.getItem('passExam')).toBeNull()
      expect(axiosModule.setAxiosToken).toHaveBeenCalledWith(null)
    })
  })

  describe('getPermission', () => {
    it('should fetch user info and store it', async () => {
      const store = useAuthStore()
      const mockUserInfo = {
        userId: 'u1',
        email: 'test@test.com',
        displayName: 'Test',
        tenantId: 't1',
        tenantName: 'Tenant',
        roles: ['ADMIN'],
        deptId: null,
        deptName: null,
        permissions: ['USER_LIST'],
        isSuperAdmin: false,
        availableTenants: [
          { tenantId: 't1', tenantCode: 'T1', tenantName: 'Tenant', roleName: 'Admin', deptName: null },
        ],
      }

      vi.mocked(authApi.getUserInfo).mockResolvedValue({
        errorCode: '00000',
        errorMsg: '',
        errorDetail: '',
        timestamp: '',
        body: mockUserInfo,
      })

      await store.getPermission()

      expect(store.userInfo).toEqual(mockUserInfo)
    })
  })

  describe('_initAxiosCallbacks', () => {
    it('should register refresh and logout handlers', () => {
      const store = useAuthStore()
      store._initAxiosCallbacks()

      expect(axiosModule.setAxiosRefreshHandler).toHaveBeenCalled()
      expect(axiosModule.setAxiosLogoutHandler).toHaveBeenCalled()
    })

    it('should set existing token to axios if available', () => {
      const store = useAuthStore()
      store.accessToken = 'existing-token'
      store._initAxiosCallbacks()

      expect(axiosModule.setAxiosToken).toHaveBeenCalledWith('existing-token')
    })
  })
})
