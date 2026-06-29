<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listWorkOrders, closeWorkOrder } from '@/api/device'
import type { WorkOrderResponse } from '@/types/device'

const { t } = useI18n()

// ── Data ──
const tableData = ref<WorkOrderResponse[]>([])
const loading = ref(false)
const filterStatus = ref('')
const keyword = ref('')
const pagination = reactive({ page: 0, size: 20, total: 0 })

const statusOptions = [
  { value: '', label: t('common.all') },
  { value: 'PENDING', label: t('workOrder.statusPending') },
  { value: 'ASSIGNED', label: t('workOrder.statusAssigned') },
  { value: 'IN_PROGRESS', label: t('workOrder.statusInProgress') },
  { value: 'REVIEWING', label: t('workOrder.statusReviewing') },
  { value: 'COMPLETED', label: t('workOrder.statusCompleted') },
  { value: 'REJECTED', label: t('workOrder.statusRejected') },
  { value: 'CLOSED', label: t('workOrder.statusClosed') },
]

// ── Fetch ──
async function fetchList() {
  loading.value = true
  try {
    const res = await listWorkOrders({
      status: filterStatus.value || undefined,
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

function handleSearch() {
  pagination.page = 0
  fetchList()
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

async function handleClose(row: WorkOrderResponse) {
  try {
    await ElMessageBox.confirm('Close this work order?', 'Confirm', {
      confirmButtonText: 'Confirm',
      cancelButtonText: 'Cancel',
      type: 'warning',
    })
    await closeWorkOrder(row.id, 'system')
    ElMessage.success('Closed')
    fetchList()
  } catch {
    // cancelled
  }
}

const statusTagType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'info',
    ASSIGNED: 'primary',
    IN_PROGRESS: 'warning',
    REVIEWING: '',
    COMPLETED: 'success',
    REJECTED: 'danger',
    CLOSED: '',
  }
  return map[status] || 'info'
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>{{ t('workOrder.title') }}</h2>
        <p class="page-subtitle">{{ t('workOrder.subtitle') }}</p>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filterStatus" :placeholder="t('workOrder.filterStatus')" clearable @change="handleSearch">
        <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-input v-model="keyword" :placeholder="t('workOrder.searchPlaceholder')" clearable @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-button type="primary" @click="handleSearch">{{ t('workOrder.searchBtn') }}</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column :label="t('workOrder.colId')" prop="id" width="70" />
      <el-table-column :label="t('workOrder.colType')" prop="orderType" width="100" />
      <el-table-column :label="t('workOrder.colDevice')" prop="deviceCode" width="140" />
      <el-table-column :label="t('workOrder.colStatus')" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('workOrder.colPriority')" prop="priority" width="80" />
      <el-table-column :label="t('workOrder.colReporter')" prop="reporterName" width="120" />
      <el-table-column :label="t('workOrder.colReportedAt')" width="170">
        <template #default="{ row }">
          {{ row.reportedAt ? new Date(row.reportedAt).toLocaleString() : '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('workOrder.colAssignedTo')" prop="assignedTo" width="120" />
      <el-table-column :label="t('common.actions')" fixed="right" width="150">
        <template #default="{ row }">
          <el-button link type="primary" size="small">{{ t('workOrder.viewBtn') }}</el-button>
          <el-button v-if="row.status === 'COMPLETED' || row.status === 'REJECTED'" link type="primary" size="small" @click="handleClose(row)">{{ t('workOrder.closeBtn') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-bar .el-select {
  width: 160px;
}
.filter-bar .el-input {
  width: 240px;
}
</style>
