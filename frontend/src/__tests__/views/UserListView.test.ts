import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UserListView from '@/views/admin/UserListView.vue'

// Mock vue-router
const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

// Mock element-plus to avoid CSS import issues
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
  ElForm: { template: '<form><slot /></form>' },
  ElFormItem: { template: '<div><slot /></div>' },
  ElInput: { template: '<div class="el-input"><slot name="prefix" /><input /><slot /></div>', props: ['modelValue', 'placeholder', 'clearable'] },
  ElButton: { template: '<button><slot /></button>', props: ['type', 'loading'] },
  ElTable: { template: '<div class="el-table"><slot /></div>', props: ['data'] },
  ElTableColumn: { template: '<div class="el-table-column" />', props: ['prop', 'label', 'minWidth', 'width', 'fixed', 'align'] },
  ElTag: { template: '<span class="el-tag"><slot /></span>', props: ['type'] },
  ElPagination: { template: '<nav class="pagination" />', props: ['total', 'pageSize', 'currentPage'] },
  ElTooltip: { template: '<span><slot /></span>' },
  ElSkeleton: { template: '<div class="el-skeleton"><slot v-if="!loading" /><slot name="default" v-if="!loading" /></div>', props: ['loading', 'rows', 'animated'] },
  ElLoadingDirective: {},
  vLoading: {},
}))

// Mock user API
vi.mock('@/api/user', () => ({
  disableUser: vi.fn(),
  softDeleteUser: vi.fn(),
}))

// Mock userStore
const fetchUserListMock = vi.fn()
const mockUserList = [
  { userId: 'u1', email: 'alice@example.com', displayName: 'Alice', roleName: 'Admin', deptName: 'IT', enabled: true, locked: false },
  { userId: 'u2', email: 'bob@example.com', displayName: 'Bob', roleName: 'User', deptName: null, enabled: false, locked: false },
]

vi.mock('@/stores/userStore', () => ({
  useUserStore: () => ({
    userList: mockUserList,
    pagination: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
    fetchUserList: fetchUserListMock,
  }),
}))

// Mock authStore
vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    userInfo: { userId: 'u1' },
  }),
}))

// Mock vue-i18n
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

// Mock lucide icons
vi.mock('lucide-vue-next', () => ({
  Plus: { template: '<span />' },
  Search: { template: '<span />' },
  Pencil: { template: '<span />' },
  Ban: { template: '<span />' },
  Trash2: { template: '<span />' },
}))

describe('UserListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    fetchUserListMock.mockResolvedValue(undefined)
  })

  function factory() {
    return mount(UserListView, {
      global: {
        stubs: {
          Plus: true,
          Search: true,
          Pencil: true,
          Ban: true,
          Trash2: true,
        },
        directives: { loading: () => {} },
      },
    })
  }

  it('renders page title', () => {
    const wrapper = factory()
    expect(wrapper.text()).toContain('user.list.title')
  })

  it('calls fetchUserList on mount', async () => {
    factory()
    await flushPromises()
    expect(fetchUserListMock).toHaveBeenCalledWith({ page: 0, size: 20, keyword: undefined })
  })

  it('renders create user button', () => {
    const wrapper = factory()
    const btn = wrapper.find('.create-btn')
    expect(btn.exists()).toBe(true)
  })

  it('navigates to create page on button click', async () => {
    const wrapper = factory()
    await wrapper.find('.create-btn').trigger('click')
    expect(pushMock).toHaveBeenCalledWith('/admin/users/create')
  })

  it('renders search input', () => {
    const wrapper = factory()
    const input = wrapper.find('.search-bar input')
    expect(input.exists()).toBe(true)
  })

  it('has a table for user data', async () => {
    const wrapper = factory()
    await flushPromises()
    expect(wrapper.find('.table-card').exists()).toBe(true)
    expect(wrapper.find('.el-table').exists()).toBe(true)
  })
})
