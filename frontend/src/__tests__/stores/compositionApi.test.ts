import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync } from 'fs'
import { resolve } from 'path'

const storesDir = resolve(__dirname, '../../stores')
const storeFiles = readdirSync(storesDir).filter(f => f.endsWith('.ts'))

describe('F-5 — All Pinia stores use Composition API', () => {
  it.each(storeFiles)('%s uses defineStore with setup function (not options)', (file) => {
    const content = readFileSync(resolve(storesDir, file), 'utf-8')
    // Options API pattern: defineStore('name', { state, getters, actions })
    // Composition API pattern: defineStore('name', () => { ... })
    expect(content).toContain('defineStore')
    // Should NOT have `state:` or `actions:` as top-level store options
    const hasOptionsPattern = /defineStore\(\s*['"][^'"]+['"]\s*,\s*\{[\s\S]*?\bstate\s*:/m.test(content)
    expect(hasOptionsPattern, `${file} still uses Options API pattern (state:)`).toBe(false)
  })
})
