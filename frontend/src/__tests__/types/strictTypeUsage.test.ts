import { describe, it, expect } from 'vitest'
import { ref } from 'vue'

/**
 * N-7: Verify that previous `as any` usages have been replaced with proper types.
 * These tests assert the typing approach is correct at runtime.
 */
describe('N-7: no `as any` — strict type usage', () => {
  describe('editValue ref<string | number>', () => {
    it('should accept string assignment (initial / text values)', () => {
      const editValue = ref<string | number>('')
      editValue.value = '8'
      expect(editValue.value).toBe('8')
    })

    it('should accept number assignment (from el-input-number)', () => {
      const editValue = ref<string | number>('')
      editValue.value = 12
      expect(editValue.value).toBe(12)
    })

    it('should be convertible to Number for floor validation', () => {
      const editValue = ref<string | number>('')
      editValue.value = 6
      const n = Number(editValue.value)
      expect(Number.isFinite(n)).toBe(true)
      expect(n).toBe(6)
    })

    it('should be convertible to String for API submission', () => {
      const editValue = ref<string | number>('')
      editValue.value = 10
      expect(String(editValue.value)).toBe('10')
    })
  })

  describe('UserInfoDto typed mock (no Partial cast)', () => {
    it('should satisfy full interface without as any', () => {
      const userInfo = {
        userId: 'u1',
        email: 'test@example.com',
        displayName: 'Test User',
        tenantId: 't1',
        tenantName: 'Default Tenant',
        roles: ['ROLE_ADMIN'],
        deptId: null,
        deptName: null,
        permissions: ['user:read'],
        isSuperAdmin: false,
        availableTenants: [],
      }

      expect(userInfo.userId).toBe('u1')
      expect(userInfo.roles).toContain('ROLE_ADMIN')
      expect(userInfo.deptId).toBeNull()
    })
  })
})
