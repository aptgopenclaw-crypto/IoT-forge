<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuditStore } from '@/stores/auditStore'
import { exportAuditLogs } from '@/api/audit'
import AuditFilterBar from '@/components/audit/AuditFilterBar.vue'
import AuditTable from '@/components/audit/AuditTable.vue'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import type { AuditFilterModel } from '@/types/audit'
import dayjs from 'dayjs'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Download } from 'lucide-vue-next'

const { t } = useI18n()

const auditStore = useAuditStore()

const currentFilters = ref<AuditFilterModel & { sortBy?: string; sort?: string }>({
  startTimestamp: dayjs().subtract(7, 'day').startOf('day').toISOString(),
  endTimestamp: dayjs().endOf('day').toISOString(),
  sort: 'DESC',
})

function handleSearch(filters: AuditFilterModel) {
  currentFilters.value = { ...filters, sort: currentFilters.value.sort, sortBy: currentFilters.value.sortBy }
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
  <div class="audit-logs-page">
    <AppBreadcrumb />
    <div class="page-header">
      <h1 class="page-title">{{ t('audit.logsTitle') }}</h1>
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

    <AuditTable
      :data="auditStore.usageLogs"
      :total="auditStore.pagination.total"
      :loading="auditStore.loading"
      :page="auditStore.pagination.page"
      :page-size="auditStore.pagination.size"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
      @sort-change="handleSortChange"
    />
  </div>
</template>

<style scoped>
.audit-logs-page {
  padding: 32px 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-heading);
  line-height: 1.15;
  letter-spacing: 0;
  margin: 0;
}

.export-btn {
  display: flex;
  align-items: center;
}
</style>
