<script setup lang="ts">
/**
 * 平台公告管理 — super_admin 發布跨場域公告（如系統維護通知）。
 * 掛載於 PlatformLayout（dark theme），後端由 PLATFORM_ANNOUNCEMENT_MANAGE 權限保護。
 */
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Megaphone, Plus, RefreshCw } from 'lucide-vue-next'
import {
  listPlatformAnnouncementsAdmin,
  createPlatformAnnouncement,
  updatePlatformAnnouncement,
  deletePlatformAnnouncement,
} from '@/api/platformAnnouncement'
import type {
  PlatformAnnouncementResponse,
  PlatformAnnouncementRequest,
} from '@/types/platformAnnouncement'
import { PLATFORM_ANNOUNCEMENT_CATEGORIES } from '@/types/platformAnnouncement'

// ── State ────────────────────────────────────────────────────────────────
const items = ref<PlatformAnnouncementResponse[]>([])
const loading = ref(false)
const pagination = ref({ page: 0, size: 10, total: 0 })
const statusFilter = ref('')
const categoryFilter = ref('')
const keyword = ref('')

// ── Dialog ───────────────────────────────────────────────────────────────
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)

const emptyForm = (): PlatformAnnouncementRequest => ({
  title: '',
  content: '',
  status: 'DRAFT',
  category: 'SYSTEM',
  publishAt: null,
  expireAt: null,
})

const form = ref<PlatformAnnouncementRequest>(emptyForm())
const editingId = ref<number | null>(null)

// ── Helpers ──────────────────────────────────────────────────────────────
const categoryLabel = (cat: string) => {
  const map: Record<string, string> = { SYSTEM: '系統', MAINTENANCE: '維護', GENERAL: '一般' }
  return map[cat] ?? cat
}
const categoryTagType = (cat: string): 'danger' | 'warning' | 'info' => {
  if (cat === 'MAINTENANCE') return 'danger'
  if (cat === 'SYSTEM') return 'warning'
  return 'info'
}
const statusLabel = (row: PlatformAnnouncementResponse) => {
  if (row.status === 'DRAFT') return '草稿'
  if (row.status === 'PUBLISHED' && row.expireAt && new Date(row.expireAt) < new Date()) return '已過期'
  if (row.status === 'PUBLISHED') return '已發佈'
  return row.status
}
const statusTagType = (row: PlatformAnnouncementResponse): 'info' | 'success' | 'danger' => {
  if (row.status === 'DRAFT') return 'info'
  if (row.status === 'PUBLISHED' && row.expireAt && new Date(row.expireAt) < new Date()) return 'danger'
  return 'success'
}
const formatDateTime = (raw: string | null | undefined): string => {
  if (!raw) return '—'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  return d.toLocaleString('zh-TW')
}

// ── Data Loading ─────────────────────────────────────────────────────────
onMounted(() => loadData())

watch([statusFilter, categoryFilter], () => {
  pagination.value.page = 0
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await listPlatformAnnouncementsAdmin({
      statusFilter: statusFilter.value || undefined,
      category: categoryFilter.value || undefined,
      keyword: keyword.value || undefined,
      page: pagination.value.page,
      size: pagination.value.size,
    })
    if (res.errorCode === '00000') {
      items.value = res.body.content
      pagination.value.total = res.body.totalElements
    }
  } catch {
    ElMessage.error('載入平台公告失敗')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.value.page = 0
  loadData()
}

function handlePageChange(page: number) {
  pagination.value.page = page - 1
  loadData()
}

function handleSizeChange(size: number) {
  pagination.value.size = size
  pagination.value.page = 0
  loadData()
}

// ── Create / Edit ────────────────────────────────────────────────────────
function openCreate() {
  dialogMode.value = 'create'
  form.value = emptyForm()
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(row: PlatformAnnouncementResponse) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.value = {
    title: row.title,
    content: row.content,
    status: row.status,
    category: row.category,
    publishAt: row.publishAt,
    expireAt: row.expireAt,
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.value.title.trim()) {
    ElMessage.warning('請填寫標題')
    return
  }
  if (!form.value.content.trim()) {
    ElMessage.warning('請填寫內容')
    return
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createPlatformAnnouncement(form.value)
      ElMessage.success('公告已建立')
    } else {
      await updatePlatformAnnouncement(editingId.value!, form.value)
      ElMessage.success('公告已更新')
    }
    dialogVisible.value = false
    await loadData()
  } catch {
    ElMessage.error(dialogMode.value === 'create' ? '建立失敗' : '更新失敗')
  } finally {
    submitting.value = false
  }
}

