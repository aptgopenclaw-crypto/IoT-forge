import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { h, nextTick, type VNode } from 'vue'
import ImpersonationManageView from '@/views/platform/ImpersonationManageView.vue'

// ── i18n stub ────────────────────────────────────────────────────────────────
vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

// ── Element Plus stubs ───────────────────────────────────────────────────────
// vi.mock factories are hoisted, so any shared spies must come from vi.hoisted.
const {
  messageSpies,
  confirmMock,
  listTenantsMock,
  createMock,
  listMock,
  revokeMock,
  applyImpersonationTokenMock,
} = vi.hoisted(() => ({
  messageSpies: { error: vi.fn(), warning: vi.fn(), success: vi.fn() },
  confirmMock: vi.fn(),
  listTenantsMock: vi.fn(),
  createMock: vi.fn(),
  listMock: vi.fn(),
  revokeMock: vi.fn(),
  applyImpersonationTokenMock: vi.fn(),
}))

vi.mock('element-plus', () => {
  const stub = (name: string, extra: Record<string, unknown> = {}) => ({
    name,
    inheritAttrs: false,
    props: ['modelValue', 'type', 'size', 'loading', 'placeholder', 'rows',
            'maxlength', 'min', 'max', 'step', 'filterable', 'data', 'emptyText',
            'label', 'value', 'required', 'labelPosition', 'showWordLimit', 'link'],
    emits: ['update:modelValue', 'change', 'click'],
    template: '<div :data-testid="$attrs[\'data-testid\']" :class="$attrs.class"><slot /><slot name="default" :row="{}" /></div>',
    ...extra,
  })
  return {
    ElButton: {
      name: 'ElButton',
      inheritAttrs: false,
      props: ['type', 'size', 'loading', 'link'],
      emits: ['click'],
      template:
        '<button class="el-button" :data-loading="loading" :data-testid="$attrs[\'data-testid\']" @click="$emit(\'click\', $event)"><slot /></button>',
    },
    ElForm: stub('ElForm'),
    ElFormItem: stub('ElFormItem'),
    ElInput: {
      name: 'ElInput',
      inheritAttrs: false,
      props: ['modelValue'],
      emits: ['update:modelValue'],
      template:
        '<input :data-testid="$attrs[\'data-testid\']" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    },
    ElInputNumber: {
      name: 'ElInputNumber',
      inheritAttrs: false,
      props: ['modelValue'],
      emits: ['update:modelValue'],
      template:
        '<input type="number" :data-testid="$attrs[\'data-testid\']" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
    },
    ElSelect: {
      name: 'ElSelect',
      inheritAttrs: false,
      props: ['modelValue'],
      emits: ['update:modelValue', 'change'],
      template:
        '<select :data-testid="$attrs[\'data-testid\']" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
    },
    ElOption: {
      name: 'ElOption',
      props: ['label', 'value'],
      template: '<option :value="value">{{ label }}</option>',
    },
    ElTable: {
      name: 'ElTable',
      inheritAttrs: false,
      props: ['data', 'emptyText'],
      setup(props: { data: unknown[] }, { slots, attrs }: { slots: Record<string, ((ctx: unknown) => VNode[]) | undefined>; attrs: Record<string, unknown> }) {
        return () =>
          h(
            'div',
            { 'data-testid': attrs['data-testid'] as string | undefined, class: 'el-table' },
            (props.data ?? []).map((row) =>
              h(
                'div',
                { 'data-testid': 'impersonation-row' },
                // Invoke the default slot with each row so child ElTableColumn
                // stubs get the row context via their named scoped slot.
                (slots.default?.({ row }) ?? []).map((vnode) => {
                  // Each vnode is an ElTableColumn; render its `default` scoped
                  // slot with `{ row }` so cells can pull from row.
                  const children = (vnode.children ?? {}) as Record<string, ((ctx: unknown) => VNode[]) | undefined>
                  if (children && typeof children === 'object' && children.default) {
                    return h('div', {}, children.default({ row }))
                  }
                  return null
                }),
              ),
            ),
          )
      },
    },
    ElTableColumn: {
      name: 'ElTableColumn',
      props: ['prop', 'label', 'width'],
      // No-op render — the parent ElTable stub above invokes our default
      // scoped slot directly so we don't need a real DOM here.
      render() {
        return null
      },
    },
    ElTag: {
      name: 'ElTag',
      props: ['type', 'size'],
      template: '<span class="el-tag"><slot /></span>',
    },
    ElMessage: messageSpies,
    ElMessageBox: { confirm: (...args: unknown[]) => confirmMock(...args) },
    vLoading: { mounted() {}, updated() {} },
    ElLoadingDirective: { mounted() {}, updated() {} },
  }
})

// ── lucide icon stub ─────────────────────────────────────────────────────────
vi.mock('lucide-vue-next', () => ({
  ShieldAlert: { name: 'ShieldAlert', render: () => null },
  RefreshCw: { name: 'RefreshCw', render: () => null },
  UserCog: { name: 'UserCog', render: () => null },
}))

// ── API mocks ────────────────────────────────────────────────────────────────
vi.mock('@/api/tenant/admin', () => ({
  listTenants: () => listTenantsMock(),
}))
vi.mock('@/api/impersonation', () => ({
  createImpersonation: (p: unknown) => createMock(p),
  listImpersonations: (s?: string) => listMock(s),
  revokeImpersonation: (id: string) => revokeMock(id),
}))

vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    applyImpersonationToken: applyImpersonationTokenMock,
  }),
}))

