/**
 * [Phase 4 / 4.1.8] Pin the contract that the legacy `superAdminOnly` route
 * meta has been fully retired in favour of `requiresScope: 'PLATFORM'`.
 *
 * Scope enforcement itself is covered by router/guards.test.ts; this file is
 * a structural guard that prevents regressions where someone re-introduces
 * `meta.superAdminOnly` (which the live router no longer reads).
 */
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const ROUTER_SRC = readFileSync(
  resolve(here, '../../router/index.ts'),
  'utf-8',
)

describe('router: superAdminOnly retirement [Phase 4 / 4.1.8]', () => {
  it('does NOT carry any `superAdminOnly: true` meta on real route records', () => {
    // Comments are allowed (we keep historical context); only live meta keys
    // are forbidden.
    const stripped = ROUTER_SRC
      .split('\n')
      .filter((line) => {
        const trimmed = line.trim()
        return !trimmed.startsWith('//') && !trimmed.startsWith('*')
      })
      .join('\n')
    expect(stripped).not.toMatch(/superAdminOnly\s*:/)
  })

  it('does NOT branch on `to.meta.superAdminOnly` inside beforeEach', () => {
    expect(ROUTER_SRC).not.toMatch(/to\.meta\.superAdminOnly/)
  })

  it('keeps every /platform/* route gated by requiresScope: PLATFORM', () => {
    // platformChildren block spans from "const platformChildren" up to the
    // first `]` closing the array.
    const start = ROUTER_SRC.indexOf('const platformChildren')
    expect(start).toBeGreaterThan(-1)
    const end = ROUTER_SRC.indexOf('\n]\n', start)
    const block = ROUTER_SRC.slice(start, end)

    const routeRecords = block.match(/path:\s*'\/platform\//g) ?? []
    const scopeMetas = block.match(/requiresScope:\s*'PLATFORM'/g) ?? []
    expect(routeRecords.length).toBeGreaterThan(0)
    expect(scopeMetas.length).toBeGreaterThanOrEqual(routeRecords.length)
  })

  it('marks legacy /admin/system/tenants with requiresScope: PLATFORM', () => {
    // Locate the TenantManage record and inspect its meta line directly.
    // (Test 1 already proves no live `superAdminOnly:` keys exist anywhere
    // in router source; here we just pin the positive scope on this record.)
    const idx = ROUTER_SRC.indexOf("name: 'TenantManage'")
    expect(idx).toBeGreaterThan(-1)
    const block = ROUTER_SRC.slice(idx, idx + 400)
    expect(block).toMatch(/requiresScope:\s*'PLATFORM'/)
  })
})
