<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAnnouncementStore } from '@/stores/announcementStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { useAuthStore } from '@/stores/authStore'
import { useI18n } from 'vue-i18n'
import { Bell } from 'lucide-vue-next'
import type { NotificationItem } from '@/types/notification'

const router = useRouter()
const announcementStore = useAnnouncementStore()
const notificationStore = useNotificationStore()
const authStore = useAuthStore()
const { t } = useI18n()

const activeTab = ref<'notification' | 'announcement'>('notification')

const totalBadge = computed(() =>
  notificationStore.unreadCount + announcementStore.unreadCount
)

// [ADR-007] Announcements / notifications are tenant-scoped APIs; PLATFORM
// super_admin tokens can't call them (ScopeEnforcementFilter returns 403).
// Skip polling entirely in PLATFORM scope to avoid console noise.
const isPlatformScope = computed(() => authStore.scope === 'PLATFORM')

onMounted(() => {
  if (isPlatformScope.value) return
  announcementStore.startPolling()
  notificationStore.startPolling(authStore.accessToken ?? undefined)
})

onUnmounted(() => {
  if (isPlatformScope.value) return
  announcementStore.stopPolling()
  notificationStore.stopPolling()
})

function resolveRoute(item: NotificationItem): string {
  const { refType } = item
  if (!refType) return '/'
  switch (refType) {
    case 'ANNOUNCEMENT': return `/admin/system/announcements`
    case 'WORKFLOW':     return `/asset-transfer/pending`
    default:             return '/'
  }
}

function handleClickNotification(item: NotificationItem) {
  if (!item.read) {
    notificationStore.markRead(item.id)
  }
  router.push(resolveRoute(item))
}

function handleClickAnnouncement(_item: { id: number }) {
  router.push('/announcements')
}

async function handleMarkAllRead() {
  if (activeTab.value === 'notification') {
    await notificationStore.markAllRead()
  } else {
    await announcementStore.markAllRead()
  }
}

