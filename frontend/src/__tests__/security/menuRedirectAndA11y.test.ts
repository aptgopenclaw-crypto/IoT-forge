import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'fs'
import { resolve, join } from 'path'

/**
 * N-14: redirect field must only accept internal paths (/ prefix)
 * N-15: dialogs should have ARIA attributes for accessibility
 */

// The redirect validator regex from MenuFormDialog.vue
const REDIRECT_PATTERN = /^\/[a-zA-Z0-9\-_/]*$/

describe('N-14: Menu redirect path whitelist', () => {
  it('should accept valid internal paths', () => {
    expect(REDIRECT_PATTERN.test('/dashboard')).toBe(true)
    expect(REDIRECT_PATTERN.test('/admin/users')).toBe(true)
    expect(REDIRECT_PATTERN.test('/settings/system')).toBe(true)
    expect(REDIRECT_PATTERN.test('/a-b_c/d')).toBe(true)
    expect(REDIRECT_PATTERN.test('/')).toBe(true)
  })

  it('should reject external URLs', () => {
    expect(REDIRECT_PATTERN.test('https://evil.com')).toBe(false)
    expect(REDIRECT_PATTERN.test('http://attacker.org/phish')).toBe(false)
    expect(REDIRECT_PATTERN.test('//evil.com')).toBe(false)
  })

  it('should reject paths without leading slash', () => {
    expect(REDIRECT_PATTERN.test('dashboard')).toBe(false)
    expect(REDIRECT_PATTERN.test('admin/users')).toBe(false)
  })

  it('should reject paths with query strings or fragments', () => {
    expect(REDIRECT_PATTERN.test('/page?param=value')).toBe(false)
    expect(REDIRECT_PATTERN.test('/page#section')).toBe(false)
  })

  it('should reject javascript: protocol', () => {
    expect(REDIRECT_PATTERN.test('javascript:alert(1)')).toBe(false)
  })

  it('MenuFormDialog has redirect validation rule with prop binding', () => {
    const src = readFileSync(
      resolve(__dirname, '../../views/admin/menu/MenuFormDialog.vue'),
      'utf-8',
    )
    expect(src).toContain("redirect: [")
    expect(src).toContain('prop="redirect"')
  })
})

function collectVueFiles(dir: string): string[] {
  const files: string[] = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      files.push(...collectVueFiles(full))
    } else if (entry.endsWith('.vue')) {
      files.push(full)
    }
  }
  return files
}

describe('N-15: Dialog ARIA accessibility', () => {
  const srcDir = resolve(__dirname, '../..')
  const dialogFiles = [
    'views/admin/menu/MenuFormDialog.vue',
    'views/admin/tenant/TenantManageView.vue',
    'views/admin/role/RolePermissionView.vue',
    'views/admin/setting/SystemSettingsView.vue',
    'views/admin/setting/PlatformPasswordPolicyView.vue',
    'views/admin/setting/TenantPasswordPolicyView.vue',
    'views/admin/announcement/AnnouncementManagementView.vue',
  ]

  it('all dialog forms have aria-label', () => {
    const missing: string[] = []
    for (const file of dialogFiles) {
      const content = readFileSync(resolve(srcDir, file), 'utf-8')
      if (!content.includes('aria-label')) {
        missing.push(file)
      }
    }
    expect(missing).toEqual([])
  })

  it('all el-dialog instances have a title prop (for aria-labelledby)', () => {
    const missing: string[] = []
    for (const file of dialogFiles) {
      const content = readFileSync(resolve(srcDir, file), 'utf-8')
      const dialogMatches = content.match(/<el-dialog[^>]*>/g) || []
      for (const dialog of dialogMatches) {
        if (!dialog.includes(':title') && !dialog.includes('title=')) {
          missing.push(`${file}: ${dialog.slice(0, 60)}`)
        }
      }
    }
    expect(missing).toEqual([])
  })

  it('non-el-form dialog content has role="form"', () => {
    const files = [
      'views/admin/tenant/TenantManageView.vue',
      'views/admin/role/RolePermissionView.vue',
      'views/admin/setting/SystemSettingsView.vue',
    ]
    for (const file of files) {
      const content = readFileSync(resolve(srcDir, file), 'utf-8')
      expect(content).toContain('role="form"')
    }
  })
})
