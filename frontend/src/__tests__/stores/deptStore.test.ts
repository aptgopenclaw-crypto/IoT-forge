import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useDeptStore } from '@/stores/deptStore'

vi.mock('@/api/dept', () => ({
  getDeptOptions: vi.fn().mockResolvedValue({
    body: [
      { value: 1, label: '研發部', children: [{ value: 11, label: '前端組', children: [] }] },
      { value: 2, label: '行銷部', children: [] },
    ],
  }),
}))

describe('deptStore (Composition API)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should have correct initial state', () => {
    const store = useDeptStore()
    expect(store.deptOptions).toEqual([])
    expect(store.deptFlatMap.size).toBe(0)
    expect(store.initialized).toBe(false)
    expect(store.loading).toBe(false)
  })

  it('fetchDeptOptions should populate tree and flat map', async () => {
    const store = useDeptStore()
    await store.fetchDeptOptions()

    expect(store.deptOptions).toHaveLength(2)
    expect(store.deptFlatMap.get(1)).toBe('研發部')
    expect(store.deptFlatMap.get(11)).toBe('前端組')
    expect(store.deptFlatMap.get(2)).toBe('行銷部')
    expect(store.initialized).toBe(true)
    expect(store.loading).toBe(false)
  })

  it('getDeptName should return name for valid id', async () => {
    const store = useDeptStore()
    await store.fetchDeptOptions()

    expect(store.getDeptName(1)).toBe('研發部')
    expect(store.getDeptName('11')).toBe('前端組')
  })

  it('getDeptName should handle null/undefined/unknown', () => {
    const store = useDeptStore()
    expect(store.getDeptName(null)).toBe('')
    expect(store.getDeptName(undefined)).toBe('')
    expect(store.getDeptName(999)).toBe('未知部門')
  })
})
