<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/authStore'
import { useDeptStore } from '@/stores/deptStore'
import {
  listAnnouncementsAdmin,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
  listAnnouncementAttachments,
  uploadAnnouncementAttachment,
  downloadAnnouncementAttachment,
  deleteAnnouncementAttachment,
  getAttachmentConfig,
  getAnnouncementReadStats,
  getAnnouncementUnreadUsers,
  listPinnedAnnouncements,
  reorderPinnedAnnouncements,
} from '@/api/announcement'
import { getDeptOptions } from '@/api/dept'
import type { AnnouncementResponse, AnnouncementRequest, AnnouncementTranslationDto, AnnouncementAttachmentResponse, AnnouncementReadStatsResponse, AnnouncementUnreadUserResponse } from '@/types/announcement'
import { ANNOUNCEMENT_CATEGORIES } from '@/types/announcement'
import type { DeptOptionVO } from '@/types/dept'
import { ElMessage, ElMessageBox } from 'element-plus'
import RichTextEditor from '@/components/RichTextEditor.vue'
import RichTextRenderer from '@/components/RichTextRenderer.vue'
import draggable from 'vuedraggable'

const { t } = useI18n()
const { locale } = useI18n()
const authStore = useAuthStore()
const deptStore = useDeptStore()

// ── 權限判斷 ──
const isAdmin = computed(() => {
  const roles = authStore.userInfo?.roles ?? []
  return roles.includes('ADMIN') || roles.includes('SUPER_ADMIN')
})

const isDeptAdmin = computed(() => {
  const roles = authStore.userInfo?.roles ?? []
  return roles.includes('DEPT_ADMIN')
})

const userDeptId = computed(() => authStore.userInfo?.deptId)

// ── 列表 ──
const loading = ref(false)
const tableData = ref<AnnouncementResponse[]>([])
const pagination = reactive({ page: 0, size: 10, total: 0 })
const statusFilter = ref('ALL')
const categoryFilter = ref<string>('ALL')
const keyword = ref('')

/** 分類 → el-tag type 顏色對應 */
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

