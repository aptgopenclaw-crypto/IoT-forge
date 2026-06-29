<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listWorkOrders,
  createWorkOrder,
  getWorkOrder,
  closeWorkOrder,
} from '@/api/device'
import type { WorkOrderRequest, WorkOrderResponse } from '@/types/device'

const { t } = useI18n()

// ── Table ──
const tableData = ref<WorkOrderResponse[]>([])
const loading = ref(false)
const filterStatus = ref('')
const keyword = ref('')
const pagination = reactive({ page: 0, size: 20, total: 0 })

const statusOptions = [
  { value: '', label: t('common.all') },
  { value: 'PENDING', label: '待審核' },
  { value: 'ASSIGNED', label: '已指派' },
  { value: 'IN_PROGRESS', label: '施工中' },
  { value: 'REVIEWING', label: '待驗證' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'REJECTED', label: '已駁回' },
  { value: 'CLOSED', label: '已結案' },
]

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

const statusTagType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'info', ASSIGNED: 'primary', IN_PROGRESS: 'warning',
    REVIEWING: '', COMPLETED: 'success', REJECTED: 'danger', CLOSED: '',
  }
  return map[status] || 'info'
}

// ── Create Dialog ──
const createDialogVisible = ref(false)
const creating = ref(false)

const createForm = reactive<WorkOrderRequest>({
  deviceCode: '',
  deviceId: null,
  orderType: 'REPAIR',
  sourceType: 'CITIZEN',
  priority: 'NORMAL',
  reporterName: '',
  reporterContact: '',
  description: '',
})

function openCreate() {
  createForm.deviceCode = ''
  createForm.deviceId = null
  createForm.orderType = 'REPAIR'
  createForm.sourceType = 'CITIZEN'
  createForm.priority = 'NORMAL'
  createForm.reporterName = ''
  createForm.reporterContact = ''
  createForm.description = ''
  createDialogVisible.value = true
}

async function handleCreate() {
  creating.value = true
  try {
    await createWorkOrder(createForm)
    ElMessage.success('工單已建立')
    createDialogVisible.value = false
    fetchList()
  } finally {
    creating.value = false
  }
}

// ── Detail Drawer ──
const detailDrawerVisible = ref(false)
const detailLoading = ref(false)
const detailDevice = ref<WorkOrderResponse | null>(null)

async function openDetail(row: WorkOrderResponse) {
  detailLoading.value = true
  detailDrawerVisible.value = true
  try {
    const res = await getWorkOrder(row.id)
    if (res.errorCode === '00000') {
      detailDevice.value = res.body
    }
  } finally {
    detailLoading.value = false
  }
}

