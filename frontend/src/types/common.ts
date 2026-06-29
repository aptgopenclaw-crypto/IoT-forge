import type { BaseResponse } from '@/types/auth'

/**
 * 統一分頁回應型別 — 對應後端 PageResponse<T>
 */
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export type { BaseResponse }
