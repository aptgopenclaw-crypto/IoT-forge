import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'
import type { PageResponse } from '@/types/common'
import type {
  NotificationItem,
  NotificationUnreadCount,
} from '@/types/notification'

export const listNotifications = (params: { page: number; size: number }) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<NotificationItem>>>('/auth/notifications', { params })

export const listTodos = (params: { page: number; size: number }) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<NotificationItem>>>('/auth/notifications/todos', { params })

export const getNotificationUnreadCount = () =>
  axiosIns.get<unknown, BaseResponse<NotificationUnreadCount>>('/auth/notifications/unread-count')

export const markNotificationRead = (id: number) =>
  axiosIns.patch<unknown, BaseResponse<void>>(`/auth/notifications/${id}/read`)

export const markAllNotificationsRead = () =>
  axiosIns.patch<unknown, BaseResponse<void>>('/auth/notifications/read-all')
