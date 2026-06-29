import { describe, it, expect, vi, beforeEach } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import { usePasswordPolicy } from '@/composables/usePasswordPolicy'

const mockDescribePolicy = vi.fn()

vi.mock('@/api/passwordPolicy', () => ({
  describePolicy: (...args: unknown[]) => mockDescribePolicy(...args),
}))

function makePolicy(overrides = {}) {
  return {
    body: {
      minLength: 8,
      requireUppercase: true,
      requireLowercase: true,
      requireDigit: true,
      requireSpecial: true,
      historyCount: 0,
      maxLength: 64,
      minSpecialChars: 1,
      minDigits: 1,
      minUppercase: 1,
      minLowercase: 1,
      notContainsUsername: false,
      expireDays: 0,
      forceChangeOnFirstLogin: false,
      forceChangeOnAdminReset: false,
      describe: ['至少8字元', '須包含大寫英文字母'],
      ...overrides,
    },
  }
}

describe('usePasswordPolicy', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    vi.clearAllMocks()
    scope = effectScope()
  })

  it('fetches policy on mount and exposes validatePassword', async () => {
    mockDescribePolicy.mockResolvedValue(makePolicy())

    const { validatePassword, policy } = scope.run(() => usePasswordPolicy())!
    await nextTick()
    // Wait for async load
    await vi.waitFor(() => expect(policy.value).not.toBeNull())

    // Valid password
    expect(validatePassword('Abc1234!')).toBe(true)
    // Missing uppercase
    expect(validatePassword('abc1234!')).toBe(false)
    // Missing lowercase
    expect(validatePassword('ABC1234!')).toBe(false)
    // Missing digit
    expect(validatePassword('Abcdefg!')).toBe(false)
    // Missing special
    expect(validatePassword('Abc12345')).toBe(false)
    // Too short
    expect(validatePassword('Ab1!')).toBe(false)

    scope.stop()
  })

  it('returns true when policy has not loaded (defers to backend)', () => {
    mockDescribePolicy.mockReturnValue(new Promise(() => {})) // never resolves

    const { validatePassword } = scope.run(() => usePasswordPolicy())!
    // Policy is still null — should return true (defer)
    expect(validatePassword('anything')).toBe(true)

    scope.stop()
  })

  it('enforces maxLength when set', async () => {
    mockDescribePolicy.mockResolvedValue(makePolicy({ maxLength: 16 }))

    const { validatePassword, policy } = scope.run(() => usePasswordPolicy())!
    await vi.waitFor(() => expect(policy.value).not.toBeNull())

    expect(validatePassword('Abc1234!')).toBe(true)
    expect(validatePassword('Abc1234!Abc1234!X')).toBe(false) // 17 chars
    scope.stop()
  })

  it('skips maxLength check when maxLength is 0', async () => {
    mockDescribePolicy.mockResolvedValue(makePolicy({ maxLength: 0 }))

    const { validatePassword, policy } = scope.run(() => usePasswordPolicy())!
    await vi.waitFor(() => expect(policy.value).not.toBeNull())

    const longPassword = 'A'.repeat(100) + 'a1!'
    expect(validatePassword(longPassword)).toBe(true)
    scope.stop()
  })

  it('reloads when tenantId changes', async () => {
    const tenantA = makePolicy({ minLength: 10 })
    const tenantB = makePolicy({ minLength: 12 })
    mockDescribePolicy
      .mockResolvedValueOnce(tenantA)
      .mockResolvedValueOnce(tenantB)

    const tenantId = ref<string | undefined>('TENANT_A')
    const { validatePassword, policy } = scope.run(() => usePasswordPolicy(tenantId))!
    await vi.waitFor(() => expect(policy.value).not.toBeNull())

    expect(mockDescribePolicy).toHaveBeenCalledWith('TENANT_A')
    expect(validatePassword('Abc12345!')).toBe(false) // minLength 10

    tenantId.value = 'TENANT_B'
    await nextTick()
    await vi.waitFor(() => expect(policy.value?.minLength).toBe(12))

    expect(mockDescribePolicy).toHaveBeenCalledWith('TENANT_B')
    scope.stop()
  })

  it('does not require special when requireSpecial is false', async () => {
    mockDescribePolicy.mockResolvedValue(makePolicy({ requireSpecial: false }))

    const { validatePassword, policy } = scope.run(() => usePasswordPolicy())!
    await vi.waitFor(() => expect(policy.value).not.toBeNull())

    // No special char but meets all other criteria
    expect(validatePassword('Abc12345')).toBe(true)
    scope.stop()
  })

  it('exposes descriptions from the policy', async () => {
    mockDescribePolicy.mockResolvedValue(
      makePolicy({ describe: ['Rule 1', 'Rule 2'] }),
    )

    const { descriptions, policy } = scope.run(() => usePasswordPolicy())!
    await vi.waitFor(() => expect(policy.value).not.toBeNull())

    expect(descriptions.value).toEqual(['Rule 1', 'Rule 2'])
    scope.stop()
  })
})
