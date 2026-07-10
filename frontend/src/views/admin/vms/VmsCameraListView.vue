<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCamerasAdmin, createCamera, deleteCamera, listServers } from '@/api/vms'
import type { VmsCamera, VmsCameraRequest, VmsServer } from '@/types/vms'
import VmsCameraImportDialog from './VmsCameraImportDialog.vue'

const { t } = useI18n()

// ── Table ──
const cameras = ref<VmsCamera[]>([])
const servers = ref<VmsServer[]>([])
const loading = ref(false)

// ── Filters ──
const filterServerId = ref<number | undefined>(undefined)
const filterStatus = ref<string>('')
const filterKeyword = ref('')

// ── Pagination ──
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

// ── Dialog ──
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<VmsCameraRequest>({
  serverId: 0,
  vmsCameraId: '',
  displayName: '',
  rtspUrl: '',
  deviceId: undefined,
  deptId: undefined,
})

const importDialogVisible = ref(false)

function onImported() {
  fetchList()
}

async function loadServers() {
  try {
    const res = await listServers()
    if (res.errorCode === '00000') {
      servers.value = res.body
    }
  } catch {
    // ignore
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {}
    if (filterServerId.value) params.serverId = filterServerId.value
    const res = await listCamerasAdmin(params)
    if (res.errorCode === '00000') {
      cameras.value = res.body
      pagination.total = res.body.length
    }
  } catch {
    // ignore
  }
  finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function handleReset() {
  filterServerId.value = undefined
  filterStatus.value = ''
  filterKeyword.value = ''
  handleSearch()
}

function openCreate() {
  form.serverId = servers.value[0]?.id ?? 0
  form.vmsCameraId = ''
  form.displayName = ''
  form.rtspUrl = ''
  form.deviceId = undefined
  form.deptId = undefined
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.serverId || !form.vmsCameraId) {
    ElMessage.warning(t('common.requiredFields'))
    return
  }
  saving.value = true
  try {
    const res = await createCamera(form)
    if (res.errorCode === '00000') {
      ElMessage.success(t('common.saveSuccess'))
      dialogVisible.value = false
      await fetchList()
    }
  }
  finally {
    saving.value = false
  }
}

async function handleDelete(row: VmsCamera) {
  try {
    await ElMessageBox.confirm(
      t('vms.deleteCameraConfirm', { name: row.displayName || row.vmsCameraId }),
      t('common.tips'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' },
    )
    const res = await deleteCamera(row.id)
    if (res.errorCode === '00000') {
      ElMessage.success(t('common.deleted'))
      await fetchList()
    }
  } catch {
    // cancelled
  }
}

const paginatedCameras = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  const end = start + pagination.size
  return cameras.value.slice(start, end)
})

function handlePageChange(page: number) {
  pagination.page = page
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
}

const statusOptions = [
  { value: 'ONLINE', label: t('vms.online') },
  { value: 'OFFLINE', label: t('vms.offline') },
  { value: 'ERROR', label: t('vms.error') },
]

onMounted(() => {
  loadServers()
  fetchList()
})
</script>

<template>
  <div class="vms-camera-list">
    <!-- 工具列 -->
    <div class="toolbar">
      <div class="filters">
        <el-select
          v-model="filterServerId"
          :placeholder="t('vms.server')"
          clearable
          style="width: 180px"
          @change="handleSearch"
        >
          <el-option v-for="s in servers" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-select
          v-model="filterStatus"
          :placeholder="t('vms.status')"
          clearable
          style="width: 120px"
          @change="handleSearch"
        >
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-input
          v-model="filterKeyword"
          :placeholder="t('vms.searchPlaceholder')"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
      </div>
      <div class="actions">
        <el-button type="primary" @click="openCreate">{{ t('common.add') }}</el-button>
        <el-button @click="importDialogVisible = true">{{ t('vms.importCameras') }}</el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="paginatedCameras" v-loading="loading" style="width: 100%" border stripe>
      <el-table-column prop="vmsCameraId" :label="t('vms.vmsCameraId')" width="180" show-overflow-tooltip />
      <el-table-column prop="displayName" :label="t('vms.displayName')" min-width="160" />
      <el-table-column prop="serverId" :label="t('vms.server')" width="160">
        <template #default="{ row }: { row: VmsCamera }">
          {{ servers.find(s => s.id === row.serverId)?.name ?? row.serverId }}
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="t('vms.status')" width="100">
        <template #default="{ row }: { row: VmsCamera }">
          <el-tag :type="row.status === 'ONLINE' ? 'success' : row.status === 'OFFLINE' ? 'danger' : 'warning'" size="small">
            {{ t(`vms.${row.status.toLowerCase()}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deviceId" :label="t('vms.deviceId')" width="100" />
      <el-table-column prop="rtspUrl" :label="t('vms.rtspUrl')" min-width="200" show-overflow-tooltip />
      <el-table-column :label="t('common.operations')" width="100" fixed="right">
        <template #default="{ row }: { row: VmsCamera }">
          <el-button size="small" type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分頁 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 新增對話框 -->
    <el-dialog v-model="dialogVisible" :title="t('vms.addCamera')" width="450px">
      <el-form :model="form" label-width="110px">
        <el-form-item :label="t('vms.server')" required>
          <el-select v-model="form.serverId" style="width: 100%">
            <el-option v-for="s in servers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('vms.vmsCameraId')" required>
          <el-input v-model="form.vmsCameraId" :placeholder="t('vms.vmsCameraIdPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('vms.displayName')">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item :label="t('vms.rtspUrl')">
          <el-input v-model="form.rtspUrl" placeholder="rtsp://..." />
        </el-form-item>
        <el-form-item :label="t('vms.deviceId')">
          <el-input-number v-model="form.deviceId" :min="1" :value-on-clear="undefined" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 匯入對話框 -->
    <VmsCameraImportDialog v-model:visible="importDialogVisible" @imported="onImported" />
  </div>
</template>

<style scoped>
.vms-camera-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.filters {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.actions {
  display: flex;
  gap: 8px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
