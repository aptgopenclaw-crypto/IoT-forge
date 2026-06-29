import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useNotificationStore } from '@/stores/notificationStore'

// Mock the API module
vi.mock('@/api/notification', () => ({
  listNotifications: vi.fn().mockResolvedValue({ errorCode: '00000', body: { content: [] } }),
  getNotificationUnreadCount: vi.fn().mockResolvedValue({ errorCode: '00000', body: { count: 0 } }),
  markNotificationRead: vi.fn().mockResolvedValue({}),
  markAllNotificationsRead: vi.fn().mockResolvedValue({}),
}))

// Mock STOMP Client
const mockSubscribe = vi.fn()
const mockActivate = vi.fn()
const mockDeactivate = vi.fn()
let onConnectCb: (() => void) | null = null
let onDisconnectCb: (() => void) | null = null
let onStompErrorCb: ((frame: { headers: Record<string, string> }) => void) | null = null

vi.mock('@stomp/stompjs', () => {
  return {
    Client: class MockClient {
      active = false
      subscribe = mockSubscribe
      deactivate = mockDeactivate

      constructor(config: {
        onConnect?: () => void
        onDisconnect?: () => void
        onStompError?: (frame: { headers: Record<string, string> }) => void
      }) {
        onConnectCb = config.onConnect || null
        onDisconnectCb = config.onDisconnect || null
        onStompErrorCb = config.onStompError || null
      }

      activate() {
        this.active = true
        mockActivate()
      }
    },
  }
})

describe('notificationStore — polling/WS coordination', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    onConnectCb = null
    onDisconnectCb = null
    onStompErrorCb = null
    mockActivate.mockClear()
    mockDeactivate.mockClear()
    mockSubscribe.mockClear()
  })

  afterEach(() => {
    const store = useNotificationStore()
    store.stopPolling()
    vi.useRealTimers()
  })

  it('should start polling when no token provided', () => {
    const store = useNotificationStore()
    store.startPolling()

    expect(store.pollTimer).not.toBeNull()
    expect(mockActivate).not.toHaveBeenCalled()
  })

  it('should start polling initially when token provided (before WS connects)', () => {
    const store = useNotificationStore()
    store.startPolling('test-token')

    // Polling should be running (WS not yet connected)
    expect(store.pollTimer).not.toBeNull()
    expect(mockActivate).toHaveBeenCalled()
  })

  it('should stop polling when WebSocket connects', () => {
    const store = useNotificationStore()
    store.startPolling('test-token')

    expect(store.pollTimer).not.toBeNull()

    // Simulate WS connection
    onConnectCb?.()

    expect(store.wsConnected).toBe(true)
    expect(store.pollTimer).toBeNull()
  })

  it('should resume polling when WebSocket disconnects', () => {
    const store = useNotificationStore()
    store.startPolling('test-token')

    // Connect then disconnect
    onConnectCb?.()
    expect(store.pollTimer).toBeNull()

    onDisconnectCb?.()
    expect(store.wsConnected).toBe(false)
    expect(store.pollTimer).not.toBeNull()
  })

  it('should resume polling on STOMP error', () => {
    const store = useNotificationStore()
    store.startPolling('test-token')

    onConnectCb?.()
    expect(store.pollTimer).toBeNull()

    onStompErrorCb?.({ headers: { message: 'test error' } })
    expect(store.wsConnected).toBe(false)
    expect(store.pollTimer).not.toBeNull()
  })

  it('should not create duplicate poll timers on multiple resumePolling calls', () => {
    const store = useNotificationStore()
    store.resumePolling()
    const firstTimer = store.pollTimer

    store.resumePolling()
    expect(store.pollTimer).toBe(firstTimer) // same timer, not a new one
  })

  it('should clear everything on stopPolling', () => {
    const store = useNotificationStore()
    store.startPolling('test-token')
    onConnectCb?.()

    store.stopPolling()

    expect(store.pollTimer).toBeNull()
    expect(store.wsConnected).toBe(false)
    expect(mockDeactivate).toHaveBeenCalled()
  })

  it('polling timer should fire fetchUnreadCount every 60s', async () => {
    const { getNotificationUnreadCount } = await import('@/api/notification')
    const apiMock = vi.mocked(getNotificationUnreadCount)

    const store = useNotificationStore()
    // Clear calls from any initial startPolling
    apiMock.mockClear()

    store.resumePolling()
    apiMock.mockClear()

    vi.advanceTimersByTime(60_000)
    expect(apiMock).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(60_000)
    expect(apiMock).toHaveBeenCalledTimes(2)
  })
})

