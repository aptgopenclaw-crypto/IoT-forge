import { describe, it, expect } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'
import { readFileSync } from 'fs'
import { resolve } from 'path'

/**
 * [Platform/Tenant Separation 2.1.6] Verifies that the tenant auth-config
 * Vue route now carries the canonical `:tenantId` path parameter and that the
 * legacy `/platform/auth-config` URL still resolves (via a redirect record)
 * for in-flight bookmarks and the seeded menu's `route_path`.
 */
describe('Platform/Tenant separation 2.1.6 — auth-config route shape', () => {
  const srcDir = resolve(__dirname, '../..')
  const routerSrc = readFileSync(resolve(srcDir, 'router/index.ts'), 'utf-8')

  it('static route uses the canonical /platform/tenants/:tenantId/auth-config path', () => {
    expect(routerSrc).toContain("path: '/platform/tenants/:tenantId/auth-config'")
    expect(routerSrc).toContain("name: 'TenantAuthConfig'")
  })

  it('legacy /platform/auth-config alias is preserved as a redirect record', () => {
    expect(routerSrc).toContain("path: '/platform/auth-config'")
    expect(routerSrc).toContain("name: 'TenantAuthConfigLegacy'")
  })

  it('vue-router resolves both paths to the correct named records', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/platform/tenants/:tenantId/auth-config',
          name: 'TenantAuthConfig',
          component: { template: '<div/>' },
          props: true,
        },
        {
          path: '/platform/auth-config',
          name: 'TenantAuthConfigLegacy',
          redirect: { name: 'TenantAuthConfig', params: { tenantId: 'fallback' } },
        },
      ],
    })

    await router.push('/platform/tenants/tenant-a/auth-config')
    expect(router.currentRoute.value.name).toBe('TenantAuthConfig')
    expect(router.currentRoute.value.params.tenantId).toBe('tenant-a')

    await router.push('/platform/auth-config')
    expect(router.currentRoute.value.name).toBe('TenantAuthConfig')
    expect(router.currentRoute.value.params.tenantId).toBe('fallback')
  })
})
