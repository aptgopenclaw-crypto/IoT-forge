/**
 * F-12: i18n key alignment check
 *
 * Validates that all three locale files (zh-TW, zh-CN, en) have the same
 * set of nested keys. Reports missing keys per locale.
 */
import { describe, it, expect } from 'vitest'
import zhTW from '@/locales/zh-TW'
import zhCN from '@/locales/zh-CN'
import en from '@/locales/en'

function flattenKeys(obj: Record<string, unknown>, prefix = ''): string[] {
  const keys: string[] = []
  for (const key of Object.keys(obj)) {
    const path = prefix ? `${prefix}.${key}` : key
    const value = obj[key]
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...flattenKeys(value as Record<string, unknown>, path))
    } else {
      keys.push(path)
    }
  }
  return keys
}

describe('i18n key alignment', () => {
  const twKeys = new Set(flattenKeys(zhTW))
  const cnKeys = new Set(flattenKeys(zhCN))
  const enKeys = new Set(flattenKeys(en))

  // Use zh-TW as the reference (primary locale / fallbackLocale)
  const allKeys = new Set([...twKeys, ...cnKeys, ...enKeys])

  it('zh-TW should contain all keys defined across locales', () => {
    const missing = [...allKeys].filter((k) => !twKeys.has(k))
    expect(missing, `zh-TW is missing keys: ${missing.join(', ')}`).toEqual([])
  })

  it('zh-CN should contain all keys defined in zh-TW', () => {
    const missing = [...twKeys].filter((k) => !cnKeys.has(k))
    expect(missing, `zh-CN is missing keys: ${missing.join(', ')}`).toEqual([])
  })

  it('en should contain all keys defined in zh-TW', () => {
    const missing = [...twKeys].filter((k) => !enKeys.has(k))
    expect(missing, `en is missing keys: ${missing.join(', ')}`).toEqual([])
  })

  it('no locale should have extra keys not in zh-TW (reference)', () => {
    const extraCN = [...cnKeys].filter((k) => !twKeys.has(k))
    const extraEN = [...enKeys].filter((k) => !twKeys.has(k))
    const extras = [...extraCN.map((k) => `zh-CN:${k}`), ...extraEN.map((k) => `en:${k}`)]
    expect(extras, `Extra keys found: ${extras.join(', ')}`).toEqual([])
  })
})
