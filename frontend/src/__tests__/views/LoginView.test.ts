import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import LoginView from '@/views/login/LoginView.vue'

// Mock vue-router
const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

// Mock element-plus to avoid CSS import issues
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
  ElForm: { template: '<form><slot /></form>', props: ['model', 'rules', 'labelWidth', 'size'], methods: { validate: () => Promise.resolve(true) } },
  ElFormItem: { template: '<div class="form-item"><slot /></div>', props: ['prop'] },
  ElInput: { template: '<div class="el-input"><slot name="prefix" /><input /><slot name="append" /></div>', props: ['modelValue', 'placeholder', 'type', 'showPassword'] },
  ElButton: { template: '<button class="el-button"><slot /></button>', props: ['loading', 'nativeType'] },
}))

// Mock authStore actions
const doGetCaptchaMock = vi.fn()
const doLoginMock = vi.fn()

vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    doGetCaptcha: doGetCaptchaMock,
    doLogin: doLoginMock,
  }),
}))

// Mock vue-i18n
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

// Mock lucide-vue-next icons as stubs
vi.mock('lucide-vue-next', () => ({
  Mail: { name: 'Mail', render: () => null },
  Lock: { name: 'Lock', render: () => null },
  ShieldCheck: { name: 'ShieldCheck', render: () => null },
}))

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    doGetCaptchaMock.mockResolvedValue({ captchaImage: 'data:image/png;base64,abc', captchaKey: 'key-1' })
  })

  function factory() {
    return mount(LoginView, {
      global: {
        stubs: {
          Mail: true,
          Lock: true,
          ShieldCheck: true,
        },
      },
    })
  }

  it('renders login title and subtitle', () => {
    const wrapper = factory()
    expect(wrapper.text()).toContain('login.title')
    expect(wrapper.text()).toContain('login.subtitle')
  })

  it('calls doGetCaptcha on mount', async () => {
    factory()
    await flushPromises()
    expect(doGetCaptchaMock).toHaveBeenCalledOnce()
  })

  it('displays captcha image when loaded', async () => {
    const wrapper = factory()
    await flushPromises()
    const img = wrapper.find('img.captcha-image')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('data:image/png;base64,abc')
  })

  it('shows forgot password link', () => {
    const wrapper = factory()
    const link = wrapper.find('.forgot-password-link')
    expect(link.exists()).toBe(true)
  })

  it('navigates to forgot-password on link click', async () => {
    const wrapper = factory()
    await wrapper.find('.forgot-password-link').trigger('click')
    expect(pushMock).toHaveBeenCalledWith('/forgot-password')
  })

  it('has a submit button', () => {
    const wrapper = factory()
    const btn = wrapper.find('.login-btn')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toContain('login.submit')
  })
})
