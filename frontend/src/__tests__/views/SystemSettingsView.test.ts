import { describe, it, expect } from 'vitest'
import zhTW from '@/locales/zh-TW'
import en from '@/locales/en'
import zhCN from '@/locales/zh-CN'
import { readFileSync } from 'fs'
import { resolve } from 'path'

/**
 * N-7: Setting descriptions should go through i18n.
 * This test verifies that:
 * 1. All known setting keys have corresponding i18n entries in all locales
 * 2. The Vue template uses t() with proper keys (not raw DB description)
 */

const KNOWN_SETTING_KEYS = [
  'idle_timeout_minutes',
  'audit_retention_days',
  'notification_retention_days',
  'frontend_base_url',
]

describe('SystemSettingsView i18n descriptions (N-7)', () => {
  describe('locale files contain setting.keys entries', () => {
    it('zh-TW has all setting key descriptions', () => {
      for (const key of KNOWN_SETTING_KEYS) {
        expect((zhTW as Record<string, unknown>).setting).toHaveProperty(`keys.${key}`)
        const value = (zhTW.setting as Record<string, Record<string, string>>).keys[key]
        expect(value).toBeTruthy()
      }
    })

    it('en has all setting key descriptions', () => {
      for (const key of KNOWN_SETTING_KEYS) {
        expect((en as Record<string, unknown>).setting).toHaveProperty(`keys.${key}`)
        const value = (en.setting as Record<string, Record<string, string>>).keys[key]
        expect(value).toBeTruthy()
      }
    })

    it('zh-CN has all setting key descriptions', () => {
      for (const key of KNOWN_SETTING_KEYS) {
        expect((zhCN as Record<string, unknown>).setting).toHaveProperty(`keys.${key}`)
        const value = (zhCN.setting as Record<string, Record<string, string>>).keys[key]
        expect(value).toBeTruthy()
      }
    })
  })

  describe('template uses i18n key pattern', () => {
    const templateSource = readFileSync(
      resolve(__dirname, '../../views/admin/setting/SystemSettingsView.vue'),
      'utf-8',
    )

    it('table column uses t() with setting.keys prefix and row.description fallback', () => {
      // Verify template does NOT use raw prop="description" for the description column
      expect(templateSource).not.toMatch(/<el-table-column[^>]*prop="description"/)
      // Verify template uses t(`setting.keys.${...}`, ...) pattern
      expect(templateSource).toContain('t(`setting.keys.${row.settingKey}`, row.description)')
    })

    it('edit dialog uses t() for description instead of raw interpolation', () => {
      // Should NOT have raw {{ editingRow?.description }}
      expect(templateSource).not.toContain('{{ editingRow?.description }}')
      // Should use i18n pattern
      expect(templateSource).toContain('t(`setting.keys.${editingRow.settingKey}`, editingRow.description)')
    })
  })
})
