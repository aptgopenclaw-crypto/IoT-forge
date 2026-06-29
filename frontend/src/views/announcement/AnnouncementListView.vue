<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useAnnouncementStore } from '@/stores/announcementStore'
import type { AnnouncementResponse, AnnouncementAttachmentResponse } from '@/types/announcement'
import { ANNOUNCEMENT_CATEGORIES } from '@/types/announcement'
import { downloadAnnouncementAttachment } from '@/api/announcement'
import { listPlatformAnnouncementsPublished } from '@/api/platformAnnouncement'
import type { PlatformAnnouncementResponse } from '@/types/platformAnnouncement'
import RichTextRenderer from '@/components/RichTextRenderer.vue'

const { t } = useI18n()
const { locale } = useI18n()
const announcementStore = useAnnouncementStore()
const { listItems: items, listLoading: loading, listPagination: pagination, listCategory } =
  storeToRefs(announcementStore)

// 語言切換時重新抓入公告清單，以取得對應語言的 title/content
watch(locale, () => {
  announcementStore.fetchListPage(0)
})

const expandedId = ref<number | null>(null)

/** 分類 → el-tag type 顏色對應（各類别視覺區隔） */
const CATEGORY_TAG_TYPE: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  GENERAL: 'info',
  SYSTEM: 'primary',
  POLICY: 'success',
  EVENT: 'warning',
  MAINTENANCE: 'danger',
}
function getCategoryTagType(c: string | undefined) {
  return CATEGORY_TAG_TYPE[c ?? 'GENERAL'] ?? 'info'
}
function getCategoryLabel(c: string | undefined) {
  return t(`announcement.category.${(c ?? 'general').toLowerCase()}`)
}

async function toggleExpand(item: AnnouncementResponse) {
  if (expandedId.value === item.id) {
    expandedId.value = null
    return
  }
  expandedId.value = item.id

  // 需確認公告：不自動標記已讀，需使用者明確點「我已閱讀並了解」按鈕。
  if (item.requiresAck) return

  // 一般公告：展開即視為已讀（各如以往行為）
  if (!item.isRead) {
    await announcementStore.markRead(item.id)
  }
}

/** 使用者明確點選「我已閱讀並了解」（需確認公告專用） */
async function handleAcknowledge(item: AnnouncementResponse, e: MouseEvent) {
  e.stopPropagation()
  if (item.isRead) return
  try {
    await announcementStore.markRead(item.id)
    ElMessage.success(t('announcement.ack.success'))
  } catch {
    ElMessage.error(t('common.error'))
  }
}

function handlePageChange(page: number) {
  expandedId.value = null
  announcementStore.fetchListPage(page - 1)
}