// ── Delete ───────────────────────────────────────────────────────────────
async function handleDelete(row: PlatformAnnouncementResponse) {
  try {
    await ElMessageBox.confirm(`確定刪除公告「${row.title}」？`, '刪除確認', {
      type: 'warning',
      confirmButtonText: '刪除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deletePlatformAnnouncement(row.id)
    ElMessage.success('公告已刪除')
    await loadData()
  } catch {
    ElMessage.error('刪除失敗')
  }
}
</script>

<template>
  <div class="platform-announcement-manage">
    <header class="page-header">
      <div class="page-header__icon">
        <Megaphone :size="22" />
      </div>
      <div>
        <h1>公告管理</h1>
        <p>發布跨場域公告（如系統維護通知），所有場域使用者皆可看到。</p>
      </div>
    </header>

    <section class="panel">
      <!-- Toolbar -->
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-select v-model="statusFilter" placeholder="狀態" clearable size="default" style="width: 120px">
            <el-option label="全部" value="" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已發佈" value="PUBLISHED" />
            <el-option label="已過期" value="EXPIRED" />
          </el-select>
          <el-select v-model="categoryFilter" placeholder="分類" clearable size="default" style="width: 120px">
            <el-option label="全部" value="" />
            <el-option v-for="cat in PLATFORM_ANNOUNCEMENT_CATEGORIES" :key="cat" :label="categoryLabel(cat)" :value="cat" />
          </el-select>
          <el-input
            v-model="keyword"
            placeholder="搜尋標題或內容"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-button :loading="loading" @click="handleSearch">
            <RefreshCw :size="14" style="margin-right: 4px" />
            搜尋
          </el-button>
        </div>
        <el-button type="primary" @click="openCreate">
          <Plus :size="14" style="margin-right: 4px" />
          新增公告
        </el-button>
      </div>

      <!-- Table -->
      <el-table v-loading="loading" :data="items" style="width: 100%" empty-text="尚無平台公告">
        <el-table-column prop="title" label="標題" min-width="200" show-overflow-tooltip />
        <el-table-column label="分類" width="100">
          <template #default="{ row }">
            <el-tag :type="categoryTagType(row.category)" size="small">{{ categoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="狀態" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row)" size="small">{{ statusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="發佈時間" width="170">
          <template #default="{ row }">{{ formatDateTime(row.publishAt) }}</template>
        </el-table-column>
        <el-table-column label="失效時間" width="170">
          <template #default="{ row }">{{ formatDateTime(row.expireAt) }}</template>
        </el-table-column>
        <el-table-column label="建立者" prop="createdByName" width="120" />
        <el-table-column label="建立時間" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openEdit(row)">編輯</el-button>
            <el-button type="danger" size="small" link @click="handleDelete(row)">刪除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div class="pagination-wrapper" v-if="pagination.total > 0">
        <el-pagination
          v-model:current-page="pagination.page"
          :page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增平台公告' : '編輯平台公告'"
      width="680px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="標題" required>
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="公告標題" />
        </el-form-item>

        <el-form-item label="內容" required>
          <el-input v-model="form.content" type="textarea" :rows="8" maxlength="50000" show-word-limit placeholder="公告內容" />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="分類" style="flex: 1">
            <el-select v-model="form.category" style="width: 100%">
              <el-option v-for="cat in PLATFORM_ANNOUNCEMENT_CATEGORIES" :key="cat" :label="categoryLabel(cat)" :value="cat" />
            </el-select>
          </el-form-item>
          <el-form-item label="狀態" style="flex: 1">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="立即發佈" value="PUBLISHED" />
            </el-select>
          </el-form-item>
        </div>

        <div class="form-row">
          <el-form-item label="發佈時間" style="flex: 1">
            <el-date-picker
              v-model="form.publishAt"
              type="datetime"
              placeholder="留空表示立即發佈"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="失效時間" style="flex: 1">
            <el-date-picker
              v-model="form.expireAt"
              type="datetime"
              placeholder="留空表示永不過期"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ dialogMode === 'create' ? '建立' : '儲存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.platform-announcement-manage {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: flex;
  gap: 16px;
  align-items: center;
}

.page-header__icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--bg-secondary, #1f2937);
  color: var(--accent, #60a5fa);
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-header h1 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary, #f9fafb);
}

.page-header p {
  margin: 4px 0 0;
  color: var(--text-secondary, #9ca3af);
  font-size: 13px;
}

.panel {
  background: var(--bg-elevated, #111827);
  border: 1px solid var(--border-subtle, #1f2937);
  border-radius: 10px;
  padding: 20px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}

.toolbar__filters {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.form-row {
  display: flex;
  gap: 16px;
}

.form-row > * {
  flex: 1;
}
</style>
