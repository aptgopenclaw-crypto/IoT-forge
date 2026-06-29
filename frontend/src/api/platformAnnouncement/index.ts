import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'
import type { PageResponse } from '@/types/common'
import type {
  PlatformAnnouncementRequest,
  PlatformAnnouncementResponse,
} from '@/types/platformAnnouncement'

// ── 管理端（/v1/platform/announcements，需 PLATFORM scope） ──

export const listPlatformAnnouncementsAdmin = (params: {
  statusFilter?: string
  category?: string
  keyword?: string
  page?: number
  size?: number
}) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<PlatformAnnouncementResponse>>>(
    '/platform/announcements',
    { params },
  )

export const getPlatformAnnouncement = (id: number) =>
  axiosIns.get<unknown, BaseResponse<PlatformAnnouncementResponse>>(
    `/platform/announcements/${id}`,
  )

export const createPlatformAnnouncement = (payload: PlatformAnnouncementRequest) =>
  axiosIns.post<unknown, BaseResponse<PlatformAnnouncementResponse>>(
    '/platform/announcements',
    payload,
  )

export const updatePlatformAnnouncement = (id: number, payload: PlatformAnnouncementRequest) =>
  axiosIns.put<unknown, BaseResponse<PlatformAnnouncementResponse>>(
    `/platform/announcements/${id}`,
    payload,
  )

export const deletePlatformAnnouncement = (id: number) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/platform/announcements/${id}`)

// ── 租戶端唯讀（/v1/auth/platform-announcements） ──

export const listPlatformAnnouncementsPublished = (params: {
  category?: string
  page?: number
  size?: number
}) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<PlatformAnnouncementResponse>>>(
    '/auth/platform-announcements',
    { params },
  )
