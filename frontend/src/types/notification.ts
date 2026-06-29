export interface NotificationItem {
  id: number
  type: 'TODO' | 'ALERT' | 'INFO'
  title: string
  content: string | null
  refType: string | null
  refId: string | null
  read: boolean
  readAt: string | null
  createdAt: string
}

// Use PageResponse<NotificationItem> from @/types/common instead

export interface NotificationUnreadCount {
  count: number
}
