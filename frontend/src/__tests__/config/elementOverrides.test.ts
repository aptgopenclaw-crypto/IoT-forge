import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { resolve } from 'path'

describe('element-overrides.css', () => {
  const cssPath = resolve(__dirname, '../../assets/styles/element-overrides.css')
  const css = readFileSync(cssPath, 'utf-8')

  it('should contain form input wrapper styles', () => {
    expect(css).toContain('.el-input__wrapper')
    expect(css).toContain('border-radius: 8px')
  })

  it('should contain form label styles', () => {
    expect(css).toContain('.el-form-item__label')
    expect(css).toContain('color: var(--text-label)')
  })

  it('should contain form error styles', () => {
    expect(css).toContain('.el-form-item__error')
    expect(css).toContain('color: #FF6363')
  })

  it('should contain table header styles', () => {
    expect(css).toContain('.el-table th.el-table__cell')
    expect(css).toContain('text-transform: uppercase')
  })

  it('should be imported in main.ts', () => {
    const mainTs = readFileSync(resolve(__dirname, '../../main.ts'), 'utf-8')
    expect(mainTs).toContain("import './assets/styles/element-overrides.css'")
  })

  it('should not use :deep() selectors (global stylesheet)', () => {
    const lines = css.split('\n').filter(l => !l.trim().startsWith('/*') && !l.trim().startsWith('*'))
    const hasDeep = lines.some(l => l.includes(':deep('))
    expect(hasDeep).toBe(false)
  })
})
