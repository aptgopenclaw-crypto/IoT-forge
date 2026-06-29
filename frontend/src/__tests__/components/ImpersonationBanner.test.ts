import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import ImpersonationBanner from '@/components/ImpersonationBanner.vue'

// ── i18n stub ────────────────────────────────────────────────────────────────
vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

// ── Element Plus stubs ───────────────────────────────────────────────────────
const confirmMock = vi.fn()
vi.mock('element-plus', () => ({
  ElButton: {
    name: 'ElButton',
    inheritAttrs: false,
    props: ['type', 'size', 'loading'],
    emits: ['click'],
    template:
      // Re-expose data-* attrs only — explicitly avoid re-binding listeners
      // (which would double-fire the parent\'s @click handler).
      '<button class="el-button" :data-loading="loading" :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\', $event)"><slot /></button>',
  },
  ElMessageBox: { confirm: (...args: unknown[]) => confirmMock(...args) },
}))

// ── lucide icon stub ─────────────────────────────────────────────────────────
vi.mock('lucide-vue-next', () => ({
  AlertTriangle: { name: 'AlertTriangle', render: () => null },
}))

// ── Composable mock ──────────────────────────────────────────────────────────
const state = {
  isImpersonating: ref(false),
  sessionId: ref<string | null>(null),
  formattedRemaining: ref('00:00'),
  isExpired: ref(false),
  ending: ref(false),
  endImpersonation: vi.fn(),
}

vi.mock('@/composables/useImpersonation', () => ({
  useImpersonation: () => state,
}))

describe('ImpersonationBanner [Phase 4 / 4.1.6]', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    state.isImpersonating.value = false
    state.sessionId.value = null
    state.formattedRemaining.value = '00:00'
    state.isExpired.value = false
    state.ending.value = false
    state.endImpersonation.mockReset()
    confirmMock.mockReset()
  })

  it('renders nothing when not impersonating', () => {
    state.isImpersonating.value = false
    const wrapper = mount(ImpersonationBanner)
    expect(wrapper.find('[data-testid="impersonation-banner"]').exists()).toBe(false)
  })

  it('renders the red banner with sessionId + countdown when impersonating', () => {
    state.isImpersonating.value = true
    state.sessionId.value = 'sess-ABC'
    state.formattedRemaining.value = '04:32'
    const wrapper = mount(ImpersonationBanner)

    const banner = wrapper.find('[data-testid="impersonation-banner"]')
    expect(banner.exists()).toBe(true)
    expect(banner.attributes('role')).toBe('alert')
    expect(wrapper.find('[data-testid="impersonation-session"]').text()).toBe('sess-ABC')
    expect(wrapper.find('[data-testid="impersonation-countdown"]').text()).toBe('04:32')
    expect(wrapper.text()).toContain('impersonation.bannerLabel')
    expect(wrapper.text()).toContain('impersonation.endButton')
  })

  it('shows the expired badge once the session is past expiry', () => {
    state.isImpersonating.value = true
    state.sessionId.value = 'sess-EXP'
    state.formattedRemaining.value = '00:00'
    state.isExpired.value = true
    const wrapper = mount(ImpersonationBanner)
    expect(wrapper.find('[data-testid="impersonation-expired"]').exists()).toBe(true)
  })

  it('omits the expired badge while time still remains', () => {
    state.isImpersonating.value = true
    state.sessionId.value = 'sess-OK'
    state.isExpired.value = false
    const wrapper = mount(ImpersonationBanner)
    expect(wrapper.find('[data-testid="impersonation-expired"]').exists()).toBe(false)
  })

  it('calls endImpersonation after user confirms the dialog', async () => {
    state.isImpersonating.value = true
    state.sessionId.value = 'sess-1'
    confirmMock.mockResolvedValueOnce('confirm')
    state.endImpersonation.mockResolvedValueOnce(undefined)

    const wrapper = mount(ImpersonationBanner)
    await wrapper.find('[data-testid="impersonation-end-btn"]').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledOnce()
    expect(state.endImpersonation).toHaveBeenCalledOnce()
  })

  it('does NOT call endImpersonation when user cancels the confirm dialog', async () => {
    state.isImpersonating.value = true
    state.sessionId.value = 'sess-2'
    confirmMock.mockRejectedValueOnce('cancel')

    const wrapper = mount(ImpersonationBanner)
    await wrapper.find('[data-testid="impersonation-end-btn"]').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledOnce()
    expect(state.endImpersonation).not.toHaveBeenCalled()
  })

  it('forwards the `ending` flag to the End button loading prop', () => {
    state.isImpersonating.value = true
    state.sessionId.value = 'sess-3'
    state.ending.value = true
    const wrapper = mount(ImpersonationBanner)
    const btn = wrapper.find('[data-testid="impersonation-end-btn"]')
    expect(btn.attributes('data-loading')).toBe('true')
  })
})
