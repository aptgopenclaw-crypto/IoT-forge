<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuditStore } from '@/stores/auditStore'
import { exportAuditLogs } from '@/api/audit'
import AuditFilterBar from '@/components/audit/AuditFilterBar.vue'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import { formatDateTime } from '@/utils/datetime'
import type { AuditFilterModel } from '@/types/audit'
import dayjs from 'dayjs'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ClipboardList, Download } from 'lucide-vue-next'

const { t } = useI18n()

const auditStore = useAuditStore()

const currentFilters = ref<AuditFilterModel & { sortBy?: string; sort?: string }>({
  startTimestamp: dayjs().subtract(7, 'day').startOf('day').toISOString(),
  endTimestamp: dayjs().endOf('day').toISOString(),
  sort: 'DESC',
})

function handleSearch(filters: AuditFilterModel) {
  currentFilters.value = {
    ...filters,
    sort: currentFilters.value.sort,
    sortBy: currentFilters.value.sortBy,
  }
  auditStore.pagination.page = 0
  auditStore.fetchUsageLogs(currentFilters.value)
}

function handleReset() {
  currentFilters.value = {
    startTimestamp: dayjs().subtract(7, 'day').startOf('day').toISOString(),
    endTimestamp: dayjs().endOf('day').toISOString(),
    sort: 'DESC',
  }
  auditStore.pagination.page = 0
  auditStore.fetchUsageLogs(currentFilters.value)
}

function handlePageChange(page: number) {
  auditStore.pagination.page = page - 1
  auditStore.fetchUsageLogs(currentFilters.value)
}

function handleSizeChange(size: number) {
  auditStore.pagination.size = size
  auditStore.pagination.page = 0
  auditStore.fetchUsageLogs(currentFilters.value)
}

function handleSortChange(sortProps: { prop: string; order: string | null }) {
  if (sortProps.order) {
    currentFilters.value.sortBy = sortProps.prop
    currentFilters.value.sort = sortProps.order === 'ascending' ? 'ASC' : 'DESC'
  } else {
    currentFilters.value.sortBy = undefined
    currentFilters.value.sort = 'DESC'
  }
  auditStore.pagination.page = 0
  auditStore.fetchUsageLogs(currentFilters.value)
}

onMounted(() => {
  auditStore.fetchUsageLogs(currentFilters.value)
})

// ── Export ──
const exporting = ref(false)

