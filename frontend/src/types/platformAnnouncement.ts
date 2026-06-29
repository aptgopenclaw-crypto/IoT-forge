export interface PlatformAnnouncementResponse {
  id: number
  title: string
  content: string
  status: string
  category: string
  publishAt: string | null
  expireAt: string | null
  createdBy: string
  createdByName: string
  createdAt: string
  updatedAt: string
}

export interface PlatformAnnouncementRequest {
  title: string
  content: string
  status: string
  category?: string
  publishAt?: string | null
  expireAt?: string | null
}

export const PLATFORM_ANNOUNCEMENT_CATEGORIES = ['SYSTEM', 'MAINTENANCE', 'GENERAL'] as const
export type PlatformAnnouncementCategory = (typeof PLATFORM_ANNOUNCEMENT_CATEGORIES)[number]
