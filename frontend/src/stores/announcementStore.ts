import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { getUnreadCount, listAnnouncements, markAsRead, markAllAsRead } from '@/api/announcement'
import type { AnnouncementResponse } from '@/types/announcement'

export const useAnnouncementStore = defineStore('announcement', () => {
  const unreadCount = ref(0)
  const popoverItems = ref<AnnouncementResponse[]>([])
  // List view 共享狀態：下沉至 store 以集中已讀同步邏輯
  const listItems = ref<AnnouncementResponse[]>([])
  const listLoading = ref(false)
  const listPagination = reactive({ page: 0, size: 10, total: 0 })
  /** 前台列表分類過濾；空串 / null = 全部 */
  const listCategory = ref<string | null>(null)
  let pollTimer: ReturnType<typeof setInterval> | null = null

  async function fetchUnreadCount() {
    try {
      const res = await getUnreadCount()
      if (res.errorCode === '00000') {
        unreadCount.value = res.body.count
      }
    } catch (e) {
      console.warn('[announcementStore] fetchUnreadCount failed:', e)
    }
  }

  async function fetchPopoverItems() {
    try {
      const res = await listAnnouncements({ page: 0, size: 10 })
      if (res.errorCode === '00000') {
        popoverItems.value = res.body.content
      }
    } catch (e) {
      console.warn('[announcementStore] fetchPopoverItems failed:', e)
    }
  }

  /** List view 分頁載入；由 view 觸發但狀態由 store 管理 */
  async function fetchListPage(page?: number, size?: number) {
    if (typeof page === 'number') listPagination.page = page
    if (typeof size === 'number') listPagination.size = size
    listLoading.value = true
    try {
      const res = await listAnnouncements({
        page: listPagination.page,
        size: listPagination.size,
        category: listCategory.value ?? undefined,
      })
      if (res.errorCode === '00000') {
        listItems.value = res.body.content
        listPagination.total = res.body.totalElements
      }
    } finally {
      listLoading.value = false
    }
  }

  /** 切換分類過濾時重載第一頁 */
  function setListCategory(category: string | null) {
    listCategory.value = category && category !== 'ALL' ? category : null
    listPagination.page = 0
    return fetchListPage()
  }

  /** 內部：同步所有快取列表的 isRead 狀態並遞減 unreadCount */
  function _applyMarkRead(id: number) {
    let changed = false
    const sync = (list: AnnouncementResponse[]) => {
      const item = list.find(i => i.id === id)
      if (item && !item.isRead) {
        item.isRead = true
        changed = true
      }
    }
    sync(popoverItems.value)
    sync(listItems.value)
    if (changed) {
      decrementUnread()
    }
  }

  function decrementUnread(by = 1) {
    unreadCount.value = Math.max(0, unreadCount.value - by)
  }

  async function markRead(id: number) {
    try {
      await markAsRead(id)
      _applyMarkRead(id)
    } catch (e) {
      console.warn('[announcementStore] markRead failed:', e)
    }
  }

  async function markAllRead() {
    try {
      await markAllAsRead()
      unreadCount.value = 0
      popoverItems.value.forEach(i => { i.isRead = true })
      listItems.value.forEach(i => { i.isRead = true })
    } catch (e) {
      console.warn('[announcementStore] markAllRead failed:', e)
    }
  }

  function startPolling() {
    fetchUnreadCount()
    fetchPopoverItems()
    if (pollTimer) clearInterval(pollTimer)
    pollTimer = setInterval(() => {
      fetchUnreadCount()
    }, 5 * 60 * 1000) // 5 分鐘
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return {
    unreadCount, popoverItems, listItems, listLoading, listPagination, listCategory,
    fetchUnreadCount, fetchPopoverItems, fetchListPage, setListCategory,
    decrementUnread, markRead, markAllRead, startPolling, stopPolling,
  }
})
