import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import TenantLayout from '@/layouts/TenantLayout.vue'

// Stub child components — TenantLayout itself is just a thin shell; deep behaviour
// is covered by AppSidebar/AppTopBar own tests.
vi.mock('@/components/AppSidebar.vue', () => ({
  default: { name: 'AppSidebar', template: '<aside data-testid="sidebar-stub" />' },
}))
vi.mock('@/components/AppTopBar.vue', () => ({
  default: { name: 'AppTopBar', template: '<div data-testid="topbar-stub" />' },
}))
// [Phase 4 / 4.1.6] Stub the banner — its own tests cover the live behaviour.
vi.mock('@/components/ImpersonationBanner.vue', () => ({
  default: { name: 'ImpersonationBanner', template: '<div data-testid="banner-stub" />' },
}))

describe('TenantLayout [Phase 4 / 4.1.2]', () => {
  function factory() {
    return mount(TenantLayout, {
      global: {
        stubs: {
          'router-view': { template: '<div class="router-view-stub" />' },
        },
      },
    })
  }

  it('renders the tenant shell with sidebar, topbar and router-view', () => {
    const wrapper = factory()
    expect(wrapper.find('[data-testid="tenant-layout"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="sidebar-stub"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="topbar-stub"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="banner-stub"]').exists()).toBe(true)
    expect(wrapper.find('.tenant-content').exists()).toBe(true)
    expect(wrapper.find('.router-view-stub').exists()).toBe(true)
  })

  it('orders sidebar before main content (flex row shell)', () => {
    const wrapper = factory()
    const root = wrapper.find('.tenant-layout').element as HTMLElement
    const sidebar = wrapper.find('[data-testid="sidebar-stub"]').element
    const main = wrapper.find('.tenant-main').element
    expect(root.children[0]).toBe(sidebar)
    expect(root.children[1]).toBe(main)
  })

  it('does NOT render platform-specific brand text (tenant layout is theme-neutral)', () => {
    const wrapper = factory()
    // PlatformLayout uses .brand-text with 'Platform'; TenantLayout must not.
    expect(wrapper.find('.platform-layout').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Platform')
  })
})
