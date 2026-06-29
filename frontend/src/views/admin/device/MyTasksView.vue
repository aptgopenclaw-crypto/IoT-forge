<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  myTasksWorkOrders,
  startWorkOrder,
  completeWorkOrder,
} from '@/api/device'
import type { WorkOrderResponse } from '@/types/device'

// ── Table ──
const tableData = ref<WorkOrderResponse[]>([])
const loading = ref(false)
const pagination = reactive({ page: 0, size: 20, total: 0 })

async function fetchList(page = 0) {
  loading.value = true
  try {
    const res = await myTasksWorkOrders({ page, size: pagination.size })
    if (res.errorCode === '00000') {
      tableData.value = res.body.content
      pagination.total = res.body.totalElements
      pagination.page = page
    }
  } finally {
    loading.value = false
  }
}

// ── Start Work Dialog ──
const startDialogVisible = ref(false)
const startTargetId = ref<number | null>(null)
const latitude = ref<number | null>(null)
const longitude = ref<number | null>(null)

function openStart(row: WorkOrderResponse) {
  startTargetId.value = row.id
  latitude.value = null
  longitude.value = null
  startDialogVisible.value = true
}

async function handleStart() {
  if (!startTargetId.value) return
  try {
    await startWorkOrder(startTargetId.value, latitude.value, longitude.value)
    ElMessage.success('已打卡到場')
    startDialogVisible.value = false
    fetchList(pagination.page)
  } catch { /* error */ }
}

// ── Complete Dialog ──
const completeDialogVisible = ref(false)
const completeTargetId = ref<number | null>(null)
const remark = ref('')
const faultCause = ref('')
const repairCost = ref<number | null>(null)

function openComplete(row: WorkOrderResponse) {
  completeTargetId.value = row.id
  remark.value = ''
  faultCause.value = ''
  repairCost.value = null
  completeDialogVisible.value = true
}

async function handleComplete() {
  if (!completeTargetId.value) return
  try {
    await completeWorkOrder(completeTargetId.value, {
      remark: remark.value,
      faultCause: faultCause.value,
      repairCost: repairCost.value ?? undefined,
    })
    ElMessage.success('維修完成，已送審')
    completeDialogVisible.value = false
    fetchList(pagination.page)
  } catch { /* error */ }
}

onMounted(() => fetchList())
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>施工任務</h2>
        <p class="page-subtitle">所有待施工工單 — 點選「到場打卡」即接手任務</p>
      </div>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="deviceCode" label="設備代碼" width="120" />
      <el-table-column prop="deviceName" label="設備名稱" min-width="160" />
      <el-table-column prop="orderType" label="類型" width="90" />
      <el-table-column label="狀態" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ASSIGNED' ? 'primary' : 'warning'" size="small">
            {{ row.status === 'ASSIGNED' ? '待施工' : '施工中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column label="指派對象" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.assignedTo" size="small">{{ row.assignedToName || row.assignedTo }}</el-tag>
          <span v-else class="text-secondary">待接手</span>
        </template>
      </el-table-column>
      <el-table-column label="指派的時間" width="160">
        <template #default="{ row }">{{ row.assignedAt ? new Date(row.assignedAt).toLocaleString() : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="220">
        <template #default="{ row }">
          <el-button v-if="row.status === 'ASSIGNED'" type="primary" size="small" @click="openStart(row)">到場打卡</el-button>
          <el-button v-if="row.status === 'IN_PROGRESS'" type="success" size="small" @click="openComplete(row)">完成維修</el-button>
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

    <!-- Start Work Dialog -->
    <el-dialog v-model="startDialogVisible" title="到場打卡" width="400px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="緯度">
          <el-input-number v-model="latitude" :precision="7" :step="0.001" style="width: 100%" />
        </el-form-item>
        <el-form-item label="經度">
          <el-input-number v-model="longitude" :precision="7" :step="0.001" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleStart">確認到場</el-button>
      </template>
    </el-dialog>

    <!-- Complete Work Dialog -->
    <el-dialog v-model="completeDialogVisible" title="完成維修" width="500px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="維修備註" required>
          <el-input v-model="remark" type="textarea" :rows="3" placeholder="請填寫維修內容" />
        </el-form-item>
        <el-form-item label="故障原因">
          <el-input v-model="faultCause" placeholder="例如：燈泡燒毀、線路斷裂" />
        </el-form-item>
        <el-form-item label="維修費用">
          <el-input-number v-model="repairCost" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialogVisible = false">取消</el-button>
        <el-button type="success" :disabled="!remark.trim()" @click="handleComplete">完成並送審</el-button>
      </template>
    </el-dialog>
  </div>
</template>
