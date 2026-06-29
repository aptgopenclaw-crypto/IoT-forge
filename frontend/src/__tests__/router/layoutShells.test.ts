import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { resolve } from 'path'

/**
 * [Phase 4 / 4.1.3 / ADR-004] Verifies the router has been restructured so
 * that authenticated routes are nested under one of two layout shells:
 *
 *   - `PlatformRoot` (path `/platform`) → `PlatformLayout` (dark, super_admin)
 *   - `TenantRoot`   (path `/`)         → `TenantLayout`   (green, tenant)
 *
 * These named parent routes are also the attach points used by
 * `menuStore.fetchMyMenus()` when injecting backend-driven menu routes.
 */
describe('Router layout shells [Phase 4 / 4.1.3]', () => {
  const srcDir = resolve(__dirname, '../..')
  const routerSrc = readFileSync(resolve(srcDir, 'router/index.ts'), 'utf-8')
  const menuStoreSrc = readFileSync(resolve(srcDir, 'stores/menuStore.ts'), 'utf-8')
  const appVueSrc = readFileSync(resolve(srcDir, 'App.vue'), 'utf-8')

  it('imports both layout shells', () => {
    expect(routerSrc).toContain("from '@/layouts/PlatformLayout.vue'")
    expect(routerSrc).toContain("from '@/layouts/TenantLayout.vue'")
  })

  it('defines a PlatformRoot record at /platform mounted on PlatformLayout', () => {
    expect(routerSrc).toMatch(/name:\s*'PlatformRoot'/)
    expect(routerSrc).toMatch(/path:\s*'\/platform'[\s,]/)
    expect(routerSrc).toMatch(/component:\s*PlatformLayout/)
  })

  it('defines a TenantRoot record at / mounted on TenantLayout', () => {
    expect(routerSrc).toMatch(/name:\s*'TenantRoot'/)
    expect(routerSrc).toMatch(/component:\s*TenantLayout/)
  })

  it('tags the PlatformRoot record with requiresScope: PLATFORM', () => {
    // Match within the PlatformRoot block specifically.
    const platformBlock = routerSrc.slice(
      routerSrc.indexOf("name: 'PlatformRoot'"),
      routerSrc.indexOf("name: 'TenantRoot'"),
    )
    expect(platformBlock).toMatch(/requiresScope:\s*'PLATFORM'/)
  })

  it('keeps platform child paths absolute so external bookmarks still resolve', () => {
    expect(routerSrc).toContain("path: '/platform/tenants/:tenantId/auth-config'")
    expect(routerSrc).toContain("path: '/platform/auth-config'")
  })

  it('noauth routes (login etc.) stay top-level — no layout chrome', () => {
    // Ensure /login is NOT a child of either layout root by checking that the
    // Login record sits outside the PlatformRoot / TenantRoot blocks.
    const loginIdx = routerSrc.indexOf("name: 'Login'")
    const platformIdx = routerSrc.indexOf("name: 'PlatformRoot'")
    expect(loginIdx).toBeGreaterThan(-1)
    expect(loginIdx).toBeLessThan(platformIdx)
  })

  it('menuStore attaches dynamic /platform/* routes under PlatformRoot, others under TenantRoot', () => {
    expect(menuStoreSrc).toContain("router.addRoute(parentName, route)")
    expect(menuStoreSrc).toMatch(/startsWith\(['"]\/platform['"]\)/)
    expect(menuStoreSrc).toContain("'PlatformRoot'")
    expect(menuStoreSrc).toContain("'TenantRoot'")
  })

  it('App.vue no longer renders its own sidebar/topbar wrapper (layouts own that chrome)', () => {
    expect(appVueSrc).not.toContain('AppSidebar')
    expect(appVueSrc).not.toContain('AppTopBar')
    expect(appVueSrc).not.toContain('app-layout')
    // Must still mount the root router-view + global dialog.
    expect(appVueSrc).toContain('<router-view />')
    expect(appVueSrc).toContain('IdleTimeoutDialog')
  })
})
