import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock axiosIns BEFORE importing the modules under test so the import-time
// reference resolves to our spy.
vi.mock('@/api/axios/axiosIns', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ body: undefined }),
    post: vi.fn().mockResolvedValue({ body: undefined }),
    put: vi.fn().mockResolvedValue({ body: undefined }),
    delete: vi.fn().mockResolvedValue({ body: undefined }),
    patch: vi.fn().mockResolvedValue({ body: undefined }),
  },
}))

import axiosIns from '@/api/axios/axiosIns'
import {
  getAuthConfig,
  updateAuthConfig,
  deleteAuthConfig,
  testAuthConnection,
} from '@/api/authConfig'
import {
  listTenants,
  createTenant,
  updateTenant,
  toggleTenantEnabled,
} from '@/api/tenant/admin'
import {
  getUserTenantRoles,
  addTenantRole,
  removeTenantRole,
} from '@/api/user'

/**
 * [Platform/Tenant Separation 2.1.5] Verifies the frontend API clients call the
 * new `/platform/**` URLs (relative to the `/v1` baseURL) rather than the
 * deprecated `/admin/**` and `/auth/**` routes.
 */
describe('Platform/Tenant separation 2.1.5 — API client paths', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('authConfig', () => {
    const TENANT = 'tenant-a'

    it('getAuthConfig hits /platform/tenants/{tenantId}/auth-config', async () => {
      await getAuthConfig(TENANT)
      expect(axiosIns.get).toHaveBeenCalledWith('/platform/tenants/tenant-a/auth-config')
    })

    it('updateAuthConfig hits /platform/tenants/{tenantId}/auth-config (PUT)', async () => {
      const payload = { authType: 'LOCAL', config: null, fallbackLocal: true } as never
      await updateAuthConfig(TENANT, payload)
      expect(axiosIns.put).toHaveBeenCalledWith(
        '/platform/tenants/tenant-a/auth-config',
        payload,
      )
    })

    it('deleteAuthConfig hits /platform/tenants/{tenantId}/auth-config (DELETE)', async () => {
      await deleteAuthConfig(TENANT)
      expect(axiosIns.delete).toHaveBeenCalledWith('/platform/tenants/tenant-a/auth-config')
    })

    it('testAuthConnection hits /platform/tenants/{tenantId}/auth-config/test-connection', async () => {
      const payload = { authType: 'LDAP', config: {}, fallbackLocal: false } as never
      await testAuthConnection(TENANT, payload)
      expect(axiosIns.post).toHaveBeenCalledWith(
        '/platform/tenants/tenant-a/auth-config/test-connection',
        payload,
      )
    })

    it('encodes tenantId path segment', async () => {
      await getAuthConfig('a/b c')
      expect(axiosIns.get).toHaveBeenCalledWith('/platform/tenants/a%2Fb%20c/auth-config')
    })
  })

  describe('tenant admin', () => {
    it('listTenants hits /platform/tenants', async () => {
      await listTenants()
      expect(axiosIns.get).toHaveBeenCalledWith('/platform/tenants')
    })

    it('createTenant hits /platform/tenants (POST)', async () => {
      const payload = { tenantId: 't1', name: 'T1' } as never
      await createTenant(payload)
      expect(axiosIns.post).toHaveBeenCalledWith('/platform/tenants', payload)
    })

    it('updateTenant hits /platform/tenants/{tenantId} (PUT)', async () => {
      const payload = { name: 'T1' } as never
      await updateTenant('t1', payload)
      expect(axiosIns.put).toHaveBeenCalledWith('/platform/tenants/t1', payload)
    })

    it('toggleTenantEnabled hits /platform/tenants/{tenantId}/enabled (PATCH)', async () => {
      await toggleTenantEnabled('t1', true)
      expect(axiosIns.patch).toHaveBeenCalledWith(
        '/platform/tenants/t1/enabled',
        null,
        { params: { enabled: true } },
      )
    })
  })

  describe('user tenant-roles', () => {
    const USER = '11111111-1111-1111-1111-111111111111'

    it('getUserTenantRoles hits /platform/users/{userId}/tenant-roles', async () => {
      await getUserTenantRoles(USER)
      expect(axiosIns.get).toHaveBeenCalledWith(
        `/platform/users/${USER}/tenant-roles`,
      )
    })

    it('addTenantRole hits /platform/users/{userId}/tenant-roles (POST)', async () => {
      const payload = { tenantId: 't1', roleId: 'r1' } as never
      await addTenantRole(USER, payload)
      expect(axiosIns.post).toHaveBeenCalledWith(
        `/platform/users/${USER}/tenant-roles`,
        payload,
      )
    })

    it('removeTenantRole hits /platform/users/{userId}/tenant-roles/{mappingId} (DELETE)', async () => {
      await removeTenantRole(USER, 42)
      expect(axiosIns.delete).toHaveBeenCalledWith(
        `/platform/users/${USER}/tenant-roles/42`,
      )
    })
  })
})
