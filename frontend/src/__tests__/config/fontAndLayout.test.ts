import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'fs'
import { resolve, join } from 'path'

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

describe('N-13: font-family and layout deduplication', () => {
  const srcDir = resolve(__dirname, '../..')

  it('App.vue sets font-family on root #app', () => {
    const app = readFileSync(resolve(srcDir, 'App.vue'), 'utf-8')
    expect(app).toContain("font-family: 'Inter', sans-serif")
  })

  it('page-layout.css provides font-family: inherit for form elements', () => {
    const css = readFileSync(resolve(srcDir, 'assets/styles/page-layout.css'), 'utf-8')
    expect(css).toContain('font-family: inherit')
    expect(css).toMatch(/input.*button.*select.*textarea/)
  })

  it('no view or component file re-declares font-family Inter', () => {
    const viewFiles = collectVueFiles(resolve(srcDir, 'views'))
    const componentFiles = collectVueFiles(resolve(srcDir, 'components'))
    const allFiles = [...viewFiles, ...componentFiles]

    const violators: string[] = []
    for (const file of allFiles) {
      const content = readFileSync(file, 'utf-8')
      if (content.includes("font-family: 'Inter', sans-serif")) {
        violators.push(file.replace(srcDir + '/', ''))
      }
    }
    expect(violators).toEqual([])
  })

  it('page-layout.css defines .page-container global class', () => {
    const css = readFileSync(resolve(srcDir, 'assets/styles/page-layout.css'), 'utf-8')
    expect(css).toContain('.page-container')
    expect(css).toContain('padding: 32px 24px')
    expect(css).toContain('min-height: 100vh')
  })

  it('page-layout.css defines .page-title global class', () => {
    const css = readFileSync(resolve(srcDir, 'assets/styles/page-layout.css'), 'utf-8')
    expect(css).toContain('.page-title')
    expect(css).toContain('font-size: 28px')
    expect(css).toContain('font-weight: 600')
  })
})
