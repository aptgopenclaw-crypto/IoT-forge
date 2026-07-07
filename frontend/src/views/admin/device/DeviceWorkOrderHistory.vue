<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { listWorkOrders, closeWorkOrder } from '@/api/device'
import type { WorkOrderResponse } from '@/types/device'

const props = defineProps<{ deviceId: number }>()

const { t } = useI18n()

const data = ref<WorkOrderResponse[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(0)
const PAGE_SIZE = 10

async function fetchList() {
  loading.value = true
  try {
    const res = await listWorkOrders({
      deviceId: props.deviceId,
      page: page.value,
      size: PAGE_SIZE,
    })
    if (res.errorCode === '00000') {
      data.value = res.body.content
      total.value = res.body.totalElements
    }
  } finally {
    loading.value = false
  }
}

async function handleClose(row: WorkOrderResponse) {
  try {
    await ElMessageBox.confirm(t('workOrder.closeConfirm'), t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await closeWorkOrder(row.id, 'system')
    ElMessage.success(t('workOrder.closedSuccess'))
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

// reload when deviceId changes (user opens a different device)
watch(() => props.deviceId, () => {
  page.value = 0
  fetchList()
})

onMounted(fetchList)
</script>

<template>
  <div v-loading="loading">
    <el-table :data="data" size="small" stripe>
      <el-table-column prop="id" :label="t('workOrder.colId')" width="60" />
      <el-table-column prop="orderType" :label="t('workOrder.colType')" width="90" />
      <el-table-column :label="t('workOrder.colStatus')" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ t(`workOrder.status${row.status.charAt(0) + row.status.slice(1).toLowerCase().replace(/_([a-z])/g, (_: string, c: string) => c.toUpperCase())}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reporterName" :label="t('workOrder.colReporter')" width="90" />
      <el-table-column :label="t('workOrder.colReportedAt')" min-width="140">
        <template #default="{ row }">
          {{ row.reportedAt ? new Date(row.reportedAt).toLocaleString() : '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="80" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'COMPLETED' || row.status === 'REJECTED'"
            link
            type="primary"
            size="small"
            @click="handleClose(row)"
          >
            {{ t('workOrder.closeBtn') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="total > PAGE_SIZE" style="margin-top: 12px; text-align: right">
      <el-pagination
        v-model:current-page="page"
        :page-size="PAGE_SIZE"
        :total="total"
        layout="total, prev, pager, next"
        small
        @current-change="(p) => { page = p - 1; fetchList() }"
      />
    </div>

    <el-empty v-if="!loading && data.length === 0" :description="t('workOrder.noRecords')" :image-size="60" />
  </div>
</template>