function mountView() {
  return mount(ImpersonationManageView)
}

const baseResponse = <T,>(body: T) => ({
  errorCode: '00000',
  errorMsg: '',
  errorDetail: '',
  timestamp: '',
  body,
})

describe('ImpersonationManageView [Phase 4 / 4.1.9]', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listTenantsMock.mockResolvedValue(
      baseResponse([
        { tenantId: 'tn-1', tenantCode: 'ALPHA', tenantName: 'Alpha', enabled: true },
        { tenantId: 'tn-2', tenantCode: 'BETA', tenantName: 'Beta', enabled: true },
        { tenantId: 'tn-3', tenantCode: 'OFF', tenantName: 'Disabled', enabled: false },
      ]),
    )
    listMock.mockResolvedValue(
      baseResponse([
        {
          id: 'sess-1',
          operatorUserId: 'op-1',
          targetTenantId: 'tn-1',
          targetTenantName: 'Alpha',
          reason: 'support ticket',
          status: 'ACTIVE',
          startedAt: '2026-05-31T10:00:00',
          expiresAt: '2026-05-31T10:30:00',
          revokedAt: null,
        },
        {
          id: 'sess-2',
          operatorUserId: 'op-1',
          targetTenantId: 'tn-2',
          targetTenantName: 'Beta',
          reason: 'data fix',
          status: 'EXPIRED',
          startedAt: '2026-05-30T08:00:00',
          expiresAt: '2026-05-30T08:15:00',
          revokedAt: null,
        },
      ]),
    )
    createMock.mockResolvedValue(
      baseResponse({
        accessToken: 'new-imp-token',
        sessionId: 'sess-new',
        targetTenantId: 'tn-1',
        expiresAt: '2026-05-31T10:30:00',
        scope: 'IMPERSONATION',
      }),
    )
    revokeMock.mockResolvedValue(baseResponse(null))
  })

  it('renders the create form and history panels', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-testid="impersonation-manage-view"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="impersonation-create-panel"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="impersonation-history-panel"]').exists()).toBe(true)
  })

  it('loads tenants and sessions on mount', async () => {
    mountView()
    await flushPromises()
    expect(listTenantsMock).toHaveBeenCalledOnce()
    expect(listMock).toHaveBeenCalledOnce()
  })

  it('filters out disabled tenants from the select', async () => {
    const wrapper = mountView()
    await flushPromises()
    const options = wrapper.findAll('option')
    const labels = options.map((o) => o.text())
    expect(labels.some((l) => l.includes('Alpha'))).toBe(true)
    expect(labels.some((l) => l.includes('Beta'))).toBe(true)
    expect(labels.some((l) => l.includes('Disabled'))).toBe(false)
  })

  it('warns when tenant or reason missing on submit', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('[data-testid="impersonation-submit-btn"]').trigger('click')
    expect(messageSpies.warning).toHaveBeenCalledWith('impersonationManage.errors.tenantRequired')
    expect(createMock).not.toHaveBeenCalled()
  })

  it('warns when duration is out of range', async () => {
    const wrapper = mountView()
    await flushPromises()
    // tenant + reason set, duration invalid
    await wrapper.find('[data-testid="impersonation-tenant-select"]').setValue('tn-1')
    await wrapper.find('[data-testid="impersonation-reason-input"]').setValue('valid reason')
    await wrapper.find('[data-testid="impersonation-duration-input"]').setValue('120')
    await wrapper.find('[data-testid="impersonation-submit-btn"]').trigger('click')
    await flushPromises()
    expect(messageSpies.warning).toHaveBeenCalledWith('impersonationManage.errors.durationRange')
    expect(createMock).not.toHaveBeenCalled()
  })

  it('creates session and swaps token via authStore on success', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('[data-testid="impersonation-tenant-select"]').setValue('tn-1')
    await wrapper.find('[data-testid="impersonation-reason-input"]').setValue('debug bug #42')
    await wrapper.find('[data-testid="impersonation-duration-input"]').setValue('20')
    await wrapper.find('[data-testid="impersonation-submit-btn"]').trigger('click')
    await flushPromises()

    expect(createMock).toHaveBeenCalledWith({
      tenantId: 'tn-1',
      reason: 'debug bug #42',
      durationMinutes: 20,
    })
    expect(messageSpies.success).toHaveBeenCalledWith('impersonationManage.createSuccess')
    expect(applyImpersonationTokenMock).toHaveBeenCalledWith('new-imp-token')
  })

  it('shows an error toast when create fails', async () => {
    createMock.mockRejectedValueOnce(new Error('boom'))
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('[data-testid="impersonation-tenant-select"]').setValue('tn-1')
    await wrapper.find('[data-testid="impersonation-reason-input"]').setValue('reason')
    await wrapper.find('[data-testid="impersonation-submit-btn"]').trigger('click')
    await flushPromises()
    expect(messageSpies.error).toHaveBeenCalledWith('impersonationManage.createFailed')
    expect(applyImpersonationTokenMock).not.toHaveBeenCalled()
  })

  it('reloads sessions with the selected status filter', async () => {
    const wrapper = mountView()
    await flushPromises()
    listMock.mockClear()
    const select = wrapper.find('[data-testid="impersonation-status-filter"]')
    await select.setValue('ACTIVE')
    await select.trigger('change')
    await flushPromises()
    expect(listMock).toHaveBeenCalledWith('ACTIVE')
  })

  it('refresh button re-fetches sessions', async () => {
    const wrapper = mountView()
    await flushPromises()
    listMock.mockClear()
    await wrapper.find('[data-testid="impersonation-refresh-btn"]').trigger('click')
    await flushPromises()
    expect(listMock).toHaveBeenCalledOnce()
  })

  it('shows a load error when list call fails', async () => {
    listMock.mockRejectedValueOnce(new Error('500'))
    mountView()
    await flushPromises()
    expect(messageSpies.error).toHaveBeenCalledWith('impersonationManage.loadFailed')
  })

  it('confirms then revokes a session and reloads history', async () => {
    confirmMock.mockResolvedValueOnce('confirm')
    const wrapper = mountView()
    await flushPromises()
    listMock.mockClear()

    // The view uses table-scoped slots; revoke buttons appear once per row.
    const revokeBtns = wrapper.findAll('[data-testid="impersonation-row-revoke-btn"]')
    expect(revokeBtns.length).toBeGreaterThan(0)
    await revokeBtns[0].trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledOnce()
    expect(revokeMock).toHaveBeenCalled()
    expect(messageSpies.success).toHaveBeenCalledWith('impersonationManage.revokeSuccess')
    expect(listMock).toHaveBeenCalledOnce()
  })

  it('does NOT revoke when user cancels the confirm dialog', async () => {
    confirmMock.mockRejectedValueOnce('cancel')
    const wrapper = mountView()
    await flushPromises()
    const revokeBtns = wrapper.findAll('[data-testid="impersonation-row-revoke-btn"]')
    await revokeBtns[0].trigger('click')
    await flushPromises()
    expect(revokeMock).not.toHaveBeenCalled()
  })

  it('renders revoke button only for ACTIVE rows', async () => {
    const wrapper = mountView()
    await flushPromises()
    await nextTick()
    const buttons = wrapper.findAll('[data-testid="impersonation-row-revoke-btn"]')
    // sess-1 is ACTIVE, sess-2 is EXPIRED → exactly 1 revoke button
    expect(buttons.length).toBe(1)
  })
})
