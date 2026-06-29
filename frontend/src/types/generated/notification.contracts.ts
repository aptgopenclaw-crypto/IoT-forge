// =============================================================================
// AUTO-GENERATED — do not edit manually.
// Source: scripts/generate-contract.py
// Re-generate: python scripts/generate-contract.py --module notification
// =============================================================================
//
// Request / Response TypeScript interfaces for the `notification` module.
// These are derived from the Java @RequestBody and return types in the
// corresponding Spring Boot Controllers.
//
// Usage:
//   import type { UserCreateRequest, UserResponse } from '@/types/generated/notification.contracts';
// =============================================================================

// GET /v1/auth/notifications/todos  (NotificationController.listTodos)
export interface PageResponse<T> {
  content: T[];
  total: number;
  page: number;
  size: number;
}
export interface NotificationResponse {
  id: number;
  type: NotificationType;
  title: string;
  content: string;
  refType: NotificationRefType;
  refId: string;
  read: boolean;
  readAt: string;
  createdAt: string;
}

// GET /v1/auth/notifications/unread-count  (NotificationController.unreadCount)
export interface UnreadCountResponse {
  count: number;
}
