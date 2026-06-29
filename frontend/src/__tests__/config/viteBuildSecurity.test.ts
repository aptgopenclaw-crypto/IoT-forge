import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * Verify vite.config.ts contains the security-critical build settings.
 * This test acts as a guard against accidental removal of the console-stripping config.
 */
describe('vite.config.ts build security settings', () => {
  const configContent = readFileSync(
    resolve(__dirname, '../../..', 'vite.config.ts'),
    'utf-8',
  )

  it('should configure esbuild to drop console in production', () => {
    expect(configContent).toContain("drop:")
    expect(configContent).toContain("'console'")
  })

  it('should configure esbuild to drop debugger in production', () => {
    expect(configContent).toContain("'debugger'")
  })

  it('should only drop in production (not dev/test)', () => {
    // The config should be conditional on NODE_ENV
    expect(configContent).toContain("process.env.NODE_ENV === 'production'")
  })

  it('should use esbuild as minifier', () => {
    expect(configContent).toContain("minify: 'esbuild'")
  })
})
