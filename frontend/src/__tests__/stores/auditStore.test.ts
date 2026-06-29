import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuditStore } from '@/stores/auditStore'

vi.mock('@/api/audit', () => ({
  getUserUsageHistory: vi.fn().mockResolvedValue({
    body: {
      content: [{ id: 1, userName: 'admin', eventDesc: 'LOGIN' }],
      totalElements: 1,
    },
  }),
  getMyLoginLog: vi.fn().mockResolvedValue({
    body: {
      content: [{ id: 2, eventType: 'LOGIN', timestamp: '2026-01-01' }],
      totalElements: 1,
    },
  }),
}))

describe('auditStore (Composition API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should have correct initial state', () => {
    const store = useAuditStore()
    expect(store.usageLogs).toEqual([])
    expect(store.myLogs).toEqual([])
    expect(store.pagination.page).toBe(0)
    expect(store.pagination.size).toBe(20)
    expect(store.pagination.total).toBe(0)
    expect(store.loading).toBe(false)
  })

  it('fetchUsageLogs should populate usageLogs and pagination', async () => {
    const store = useAuditStore()
    await store.fetchUsageLogs({ userName: 'admin' })

    expect(store.usageLogs).toHaveLength(1)
    expect(store.usageLogs[0]).toEqual({ id: 1, userName: 'admin', eventDesc: 'LOGIN' })
    expect(store.pagination.total).toBe(1)
    expect(store.loading).toBe(false)
  })

  it('fetchMyLogs should populate myLogs and myPagination', async () => {
    const store = useAuditStore()
    await store.fetchMyLogs({ eventType: 'LOGIN' })

    expect(store.myLogs).toHaveLength(1)
    expect(store.myLogs[0]).toEqual({ id: 2, eventType: 'LOGIN', timestamp: '2026-01-01' })
    expect(store.myPagination.total).toBe(1)
    expect(store.loading).toBe(false)
  })

  it('loading should be true during fetch', async () => {
    const store = useAuditStore()
    const promise = store.fetchUsageLogs({})
    // After microtask resolves it should be false again
    await promise
    expect(store.loading).toBe(false)
  })
})
