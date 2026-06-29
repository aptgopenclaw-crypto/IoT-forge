// =============================================================================
// AUTO-GENERATED — do not edit manually.
// Source: scripts/generate-contract.py
// Re-generate: python scripts/generate-contract.py --module announcement
// =============================================================================
//
// Request / Response TypeScript interfaces for the `announcement` module.
// These are derived from the Java @RequestBody and return types in the
// corresponding Spring Boot Controllers.
//
// Usage:
//   import type { UserCreateRequest, UserResponse } from '@/types/generated/announcement.contracts';
// =============================================================================

// GET /v1/auth/announcements/admin  (AnnouncementController.listAdmin)
export interface PageResponse<T> {
  content: T[];
  total: number;
  page: number;
  size: number;
}
export interface AnnouncementResponse {
  id: number;
  title: string;
  content: string;
  status: string;
  scope: string;
  category: string;
  targetDeptIds: number[];
  targetDeptNames: string[];
  pinned: boolean;
  pinOrder: number;
  requiresAck: boolean;
  publishAt: string;
  expireAt: string;
  createdBy: string;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
  isRead: boolean;
  editable: boolean;
  version: number;
  attachments: AnnouncementAttachmentResponse[];
  resolvedLang: string;
  translations: AnnouncementTranslationDto[];
}

// GET /v1/auth/announcements/unread-count  (AnnouncementController.getUnreadCount)
export interface UnreadCountResponse {
  /** 當前登入使用者尚未讀取的公告數量 */
  count: number;
}

// GET /v1/auth/announcements/{id}/read-stats  (AnnouncementController.getReadStats)
export interface AnnouncementReadStatsResponse {
  /** 公告 ID */
  announcementId: number;
  /** 是否為需確認公告 */
  requiresAck: boolean;
  /** 目標受眾總人數（依 scope 計算：ALL=租戶內啟用使用者數；DEPT=目標部門啟用使用者數） */
  totalAudience: number;
  /** 已讀人數（限受眾範圍內） */
  readCount: number;
  /** 未讀人數 = totalAudience - readCount */
  unreadCount: number;
  /** 已讀比例（0.0000 ~ 1.0000）；totalAudience=0 時為 0 */
  readRatio: number;
}

// GET /v1/auth/announcements/{id}/unread-users  (AnnouncementController.getUnreadUsers)
export interface AnnouncementUnreadUserResponse {
  userId: string;
  displayName: string;
  email: string;
  deptId: number;
  deptName: string;
}

// PUT /v1/auth/announcements/pin-order  (AnnouncementController.reorderPins)
export interface AnnouncementPinOrderRequest {
  /** 依新順序排列的公告 id 清單（第一個為最前面） */
  orderedIds: number[];
}

// PUT /v1/auth/announcements/{id}  (AnnouncementController.update)
export interface AnnouncementRequest {
  /** 標題（最長 200 字） */
  title: string;
  /** 內文（純文字，最長 50000 字） */
  content: string;
  /** 狀態 */
  status: string;
  /** 受眾範圍：ALL=全公司、DEPT=指定部門 */
  scope: string;
  /** 分類；省略時 service 端預設為 GENERAL */
  category: string;
  /** 目標部門 ID 清單；當 scope=DEPT 時必填 */
  targetDeptIds: number[];
  /** 是否置頂 */
  pinned: boolean;
  /** 置頂順序（數字越小越靠前）；取消置頂會被清為 null */
  pinOrder: number;
  /** 是否需使用者明確確認（需點「我已閱讀並了解」），預設 false */
  requiresAck: boolean;
  /** 發佈時間；為 null 表示立即發佈 */
  publishAt: string;
  /** 失效時間；為 null 表示永不過期；必須晚於 publishAt */
  expireAt: string;
  /** 樂觀鎖版本號；編輯時必填，新增時可省略 */
  version: number;
  /** 額外語言翻譯（不含預設語言 zh-TW） */
  translations: AnnouncementTranslationDto[];
}

// GET /v1/auth/announcements/{id}/attachments  (AnnouncementController.listAttachments)
export interface AnnouncementAttachmentResponse {
  id: number;
  announcementId: number;
  fileName: string;
  fileSize: number;
  mimeType: string;
  createdAt: string;
}
