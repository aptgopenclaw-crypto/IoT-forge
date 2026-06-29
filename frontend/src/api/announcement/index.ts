import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'
import type { PageResponse } from '@/types/common'
import type {
  AnnouncementAttachmentResponse,
  AnnouncementReadStatsResponse,
  AnnouncementRequest,
  AnnouncementResponse,
  AnnouncementUnreadUserResponse,
  UnreadCountResponse,
} from '@/types/announcement'

// 前台查詢（已發佈 + 未過期 + 受眾符合）
export const listAnnouncements = (params: {
  category?: string
  page?: number
  size?: number
}) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<AnnouncementResponse>>>('/auth/announcements', { params })

// 管理端查詢（需 ANNOUNCEMENT_MANAGE 權限）
export const listAnnouncementsAdmin = (params: {
  statusFilter?: string
  category?: string
  keyword?: string
  page?: number
  size?: number
}) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<AnnouncementResponse>>>('/auth/announcements/admin', { params })

// 單筆公告詳情
export const getAnnouncement = (id: number) =>
  axiosIns.get<unknown, BaseResponse<AnnouncementResponse>>(`/auth/announcements/${id}`)

// 未讀數量
export const getUnreadCount = () =>
  axiosIns.get<unknown, BaseResponse<UnreadCountResponse>>('/auth/announcements/unread-count')

// 標記某則已讀
export const markAsRead = (id: number) =>
  axiosIns.post<unknown, BaseResponse<void>>(`/auth/announcements/${id}/read`)

// 全部已讀
export const markAllAsRead = () =>
  axiosIns.post<unknown, BaseResponse<void>>('/auth/announcements/read-all')

// ── 管理端：已讀統計與未讀名單 ──

export const getAnnouncementReadStats = (id: number) =>
  axiosIns.get<unknown, BaseResponse<AnnouncementReadStatsResponse>>(
    `/auth/announcements/${id}/read-stats`,
  )

export const getAnnouncementUnreadUsers = (
  id: number,
  params: { keyword?: string; page?: number; size?: number },
) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<AnnouncementUnreadUserResponse>>>(
    `/auth/announcements/${id}/unread-users`,
    { params },
  )

// ── 管理端：置頂順序（拖曳排序）──

/** 列出目前所有置頂公告（依 pin_order 排序） */
export const listPinnedAnnouncements = () =>
  axiosIns.get<unknown, BaseResponse<AnnouncementResponse[]>>('/auth/announcements/pinned')

/** 依拖曳結果重新指派 pin_order */
export const reorderPinnedAnnouncements = (orderedIds: number[]) =>
  axiosIns.put<unknown, BaseResponse<void>>('/auth/announcements/pin-order', { orderedIds })

// 新增公告
export const createAnnouncement = (payload: AnnouncementRequest) =>
  axiosIns.post<unknown, BaseResponse<AnnouncementResponse>>('/auth/announcements', payload)

// 編輯公告
export const updateAnnouncement = (id: number, payload: AnnouncementRequest) =>
  axiosIns.put<unknown, BaseResponse<AnnouncementResponse>>(`/auth/announcements/${id}`, payload)

// 刪除公告
export const deleteAnnouncement = (id: number) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/auth/announcements/${id}`)

// ── 附件 ──

// 列出附件
export const listAnnouncementAttachments = (id: number) =>
  axiosIns.get<unknown, BaseResponse<AnnouncementAttachmentResponse[]>>(
    `/auth/announcements/${id}/attachments`,
  )

// 上傳附件（multipart/form-data）
export const uploadAnnouncementAttachment = (id: number, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return axiosIns.post<unknown, BaseResponse<AnnouncementAttachmentResponse>>(
    `/auth/announcements/${id}/attachments`,
    form,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
}

// 下載附件 → 返回 Blob
export const downloadAnnouncementAttachment = (id: number, attachmentId: number) =>
  axiosIns.get<unknown, Blob>(
    `/auth/announcements/${id}/attachments/${attachmentId}/download`,
    { responseType: 'blob' },
  )

// 削除附件
export const deleteAnnouncementAttachment = (id: number, attachmentId: number) =>
  axiosIns.delete<unknown, BaseResponse<void>>(
    `/auth/announcements/${id}/attachments/${attachmentId}`,
  )
// 取得附件上傳政策（允許副檔名，例如 ["pdf"]）
export const getAttachmentConfig = () =>
  axiosIns.get<unknown, BaseResponse<string[]>>(
    '/auth/announcements/attachments/config',
  )