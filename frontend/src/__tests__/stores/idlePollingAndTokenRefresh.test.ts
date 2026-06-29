import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { resolve } from 'path'

describe('N-17: notificationStore idle-aware polling', () => {
  const storeSrc = readFileSync(
    resolve(__dirname, '../../stores/notificationStore.ts'),
    'utf-8',
  )

  it('exposes userIdle ref', () => {
    expect(storeSrc).toContain('userIdle')
    expect(storeSrc).toContain('const userIdle = ref(false)')
  })

  it('listens for visibilitychange events', () => {
    expect(storeSrc).toContain('visibilitychange')
    expect(storeSrc).toContain('document.addEventListener')
  })

  it('pauses polling when document is hidden', () => {
    expect(storeSrc).toContain("document.visibilityState === 'hidden'")
    expect(storeSrc).toContain('clearPollTimer()')
  })

  it('resumes polling when document becomes visible', () => {
    expect(storeSrc).toContain('resumePolling()')
    expect(storeSrc).toContain('fetchUnreadCount()')
  })

  it('starts idle detection in startPolling()', () => {
    expect(storeSrc).toContain('_startIdleDetection()')
  })

  it('stops idle detection in stopPolling()', () => {
    expect(storeSrc).toContain('_stopIdleDetection()')
  })
})

describe('N-18: authStore proactive token refresh', () => {
  const storeSrc = readFileSync(
    resolve(__dirname, '../../stores/authStore.ts'),
    'utf-8',
  )

  it('imports getJwtRemainingMs utility', () => {
    expect(storeSrc).toContain("import { getJwtRemainingMs } from '@/utils/jwt'")
  })

  it('defines REFRESH_BEFORE_EXPIRY_MS constant (60s)', () => {
    expect(storeSrc).toContain('REFRESH_BEFORE_EXPIRY_MS = 60_000')
  })

  it('has _scheduleTokenRefresh method', () => {
    expect(storeSrc).toContain('_scheduleTokenRefresh(')
  })

  it('schedules refresh on token assignment in doSelectTenant', () => {
    const doSelectSection = storeSrc.slice(
      storeSrc.indexOf('async function doSelectTenant'),
      storeSrc.indexOf('async function doSwitchTenant'),
    )
    expect(doSelectSection).toContain('_scheduleTokenRefresh(data.accessToken)')
  })

  it('schedules refresh on restoreSession', () => {
    const restoreSection = storeSrc.slice(
      storeSrc.indexOf('async function restoreSession'),
      storeSrc.indexOf('async function doLogout'),
    )
    expect(restoreSection).toContain('_scheduleTokenRefresh(newToken)')
  })

  it('clears refresh timer in clearAuth', () => {
    const clearSection = storeSrc.slice(storeSrc.indexOf('clearAuth()'))
    expect(clearSection).toContain('clearTimeout(_refreshTimer)')
  })
})
