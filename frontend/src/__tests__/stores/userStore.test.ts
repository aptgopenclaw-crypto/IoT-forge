import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/userStore'

vi.mock('@/api/user', () => ({
  listUsers: vi.fn().mockResolvedValue({
    body: {
      content: [{ id: '1', name: 'User A' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    },
  }),
  getUserTenantRoles: vi.fn().mockResolvedValue({
    body: [{ tenantId: 't1', roleName: 'admin' }],
  }),
}))

describe('userStore (Composition API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should have correct initial state', () => {
    const store = useUserStore()
    expect(store.userList).toEqual([])
    expect(store.pagination.page).toBe(0)
    expect(store.pagination.size).toBe(20)
    expect(store.pagination.totalElements).toBe(0)
    expect(store.tenantRoles).toEqual([])
  })

  it('fetchUserList should populate userList and pagination', async () => {
    const store = useUserStore()
    await store.fetchUserList({ page: 0, size: 20 })

    expect(store.userList).toHaveLength(1)
    expect(store.userList[0]).toEqual({ id: '1', name: 'User A' })
    expect(store.pagination.totalElements).toBe(1)
    expect(store.pagination.totalPages).toBe(1)
  })

  it('fetchTenantRoles should populate tenantRoles', async () => {
    const store = useUserStore()
    await store.fetchTenantRoles('user-1')

    expect(store.tenantRoles).toHaveLength(1)
    expect(store.tenantRoles[0]).toEqual({ tenantId: 't1', roleName: 'admin' })
  })
})