// ── Close ──
async function handleClose(row: WorkOrderResponse) {
  try {
    await ElMessageBox.confirm('確定結案此工單？', '確認', {
      confirmButtonText: '確定', cancelButtonText: '取消', type: 'warning',
    })
    await closeWorkOrder(row.id, 'system')
    ElMessage.success('已結案')
    fetchList()
  } catch { /* cancelled */ }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>所有工單</h2>
        <p class="page-subtitle">檢視全部派工單</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate">+ 新增工單</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filterStatus" placeholder="工單狀態" clearable @change="handleSearch">
        <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜尋描述/通報人" clearable @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜尋</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="deviceCode" label="設備代碼" width="120" />
      <el-table-column prop="deviceName" label="設備名稱" min-width="160" />
      <el-table-column prop="orderType" label="類型" width="90" />
      <el-table-column label="狀態" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="優先級" width="90">
        <template #default="{ row }">{{ row.priority === 'NORMAL' ? '一般' : row.priority === 'URGENT' ? '緊急' : row.priority === 'EMERGENCY' ? '特急' : row.priority }}</template>
      </el-table-column>
      <el-table-column prop="assignedToName" label="指派給" width="120" />
      <el-table-column label="通報時間" width="170">
        <template #default="{ row }">{{ row.reportedAt ? new Date(row.reportedAt).toLocaleString() : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="180">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row)">檢視</el-button>
          <el-button v-if="row.status === 'COMPLETED' || row.status === 'REJECTED'" link type="primary" size="small" @click="handleClose(row)">結案</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        :current-page="pagination.page + 1"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="createDialogVisible" title="新增工單" width="500px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="設備代碼" required>
          <el-input v-model="createForm.deviceCode" placeholder="請輸入設備代碼，如 SL-001" maxlength="100" />
        </el-form-item>
        <el-form-item label="工單類型" required>
          <el-select v-model="createForm.orderType" style="width: 100%">
            <el-option label="維修" value="REPAIR" />
            <el-option label="巡檢" value="PATROL" />
            <el-option label="安裝" value="INSTALL" />
            <el-option label="拆除" value="REMOVAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="通報來源" required>
          <el-select v-model="createForm.sourceType" style="width: 100%">
            <el-option label="民眾通報" value="CITIZEN" />
            <el-option label="系統自動" value="AUTO" />
            <el-option label="巡檢發現" value="PATROL" />
            <el-option label="內部通報" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item label="優先級">
          <el-select v-model="createForm.priority" style="width: 100%">
            <el-option label="一般" value="NORMAL" />
            <el-option label="緊急" value="URGENT" />
            <el-option label="特急" value="EMERGENCY" />
          </el-select>
        </el-form-item>
        <el-form-item label="通報人">
          <el-input v-model="createForm.reporterName" maxlength="100" />
        </el-form-item>
        <el-form-item label="通報人聯絡方式">
          <el-input v-model="createForm.reporterContact" maxlength="100" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">建立</el-button>
      </template>
    </el-dialog>

    <!-- Detail Drawer -->
    <el-drawer v-model="detailDrawerVisible" title="工單明細" size="500px" v-loading="detailLoading">
      <template v-if="detailDevice">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID" :span="2">{{ detailDevice.id }}</el-descriptions-item>
          <el-descriptions-item label="設備代碼">{{ detailDevice.deviceCode || detailDevice.deviceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="設備名稱">{{ detailDevice.deviceName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工單類型">{{ detailDevice.orderType }}</el-descriptions-item>
          <el-descriptions-item label="狀態" :span="2">
            <el-tag :type="statusTagType(detailDevice.status)" size="small">{{ detailDevice.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="優先級">{{ detailDevice.priority === 'NORMAL' ? '一般' : detailDevice.priority === 'URGENT' ? '緊急' : detailDevice.priority === 'EMERGENCY' ? '特急' : detailDevice.priority }}</el-descriptions-item>
          <el-descriptions-item label="通報來源">{{ detailDevice.sourceType }}</el-descriptions-item>
          <el-descriptions-item label="通報人">{{ detailDevice.reporterName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="聯絡方式">{{ detailDevice.reporterContact || '-' }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ detailDevice.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="指派給">{{ detailDevice.assignedToName || detailDevice.assignedTo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="指派的時間">{{ detailDevice.assignedAt ? new Date(detailDevice.assignedAt).toLocaleString() : '-' }}</el-descriptions-item>
          <el-descriptions-item label="維修完成備註" :span="2">{{ detailDevice.completionRemark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="故障原因">{{ detailDevice.faultCause || '-' }}</el-descriptions-item>
          <el-descriptions-item label="維修費用">{{ detailDevice.repairCost != null ? '$' + detailDevice.repairCost : '-' }}</el-descriptions-item>
          <el-descriptions-item label="審核者">{{ detailDevice.reviewerId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="駁回原因">{{ detailDevice.rejectReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="通報時間" :span="2">{{ detailDevice.reportedAt ? new Date(detailDevice.reportedAt).toLocaleString() : '-' }}</el-descriptions-item>
          <el-descriptions-item label="建立時間" :span="2">{{ detailDevice.createdAt ? new Date(detailDevice.createdAt).toLocaleString() : '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新時間" :span="2">{{ detailDevice.updatedAt ? new Date(detailDevice.updatedAt).toLocaleString() : '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.header-actions { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-bar .el-select { width: 160px; }
.filter-bar .el-input { width: 240px; }
</style>
