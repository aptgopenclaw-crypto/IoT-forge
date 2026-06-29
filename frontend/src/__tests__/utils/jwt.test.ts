import { describe, it, expect } from 'vitest'
import { getJwtExp, getJwtRemainingMs } from '@/utils/jwt'

function makeToken(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = btoa(JSON.stringify(payload))
  return `${header}.${body}.signature`
}

describe('getJwtExp', () => {
  it('parses exp from a valid JWT', () => {
    const token = makeToken({ sub: 'user1', exp: 1700000000 })
    expect(getJwtExp(token)).toBe(1700000000)
  })

  it('returns null for missing exp', () => {
    const token = makeToken({ sub: 'user1' })
    expect(getJwtExp(token)).toBeNull()
  })

  it('returns null for malformed token', () => {
    expect(getJwtExp('not-a-jwt')).toBeNull()
    expect(getJwtExp('')).toBeNull()
  })

  it('handles base64url encoding', () => {
    // Create a payload that would normally need base64url
    const token = makeToken({ sub: 'user+special/chars', exp: 1800000000 })
    expect(getJwtExp(token)).toBe(1800000000)
  })
})

describe('getJwtRemainingMs', () => {
  it('returns positive ms for future expiry', () => {
    const futureExp = Math.floor(Date.now() / 1000) + 300 // 5 min from now
    const token = makeToken({ exp: futureExp })
    const remaining = getJwtRemainingMs(token)
    expect(remaining).not.toBeNull()
    expect(remaining!).toBeGreaterThan(290_000)
    expect(remaining!).toBeLessThanOrEqual(300_000)
  })

  it('returns negative ms for expired token', () => {
    const pastExp = Math.floor(Date.now() / 1000) - 60 // 1 min ago
    const token = makeToken({ exp: pastExp })
    const remaining = getJwtRemainingMs(token)
    expect(remaining).not.toBeNull()
    expect(remaining!).toBeLessThan(0)
  })

  it('returns null for invalid token', () => {
    expect(getJwtRemainingMs('invalid')).toBeNull()
  })
})