function handleViewAll() {
  if (activeTab.value === 'notification') {
    router.push('/')
  } else {
    router.push('/announcements')
  }
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function typeTag(type: string): { label: string; tagType: 'danger' | 'warning' | 'info' } {
  switch (type) {
    case 'TODO':  return { label: t('notification.bell.typeTodo'),  tagType: 'danger' }
    case 'ALERT': return { label: t('notification.bell.typeAlert'), tagType: 'warning' }
    default:      return { label: t('notification.bell.typeInfo'),  tagType: 'info' }
  }
}

const currentUnreadCount = computed(() =>
  activeTab.value === 'notification'
    ? notificationStore.unreadCount
    : announcementStore.unreadCount
)
</script>

<template>
  <el-popover
    placement="bottom-end"
    :width="360"
    trigger="click"
    popper-class="notification-popover"
  >
    <template #reference>
      <el-badge :value="totalBadge" :hidden="totalBadge === 0" :max="99">
        <button class="icon-btn">
          <Bell :size="16" />
        </button>
      </el-badge>
    </template>

    <!-- Tabs -->
    <div class="notification-tabs">
      <button
        class="tab-btn" :class="{ active: activeTab === 'notification' }"
        @click="activeTab = 'notification'"
      >
        {{ t('notification.bell.tabNotification') }}
        <span v-if="notificationStore.unreadCount > 0" class="tab-badge">
          {{ notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount }}
        </span>
      </button>
      <button
        class="tab-btn" :class="{ active: activeTab === 'announcement' }"
        @click="activeTab = 'announcement'"
      >
        {{ t('notification.bell.tabAnnouncement') }}
        <span v-if="announcementStore.unreadCount > 0" class="tab-badge">
          {{ announcementStore.unreadCount > 99 ? '99+' : announcementStore.unreadCount }}
        </span>
      </button>
    </div>

    <!-- Header actions -->
    <div class="notification-header">
      <el-button
        v-if="currentUnreadCount > 0"
        type="primary"
        link
        size="small"
        @click="handleMarkAllRead"
      >
        {{ t('notification.bell.markAllRead') }}
      </el-button>
    </div>

    <!-- Notification tab -->
    <div v-if="activeTab === 'notification'">
      <div v-if="notificationStore.items.length === 0" class="notification-empty">
        {{ t('notification.bell.emptyNotification') }}
      </div>

      <div
        v-for="item in notificationStore.items"
        :key="item.id"
        class="notification-item"
        @click="handleClickNotification(item)"
      >
        <div class="notification-item-header">
          <span v-if="!item.read" class="unread-dot" />
          <el-tag :type="typeTag(item.type).tagType" size="small" effect="light" style="margin-right: 4px;">
            {{ typeTag(item.type).label }}
          </el-tag>
          <span class="notification-item-title" :class="{ 'is-unread': !item.read }">
            {{ item.title }}
          </span>
        </div>
        <div v-if="item.content" class="notification-item-content">
          {{ item.content }}
        </div>
        <div class="notification-item-meta">
          {{ formatDate(item.createdAt) }}
        </div>
      </div>
    </div>

    <!-- Announcement tab -->
    <div v-if="activeTab === 'announcement'">
      <div v-if="announcementStore.popoverItems.length === 0" class="notification-empty">
        {{ t('notification.bell.emptyAnnouncement') }}
      </div>

      <div
        v-for="item in announcementStore.popoverItems"
        :key="item.id"
        class="notification-item"
        @click="handleClickAnnouncement(item)"
      >
        <div class="notification-item-header">
          <span v-if="!item.isRead" class="unread-dot" />
          <span v-if="item.pinned" class="pin-icon">📌</span>
          <span class="notification-item-title" :class="{ 'is-unread': !item.isRead }">
            {{ item.title }}
          </span>
        </div>
        <div class="notification-item-meta">
          {{ formatDate(item.publishAt) }} ｜ {{ item.createdByName }}
        </div>
      </div>
    </div>

    <el-divider style="margin: 8px 0" />

    <div class="notification-footer" @click="handleViewAll">
      {{ activeTab === 'notification'
        ? t('notification.bell.viewAllNotifications')
        : t('notification.bell.viewAllAnnouncements') }} →
    </div>
  </el-popover>
</template>

<style scoped>
.notification-tabs {
  display: flex;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 8px;
}

.tab-btn {
  flex: 1;
  padding: 8px 0;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  border-bottom: 2px solid transparent;
  transition: all 150ms ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.tab-btn.active {
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
}

.tab-badge {
  font-size: 11px;
  background: var(--el-color-danger);
  color: #fff;
  border-radius: 8px;
  padding: 0 5px;
  min-width: 16px;
  text-align: center;
  line-height: 16px;
}

.notification-header {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  min-height: 24px;
}

.notification-empty {
  text-align: center;
  color: var(--text-muted);
  padding: 16px 0;
  font-size: 13px;
}

.notification-item {
  padding: 8px 4px;
  cursor: pointer;
  border-radius: 6px;
  transition: background 150ms ease;
}

.notification-item:hover {
  background: var(--bg-hover);
}

.notification-item-header {
  display: flex;
  align-items: center;
  gap: 6px;
}

.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-primary);
  flex-shrink: 0;
}

.pin-icon {
  font-size: 12px;
  flex-shrink: 0;
}

.notification-item-title {
  font-size: 13px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-item-title.is-unread {
  color: var(--text-primary);
  font-weight: 500;
}

.notification-item-content {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
  padding-left: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-item-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
  padding-left: 14px;
}

.notification-footer {
  text-align: center;
  color: var(--el-color-primary);
  font-size: 13px;
  cursor: pointer;
  padding: 4px 0;
}

.notification-footer:hover {
  text-decoration: underline;
}
</style>
