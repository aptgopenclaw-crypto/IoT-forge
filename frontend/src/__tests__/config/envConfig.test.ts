import { describe, it, expect } from 'vitest'

/**
 * N-8: Verify environment variable configuration for Vite.
 */
describe('environment variable configuration', () => {
  it('VITE_API_BASE_URL should be defined via .env files', () => {
    // In test environment, Vite loads .env (production defaults)
    const baseUrl = import.meta.env.VITE_API_BASE_URL
    expect(baseUrl).toBeDefined()
    expect(typeof baseUrl).toBe('string')
    expect(baseUrl.length).toBeGreaterThan(0)
  })

  it('VITE_API_BASE_URL should contain /v1 path', () => {
    const baseUrl = import.meta.env.VITE_API_BASE_URL
    expect(baseUrl).toContain('/v1')
  })

  it('import.meta.env should expose MODE', () => {
    expect(import.meta.env.MODE).toBeDefined()
  })

  it('axiosIns should use VITE_API_BASE_URL as baseURL', async () => {
    const { default: axiosIns } = await import('@/api/axios/axiosIns')
    const envUrl = import.meta.env.VITE_API_BASE_URL
    expect(axiosIns.defaults.baseURL).toBe(envUrl)
  })
})
