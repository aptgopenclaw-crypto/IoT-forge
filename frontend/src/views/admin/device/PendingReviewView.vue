<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listWorkOrders, approveWorkOrder, rejectWorkOrder } from '@/api/device'
import type { WorkOrderResponse } from '@/types/device'

const tableData = ref<WorkOrderResponse[]>([])
const loading = ref(false)
const pagination = reactive({ page: 0, size: 20, total: 0 })

const rejectDialogVisible = ref(false)
const rejectTargetId = ref<number | null>(null)
const rejectReason = ref('')

async function fetchList(page = 0) {
  loading.value = true
  try {
    const res = await listWorkOrders({ status: 'REVIEWING', page, size: pagination.size })
    if (res.errorCode === '00000') {
      tableData.value = res.body.content
      pagination.total = res.body.totalElements
      pagination.page = page
    }
  } finally {
    loading.value = false
  }
}

async function handleApprove(row: WorkOrderResponse) {
  try {
    await ElMessageBox.confirm('核准此工單？', '確認', { type: 'info' })
    await approveWorkOrder(row.id, 'system')
    ElMessage.success('已核准')
    fetchList(pagination.page)
  } catch { /* cancelled */ }
}

function openReject(row: WorkOrderResponse) {
  rejectTargetId.value = row.id
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

async function handleReject() {
  if (!rejectTargetId.value || !rejectReason.value.trim()) return
  try {
    await rejectWorkOrder(rejectTargetId.value, 'system', rejectReason.value.trim())
    ElMessage.success('已駁回')
    rejectDialogVisible.value = false
    fetchList(pagination.page)
  } catch { /* error */ }
}

onMounted(() => fetchList())
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>待驗證</h2>
        <p class="page-subtitle">施工完成，等待驗證的工單</p>
      </div>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="deviceCode" label="設備代碼" width="120" />
      <el-table-column prop="deviceName" label="設備名稱" min-width="160" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="assignedToName" label="施工人員" width="120" />
      <el-table-column label="完成時間" width="170">
        <template #default="{ row }">{{ row.completedAt ? new Date(row.completedAt).toLocaleString() : '-' }}</template>
      </el-table-column>
      <el-table-column prop="faultCause" label="故障原因" width="130" show-overflow-tooltip />
      <el-table-column label="維修費用" width="100">
        <template #default="{ row }">{{ row.repairCost != null ? '$' + row.repairCost : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="180">
        <template #default="{ row }">
          <el-button type="success" size="small" @click="handleApprove(row)">核准</el-button>
          <el-button type="danger" size="small" @click="openReject(row)">駁回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        :current-page="pagination.page + 1"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="(p) => fetchList(p - 1)"
        @size-change="(s) => { pagination.size = s; fetchList(0) }"
      />
    </div>

    <el-dialog v-model="rejectDialogVisible" title="駁回工單" width="400px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="駁回原因" required>
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="請填寫駁回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :disabled="!rejectReason.trim()" @click="handleReject">確認駁回</el-button>
      </template>
    </el-dialog>
  </div>
</template>