/**
 * Phase 1.1.11: ROLE_DEPT_ADMIN i18n 顯示名稱（zh-TW / en / zh-CN）
 *
 * 驗證所有內建 role 在三個 locale 皆有對應 displayNames，且 ROLE_DEPT_ADMIN
 * 為非空字串（ADR-005）。
 */
import { describe, it, expect } from 'vitest'
import zhTW from '@/locales/zh-TW'
import zhCN from '@/locales/zh-CN'
import en from '@/locales/en'

const BUILT_IN_ROLE_IDS = [
  'ROLE_SUPER_ADMIN',
  'ROLE_ADMIN',
  'ROLE_DEPT_ADMIN',
  'ROLE_OPERATOR',
  'ROLE_VIEWER',
  'ROLE_FIELD_USER',
  'ROLE_MONITOR',
] as const

const locales: Array<{ name: string; messages: typeof zhTW }> = [
  { name: 'zh-TW', messages: zhTW },
  { name: 'en', messages: en },
  { name: 'zh-CN', messages: zhCN },
]

describe('role.displayNames i18n entries', () => {
  for (const { name, messages } of locales) {
    describe(`locale: ${name}`, () => {
      it('exposes role.displayNames map', () => {
        expect(messages.role).toBeDefined()
        expect((messages.role as Record<string, unknown>).displayNames).toBeDefined()
      })

      for (const roleId of BUILT_IN_ROLE_IDS) {
        it(`has non-empty display name for ${roleId}`, () => {
          const map = (messages.role as Record<string, Record<string, string>>).displayNames
          expect(map[roleId]).toBeDefined()
          expect(typeof map[roleId]).toBe('string')
          expect(map[roleId].trim().length).toBeGreaterThan(0)
        })
      }
    })
  }

  it('ROLE_DEPT_ADMIN translations are distinct per locale', () => {
    const tw = (zhTW.role as Record<string, Record<string, string>>).displayNames.ROLE_DEPT_ADMIN
    const enName = (en.role as Record<string, Record<string, string>>).displayNames.ROLE_DEPT_ADMIN
    expect(tw).toBe('部門管理者')
    expect(enName).toMatch(/admin/i)
  })
})
