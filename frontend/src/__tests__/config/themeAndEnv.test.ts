import { describe, it, expect } from 'vitest'
import { readFileSync, existsSync } from 'fs'
import { resolve } from 'path'

const frontendRoot = resolve(__dirname, '../../../')

describe('F-6 — Theme tokens file', () => {
  const tokensPath = resolve(frontendRoot, 'src/assets/styles/theme-tokens.css')

  it('theme-tokens.css exists', () => {
    expect(existsSync(tokensPath)).toBe(true)
  })

  it('defines dark theme variables with actual values (not self-referential)', () => {
    const content = readFileSync(tokensPath, 'utf-8')
    // Should define --bg-base with an actual color value, not var(--bg-base)
    const darkSection = content.match(/html\.dark\s*\{([^}]+)\}/s)?.[1] ?? content
    expect(darkSection).toContain('--bg-base:')
    expect(darkSection).not.toContain('--bg-base: var(--bg-base)')
    expect(darkSection).not.toContain('--bg-base:           var(--bg-base)')
  })

  it('maps app tokens to --el-* Element Plus tokens', () => {
    const content = readFileSync(tokensPath, 'utf-8')
    expect(content).toContain('--el-bg-color:')
    expect(content).toContain('--el-text-color-primary:')
    expect(content).toContain('--el-border-color:')
    expect(content).toContain('--el-color-primary:')
  })

  it('defines both dark and light themes', () => {
    const content = readFileSync(tokensPath, 'utf-8')
    expect(content).toContain('html.dark')
    expect(content).toContain('html.light')
  })
})

describe('F-7 — Build profiles and typed env', () => {
  it('env.d.ts declares VITE_SENTRY_DSN and VITE_ENABLE_IOT', () => {
    const envDts = readFileSync(resolve(frontendRoot, 'env.d.ts'), 'utf-8')
    expect(envDts).toContain('VITE_SENTRY_DSN')
    expect(envDts).toContain('VITE_ENABLE_IOT')
  })

  it('.env.staging exists with feature flags', () => {
    const staging = readFileSync(resolve(frontendRoot, '.env.staging'), 'utf-8')
    expect(staging).toContain('VITE_API_BASE_URL')
    expect(staging).toContain('VITE_SENTRY_DSN')
    expect(staging).toContain('VITE_ENABLE_IOT')
  })

  it('.env.production exists with feature flags', () => {
    const prod = readFileSync(resolve(frontendRoot, '.env.production'), 'utf-8')
    expect(prod).toContain('VITE_API_BASE_URL')
    expect(prod).toContain('VITE_SENTRY_DSN')
    expect(prod).toContain('VITE_ENABLE_IOT')
  })

  it('package.json has build:staging script', () => {
    const pkg = JSON.parse(readFileSync(resolve(frontendRoot, 'package.json'), 'utf-8'))
    expect(pkg.scripts['build:staging']).toContain('--mode staging')
  })
})