async function handleExport(format: 'csv' | 'xlsx') {
  exporting.value = true
  try {
    const res = await exportAuditLogs({
      format,
      userName: currentFilters.value.userName,
      eventDesc: currentFilters.value.eventDesc,
      startTimestamp: currentFilters.value.startTimestamp,
      endTimestamp: currentFilters.value.endTimestamp,
    })
    const blob = new Blob([res as unknown as BlobPart])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `audit-logs.${format}`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(t('audit.exportSuccess'))
  } catch {
    ElMessage.error(t('audit.exportFailed'))
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <AppBreadcrumb />
    <div class="page-card">
      <div class="page-header">
        <div class="header-left">
          <ClipboardList :size="24" class="header-icon" />
          <div>
            <h1 class="page-title">{{ t('audit.logsTitle') }}</h1>
            <p class="page-subtitle">{{ t('audit.subtitle') }}</p>
          </div>
        </div>
        <el-dropdown @command="handleExport" :disabled="exporting">
          <el-button class="export-btn" :loading="exporting">
            <Download :size="16" style="margin-right: 6px" />
            {{ t('audit.exportBtn') }}
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="csv">CSV</el-dropdown-item>
              <el-dropdown-item command="xlsx">XLSX (Excel)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <AuditFilterBar
        :show-user-name="true"
        :show-event-desc="true"
        @search="handleSearch"
        @reset="handleReset"
      />

      <div class="table-wrapper">
        <el-table
          :data="auditStore.usageLogs"
          v-loading="auditStore.loading"
          row-key="userEventLogPk"
          class="history-table"
          @sort-change="handleSortChange"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand-detail">
                <div v-if="row.payload" class="detail-row">
                  <span class="detail-label">{{ t('audit.colPayload') }}：</span>
                  <span class="detail-value payload-text">{{ row.payload }}</span>
                </div>
                <div v-if="row.message" class="detail-row">
                  <span class="detail-label">{{ t('audit.colMessage') }}：</span>
                  <span class="detail-value">{{ row.message }}</span>
                </div>
                <div v-if="row.userAgent" class="detail-row">
                  <span class="detail-label">{{ t('audit.colUserAgent') }}：</span>
                  <span class="detail-value">{{ row.userAgent }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="username" :label="t('audit.colAccount')" min-width="140" show-overflow-tooltip />
          <el-table-column prop="userLabel" :label="t('audit.colUser')" min-width="100" show-overflow-tooltip />
          <el-table-column prop="eventDesc" :label="t('audit.colEventDesc')" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="category-badge" :class="'badge-' + (row.eventDesc || '').toLowerCase()">
                {{ row.eventDesc }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="eventType" :label="t('audit.colEventType')" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="event-type-text">{{ row.eventType }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="apiEndpoint" :label="t('audit.colApi')" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="api-text">{{ row.apiEndpoint }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="errorCode" :label="t('audit.colResultCode')" width="100" align="center">
            <template #default="{ row }">
              <span :class="row.errorCode === '00000' ? 'code-success' : 'code-error'">
                {{ row.errorCode }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="executionTime" :label="t('audit.colExecTime')" width="100" align="right">
            <template #default="{ row }">
              <span class="time-num">{{ row.executionTime }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="ipAddress" :label="t('audit.colIp')" width="140" show-overflow-tooltip />
          <el-table-column prop="impersonatedBy" :label="t('audit.colImpersonator')" width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.impersonatedBy" class="impersonator-cell">
                <el-tag type="warning" size="small" effect="dark">{{ t('audit.impersonatedBadge') }}</el-tag>
                <span class="impersonator-id">{{ row.impersonatedBy }}</span>
              </span>
              <span v-else class="impersonator-empty">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" :label="t('audit.colTime')" min-width="170" sortable="custom">
            <template #default="{ row }">
              <span class="time-text">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            :current-page="auditStore.pagination.page + 1"
            :page-size="auditStore.pagination.size"
            :page-sizes="[20, 50, 100]"
            :total="auditStore.pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>

        <div v-if="!auditStore.loading && auditStore.usageLogs.length === 0" class="empty-state">
          {{ t('audit.noData') }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  padding: 32px 24px;
  min-height: 100vh;
  background-color: var(--bg-base);
}

.page-card {
  background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 24px;
  box-shadow: var(--shadow-card);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.export-btn {
  display: flex;
  align-items: center;
}

.header-icon {
  color: #55b3ff;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: 0.2px;
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

/* Table */
.table-wrapper {
  margin-top: 4px;
}

.history-table {
  --el-table-bg-color: var(--bg-surface);
  --el-table-tr-bg-color: var(--bg-surface);
  --el-table-header-bg-color: var(--bg-surface);
  --el-table-header-text-color: var(--text-secondary);
  --el-table-text-color: var(--text-primary);
  --el-table-border-color: var(--bg-active);
  --el-table-row-hover-bg-color: var(--bg-hover-subtle);
  --el-fill-color-lighter: var(--bg-surface);
}

/* Category badges */
.category-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.badge-user_auth {
  background: rgba(85, 179, 255, 0.15);
  color: #55b3ff;
}

.badge-account {
  background: rgba(182, 130, 255, 0.15);
  color: #b682ff;
}

.badge-system {
  background: rgba(255, 183, 77, 0.15);
  color: #ffb74d;
}

.event-type-text {
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.2px;
}

.api-text {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  color: var(--text-secondary);
}

.code-success {
  color: #5fc992;
  font-size: 13px;
  font-weight: 600;
}

.code-error {
  color: #FF6363;
  font-size: 13px;
  font-weight: 600;
}

.time-num {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: var(--text-secondary);
}

.time-text {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
  letter-spacing: 0.2px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.empty-state {
  text-align: center;
  padding: 40px 0;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.2px;
}

/* Expand row */
:deep(.el-table__expand-icon) {
  color: var(--text-secondary);
}

.expand-detail {
  padding: 12px 16px;
}

.detail-row {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.detail-row:last-child {
  margin-bottom: 0;
}

.detail-label {
  font-weight: 600;
  color: var(--text-heading);
}

.detail-value {
  word-break: break-all;
}

.payload-text {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
}

:deep(.el-table td.el-table__cell) {
  font-size: 13px;
}
</style>
