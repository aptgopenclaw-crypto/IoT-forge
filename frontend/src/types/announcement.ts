export interface AnnouncementAttachmentResponse {
  id: number
  announcementId: number
  fileName: string
  fileSize: number
  mimeType: string
  createdAt: string
}

/** 公告多語系翻譯（單一語言）。 */
export interface AnnouncementTranslationDto {
  /** BCP-47 語言代碼：zh-TW / zh-CN / en 。 */
  langCode: string
  title: string
  content: string
}

export interface AnnouncementResponse {
  id: number
  title: string
  content: string
  status: string
  scope: string
  /** 分類：GENERAL | SYSTEM | POLICY | EVENT | MAINTENANCE */
  category: string
  targetDeptIds: number[]
  targetDeptNames: string[]
  pinned: boolean
  /** 置頂順序（數字越小越靠前）；未置頂為 null */
  pinOrder: number | null
  /** 是否為需確認公告：UI 依此決定是否顯示「我已閱讀並了解」按鈕與押止展開即標記已讀的行為 */
  requiresAck: boolean
  publishAt: string | null
  expireAt: string | null
  createdBy: string
  createdByName: string
  createdAt: string
  updatedAt: string
  isRead: boolean
  editable: boolean
  version: number
  attachments?: AnnouncementAttachmentResponse[]
  /** 本筆回應 title / content 實際使用的語言（可能 fallback） */
  resolvedLang?: string
  /** 所有翻譯（含預設 zh-TW）；管理端編輯使用 */
  translations?: AnnouncementTranslationDto[]
}

export interface AnnouncementRequest {
  title: string
  content: string
  status: string
  scope: string
  /** 分類；省略則預設 GENERAL */
  category?: string
  targetDeptIds?: number[]
  pinned?: boolean
  /** 置頂順序；取消置頂會被清為 null；一般新增/編輯不需填，由后端自動指定或以拖曳端點修改 */
  pinOrder?: number | null
  /** 是否為需確認公告，預設 false */
  requiresAck?: boolean
  publishAt?: string | null
  expireAt?: string | null
  /** 樂觀鎖版本號；編輯時必填，新增時可省略 */
  version?: number
  /** 额外語言翻譯（不含預設語言 zh-TW）；title/content 即代表 zh-TW 版本 */
  translations?: AnnouncementTranslationDto[]
}

export const ANNOUNCEMENT_CATEGORIES = [
  'GENERAL',
  'SYSTEM',
  'POLICY',
  'EVENT',
  'MAINTENANCE',
] as const
export type AnnouncementCategory = (typeof ANNOUNCEMENT_CATEGORIES)[number]

export interface UnreadCountResponse {
  count: number
}

/** 公告已讀統計（管理端） */
export interface AnnouncementReadStatsResponse {
  announcementId: number
  requiresAck: boolean
  totalAudience: number
  readCount: number
  unreadCount: number
  /** 0.0000 ~ 1.0000 */
  readRatio: number
}

/** 公告未讀使用者（管理端） */
export interface AnnouncementUnreadUserResponse {
  userId: string
  displayName: string
  email: string
  deptId: number | null
  deptName: string | null
}

// Use PageResponse<AnnouncementResponse> from @/types/common instead