async function fetchList() {
  loading.value = true
  try {
    const res = await listAnnouncementsAdmin({
      statusFilter: statusFilter.value,
      category: categoryFilter.value === 'ALL' ? undefined : categoryFilter.value,
      keyword: keyword.value || undefined,
      page: pagination.page,
      size: pagination.size,
    })
    if (res.errorCode === '00000') {
      tableData.value = res.body.content
      pagination.total = res.body.totalElements
    }
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  pagination.page = page - 1
  fetchList()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 0
  fetchList()
}

function handleSearch() {
  pagination.page = 0
  fetchList()
}

// ── Dialog ──
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const deptOptions = ref<DeptOptionVO[]>([])

const form = reactive<AnnouncementRequest & { publishMode: 'now' | 'schedule'; neverExpire: boolean }>({
  title: '',
  content: '',
  status: 'DRAFT',
  scope: 'ALL',
  category: 'GENERAL',
  targetDeptIds: [],
  pinned: false,
  requiresAck: false,
  publishAt: null,
  expireAt: null,
  version: undefined,
  publishMode: 'now',
  neverExpire: false,
})

// ── 多語系：zh-TW 對應 form.title / form.content（canonical）；其他語言放在 extraTranslations ──
const DEFAULT_LANG = 'zh-TW'
const EXTRA_LANGS = ['zh-CN', 'en'] as const
type ExtraLang = typeof EXTRA_LANGS[number]
const extraTranslations = reactive<Record<ExtraLang, { title: string; content: string }>>({
  'zh-CN': { title: '', content: '' },
  'en': { title: '', content: '' },
})
/** 表單目前顯示的語言 tab；預設為使用者目前 locale（若在支援清單內） */
function initialLangTab(): string {
  try {
    const stored = localStorage.getItem('locale') || ''
    if (stored === DEFAULT_LANG || (EXTRA_LANGS as readonly string[]).includes(stored)) return stored
  } catch {
    /* ignore */
  }
  return DEFAULT_LANG
}
const currentLangTab = ref<string>(initialLangTab())

/** 將 extraTranslations 轉成送往後端的清單；過濾掉 title+content 皆空的語言 */
function buildTranslationsPayload(): AnnouncementTranslationDto[] {
  const out: AnnouncementTranslationDto[] = []
  for (const lang of EXTRA_LANGS) {
    const entry = extraTranslations[lang]
    if (entry.title.trim() || entry.content.trim()) {
      out.push({ langCode: lang, title: entry.title, content: entry.content })
    }
  }
  return out
}

/** 將回應的 translations[] 套入 extraTranslations（zh-TW 由 form.title/content 承接） */
function applyResponseTranslations(translations: AnnouncementTranslationDto[] | undefined) {
  for (const lang of EXTRA_LANGS) {
    extraTranslations[lang] = { title: '', content: '' }
  }
  if (!translations) return
  for (const t of translations) {
    if (t.langCode === DEFAULT_LANG) continue
    if ((EXTRA_LANGS as readonly string[]).includes(t.langCode)) {
      extraTranslations[t.langCode as ExtraLang] = { title: t.title ?? '', content: t.content ?? '' }
    }
  }
}

const expireManuallyEdited = ref(false)

// ── 附件 ──
/** 編輯中公告的附件清單；新增模式時為空、需儲存後才能上傳 */
const attachments = ref<AnnouncementAttachmentResponse[]>([])
const attachmentUploading = ref(false)
const MAX_ATTACHMENTS = 10
/** 後端提供的允許副檔名白名單（全小寫，不含點），預設 ["pdf"] */
const allowedExtensions = ref<string[]>(['pdf'])
const acceptAttr = computed(() => allowedExtensions.value.map((e) => `.${e}`).join(','))
const extensionsHint = computed(() => allowedExtensions.value.map((e) => e.toUpperCase()).join(' / '))

async function loadAttachments(id: number) {
  try {
    const res = await listAnnouncementAttachments(id)
    if (res.errorCode === '00000') {
      attachments.value = res.body ?? []
    }
  } catch {
    attachments.value = []
  }
}

async function handleUploadAttachment(opts: { file: File }) {
  if (!editingId.value) {
    ElMessage.warning(t('announcement.attachments.saveFirst'))
    return
  }
  if (attachments.value.length >= MAX_ATTACHMENTS) {
    ElMessage.warning(t('announcement.attachments.maxReached'))
    return
  }
  attachmentUploading.value = true
  try {
    const res = await uploadAnnouncementAttachment(editingId.value, opts.file)
    if (res.errorCode === '00000') {
      attachments.value.push(res.body)
      ElMessage.success(t('announcement.attachments.uploadSuccess'))
    }
  } catch {
    ElMessage.error(t('announcement.attachments.uploadFailed'))
  } finally {
    attachmentUploading.value = false
  }
}

async function handleDownloadAttachment(att: AnnouncementAttachmentResponse) {
  if (!editingId.value) return
  try {
    const blob = await downloadAnnouncementAttachment(editingId.value, att.id)
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

async function handleRemoveAttachment(att: AnnouncementAttachmentResponse) {
  if (!editingId.value) return
  try {
    await ElMessageBox.confirm(
      t('announcement.attachments.removeConfirm'),
      t('common.confirm'),
      { type: 'warning' },
    )
    const res = await deleteAnnouncementAttachment(editingId.value, att.id)
    if (res.errorCode === '00000') {
      attachments.value = attachments.value.filter((a) => a.id !== att.id)
      ElMessage.success(t('announcement.attachments.deleted'))
    }
  } catch {
    // cancelled
  }
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

// ── 已讀統計（需確認公告）──
const statsDialogVisible = ref(false)
const statsLoading = ref(false)
const statsAnnouncement = ref<AnnouncementResponse | null>(null)
const readStats = ref<AnnouncementReadStatsResponse | null>(null)
const unreadUsers = ref<AnnouncementUnreadUserResponse[]>([])
const unreadKeyword = ref('')
const unreadPagination = reactive({ page: 0, size: 20, total: 0 })

async function openStatsDialog(row: AnnouncementResponse) {
  statsAnnouncement.value = row
  statsDialogVisible.value = true
  unreadKeyword.value = ''
  unreadPagination.page = 0
  await refreshStats()
}

async function refreshStats() {
  if (!statsAnnouncement.value) return
  statsLoading.value = true
  try {
    const [statsRes, usersRes] = await Promise.all([
      getAnnouncementReadStats(statsAnnouncement.value.id),
      getAnnouncementUnreadUsers(statsAnnouncement.value.id, {
        keyword: unreadKeyword.value || undefined,
        page: unreadPagination.page,
        size: unreadPagination.size,
      }),
    ])
    if (statsRes.errorCode === '00000') readStats.value = statsRes.body
    if (usersRes.errorCode === '00000') {
      unreadUsers.value = usersRes.body.content
      unreadPagination.total = usersRes.body.totalElements
    }
  } catch {
    ElMessage.error(t('common.error'))
  } finally {
    statsLoading.value = false
  }
}

function handleUnreadSearch() {
  unreadPagination.page = 0
  refreshStats()
}

function handleUnreadPageChange(page: number) {
  unreadPagination.page = page - 1
  refreshStats()
}

/** 格式化已讀比例：0.8421 → "84.2%" */
function formatRatio(ratio: number | null | undefined): string {
  if (ratio == null) return '—'
  return `${(ratio * 100).toFixed(1)}%`
}

// ── 自動儲存草稿（每 30 秒）──
const AUTO_SAVE_INTERVAL_MS = 30_000
const autoSaveTimer = ref<number | null>(null)
const autoSaveStatus = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const lastSavedAt = ref<Date | null>(null)
const lastSavedSnapshot = ref<string>('')

/** 取得表單需追蹤變更的欄位快照，用來判斷是否需要重新存檔 */
function snapshotForm(): string {
  return JSON.stringify({
    title: form.title,
    content: form.content,
    translations: buildTranslationsPayload(),
    scope: form.scope,
    category: form.category,
    targetDeptIds: form.targetDeptIds,
    pinned: form.pinned,
    requiresAck: form.requiresAck,
    publishAt: form.publishAt,
    expireAt: form.expireAt,
    publishMode: form.publishMode,
    neverExpire: form.neverExpire,
  })
}

/** 自動儲存草稿：僅針對 DRAFT 狀態、且有變更時儲存 */
async function autoSaveDraft() {
  // 僅 DRAFT 狀態才自動儲存，避免覆蓋已發佈 / 已排程內容
  if (form.status !== 'DRAFT') return
  // 空白表單不儲存
  if (!form.title?.trim() && !form.content?.trim()) return
  // 無變更不儲存
  const snap = snapshotForm()
  if (snap === lastSavedSnapshot.value) return
  // 邊存邊輸入：避免並發
  if (autoSaveStatus.value === 'saving') return

  autoSaveStatus.value = 'saving'
  const payload: AnnouncementRequest = {
    title: form.title,
    content: form.content,
    translations: buildTranslationsPayload(),
    status: 'DRAFT',
    scope: form.scope,
    category: form.category || 'GENERAL',
    targetDeptIds: form.scope === 'DEPT' ? form.targetDeptIds : [],
    pinned: form.pinned,
    requiresAck: form.requiresAck === true,
    publishAt: form.publishMode === 'now' ? null : form.publishAt,
    expireAt: form.neverExpire ? null : form.expireAt,
    version: dialogMode.value === 'edit' ? form.version : undefined,
  }

  try {
    if (dialogMode.value === 'create') {
      const res = await createAnnouncement(payload)
      if (res.errorCode === '00000') {
        // 首次自動儲存 → 切換為 edit 模式，後續以 update 接續儲存
        editingId.value = res.body.id
        form.version = res.body.version
        dialogMode.value = 'edit'
        lastSavedSnapshot.value = snap
        lastSavedAt.value = new Date()
        autoSaveStatus.value = 'saved'
      } else {
        autoSaveStatus.value = 'error'
      }
    } else if (editingId.value) {
      const res = await updateAnnouncement(editingId.value, payload)
      if (res.errorCode === '00000') {
        form.version = res.body.version
        lastSavedSnapshot.value = snap
        lastSavedAt.value = new Date()
        autoSaveStatus.value = 'saved'
      } else {
        autoSaveStatus.value = 'error'
      }
    }
  } catch {
    autoSaveStatus.value = 'error'
  }
}

function startAutoSaveTimer() {
  stopAutoSaveTimer()
  autoSaveTimer.value = window.setInterval(autoSaveDraft, AUTO_SAVE_INTERVAL_MS)
}
function stopAutoSaveTimer() {
  if (autoSaveTimer.value != null) {
    clearInterval(autoSaveTimer.value)
    autoSaveTimer.value = null
  }
}

/** 自動儲存狀態顯示文字 */
const autoSaveText = computed(() => {
  if (autoSaveStatus.value === 'saving') return t('announcement.autoSave.saving')
  if (autoSaveStatus.value === 'error') return t('announcement.autoSave.error')
  if (autoSaveStatus.value === 'saved' && lastSavedAt.value) {
    const d = lastSavedAt.value
    const pad = (n: number) => String(n).padStart(2, '0')
    return t('announcement.autoSave.savedAt', {
      time: `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`,
    })
  }
  return ''
})

// 在 dialog 開關時啟動/停止定時器
watch(() => dialogVisible.value, (visible) => {
  if (visible) {
    lastSavedSnapshot.value = snapshotForm()
    lastSavedAt.value = null
    autoSaveStatus.value = 'idle'
    startAutoSaveTimer()
  } else {
    stopAutoSaveTimer()
  }
})

// ── 預覽 Dialog ──
const previewDialogVisible = ref(false)
function openPreview() {
  previewDialogVisible.value = true
}

// ── 置頂順序 Dialog（拖曳排序）──
const pinOrderDialogVisible = ref(false)
const pinOrderLoading = ref(false)
const pinOrderSaving = ref(false)
const pinOrderList = ref<AnnouncementResponse[]>([])

async function openPinOrderDialog() {
  pinOrderDialogVisible.value = true
  pinOrderLoading.value = true
  try {
    const res = await listPinnedAnnouncements()
    if (res.errorCode === '00000') {
      pinOrderList.value = res.body
    }
  } catch {
    ElMessage.error(t('common.error'))
  } finally {
    pinOrderLoading.value = false
  }
}

async function handlePinOrderSave() {
  if (pinOrderList.value.length === 0) {
    pinOrderDialogVisible.value = false
    return
  }
  pinOrderSaving.value = true
  try {
    const orderedIds = pinOrderList.value.map((a) => a.id)
    const res = await reorderPinnedAnnouncements(orderedIds)
    if (res.errorCode === '00000') {
      ElMessage.success(t('announcement.pinOrder.saved'))
      pinOrderDialogVisible.value = false
      fetchList()
    }
  } catch {
    ElMessage.error(t('common.error'))
  } finally {
    pinOrderSaving.value = false
  }
}

function resetForm() {
  form.title = ''
  form.content = ''
  form.status = 'DRAFT'
  form.scope = 'ALL'
  form.category = 'GENERAL'
  form.targetDeptIds = []
  form.pinned = false
  form.requiresAck = false
  form.publishAt = null
  form.expireAt = null
  form.version = undefined
  form.publishMode = 'now'
  form.neverExpire = false
  expireManuallyEdited.value = false
  attachments.value = []
  for (const lang of EXTRA_LANGS) extraTranslations[lang] = { title: '', content: '' }
  currentLangTab.value = initialLangTab()
}

function openCreate() {
  resetForm()
  dialogMode.value = 'create'
  editingId.value = null

  // DEPT_ADMIN：自動填入自己部門
  if (isDeptAdmin.value && userDeptId.value) {
    form.scope = 'DEPT'
    form.targetDeptIds = [Number(userDeptId.value)]
  }

  dialogVisible.value = true
}

function openEdit(row: AnnouncementResponse) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  // \u540e\u7aef\u4e00\u5b9a\u4ee5 translations[] \u5305\u542b zh-TW canonical\uff1b\u4ee5\u6b64\u70ba\u4e3b\uff0c\u5426\u5247 fallback \u5230 row.title/content
  const zhTw = row.translations?.find((t) => t.langCode === DEFAULT_LANG)
  form.title = zhTw?.title ?? row.title
  form.content = zhTw?.content ?? row.content
  applyResponseTranslations(row.translations)
  form.status = row.status
  form.scope = row.scope
  form.category = row.category || 'GENERAL'
  form.targetDeptIds = row.targetDeptIds ?? []
  form.pinned = row.pinned
  form.requiresAck = row.requiresAck === true
  form.publishAt = row.publishAt
  form.expireAt = row.expireAt
  form.version = row.version
  form.publishMode = 'schedule'
  form.neverExpire = row.expireAt === null
  expireManuallyEdited.value = true

  // DEPT_ADMIN：強制自己部門
  if (isDeptAdmin.value && userDeptId.value) {
    form.scope = 'DEPT'
    form.targetDeptIds = [Number(userDeptId.value)]
  }

  // 載入附件清單
  attachments.value = row.attachments ?? []
  loadAttachments(row.id)

  dialogVisible.value = true
}

// 自動計算 expire_at（publish_at + 30 天）
function toDateTimeString(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

watch(() => form.publishAt, (newVal) => {
  if (!expireManuallyEdited.value && !form.neverExpire && newVal) {
    const d = new Date(newVal)
    d.setDate(d.getDate() + 30)
    form.expireAt = toDateTimeString(d)
  }
})

watch(() => form.neverExpire, (val) => {
  if (val) {
    form.expireAt = null
  } else if (form.publishAt) {
    expireManuallyEdited.value = false
    const d = new Date(form.publishAt)
    d.setDate(d.getDate() + 30)
    form.expireAt = toDateTimeString(d)
  }
})

function onExpireAtManualChange() {
  expireManuallyEdited.value = true
}

async function handleSave() {
  const payload: AnnouncementRequest = {
    title: form.title,
    content: form.content,
    translations: buildTranslationsPayload(),
    status: form.status,
    scope: form.scope,
    category: form.category || 'GENERAL',
    targetDeptIds: form.scope === 'DEPT' ? form.targetDeptIds : [],
    pinned: form.pinned,
    requiresAck: form.requiresAck === true,
    publishAt: form.publishMode === 'now' ? null : form.publishAt,
    expireAt: form.neverExpire ? null : form.expireAt,
    version: dialogMode.value === 'edit' ? form.version : undefined,
  }

  try {
    if (dialogMode.value === 'create') {
      const res = await createAnnouncement(payload)
      if (res.errorCode === '00000') {
        ElMessage.success(t('announcement.msg.created'))
        lastSavedSnapshot.value = snapshotForm()
        dialogVisible.value = false
        fetchList()
      }
    } else if (editingId.value) {
      const res = await updateAnnouncement(editingId.value, payload)
      if (res.errorCode === '00000') {
        ElMessage.success(t('announcement.msg.updated'))
        lastSavedSnapshot.value = snapshotForm()
        dialogVisible.value = false
        fetchList()
      }
    }
  } catch {
    ElMessage.error(t('common.error'))
  }
}

async function handleDelete(row: AnnouncementResponse) {
  try {
    await ElMessageBox.confirm(
      t('announcement.msg.deleteConfirm'),
      t('common.confirm'),
      { type: 'warning' },
    )
    const res = await deleteAnnouncement(row.id)
    if (res.errorCode === '00000') {
      ElMessage.success(t('announcement.msg.deleted'))
      fetchList()
    }
  } catch {
    // cancelled
  }
}

// ── 狀態顯示 ──
/**
 * 計算狀態：
 * - DRAFT     ：草稿
 * - SCHEDULED ：status=PUBLISHED 但 publishAt > now（排程中尚未生效）
 * - EXPIRED   ：expireAt < now
 * - PUBLISHED ：以上都不是的已生效公告
 */
function computeEffectiveStatus(row: AnnouncementResponse): 'DRAFT' | 'SCHEDULED' | 'EXPIRED' | 'PUBLISHED' {
  if (row.status === 'DRAFT') return 'DRAFT'
  const now = new Date()
  if (row.publishAt && new Date(row.publishAt) > now) return 'SCHEDULED'
  if (row.expireAt && new Date(row.expireAt) < now) return 'EXPIRED'
  return 'PUBLISHED'
}

function getStatusType(row: AnnouncementResponse): string {
  switch (computeEffectiveStatus(row)) {
    case 'DRAFT': return 'info'
    case 'SCHEDULED': return 'warning'
    case 'EXPIRED': return 'danger'
    default: return 'success'
  }
}

function getStatusLabel(row: AnnouncementResponse): string {
  switch (computeEffectiveStatus(row)) {
    case 'DRAFT': return t('announcement.status.draft')
    case 'SCHEDULED': return t('announcement.status.scheduled')
    case 'EXPIRED': return t('announcement.status.expired')
    default: return t('announcement.status.published')
  }
}

function getScopeLabel(row: AnnouncementResponse): string {
  if (row.scope === 'ALL') return t('announcement.scope.all')
  return row.targetDeptNames?.join('、') || t('announcement.scope.dept')
}

function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return '—'
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function getExpireLabel(row: AnnouncementResponse): string {
  if (!row.expireAt) return t('announcement.neverExpire')
  return formatDateTime(row.expireAt)
}

// ── 初始化 ──
// 切換 i18n locale 時：重新撈列表（後端會依 Accept-Language 回不同翻譯）
watch(locale, () => {
  fetchList()
})

onMounted(async () => {
  fetchList()
  // 載入部門選項
  try {
    const res = await getDeptOptions()
    if (res.errorCode === '00000') {
      deptOptions.value = res.body
    }
  } catch {
    // fallback
  }
  // 載入附件上傳政策
  try {
    const cfg = await getAttachmentConfig()
    if (cfg.errorCode === '00000' && cfg.body?.length) {
      allowedExtensions.value = cfg.body
    }
  } catch {
    // 保留預設 ["pdf"]
  }
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>{{ t('announcement.admin.title') }}</h2>
        <p class="page-subtitle">{{ t('announcement.admin.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-button @click="openPinOrderDialog">
          📌 {{ t('announcement.pinOrder.button') }}
        </el-button>
        <el-button type="primary" @click="openCreate">
          + {{ t('announcement.admin.create') }}
        </el-button>
      </div>
    </div>

    <!-- 篩選 -->
    <div class="filter-bar">
      <el-select v-model="statusFilter" style="width: 140px" @change="handleSearch">
        <el-option :label="t('announcement.filter.all')" value="ALL" />
        <el-option :label="t('announcement.status.draft')" value="DRAFT" />
        <el-option :label="t('announcement.status.scheduled')" value="SCHEDULED" />
        <el-option :label="t('announcement.status.published')" value="PUBLISHED" />
        <el-option :label="t('announcement.status.expired')" value="EXPIRED" />
      </el-select>
      <el-select v-model="categoryFilter" style="width: 160px" @change="handleSearch">
        <el-option :label="t('announcement.category.all')" value="ALL" />
        <el-option
          v-for="c in ANNOUNCEMENT_CATEGORIES"
          :key="c"
          :value="c"
          :label="t(`announcement.category.${c.toLowerCase()}`)"
        />
      </el-select>
      <el-input
        v-model="keyword"
        :placeholder="t('announcement.filter.searchTitle')"
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
    </div>

    <!-- 列表 -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column :label="t('announcement.field.title')" prop="title" min-width="180" show-overflow-tooltip />
      <el-table-column :label="t('announcement.field.category')" width="110">
        <template #default="{ row }">
          <el-tag :type="getCategoryTagType(row.category)" size="small" effect="light">
            {{ getCategoryLabel(row.category) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('announcement.field.scope')" width="140">
        <template #default="{ row }">{{ getScopeLabel(row) }}</template>
      </el-table-column>
      <el-table-column :label="t('announcement.field.publishAt')" width="130">
        <template #default="{ row }">{{ formatDateTime(row.publishAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('announcement.field.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row)" size="small">{{ getStatusLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('announcement.field.pinned')" width="80" align="center">
        <template #default="{ row }">
          <span v-if="row.pinned">✓</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('announcement.readStats.column')" width="130" align="center">
        <template #default="{ row }">
          <el-button
            v-if="row.requiresAck"
            link
            type="primary"
            size="small"
            @click="openStatsDialog(row)"
          >
            <el-tag type="warning" size="small" effect="plain">
              {{ t('announcement.readStats.ack') }}
            </el-tag>
            <span style="margin-left: 4px">{{ t('announcement.readStats.view') }}</span>
          </el-button>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('announcement.field.expireAt')" width="130">
        <template #default="{ row }">{{ getExpireLabel(row) }}</template>
      </el-table-column>
      <el-table-column :label="t('announcement.field.actions')" width="100" fixed="right">
        <template #default="{ row }">
          <template v-if="row.editable !== false">
            <el-button type="primary" link size="small" @click="openEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分頁 -->
    <div class="pagination-bar">
      <span>{{ t('announcement.admin.total', { count: pagination.total }) }}</span>
      <el-pagination
        :current-page="pagination.page + 1"
        :page-size="pagination.size"
        :page-sizes="[10, 20, 50]"
        :total="pagination.total"
        layout="sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 新增/編輯 Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('announcement.admin.create') : t('announcement.admin.edit')"
      width="600px"
      destroy-on-close
    >
      <el-form label-position="top" :aria-label="dialogMode === 'create' ? t('announcement.admin.create') : t('announcement.admin.edit')">
        <!-- 多語系輸入：zh-TW 為主表（必填）；zh-CN / en 為選填翻譯 -->
        <el-tabs v-model="currentLangTab">
          <el-tab-pane :label="t('announcement.lang.zhTW') + ' *'" name="zh-TW">
            <el-form-item :label="t('announcement.field.title')" required>
              <el-input v-model="form.title" maxlength="200" show-word-limit />
            </el-form-item>
            <el-form-item :label="t('announcement.field.content')" required>
              <RichTextEditor v-model="form.content" />
            </el-form-item>
          </el-tab-pane>
          <el-tab-pane :label="t('announcement.lang.zhCN')" name="zh-CN">
            <el-form-item :label="t('announcement.field.title')">
              <el-input v-model="extraTranslations['zh-CN'].title" maxlength="200" show-word-limit />
            </el-form-item>
            <el-form-item :label="t('announcement.field.content')">
              <RichTextEditor v-model="extraTranslations['zh-CN'].content" />
            </el-form-item>
            <el-alert type="info" :closable="false" show-icon>
              {{ t('announcement.lang.optionalHint') }}
            </el-alert>
          </el-tab-pane>
          <el-tab-pane :label="t('announcement.lang.en')" name="en">
            <el-form-item :label="t('announcement.field.title')">
              <el-input v-model="extraTranslations['en'].title" maxlength="200" show-word-limit />
            </el-form-item>
            <el-form-item :label="t('announcement.field.content')">
              <RichTextEditor v-model="extraTranslations['en'].content" />
            </el-form-item>
            <el-alert type="info" :closable="false" show-icon>
              {{ t('announcement.lang.optionalHint') }}
            </el-alert>
          </el-tab-pane>
        </el-tabs>

        <el-form-item :label="t('announcement.field.category')">
          <el-select v-model="form.category" style="width: 100%">
            <el-option
              v-for="c in ANNOUNCEMENT_CATEGORIES"
              :key="c"
              :value="c"
              :label="t(`announcement.category.${c.toLowerCase()}`)"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('announcement.field.scope')">
          <template v-if="isAdmin">
            <el-radio-group v-model="form.scope">
              <el-radio value="ALL">{{ t('announcement.scope.all') }}</el-radio>
              <el-radio value="DEPT">{{ t('announcement.scope.dept') }}</el-radio>
            </el-radio-group>
            <el-select
              v-if="form.scope === 'DEPT'"
              v-model="form.targetDeptIds"
              multiple
              :placeholder="t('announcement.field.selectDepts')"
              style="width: 100%; margin-top: 8px"
            >
              <el-option
                v-for="opt in deptOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </template>
          <template v-else>
            <el-tag>{{ deptStore.getDeptName(userDeptId!) }}</el-tag>
          </template>
        </el-form-item>

        <el-form-item :label="t('announcement.field.status')">
          <el-radio-group v-model="form.status">
            <el-radio value="DRAFT">{{ t('announcement.status.draft') }}</el-radio>
            <el-radio value="PUBLISHED">{{ t('announcement.status.published') }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item :label="t('announcement.field.publishAt')">
          <el-radio-group v-model="form.publishMode">
            <el-radio value="now">{{ t('announcement.publishMode.now') }}</el-radio>
            <el-radio value="schedule">{{ t('announcement.publishMode.schedule') }}</el-radio>
          </el-radio-group>
          <el-date-picker
            v-if="form.publishMode === 'schedule'"
            v-model="form.publishAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%; margin-top: 8px"
          />
        </el-form-item>

        <el-form-item :label="t('announcement.field.pinned')">
          <el-checkbox v-model="form.pinned">{{ t('announcement.field.pinnedLabel') }}</el-checkbox>
        </el-form-item>

        <el-form-item :label="t('announcement.field.requiresAck')">
          <el-checkbox v-model="form.requiresAck">
            {{ t('announcement.field.requiresAckLabel') }}
          </el-checkbox>
          <div class="hint-text">{{ t('announcement.field.requiresAckHint') }}</div>
        </el-form-item>

        <el-form-item :label="t('announcement.field.expireAt')">
          <div style="display: flex; align-items: center; gap: 12px; width: 100%">
            <el-date-picker
              v-if="!form.neverExpire"
              v-model="form.expireAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="flex: 1"
              @change="onExpireAtManualChange"
            />
            <el-checkbox v-model="form.neverExpire">{{ t('announcement.neverExpire') }}</el-checkbox>
          </div>
        </el-form-item>

        <el-form-item :label="t('announcement.attachments.title')">
          <div v-if="dialogMode === 'create'" class="attachments-hint">
            {{ t('announcement.attachments.saveFirst') }}
          </div>
          <div v-else style="width: 100%">
            <el-upload
              :show-file-list="false"
              :http-request="handleUploadAttachment"
              :accept="acceptAttr"
              :disabled="attachmentUploading || attachments.length >= MAX_ATTACHMENTS"
            >
              <el-button
                size="small"
                :loading="attachmentUploading"
                :disabled="attachments.length >= MAX_ATTACHMENTS"
              >
                {{ t('announcement.attachments.upload') }} ({{ attachments.length }}/{{ MAX_ATTACHMENTS }})
              </el-button>
              <template #tip>
                <div class="attachments-hint">
                  {{ t('announcement.attachments.uploadHint', { types: extensionsHint }) }}
                </div>
              </template>
            </el-upload>
            <div v-if="attachments.length === 0" class="attachments-empty">
              {{ t('announcement.attachments.empty') }}
            </div>
            <ul v-else class="attachments-list">
              <li v-for="att in attachments" :key="att.id" class="attachment-item">
                <span class="attachment-name" :title="att.fileName">{{ att.fileName }}</span>
                <span class="attachment-size">{{ formatFileSize(att.fileSize) }}</span>
                <el-button link type="primary" size="small" @click="handleDownloadAttachment(att)">
                  {{ t('announcement.attachments.download') }}
                </el-button>
                <el-button link type="danger" size="small" @click="handleRemoveAttachment(att)">
                  {{ t('announcement.attachments.remove') }}
                </el-button>
              </li>
            </ul>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer-row">
          <span
            class="auto-save-indicator"
            :class="{
              'is-saving': autoSaveStatus === 'saving',
              'is-saved': autoSaveStatus === 'saved',
              'is-error': autoSaveStatus === 'error',
            }"
          >
            {{ autoSaveText }}
          </span>
          <div class="dialog-footer-actions">
            <el-button @click="openPreview">
              {{ t('announcement.preview.button') }}
            </el-button>
            <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
            <el-button type="primary" @click="handleSave">{{ t('common.save') }}</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 已讀統計 Dialog -->
    <el-dialog
      v-model="statsDialogVisible"
      :title="t('announcement.readStats.dialogTitle')"
      width="720px"
      destroy-on-close
    >
      <div v-loading="statsLoading">
        <div v-if="statsAnnouncement" class="stats-title">
          {{ statsAnnouncement.title }}
        </div>

        <div v-if="readStats" class="stats-summary">
          <div class="stat-card">
            <div class="stat-label">{{ t('announcement.readStats.totalAudience') }}</div>
            <div class="stat-value">{{ readStats.totalAudience }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">{{ t('announcement.readStats.readCount') }}</div>
            <div class="stat-value" style="color: var(--el-color-success)">{{ readStats.readCount }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">{{ t('announcement.readStats.unreadCount') }}</div>
            <div class="stat-value" style="color: var(--el-color-danger)">{{ readStats.unreadCount }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">{{ t('announcement.readStats.ratio') }}</div>
            <div class="stat-value">{{ formatRatio(readStats.readRatio) }}</div>
          </div>
        </div>

        <el-progress
          v-if="readStats"
          :percentage="Number(((readStats.readRatio ?? 0) * 100).toFixed(1))"
          :status="readStats.readRatio >= 0.9 ? 'success' : readStats.readRatio >= 0.5 ? undefined : 'exception'"
          style="margin: 12px 0"
        />

        <div class="unread-header">
          <h4 style="margin: 0">{{ t('announcement.readStats.unreadList') }}</h4>
          <el-input
            v-model="unreadKeyword"
            :placeholder="t('announcement.readStats.searchUser')"
            size="small"
            clearable
            style="width: 220px"
            @keyup.enter="handleUnreadSearch"
            @clear="handleUnreadSearch"
          />
        </div>

        <el-table :data="unreadUsers" stripe size="small" style="margin-top: 8px">
          <el-table-column :label="t('announcement.readStats.displayName')" prop="displayName" min-width="140" />
          <el-table-column :label="t('announcement.readStats.email')" prop="email" min-width="200" />
          <el-table-column :label="t('announcement.readStats.dept')" prop="deptName" min-width="140">
            <template #default="{ row }">{{ row.deptName ?? '—' }}</template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar" style="margin-top: 12px">
          <span>{{ t('announcement.admin.total', { count: unreadPagination.total }) }}</span>
          <el-pagination
            :current-page="unreadPagination.page + 1"
            :page-size="unreadPagination.size"
            :total="unreadPagination.total"
            layout="prev, pager, next"
            @current-change="handleUnreadPageChange"
          />
        </div>
      </div>

      <template #footer>
        <el-button @click="statsDialogVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <!-- 預覽 Dialog：模擬前台呈現 -->
    <el-dialog
      v-model="previewDialogVisible"
      :title="t('announcement.preview.dialogTitle')"
      width="720px"
      destroy-on-close
    >
      <div v-if="!form.title?.trim() && !form.content?.trim()" class="preview-empty">
        {{ t('announcement.preview.empty') }}
      </div>
      <div v-else class="preview-card">
        <div class="preview-header">
          <span v-if="form.pinned" class="pin-icon">📌</span>
          <el-tag
            v-if="form.requiresAck"
            size="small"
            type="warning"
            effect="dark"
          >
            {{ t('announcement.ack.required') }}
          </el-tag>
          <el-tag
            size="small"
            :type="getCategoryTagType(form.category)"
            effect="light"
          >
            {{ getCategoryLabel(form.category) }}
          </el-tag>
          <span class="preview-title">{{ form.title || t('announcement.preview.untitled') }}</span>
        </div>
        <div class="preview-meta">
          {{ form.publishMode === 'now'
            ? t('announcement.preview.publishNow')
            : (form.publishAt ?? t('announcement.preview.publishPending')) }}
          ·
          {{ form.neverExpire
            ? t('announcement.neverExpire')
            : (form.expireAt ?? '—') }}
        </div>
        <RichTextRenderer class="preview-content" :html="form.content" />

        <div v-if="attachments.length > 0" class="preview-attachments">
          <div class="preview-attachments-title">{{ t('announcement.attachments.title') }}</div>
          <ul class="attachments-list">
            <li v-for="att in attachments" :key="att.id" class="attachment-item">
              <span>📎</span>
              <span class="attachment-name">{{ att.fileName }}</span>
              <span class="attachment-size">{{ formatFileSize(att.fileSize) }}</span>
            </li>
          </ul>
        </div>
      </div>

      <template #footer>
        <el-button @click="previewDialogVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <!-- 置頂順序 Dialog：拖曳排序 -->
    <el-dialog
      v-model="pinOrderDialogVisible"
      :title="t('announcement.pinOrder.dialogTitle')"
      width="560px"
      destroy-on-close
    >
      <div v-loading="pinOrderLoading">
        <p class="pin-order-hint">{{ t('announcement.pinOrder.hint') }}</p>
        <div v-if="pinOrderList.length === 0 && !pinOrderLoading" class="pin-order-empty">
          {{ t('announcement.pinOrder.empty') }}
        </div>
        <draggable
          v-else
          v-model="pinOrderList"
          item-key="id"
          handle=".pin-drag-handle"
          ghost-class="pin-drag-ghost"
          animation="180"
          class="pin-order-list"
        >
          <template #item="{ element, index }">
            <div class="pin-order-item">
              <span class="pin-drag-handle" :title="t('announcement.pinOrder.drag')">⋮⋮</span>
              <span class="pin-order-index">{{ index + 1 }}</span>
              <el-tag
                size="small"
                :type="getCategoryTagType(element.category)"
                effect="light"
              >
                {{ getCategoryLabel(element.category) }}
              </el-tag>
              <span class="pin-order-title">{{ element.title }}</span>
            </div>
          </template>
        </draggable>
      </div>
      <template #footer>
        <el-button @click="pinOrderDialogVisible = false" :disabled="pinOrderSaving">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="pinOrderSaving"
          :disabled="pinOrderList.length === 0"
          @click="handlePinOrderSave"
        >
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
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

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.pagination-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  font-size: 13px;
  color: var(--text-muted);
}

.attachments-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}
.attachments-empty {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-muted);
}
.attachments-list {
  list-style: none;
  padding: 0;
  margin: 8px 0 0;
}
.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid var(--border-divider);
  font-size: 13px;
}
.attachment-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.attachment-size {
  color: var(--text-muted);
  font-size: 12px;
  flex-shrink: 0;
}

.hint-text {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

.text-muted {
  color: var(--text-muted);
}

/* 已讀統計 dialog */
.stats-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--text-primary);
}
.stats-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.stat-card {
  padding: 12px;
  border: 1px solid var(--border-divider);
  border-radius: 6px;
  text-align: center;
}
.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
}
.unread-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}

/* dialog footer with auto-save indicator */
.dialog-footer-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.dialog-footer-actions {
  display: flex;
  gap: 8px;
}
.auto-save-indicator {
  font-size: 12px;
  color: var(--text-muted);
  min-height: 1em;
}
.auto-save-indicator.is-saving {
  color: var(--el-color-primary);
}
.auto-save-indicator.is-saved {
  color: var(--el-color-success);
}
.auto-save-indicator.is-error {
  color: var(--el-color-danger);
}

/* preview dialog */
.preview-card {
  background: var(--el-bg-color-page);
  border-radius: 8px;
  padding: 20px;
}
.preview-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.preview-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-right: 4px;
}
.preview-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 16px;
}
.preview-content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
  word-break: break-word;
}
.preview-content :deep(img) {
  max-width: 100%;
  height: auto;
}
.preview-attachments {
  margin-top: 20px;
  padding-top: 12px;
  border-top: 1px solid var(--border-divider);
}
.preview-attachments-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.preview-empty {
  color: var(--text-muted);
  font-style: italic;
  text-align: center;
  padding: 40px 0;
}

/* header action group + pin order dialog */
.header-actions {
  display: flex;
  gap: 8px;
}
.pin-order-hint {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0 0 12px;
}
.pin-order-empty {
  text-align: center;
  padding: 40px 0;
  color: var(--text-muted);
}
.pin-order-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.pin-order-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border-divider);
  border-radius: 6px;
  background: var(--el-bg-color);
  transition: background 0.15s;
}
.pin-order-item:hover {
  background: var(--el-bg-color-page);
}
.pin-drag-handle {
  cursor: grab;
  color: var(--text-muted);
  font-size: 18px;
  font-weight: 700;
  user-select: none;
  letter-spacing: -3px;
}
.pin-drag-handle:active {
  cursor: grabbing;
}
.pin-order-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.pin-order-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}
.pin-drag-ghost {
  opacity: 0.5;
  background: var(--el-color-primary-light-9);
}
</style>
