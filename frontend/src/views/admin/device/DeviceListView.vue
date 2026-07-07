<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import ImportDialog from './ImportDialog.vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listDevices,
  getDevice,
  createDevice,
  updateDevice,
  deleteDevice,
  decommissionDevice,
} from '@/api/device'
import { listDeviceTypeNames } from '@/api/schema'
import type { DeviceRequest, DeviceResponse } from '@/types/device'

const { t } = useI18n()

// ── Data ──
const tableData = ref<DeviceResponse[]>([])
const loading = ref(false)
const filterDeviceType = ref('')
const filterStatus = ref('')
const keyword = ref('')
const pagination = reactive({ page: 0, size: 20, total: 0 })
// ── Device type options ──
// Dynamically loaded from backend DeviceTemplate list
const deviceTypeOptions = ref<{ value: string; label: string }[]>([
  { value: '', label: t('common.all') },
])

async function loadDeviceTypeOptions() {
  try {
    const res = await listDeviceTypeNames()
    if (res.errorCode === '00000' && res.body) {
      const types = res.body.map((dt: string) => ({ value: dt, label: dt }))
      deviceTypeOptions.value = [{ value: '', label: t('common.all') }, ...types]
    }
  } catch {
    // fallback: keep default "All" only
  }
}

const statusOptions = [
  { value: '', label: t('common.all') },
  { value: 'ACTIVE', label: t('device.statusActive') },
  { value: 'REPORTED', label: t('device.statusReported') },
  { value: 'UNDER_REPAIR', label: t('device.statusUnderRepair') },
  { value: 'INACTIVE', label: t('device.statusInactive') },
  { value: 'DECOMMISSIONED', label: t('device.statusDecommissioned') },
]

