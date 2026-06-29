import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AnnouncementManagementView from '@/views/admin/announcement/AnnouncementManagementView.vue'

// Mock vue-router
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

// Mock element-plus to avoid CSS import issues — use Proxy to handle any export
vi.mock('element-plus', () => {
  const stub = { template: '<div><slot /></div>' }
  const noSlotStub = { template: '<div />' }
  const knownExports: Record<string, unknown> = {
    __esModule: true,
    ElMessage: { error: vi.fn(), success: vi.fn() },
    ElMessageBox: { confirm: vi.fn() },
    ElTable: { template: '<div class="el-table"><slot /></div>' },
    ElTableColumn: noSlotStub,
    ElPagination: { template: '<nav class="pagination" />' },
  }
  return new Proxy(knownExports, {
    get(target, prop) {
      if (prop in target) return (target as Record<string | symbol, unknown>)[prop]
      if (typeof prop === 'string') return stub
      return undefined
    },
    has() { return true },
  })
})

// Mock API
const listAnnouncementsAdminMock = vi.fn()
vi.mock('@/api/announcement', () => ({
  listAnnouncementsAdmin: (...args: unknown[]) => listAnnouncementsAdminMock(...args),
  createAnnouncement: vi.fn(),
  updateAnnouncement: vi.fn(),
  deleteAnnouncement: vi.fn(),
  listAnnouncementAttachments: vi.fn().mockResolvedValue([]),
  uploadAnnouncementAttachment: vi.fn(),
  downloadAnnouncementAttachment: vi.fn(),
  deleteAnnouncementAttachment: vi.fn(),
  getAttachmentConfig: vi.fn().mockResolvedValue({ allowedExtensions: ['pdf'], maxSizeMb: 10 }),
  getAnnouncementReadStats: vi.fn().mockResolvedValue({ totalTarget: 0, readCount: 0, readRate: 0 }),
  getAnnouncementUnreadUsers: vi.fn().mockResolvedValue([]),
  listPinnedAnnouncements: vi.fn().mockResolvedValue([]),
  reorderPinnedAnnouncements: vi.fn(),
}))

vi.mock('@/api/dept', () => ({
  getDeptOptions: vi.fn().mockResolvedValue([]),
}))

// Mock stores
vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    userInfo: { userId: 'u1', roles: ['ADMIN'], deptId: 'dept-1' },
  }),
}))

vi.mock('@/stores/deptStore', () => ({
  useDeptStore: () => ({
    deptTree: [],
    fetchDeptTree: vi.fn(),
  }),
}))

// Mock vue-i18n
vi.mock('vue-i18n', async () => {
  const { ref } = await import('vue')
  return {
    useI18n: () => ({
      t: (key: string) => key,
      locale: ref('zh-TW'),
    }),
  }
})

// Mock components
vi.mock('@/components/RichTextEditor.vue', () => ({
  default: { template: '<div class="rich-text-editor" />' },
}))

vi.mock('@/components/RichTextRenderer.vue', () => ({
  default: { template: '<div class="rich-text-renderer" />' },
}))

vi.mock('vuedraggable', () => ({
  default: { template: '<div class="draggable"><slot /></div>' },
}))

// Mock lucide-vue-next
vi.mock('lucide-vue-next', () => new Proxy({}, { get: () => ({ template: '<span />' }) }))

describe('AnnouncementManagementView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    listAnnouncementsAdminMock.mockResolvedValue({
      content: [
        { id: 1, title: 'Test Announcement', category: 'GENERAL', publishAt: '2024-01-01', pinned: false, status: 'PUBLISHED' },
      ],
      totalElements: 1,
    })
  })

  function factory() {
    return mount(AnnouncementManagementView, {
      global: {
        stubs: {
          RichTextEditor: true,
          RichTextRenderer: true,
          draggable: true,
        },
        directives: { loading: () => {} },
      },
    })
  }

  it('renders page title and subtitle', async () => {
    const wrapper = factory()
    await flushPromises()
    expect(wrapper.text()).toContain('announcement.admin.title')
    expect(wrapper.text()).toContain('announcement.admin.subtitle')
  })

  it('calls listAnnouncementsAdmin on mount', async () => {
    factory()
    await flushPromises()
    expect(listAnnouncementsAdminMock).toHaveBeenCalled()
  })

  it('renders the data table', async () => {
    const wrapper = factory()
    await flushPromises()
    expect(wrapper.find('.el-table').exists()).toBe(true)
  })

  it('renders create button for admin', async () => {
    const wrapper = factory()
    await flushPromises()
    // The create button text should contain the i18n key
    expect(wrapper.text()).toContain('announcement.admin.create')
  })

  it('renders pagination component', async () => {
    const wrapper = factory()
    await flushPromises()
    expect(wrapper.find('.pagination').exists()).toBe(true)
  })
})
