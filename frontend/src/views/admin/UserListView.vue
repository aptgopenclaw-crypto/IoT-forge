<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/userStore'
import { useAuthStore } from '@/stores/authStore'
import { disableUser, softDeleteUser } from '@/api/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Pencil, Ban, Trash2 } from 'lucide-vue-next'
import type { UserListItemDto } from '@/types/user'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const router = useRouter()
const userStore = useUserStore()
const authStore = useAuthStore()
const loading = ref(false)
const initialLoading = ref(true)
const keyword = ref('')
const pageSize = ref(20)

const currentUserId = computed(() => authStore.userInfo?.userId ?? '')

onMounted(async () => {
  await loadUsers()
  initialLoading.value = false
})

async function loadUsers(page = 0) {
  loading.value = true
  try {
    await userStore.fetchUserList({
      page,
      size: pageSize.value,
      keyword: keyword.value || undefined,
    })
  } catch {
    ElMessage.error(t('user.list.loadFailed'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadUsers(0)
}

function handlePageChange(page: number) {
  loadUsers(page - 1)
}

function handleSizeChange(size: number) {
  pageSize.value = size
  loadUsers(0)
}

function goCreate() {
  router.push('/admin/users/create')
}

function goEdit(row: UserListItemDto) {
  router.push(`/admin/users/${row.userId}/edit`)
}

async function handleDisable(row: UserListItemDto) {
  try {
    await ElMessageBox.confirm(
      t('user.list.disableConfirm', { name: row.displayName || row.email }),
      t('user.list.disableTitle'),
      {
        confirmButtonText: t('user.list.confirmDisable'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )
  } catch {
    return
  }

  // Optimistic update: mark disabled immediately
  const previousEnabled = row.enabled
  row.enabled = false
  try {
    await disableUser(row.userId)
    ElMessage.success(t('user.list.disabledSuccess'))
  } catch (err: unknown) {
    // Rollback on failure
    row.enabled = previousEnabled
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    if (errorCode === '20005') {
      ElMessage.error(t('user.list.userNotFound'))
    } else if (errorCode === '10010') {
      ElMessage.error(t('common.noPermission'))
    } else {
      ElMessage.error(t('user.list.disableFailed'))
    }
  }
}

async function handleSoftDelete(row: UserListItemDto) {
  try {
    await ElMessageBox.confirm(
      t('user.list.deleteConfirm', { name: row.displayName || row.email }),
      t('user.list.deleteTitle'),
      {
        confirmButtonText: t('user.list.confirmDelete'),
        cancelButtonText: t('common.cancel'),
        type: 'error',
      },
    )
  } catch {
    return
  }

  // Optimistic update: remove from list immediately
  const idx = userStore.userList.indexOf(row)
  if (idx !== -1) userStore.userList.splice(idx, 1)
  try {
    await softDeleteUser(row.userId)
    ElMessage.success(t('user.list.deletedSuccess'))
  } catch (err: unknown) {
    // Rollback on failure
    if (idx !== -1) userStore.userList.splice(idx, 0, row)
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    if (errorCode === '20005') {
      ElMessage.error(t('user.list.userNotFound'))
    } else if (errorCode === '10010') {
      ElMessage.error(t('common.noPermission'))
    } else {
      ElMessage.error(t('user.list.deleteFailed'))
    }
  }
}

function getStatusType(row: UserListItemDto) {
  if (!row.enabled) return 'danger'
  if (row.locked) return 'warning'
  return 'success'
}

function getStatusLabel(row: UserListItemDto) {
  if (!row.enabled) return t('user.list.statusDisabled')
  if (row.locked) return t('user.list.statusLocked')
  return t('user.list.statusEnabled')
}
</script>

<template>
  <div class="page-container">
    <div class="page-content">
      <div class="page-header">
        <div>
          <h1 class="page-title">{{ t('user.list.title') }}</h1>
          <p class="page-subtitle">{{ t('user.list.subtitle') }}</p>
        </div>
        <el-button class="create-btn" @click="goCreate">
          <Plus :size="16" style="margin-right: 6px" />
          {{ t('user.list.addBtn') }}
        </el-button>
      </div>

      <div class="search-bar">
        <el-input
          v-model="keyword"
          :placeholder="t('user.list.searchPlaceholder')"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <Search :size="16" class="input-icon" />
          </template>
        </el-input>
        <el-button class="search-btn" @click="handleSearch">{{ t('common.search') }}</el-button>
      </div>

      <el-skeleton :rows="8" :loading="initialLoading" animated>
        <template #default>
          <div class="table-card" v-loading="loading">
        <el-table
          :data="userStore.userList"
          style="width: 100%"
          row-class-name="table-row"
        >
          <el-table-column prop="email" :label="t('user.list.colEmail')" min-width="200" />
          <el-table-column prop="displayName" :label="t('user.list.colDisplayName')" min-width="120" />
          <el-table-column prop="roleName" :label="t('user.list.colRole')" min-width="100" />
          <el-table-column prop="deptName" :label="t('user.list.colDept')" min-width="120">
            <template #default="{ row }">
              {{ row.deptName || '-' }}
            </template>
          </el-table-column>
          <el-table-column :label="t('user.list.colStatus')" width="100">
            <template #default="{ row }">
              <span class="status-badge" :class="'status-' + getStatusType(row)">
                {{ getStatusLabel(row) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column :label="t('user.list.colActions')" width="160" fixed="right">
            <template #default="{ row }">
              <div class="action-group">
                <el-button class="action-btn" size="small" @click="goEdit(row)">
                  <Pencil :size="14" />
                </el-button>
                <el-button
                  v-if="row.enabled && row.userId !== currentUserId"
                  class="action-btn action-btn-danger"
                  size="small"
                  @click="handleDisable(row)"
                >
                  <Ban :size="14" />
                </el-button>
                <el-button
                  v-if="row.userId !== currentUserId"
                  class="action-btn action-btn-danger"
                  size="small"
                  @click="handleSoftDelete(row)"
                >
                  <Trash2 :size="14" />
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-row" v-if="userStore.pagination.totalElements > 0">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="userStore.pagination.totalElements"
            :page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :current-page="userStore.pagination.page + 1"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
        </template>
      </el-skeleton>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  padding: 32px 24px;
  min-height: 100vh;
  background-color: var(--bg-base);
}

.page-content {
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.15;
  color: var(--text-heading);
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.6;
  letter-spacing: 0.2px;
  color: var(--text-secondary);
  margin: 0;
}

.create-btn {
  background: var(--btn-primary-bg);
  color: var(--btn-primary-text);
  border: none;
  border-radius: 86px;
  padding: 8px 24px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
  transition: opacity 150ms ease;
  display: flex;
  align-items: center;
}

.create-btn:hover {
  background: var(--btn-primary-hover);
  color: var(--btn-primary-text);
}

.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  max-width: 400px;
}

.search-btn {
  background: transparent;
  color: var(--text-primary);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
  box-shadow: rgba(0, 0, 0, 0.03) 0px 7px 3px;
}

.search-btn:hover {
  opacity: 0.6;
}

.input-icon {
  color: var(--text-muted);
}

.table-card {
  background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 0;
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.status-success {
  background: rgba(95, 201, 146, 0.15);
  color: #5fc992;
}

.status-danger {
  background: rgba(255, 99, 99, 0.15);
  color: #FF6363;
}

.status-warning {
  background: rgba(255, 188, 51, 0.15);
  color: #ffbc33;
}

.action-group {
  display: flex;
  gap: 4px;
}

.action-btn {
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  padding: 4px 8px;
  min-width: auto;
}

.action-btn:hover {
  opacity: 0.6;
  color: var(--text-primary);
}

.action-btn-danger {
  color: #FF6363;
  border-color: rgba(255, 99, 99, 0.2);
}

.action-btn-danger:hover {
  color: #FF6363;
  background: rgba(255, 99, 99, 0.15);
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding: 16px 20px;
  border-top: 1px solid var(--bg-active);
}

/* Element Plus dark overrides */
:deep(.el-table) {
  --el-table-bg-color: var(--bg-surface);
  --el-table-tr-bg-color: var(--bg-surface);
  --el-table-header-bg-color: var(--bg-surface);
  --el-table-row-hover-bg-color: var(--bg-hover-subtle);
  --el-table-text-color: var(--text-primary);
  --el-table-header-text-color: var(--text-secondary);
  --el-table-border-color: var(--bg-active);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.2px;
}

:deep(.el-pagination) {
  --el-pagination-bg-color: transparent;
  --el-pagination-text-color: var(--text-secondary);
  --el-pagination-button-bg-color: transparent;
  --el-pagination-hover-color: #55b3ff;
}
</style>
