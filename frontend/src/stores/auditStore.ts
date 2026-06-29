import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { getUserUsageHistory, getMyLoginLog } from '@/api/audit'
import type { UserEventLogDto, PaginationState, AuditFilterModel } from '@/types/audit'

export const useAuditStore = defineStore('audit', () => {
  const usageLogs = ref<UserEventLogDto[]>([])
  const myLogs = ref<UserEventLogDto[]>([])
  const pagination = reactive<PaginationState>({ page: 0, size: 20, total: 0 })
  const myPagination = reactive<PaginationState>({ page: 0, size: 20, total: 0 })
  const loading = ref(false)

  async function fetchUsageLogs(filters: AuditFilterModel & { sortBy?: string; sort?: string }) {
    loading.value = true
    try {
      const res = await getUserUsageHistory({
        userName: filters.userName,
        eventDesc: filters.eventDesc,
        startTimestamp: filters.startTimestamp,
        endTimestamp: filters.endTimestamp,
        sortBy: filters.sortBy,
        sort: filters.sort ?? 'DESC',
        page: pagination.page,
        pageSize: pagination.size,
      })
      usageLogs.value = res.body.content
      pagination.total = res.body.totalElements
    } finally {
      loading.value = false
    }
  }

  async function fetchMyLogs(filters: { eventType?: string; startTimestamp?: string; endTimestamp?: string; sort?: string }) {
    loading.value = true
    try {
      const res = await getMyLoginLog({
        eventType: filters.eventType,
        startTimestamp: filters.startTimestamp,
        endTimestamp: filters.endTimestamp,
        sort: filters.sort ?? 'DESC',
        page: myPagination.page,
        pageSize: myPagination.size,
      })
      myLogs.value = res.body.content
      myPagination.total = res.body.totalElements
    } finally {
      loading.value = false
    }
  }

  return { usageLogs, myLogs, pagination, myPagination, loading, fetchUsageLogs, fetchMyLogs }
})
