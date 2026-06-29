import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'
import type { PageResponse } from '@/types/common'
import type { UserEventLogDto } from '@/types/audit'

export const getAuditCategories = () =>
  axiosIns.get<unknown, BaseResponse<string[]>>('/auth/audit/categories')

export const getUserUsageHistory = (params: {
  userName?: string
  eventDesc?: string
  startTimestamp?: string
  endTimestamp?: string
  sortBy?: string
  sort?: string
  pageSize: number
  page: number
}) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<UserEventLogDto>>>('/auth/audit/user/usage/history', { params })

export const getMyLoginLog = (params: {
  eventType?: string
  startTimestamp?: string
  endTimestamp?: string
  sort?: string
  pageSize: number
  page: number
}) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<UserEventLogDto>>>('/auth/audit/user/login/my', { params })

export const exportAuditLogs = (params: {
  format: 'csv' | 'xlsx'
  userName?: string
  eventDesc?: string
  startTimestamp?: string
  endTimestamp?: string
}) =>
  axiosIns.get('/auth/audit/user/usage/history/export', {
    params,
    responseType: 'blob',
  })
