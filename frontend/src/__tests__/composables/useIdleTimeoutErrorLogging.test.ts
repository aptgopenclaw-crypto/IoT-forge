import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { resolve } from 'path'

/**
 * N-8: useIdleTimeout should log non-401/403 errors via console.warn
 * instead of silently swallowing them.
 */
describe('useIdleTimeout error logging (N-8)', () => {
  const src = readFileSync(
    resolve(__dirname, '../../composables/useIdleTimeout.ts'),
    'utf-8',
  )

  it('should log non-401/403 errors with console.warn', () => {
    expect(src).toContain('console.warn')
    expect(src).toContain('[useIdleTimeout]')
  })

  it('should check response status before logging', () => {
    expect(src).toContain('status !== 401')
    expect(src).toContain('status !== 403')
  })

  it('should still set default timeout on error', () => {
    // Verify the fallback assignment is still present
    expect(src).toContain('timeoutMinutes.value = DEFAULT_IDLE_TIMEOUT_MINUTES')
  })

  it('should not silently swallow errors (empty catch block)', () => {
    // The catch block should NOT be empty — it should have the warn logic
    const catchMatch = src.match(/\} catch \(err[^)]*\) \{([\s\S]*?)\n    \}/)
    expect(catchMatch).not.toBeNull()
    const catchBody = catchMatch![1]
    expect(catchBody).toContain('console.warn')
  })
})