describe('notificationStore — error handling (N-6)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    const store = useNotificationStore()
    store.stopPolling()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('fetchUnreadCount should re-throw on 401', async () => {
    const { getNotificationUnreadCount } = await import('@/api/notification')
    const apiMock = vi.mocked(getNotificationUnreadCount)
    apiMock.mockRejectedValueOnce({ response: { status: 401 } })

    const store = useNotificationStore()
    await expect(store.fetchUnreadCount()).rejects.toEqual({ response: { status: 401 } })
  })

  it('fetchUnreadCount should re-throw on 403', async () => {
    const { getNotificationUnreadCount } = await import('@/api/notification')
    const apiMock = vi.mocked(getNotificationUnreadCount)
    apiMock.mockRejectedValueOnce({ response: { status: 403 } })

    const store = useNotificationStore()
    await expect(store.fetchUnreadCount()).rejects.toEqual({ response: { status: 403 } })
  })

  it('fetchUnreadCount should not throw on network error (non-401/403)', async () => {
    const { getNotificationUnreadCount } = await import('@/api/notification')
    const apiMock = vi.mocked(getNotificationUnreadCount)
    apiMock.mockRejectedValueOnce({ response: { status: 500 } })

    const store = useNotificationStore()
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    await expect(store.fetchUnreadCount()).resolves.toBeUndefined()
    expect(warnSpy).toHaveBeenCalledWith('[notification] fetchUnreadCount failed', expect.anything())
  })

  it('fetchItems should re-throw on 401', async () => {
    const { listNotifications } = await import('@/api/notification')
    const apiMock = vi.mocked(listNotifications)
    apiMock.mockRejectedValueOnce({ response: { status: 401 } })

    const store = useNotificationStore()
    await expect(store.fetchItems()).rejects.toEqual({ response: { status: 401 } })
  })

  it('fetchItems should not throw on 500', async () => {
    const { listNotifications } = await import('@/api/notification')
    const apiMock = vi.mocked(listNotifications)
    apiMock.mockRejectedValueOnce({ response: { status: 500 } })

    const store = useNotificationStore()
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    await expect(store.fetchItems()).resolves.toBeUndefined()
    expect(warnSpy).toHaveBeenCalledWith('[notification] fetchItems failed', expect.anything())
  })

  it('markRead should re-throw on 401', async () => {
    const { markNotificationRead } = await import('@/api/notification')
    const apiMock = vi.mocked(markNotificationRead)
    apiMock.mockRejectedValueOnce({ response: { status: 401 } })

    const store = useNotificationStore()
    await expect(store.markRead(1)).rejects.toEqual({ response: { status: 401 } })
  })

  it('markRead should not throw on 500', async () => {
    const { markNotificationRead } = await import('@/api/notification')
    const apiMock = vi.mocked(markNotificationRead)
    apiMock.mockRejectedValueOnce({ response: { status: 500 } })

    const store = useNotificationStore()
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    await expect(store.markRead(1)).resolves.toBeUndefined()
    expect(warnSpy).toHaveBeenCalledWith('[notification] markRead failed', expect.anything())
  })

  it('markAllRead should re-throw on 403', async () => {
    const { markAllNotificationsRead } = await import('@/api/notification')
    const apiMock = vi.mocked(markAllNotificationsRead)
    apiMock.mockRejectedValueOnce({ response: { status: 403 } })

    const store = useNotificationStore()
    await expect(store.markAllRead()).rejects.toEqual({ response: { status: 403 } })
  })

  it('markAllRead should not throw on network error without response', async () => {
    const { markAllNotificationsRead } = await import('@/api/notification')
    const apiMock = vi.mocked(markAllNotificationsRead)
    apiMock.mockRejectedValueOnce(new Error('Network Error'))

    const store = useNotificationStore()
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    await expect(store.markAllRead()).resolves.toBeUndefined()
    expect(warnSpy).toHaveBeenCalledWith('[notification] markAllRead failed', expect.anything())
  })
})
