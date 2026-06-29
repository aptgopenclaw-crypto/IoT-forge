export interface UserEventLogDto {
  userEventLogPk: number
  tenantId: number | null
  userId: string
  username: string
  userLabel: string
  email: string
  eventType: string
  eventDesc: string
  apiEndpoint: string
  payload: string
  errorCode: string
  message: string
  ipAddress: string
  userAgent: string
  executionTime: number
  deptId: number | null
  /** [Phase B] SUPER_ADMIN 代操者 userId；null = 一般操作 */
  impersonatedBy: string | null
  createTime: string
}

export interface AuditFilterModel {
  userName?: string
  eventDesc?: string
  eventType?: string
  startTimestamp?: string
  endTimestamp?: string
}

export interface PaginationState {
  page: number
  size: number
  total: number
}

// Use PageResponse<T> from @/types/common instead of PageData
