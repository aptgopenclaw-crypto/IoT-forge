import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useTenantStore } from '@/stores/tenantStore'

describe('tenantStore (Composition API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should have correct initial state', () => {
    const store = useTenantStore()
    expect(store.currentTenantId).toBeNull()
    expect(store.tenantList).toEqual([])
    expect(store.needsSelection).toBe(false)
  })

  it('setTenant should update currentTenantId', () => {
    const store = useTenantStore()
    store.setTenant('tenant-123')
    expect(store.currentTenantId).toBe('tenant-123')
  })

  it('setTenantList with single tenant should not require selection', () => {
    const store = useTenantStore()
    store.setTenantList([{ id: '1', name: 'Tenant A' }])
    expect(store.tenantList).toHaveLength(1)
    expect(store.needsSelection).toBe(false)
  })

  it('setTenantList with multiple tenants should require selection', () => {
    const store = useTenantStore()
    store.setTenantList([
      { id: '1', name: 'Tenant A' },
      { id: '2', name: 'Tenant B' },
    ])
    expect(store.tenantList).toHaveLength(2)
    expect(store.needsSelection).toBe(true)
  })
})
