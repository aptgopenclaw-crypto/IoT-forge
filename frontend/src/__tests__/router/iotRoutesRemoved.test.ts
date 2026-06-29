import { describe, it, expect } from 'vitest'
import { readFileSync, existsSync } from 'fs'
import { resolve } from 'path'

describe('N-16: IoT skeleton views removed', () => {
  const srcDir = resolve(__dirname, '../..')

  it('src/views/admin/iot directory should not exist', () => {
    expect(existsSync(resolve(srcDir, 'views/admin/iot'))).toBe(false)
  })

  it('router should not reference any /admin/iot paths', () => {
    const router = readFileSync(resolve(srcDir, 'router/index.ts'), 'utf-8')
    expect(router).not.toContain('/admin/iot')
  })

  it('router should not import any iot view components', () => {
    const router = readFileSync(resolve(srcDir, 'router/index.ts'), 'utf-8')
    expect(router).not.toContain('admin/iot/')
  })
})
