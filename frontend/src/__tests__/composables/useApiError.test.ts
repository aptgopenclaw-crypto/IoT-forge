import { describe, it, expect } from 'vitest'
import { getErrorCode } from '@/composables/useApiError'

// We test the pure utility function (getErrorCode) without Vue context
// The composable itself (useApiError) requires Vue i18n context

describe('useApiError - getErrorCode', () => {
  it('should extract errorCode from axios error', () => {
    const err = { response: { data: { errorCode: '20001' } } }
    expect(getErrorCode(err)).toBe('20001')
  })

  it('should return undefined for non-API error', () => {
    expect(getErrorCode(new Error('network'))).toBeUndefined()
  })

  it('should return undefined for null/undefined', () => {
    expect(getErrorCode(null)).toBeUndefined()
    expect(getErrorCode(undefined)).toBeUndefined()
  })

  it('should handle missing response', () => {
    expect(getErrorCode({ response: null })).toBeUndefined()
    expect(getErrorCode({ response: {} })).toBeUndefined()
    expect(getErrorCode({ response: { data: {} } })).toBeUndefined()
  })
})