// ── Fetch ──
async function fetchList() {
  loading.value = true
  try {
    const res = await listDevices({
      deviceType: filterDeviceType.value || undefined,
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

// ── Actions ──
async function handleDelete(row: DeviceResponse) {
  try {
    await ElMessageBox.confirm(t('common.confirmDelete'), t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await deleteDevice(row.id)
    ElMessage.success(t('common.deleted'))
    fetchList()
  } catch {
    // cancelled
  }
}

async function handleDecommission(row: DeviceResponse) {
  try {
    await ElMessageBox.confirm('確認報廢此設備？', t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await decommissionDevice(row.id)
    ElMessage.success('已報廢')
    fetchList()
  } catch {
    // cancelled
  }
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    ACTIVE: 'success',
    REPORTED: 'info',
    UNDER_REPAIR: 'warning',
    INACTIVE: 'info',
    DECOMMISSIONED: 'danger',
  }
  return map[status] || 'info'
}

// ── Import Dialog ──
const importDialogVisible = ref(false)

function handleImported() {
  ElMessage.success(t('import.importSuccess'))
  fetchList()
}

// ── Create / Edit Dialog ──
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref()

const form = reactive<DeviceRequest>({
  deviceType: '',
  deviceCode: '',
  deviceName: '',
  twd97X: null,
  twd97Y: null,
  lng: null,
  lat: null,
  elevation: null,
  deptId: null,
  contractId: null,
  propertyOwner: '',
  installedAt: null,
  parentDeviceId: null,
  mountPosition: '',
  connectivityType: '',
  circuitId: null,
  attributes: {},
})

function resetForm() {
  form.deviceType = ''
  form.deviceCode = ''
  form.deviceName = ''
  form.twd97X = null
  form.twd97Y = null
  form.lng = null
  form.lat = null
  form.elevation = null
  form.deptId = null
  form.contractId = null
  form.propertyOwner = ''
  form.installedAt = null
  form.parentDeviceId = null
  form.mountPosition = ''
  form.connectivityType = ''
  form.circuitId = null
  form.attributes = {}
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: DeviceResponse) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.deviceType = row.deviceType
  form.deviceCode = row.deviceCode
  form.deviceName = row.deviceName ?? ''
  form.twd97X = row.twd97X
  form.twd97Y = row.twd97Y
  form.lng = row.lng
  form.lat = row.lat
  form.elevation = row.elevation
  form.deptId = row.deptId
  form.contractId = row.contractId
  form.propertyOwner = row.propertyOwner ?? ''
  form.installedAt = row.installedAt
  form.parentDeviceId = row.parentDeviceId
  form.mountPosition = row.mountPosition ?? ''
  form.connectivityType = row.connectivityType ?? ''
  form.circuitId = row.circuitId
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await createDevice(form)
      ElMessage.success(t('common.created'))
    } else {
      await updateDevice(editingId.value!, form)
      ElMessage.success(t('common.updated'))
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    // error handled by interceptor
  } finally {
    saving.value = false
  }
}

// ── Detail Drawer ──
const detailDrawerVisible = ref(false)
const detailLoading = ref(false)
const detailDevice = ref<DeviceResponse | null>(null)

async function openDetail(row: DeviceResponse) {
  detailLoading.value = true
  detailDrawerVisible.value = true
  try {
    const res = await getDevice(row.id)
    if (res.errorCode === '00000') {
      detailDevice.value = res.body
    }
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  fetchList()
  loadDeviceTypeOptions()
})
</script>

<template>
  <div class="page-container">
    <!-- Header -->
    <div class="page-header">
      <div>
        <h2>{{ t('device.title') }}</h2>
        <p class="page-subtitle">{{ t('device.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate">+ {{ t('device.addBtn') }}</el-button>
        <el-button @click="importDialogVisible = true">
          {{ t('import.importBtn') }}
        </el-button>
      </div>
    </div>

    <!-- Filter Bar -->
    <div class="filter-bar">
      <el-select v-model="filterDeviceType" :placeholder="t('device.filterType')" clearable @change="handleSearch">
        <el-option v-for="opt in deviceTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select v-model="filterStatus" :placeholder="t('device.filterStatus')" clearable @change="handleSearch">
        <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-input v-model="keyword" :placeholder="t('device.searchPlaceholder')" clearable @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-button type="primary" @click="handleSearch">{{ t('device.searchBtn') }}</el-button>
    </div>

    <!-- Table -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="deviceCode" :label="t('device.colCode')" width="140" />
      <el-table-column prop="deviceName" :label="t('device.colName')" min-width="180" />
      <el-table-column prop="deviceType" :label="t('device.colType')" width="120" />
      <el-table-column :label="t('device.colStatus')" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">{{ statusOptions.find(o => o.value === row.status)?.label ?? row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deptName" :label="t('device.colDept')" width="140" />
      <el-table-column prop="contractCode" label="契約編號" width="140" />
      <el-table-column :label="t('device.colLastHeartbeat')" width="170">
        <template #default="{ row }">
          {{ row.lastHeartbeatAt ? new Date(row.lastHeartbeatAt).toLocaleString() : '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" fixed="right" width="260">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row)">{{ t('device.viewBtn') }}</el-button>
          <el-button link type="primary" size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button v-if="row.status !== 'DECOMMISSIONED'" link type="warning" size="small" @click="handleDecommission(row)">{{ t('device.decommissionBtn') }}</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Pagination -->
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

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('device.addBtn') : t('common.edit')"
      width="600px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('device.filterType')" required>
              <el-select v-model="form.deviceType" style="width: 100%">
                <el-option v-for="opt in deviceTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" v-show="opt.value !== ''" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('device.colCode')" required>
              <el-input v-model="form.deviceCode" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('device.colName')">
          <el-input v-model="form.deviceName" maxlength="200" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="t('device.twd97X')">
              <el-input-number v-model="form.twd97X" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('device.twd97Y')">
              <el-input-number v-model="form.twd97Y" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('device.elevation')">
              <el-input-number v-model="form.elevation" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="t('device.connectivity')">
              <el-select v-model="form.connectivityType" clearable style="width: 100%">
                <el-option label="Cellular" value="CELLULAR" />
                <el-option label="WiFi" value="WIFI" />
                <el-option label="LoRa" value="LORA" />
                <el-option label="Zigbee" value="ZIGBEE" />
                <el-option label="Ethernet" value="ETHERNET" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('device.mountPosition')">
              <el-input v-model="form.mountPosition" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('device.installedAt')">
              <el-date-picker v-model="form.installedAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('device.colDept')">
              <el-input-number v-model="form.deptId" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('device.propertyOwner')">
              <el-input v-model="form.propertyOwner" maxlength="200" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('device.contractId')">
              <el-input-number v-model="form.contractId" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('device.circuitId')">
              <el-input-number v-model="form.circuitId" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Detail Drawer -->
    <el-drawer v-model="detailDrawerVisible" :title="t('device.viewBtn')" size="500px" v-loading="detailLoading">
      <template v-if="detailDevice">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="設備 ID" :span="2">{{ detailDevice.id }}</el-descriptions-item>
          <el-descriptions-item label="設備類型">{{ detailDevice.deviceType }}</el-descriptions-item>
          <el-descriptions-item label="設備代碼">{{ detailDevice.deviceCode }}</el-descriptions-item>
          <el-descriptions-item label="設備名稱" :span="2">{{ detailDevice.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="狀態">
            <el-tag :type="getStatusType(detailDevice.status)" size="small">{{ detailDevice.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="安裝日期">{{ detailDevice.installedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="除役日期" :span="2">{{ detailDevice.decommissionedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所屬部門">{{ detailDevice.deptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="部門 ID">{{ detailDevice.deptId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="標案契約編號">{{ detailDevice.contractCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="財產所有人" :span="2">{{ detailDevice.propertyOwner || '-' }}</el-descriptions-item>
          <el-descriptions-item label="父設備 ID">{{ detailDevice.parentDeviceId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="父設備代碼">{{ detailDevice.parentDeviceCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="掛載位置">{{ detailDevice.mountPosition || '-' }}</el-descriptions-item>
          <el-descriptions-item label="連線方式">{{ detailDevice.connectivityType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="迴路編號">{{ detailDevice.circuitNumber || '-' }}</el-descriptions-item>
          <el-descriptions-item label="迴路 ID">{{ detailDevice.circuitId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="TWD97 X">{{ detailDevice.twd97X ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="TWD97 Y">{{ detailDevice.twd97Y ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="經度">{{ detailDevice.lng ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="緯度">{{ detailDevice.lat ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="海拔高度">{{ detailDevice.elevation ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="TWD67 X">{{ detailDevice.twd67X ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="TWD67 Y">{{ detailDevice.twd67Y ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="台電座標">{{ detailDevice.taipowerCoord || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最後心跳" :span="2">
            {{ detailDevice.lastHeartbeatAt ? new Date(detailDevice.lastHeartbeatAt).toLocaleString() : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="子設備數量">{{ detailDevice.childrenCount }}</el-descriptions-item>
          <el-descriptions-item label="建立者">{{ detailDevice.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="建立時間" :span="2">
            {{ detailDevice.createdAt ? new Date(detailDevice.createdAt).toLocaleString() : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="更新時間" :span="2">
            {{ detailDevice.updatedAt ? new Date(detailDevice.updatedAt).toLocaleString() : '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <!-- Import Dialog -->
    <ImportDialog v-model:visible="importDialogVisible" @imported="handleImported" />
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