function handleCategoryChange(value: string | null) {
  expandedId.value = null
  announcementStore.setListCategory(value)
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`
}

function getScopeLabel(item: AnnouncementResponse): string {
  return item.scope === 'ALL' ? t('announcement.scope.all') : t('announcement.scope.dept')
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

async function handleDownload(item: AnnouncementResponse, att: AnnouncementAttachmentResponse, e: MouseEvent) {
  e.stopPropagation()
  try {
    const blob = await downloadAnnouncementAttachment(item.id, att.id)
    const url = window.URL.createObjectURL(blob as unknown as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = att.fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error(t('common.error'))
  }
}

const initialLoading = ref(true)

// ── Platform announcements (cross-tenant) ───────────────────────────────
const platformItems = ref<PlatformAnnouncementResponse[]>([])
const expandedPlatformId = ref<number | null>(null)

function togglePlatformExpand(item: PlatformAnnouncementResponse) {
  expandedPlatformId.value = expandedPlatformId.value === item.id ? null : item.id
}

function platformCategoryLabel(cat: string): string {
  const map: Record<string, string> = { SYSTEM: '系統', MAINTENANCE: '維護', GENERAL: '一般' }
  return map[cat] ?? cat
}

function platformCategoryTagType(cat: string): 'danger' | 'warning' | 'info' {
  if (cat === 'MAINTENANCE') return 'danger'
  if (cat === 'SYSTEM') return 'warning'
  return 'info'
}

async function fetchPlatformAnnouncements() {
  try {
    const res = await listPlatformAnnouncementsPublished({ page: 0, size: 20 })
    if (res.errorCode === '00000') {
      platformItems.value = res.body.content
    }
  } catch {
    // Silent fail — platform announcements are supplementary
  }
}

onMounted(async () => {
  await Promise.all([
    announcementStore.fetchListPage(0),
    fetchPlatformAnnouncements(),
  ])
  initialLoading.value = false
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>{{ t('announcement.list.title') }}</h2>
      <p class="page-subtitle">{{ t('announcement.list.subtitle') }}</p>
    </div>

    <div class="filter-bar">
      <el-select
        :model-value="listCategory ?? 'ALL'"
        size="small"
        style="width: 160px"
        @update:model-value="(v: string) => handleCategoryChange(v === 'ALL' ? null : v)"
      >
        <el-option value="ALL" :label="t('announcement.category.all')" />
        <el-option
          v-for="c in ANNOUNCEMENT_CATEGORIES"
          :key="c"
          :value="c"
          :label="t(`announcement.category.${c.toLowerCase()}`)"
        />
      </el-select>
    </div>

    <el-skeleton :rows="6" :loading="initialLoading" animated>
      <template #default>
        <!-- Platform-wide announcements (cross-tenant) -->
        <div v-if="platformItems.length > 0" class="platform-announcements-section">
          <div class="platform-section-title">📢 平台公告</div>
          <div
            v-for="item in platformItems"
            :key="`platform-${item.id}`"
            class="announcement-card platform-card"
            :class="{ 'is-expanded': expandedPlatformId === item.id }"
            @click="togglePlatformExpand(item)"
          >
            <div class="card-header">
              <div class="card-title-row">
                <el-tag size="small" :type="platformCategoryTagType(item.category)" effect="dark">
                  {{ platformCategoryLabel(item.category) }}
                </el-tag>
                <span class="card-title is-unread">{{ item.title }}</span>
              </div>
              <div class="card-meta">
                {{ formatDate(item.publishAt ?? item.createdAt) }}
              </div>
            </div>
            <transition name="expand">
              <div v-if="expandedPlatformId === item.id" class="card-content">
                <RichTextRenderer class="content-html" :html="item.content" />
              </div>
            </transition>
          </div>
        </div>

        <div v-loading="loading" class="announcement-list">
          <div v-if="items.length === 0 && !loading" class="empty-state">
            {{ t('announcement.bell.empty') }}
          </div>

      <div
        v-for="item in items"
        :key="item.id"
        class="announcement-card"
        :class="{ 'is-expanded': expandedId === item.id }"
        @click="toggleExpand(item)"
      >
        <div class="card-header">
          <div class="card-title-row">
            <span v-if="!item.isRead" class="unread-dot" />
            <span v-if="item.pinned" class="pin-icon">📌</span>
            <el-tag
              v-if="item.requiresAck"
              size="small"
              type="warning"
              effect="dark"
              class="ack-tag"
            >
              {{ t('announcement.ack.required') }}
            </el-tag>
            <el-tag
              size="small"
              :type="getCategoryTagType(item.category)"
              effect="light"
              class="category-tag"
            >
              {{ getCategoryLabel(item.category) }}
            </el-tag>
            <span class="card-title" :class="{ 'is-unread': !item.isRead }">
              {{ item.title }}
            </span>
            <el-tag size="small" type="info" class="scope-tag">{{ getScopeLabel(item) }}</el-tag>
          </div>
          <div class="card-meta">
            {{ formatDate(item.publishAt) }} ｜ {{ item.createdByName }}
          </div>
        </div>

        <transition name="expand">
          <div v-if="expandedId === item.id" class="card-content">
            <!-- content 經後端 OWASP HTML Sanitizer + 前端 DOMPurify 雙重清洗 -->
            <RichTextRenderer class="content-html" :html="item.content" />
            <div v-if="item.attachments && item.attachments.length > 0" class="attachments-section">
              <div class="attachments-title">{{ t('announcement.attachments.title') }}</div>
              <ul class="attachments-list">
                <li v-for="att in item.attachments" :key="att.id" class="attachment-item">
                  <span class="attachment-icon">📎</span>
                  <a href="#" class="attachment-name" @click.prevent="handleDownload(item, att, $event)">
                    {{ att.fileName }}
                  </a>
                  <span class="attachment-size">{{ formatFileSize(att.fileSize) }}</span>
                </li>
              </ul>
            </div>

            <!-- 需確認公告：明確閱讀按鈕 -->
            <div v-if="item.requiresAck" class="ack-section">
              <el-alert
                :title="t('announcement.ack.notice')"
                type="warning"
                :closable="false"
                show-icon
                style="margin-bottom: 12px"
              />
              <el-button
                v-if="!item.isRead"
                type="primary"
                size="default"
                @click="handleAcknowledge(item, $event)"
              >
                ✓ {{ t('announcement.ack.button') }}
              </el-button>
              <div v-else class="ack-confirmed">
                ✓ {{ t('announcement.ack.confirmed') }}
              </div>
            </div>
          </div>
        </transition>
      </div>
    </div>
      </template>
    </el-skeleton>

    <div class="pagination-bar">
      <el-pagination
        :current-page="pagination.page + 1"
        :page-size="pagination.size"
        :total="pagination.total"
        layout="prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.page-container {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-muted);
}

.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.empty-state {
  text-align: center;
  color: var(--text-muted);
  padding: 40px 0;
}

.announcement-card {
  border: 1px solid var(--border-divider);
  border-radius: 8px;
  padding: 14px 18px;
  cursor: pointer;
  transition: border-color 150ms ease, box-shadow 150ms ease;
  background: var(--bg-surface);
}

.announcement-card:hover {
  border-color: var(--border-medium);
}

.announcement-card.is-expanded {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.06);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-primary);
  flex-shrink: 0;
}

.pin-icon {
  font-size: 13px;
  flex-shrink: 0;
}

.card-title {
  font-size: 14px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-title.is-unread {
  color: var(--text-primary);
  font-weight: 600;
}

.scope-tag {
  flex-shrink: 0;
}

.category-tag {
  flex-shrink: 0;
}

.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.card-meta {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
}

.card-content {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-divider);
}

.content-text {
  font-family: inherit;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}

.content-html {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
  word-break: break-word;
}
.content-html :deep(p) { margin: 0 0 8px; }
.content-html :deep(h1),
.content-html :deep(h2),
.content-html :deep(h3),
.content-html :deep(h4) { margin: 12px 0 8px; font-weight: 600; }
.content-html :deep(ul),
.content-html :deep(ol) { padding-left: 24px; margin: 0 0 8px; }
.content-html :deep(blockquote) {
  border-left: 3px solid var(--el-color-primary-light-5);
  padding-left: 12px;
  margin: 8px 0;
  color: var(--text-secondary);
}
.content-html :deep(a) {
  color: var(--el-color-primary);
  text-decoration: underline;
}

.expand-enter-active,
.expand-leave-active {
  transition: all 200ms ease;
  overflow: hidden;
}
.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
}

.pagination-bar {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.attachments-section {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-divider);
}
.attachments-title {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}
.attachments-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
}
.attachment-icon { flex-shrink: 0; }
.attachment-name {
  color: var(--el-color-primary);
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.attachment-name:hover { text-decoration: underline; }
.attachment-size {
  color: var(--text-muted);
  font-size: 12px;
  flex-shrink: 0;
}

.ack-tag {
  flex-shrink: 0;
}
.ack-section {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-color-warning-light-5);
}

.platform-announcements-section {
  margin-bottom: 16px;
}
.platform-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-warning);
  margin-bottom: 8px;
}
.platform-card {
  border-left: 3px solid var(--el-color-warning);
  background: var(--el-color-warning-light-9, #fdf6ec);
}
.ack-confirmed {
  color: var(--el-color-success);
  font-size: 14px;
  font-weight: 500;
}
</style>
