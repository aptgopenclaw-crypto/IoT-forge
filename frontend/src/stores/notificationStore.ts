import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listNotifications,
  getNotificationUnreadCount,
  markNotificationRead,
  markAllNotificationsRead,
} from '@/api/notification'
import type { NotificationItem } from '@/types/notification'
import { Client } from '@stomp/stompjs'

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const items = ref<NotificationItem[]>([])
  const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)
  let stompClient: Client | null = null
  const wsConnected = ref(false)
  const userIdle = ref(false)
  let visibilityHandler: (() => void) | null = null

  async function fetchUnreadCount() {
    try {
      const res = await getNotificationUnreadCount()
      if (res.errorCode === '00000') {
        unreadCount.value = res.body.count
      }
    } catch (err: any) {
      const status = err?.response?.status
      if (status === 401 || status === 403) throw err
      console.warn('[notification] fetchUnreadCount failed', err)
    }
  }

  async function fetchItems() {
    try {
      const res = await listNotifications({ page: 0, size: 10 })
      if (res.errorCode === '00000') {
        items.value = res.body.content
      }
    } catch (err: any) {
      const status = err?.response?.status
      if (status === 401 || status === 403) throw err
      console.warn('[notification] fetchItems failed', err)
    }
  }

  async function markRead(id: number) {
    try {
      await markNotificationRead(id)
      const item = items.value.find(i => i.id === id)
      if (item && !item.read) {
        item.read = true
        unreadCount.value = Math.max(0, unreadCount.value - 1)
      }
    } catch (err: any) {
      const status = err?.response?.status
      if (status === 401 || status === 403) throw err
      console.warn('[notification] markRead failed', err)
    }
  }

  async function markAllRead() {
    try {
      await markAllNotificationsRead()
      unreadCount.value = 0
      items.value.forEach(i => { i.read = true })
    } catch (err: any) {
      const status = err?.response?.status
      if (status === 401 || status === 403) throw err
      console.warn('[notification] markAllRead failed', err)
    }
  }

  function connectWebSocket(token: string) {
    if (stompClient?.active) return

    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${wsProtocol}//${window.location.host}/ws`

    stompClient = new Client({
      brokerURL: wsUrl,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        wsConnected.value = true
        // Stop polling — WS will deliver real-time updates
        clearPollTimer()

        stompClient?.subscribe('/user/queue/notifications', (message) => {
          const notification = JSON.parse(message.body) as NotificationItem
          items.value.unshift(notification)
          if (items.value.length > 10) items.value.pop()
          unreadCount.value += 1
        })
      },
      onDisconnect: () => {
        wsConnected.value = false
        // Resume polling as fallback
        resumePolling()
      },
      onStompError: (frame) => {
        wsConnected.value = false
        resumePolling()
        console.warn('STOMP error:', frame.headers['message'])
      },
    })

    stompClient.activate()
  }

  function disconnectWebSocket() {
    if (stompClient?.active) {
      stompClient.deactivate()
      stompClient = null
    }
  }

  function startPolling(token?: string) {
    fetchUnreadCount()
    fetchItems()

    // Try WebSocket first, fallback to polling
    if (token) {
      connectWebSocket(token)
    }

    // Start polling initially; will be stopped once WS connects
    if (!wsConnected.value) {
      resumePolling()
    }

    // Pause polling when tab is hidden
    _startIdleDetection()
  }

  /** Resume the 60s polling interval (used as WS fallback) */
  function resumePolling() {
    if (pollTimer.value) return // already running
    pollTimer.value = setInterval(() => {
      fetchUnreadCount()
    }, 60 * 1000)
  }

  /** Clear the polling timer without disconnecting WS */
  function clearPollTimer() {
    if (pollTimer.value) {
      clearInterval(pollTimer.value)
      pollTimer.value = null
    }
  }

  function stopPolling() {
    clearPollTimer()
    disconnectWebSocket()
    wsConnected.value = false
    _stopIdleDetection()
  }

  /** Pause polling when user is idle (tab hidden); resume on return */
  function _onVisibilityChange() {
    if (document.visibilityState === 'hidden') {
      userIdle.value = true
      clearPollTimer()
    } else {
      userIdle.value = false
      // Immediately fetch then resume interval
      fetchUnreadCount()
      if (!wsConnected.value) {
        resumePolling()
      }
    }
  }

  function _startIdleDetection() {
    if (visibilityHandler) return
    visibilityHandler = _onVisibilityChange
    document.addEventListener('visibilitychange', visibilityHandler)
  }

  function _stopIdleDetection() {
    if (visibilityHandler) {
      document.removeEventListener('visibilitychange', visibilityHandler)
      visibilityHandler = null
    }
    userIdle.value = false
  }

  return {
    unreadCount, items, wsConnected, pollTimer, userIdle,
    fetchUnreadCount, fetchItems, markRead, markAllRead,
    connectWebSocket, disconnectWebSocket, startPolling,
    resumePolling, clearPollTimer, stopPolling,
  }
})
