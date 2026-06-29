import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PlatformLayout from '@/layouts/PlatformLayout.vue'

// vue-router mocks
const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ path: '/platform/tenants' }),
  // Provide RouterView as a stub so <router-view /> in the template resolves.
  RouterView: { name: 'RouterView', template: '<div class="router-view-stub" />' },
}))

// i18n: return key as-is so we can assert on key strings.
vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

// Avoid pulling AppTopBar tree (has its own stores / icons) — just stub it.
vi.mock('@/components/AppTopBar.vue', () => ({
  default: { name: 'AppTopBar', template: '<div data-testid="topbar-stub" />' },
}))

// MenuNode shallow stub — we just need to assert it received the right menu.
vi.mock('@/components/MenuNode.vue', () => ({
  default: {
    name: 'MenuNode',
    props: ['menu'],
    template:
      '<div class="menu-node-stub" :data-menu-id="menu.menuId" :data-menu-name="menu.name" />',
  },
}))

// Mocked stores
const sidebarMenusRef = { value: [] as Array<Record<string, unknown>> }
vi.mock('@/stores/menuStore', () => ({
  useMenuStore: () => ({
    get sidebarMenus() {
      return sidebarMenusRef.value
    },
  }),
}))

const authStateRef = { value: null as null | { displayName: string } }
vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    get userInfo() {
      return authStateRef.value
    },
  }),
}))

describe('PlatformLayout [Phase 4 / 4.1.1]', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    sidebarMenusRef.value = []
    authStateRef.value = null
  })

  function factory() {
    return mount(PlatformLayout, {
      global: {
        stubs: {
          // el-menu renders default slot so MenuNode children show through.
          'el-menu': { template: '<div class="el-menu-stub"><slot /></div>' },
          // router-view is resolved as a global component; stub it here.
          'router-view': { template: '<div class="router-view-stub" />' },
        },
      },
    })
  }

  it('renders the dark platform shell with brand "Platform" (i18n key)', () => {
    const wrapper = factory()
    expect(wrapper.find('.platform-layout').exists()).toBe(true)
    expect(wrapper.find('[data-testid="platform-sidebar"]').exists()).toBe(true)
    // i18n is mocked to echo keys → brand text should equal the key.
    expect(wrapper.find('.brand-text').text()).toBe('layout.platformBrand')
    expect(wrapper.find('.brand-subtitle').text()).toBe('layout.platformSubtitle')
  })

  it('renders empty-menu hint when no platform menus are available', () => {
    sidebarMenusRef.value = []
    const wrapper = factory()
    const empty = wrapper.find('.platform-empty')
    expect(empty.exists()).toBe(true)
    expect(empty.text()).toBe('layout.platformEmptyMenu')
    expect(wrapper.findAll('.menu-node-stub')).toHaveLength(0)
  })

  it('renders MenuNode for each sidebar menu, sorted by sortOrder ascending', () => {
    // Provide out-of-order menus to assert sorting is applied.
    sidebarMenusRef.value = [
      { menuId: 2, name: 'Password Policy', sortOrder: 20 },
      { menuId: 1, name: 'Tenants', sortOrder: 10 },
      { menuId: 3, name: 'Impersonations', sortOrder: 30 },
    ]
    const wrapper = factory()
    const nodes = wrapper.findAll('.menu-node-stub')
    expect(nodes).toHaveLength(3)
    expect(nodes[0].attributes('data-menu-id')).toBe('1')
    expect(nodes[1].attributes('data-menu-id')).toBe('2')
    expect(nodes[2].attributes('data-menu-id')).toBe('3')
    expect(wrapper.find('.platform-empty').exists()).toBe(false)
  })

  it('renders user displayName when authStore.userInfo is present', () => {
    authStateRef.value = { displayName: 'Super Admin' }
    const wrapper = factory()
    expect(wrapper.find('.user-name').exists()).toBe(true)
    expect(wrapper.find('.user-name').text()).toBe('Super Admin')
  })

  it('omits user section entirely when no displayName', () => {
    authStateRef.value = null
    const wrapper = factory()
    expect(wrapper.find('.platform-user').exists()).toBe(false)
  })

  it('mounts AppTopBar and a router-view slot in the main area', () => {
    const wrapper = factory()
    expect(wrapper.find('[data-testid="topbar-stub"]').exists()).toBe(true)
    expect(wrapper.find('.platform-content').exists()).toBe(true)
    expect(wrapper.find('.router-view-stub').exists()).toBe(true)
  })

  it('does NOT render any tenant switcher or tenant-name brand (PlatformLayout is tenant-agnostic)', () => {
    const wrapper = factory()
    // Tenant-specific elements that AppSidebar exposes must not appear here.
    expect(wrapper.findComponent({ name: 'TenantSwitcher' }).exists()).toBe(false)
    expect(wrapper.text()).not.toContain('CCMS')
  })
})
