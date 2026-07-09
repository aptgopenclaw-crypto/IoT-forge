<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/authStore'
import {
  listServers,
  createServer,
  updateServer,
  deleteServer,
  testServerConnection,
  listCamerasAdmin,
  createCamera,
  deleteCamera,
} from '@/api/vms'
import type { VmsServer, VmsCamera, VmsServerRequest, VmsCameraRequest } from '@/types/vms'

const { t } = useI18n()
const authStore = useAuthStore()
const canManage = computed(() => authStore.userInfo?.permissions.includes('VMS_MANAGE') ?? false)

// ── Servers table ──
const servers = ref<VmsServer[]>([])
const loadingServers = ref(false)
const selectedServerId = ref<number | null>(null)

// ── Cameras table ──
const cameras = ref<VmsCamera[]>([])
const loadingCameras = ref(false)

// ── Server dialog ──
const serverDialogVisible = ref(false)
const serverDialogTitle = ref('')
const serverForm = reactive<VmsServerRequest>({
  name: '',
  vmsType: 'NX_WITNESS',
  baseUrl: '',
  authType: 'BASIC',
  authUsername: '',
  authPassword: '',
  apiToken: '',
})
const isEditing = ref(false)
const editingServerId = ref<number | null>(null)
const savingServer = ref(false)

// ── Camera dialog ──
const cameraDialogVisible = ref(false)
const cameraForm = reactive<VmsCameraRequest>({
  serverId: 0,
  vmsCameraId: '',
  displayName: '',
  deviceId: undefined,
})
const savingCamera = ref(false)

// ── Load servers ──
async function loadServers() {
  loadingServers.value = true
  try {
    const res = await listServers()
    if (res.errorCode === '00000') {
      servers.value = res.body
    }
  } finally {
    loadingServers.value = false
  }
}

// ── Load cameras for selected server ──
async function onServerSelect(serverId: number | null) {
  selectedServerId.value = serverId
  if (!serverId) {
    cameras.value = []
    return
  }
  loadingCameras.value = true
  try {
    const res = await listCamerasAdmin({ serverId })
    if (res.errorCode === '00000') {
      cameras.value = res.body
    }
  } finally {
    loadingCameras.value = false
  }
}

// ── Server CRUD ──
function openCreateDialog() {
  isEditing.value = false
  editingServerId.value = null
  serverDialogTitle.value = t('vms.addServer')
  serverForm.name = ''
  serverForm.vmsType = 'NX_WITNESS'
  serverForm.baseUrl = ''
  serverForm.authType = 'BASIC'
  serverForm.authUsername = ''
  serverForm.authPassword = ''
  serverForm.apiToken = ''
  serverDialogVisible.value = true
}

function openEditDialog(server: VmsServer) {
  isEditing.value = true
  editingServerId.value = server.id
  serverDialogTitle.value = t('vms.editServer')
  serverForm.name = server.name
  serverForm.vmsType = server.vmsType
  serverForm.baseUrl = server.baseUrl
  serverForm.authType = server.authType
  serverForm.authUsername = ''
  serverForm.authPassword = ''
  serverForm.apiToken = ''
  serverDialogVisible.value = true
}

async function handleServerSave() {
  savingServer.value = true
  try {
    let res: { errorCode: string }
    if (isEditing.value && editingServerId.value) {
      res = await updateServer(editingServerId.value, { ...serverForm })
    } else {
      res = await createServer({ ...serverForm })
    }
    if (res.errorCode === '00000') {
      ElMessage.success(t('common.saveSuccess'))
      serverDialogVisible.value = false
      await loadServers()
    }
  } finally {
    savingServer.value = false
  }
}

async function handleServerDelete(id: number) {
  try {
    await ElMessageBox.confirm(t('common.confirmDelete'), t('common.tips'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    const res = await deleteServer(id)
    if (res.errorCode === '00000') {
      ElMessage.success(t('common.deleted'))
      if (selectedServerId.value === id) {
        cameras.value = []
        selectedServerId.value = null
      }
      await loadServers()
    }
  } catch {
    // cancelled
  }
}

async function handleTestConnection(serverId: number) {
  try {
    const res = await testServerConnection(serverId)
    if (res.errorCode === '00000') {
      ElMessage.success(t('common.success'))
    }
  } catch {
    ElMessage.error(t('vms.connectionFailed'))
  }
}

// ── Camera CRUD ──
function openAddCameraDialog() {
  if (!selectedServerId.value) return
  cameraForm.serverId = selectedServerId.value
  cameraForm.vmsCameraId = ''
  cameraForm.displayName = ''
  cameraForm.deviceId = undefined
  cameraDialogVisible.value = true
}

async function handleCameraSave() {
  savingCamera.value = true
  try {
    const res = await createCamera({ ...cameraForm })
    if (res.errorCode === '00000') {
      ElMessage.success(t('common.saveSuccess'))
      cameraDialogVisible.value = false
      await onServerSelect(selectedServerId.value)
    }
  } finally {
    savingCamera.value = false
  }
}

async function handleCameraDelete(cameraId: number) {
  try {
    await ElMessageBox.confirm(t('common.confirmDelete'), t('common.tips'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    const res = await deleteCamera(cameraId)
    if (res.errorCode === '00000') {
      ElMessage.success(t('common.deleted'))
      await onServerSelect(selectedServerId.value)
    }
  } catch {
    // cancelled
  }
}

onMounted(loadServers)
</script>

<template>
  <div class="vms-server-management">
    <!-- 左側：VMS 伺服器列表 -->
    <div class="server-panel">
      <div class="panel-header">
        <h3>{{ t('vms.servers') }}</h3>
        <el-button v-if="canManage" type="primary" size="small" @click="openCreateDialog">
          {{ t('common.add') }}
        </el-button>
      </div>

      <el-table
        :data="servers"
        v-loading="loadingServers"
        :highlight-current-row="true"
        @current-change="(row: VmsServer | null) => onServerSelect(row?.id ?? null)"
        style="width: 100%"
      >
        <el-table-column prop="name" :label="t('vms.serverName')" min-width="140" />
        <el-table-column prop="vmsType" :label="t('vms.vmsType')" width="110" />
        <el-table-column prop="baseUrl" :label="t('vms.baseUrl')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="t('common.operations')" width="200" fixed="right">
          <template #default="{ row }: { row: VmsServer }">
            <el-button v-if="canManage" size="small" @click="openEditDialog(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button size="small" @click="handleTestConnection(row.id)">
              {{ t('common.test') }}
            </el-button>
            <el-button v-if="canManage" size="small" type="danger" @click="handleServerDelete(row.id)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 右側：所選伺服器的攝影機列表 -->
    <div v-if="selectedServerId" class="camera-panel">
      <div class="panel-header">
        <h3>{{ t('vms.camera') }}</h3>
        <el-button v-if="canManage" type="primary" size="small" @click="openAddCameraDialog">
          {{ t('common.add') }}
        </el-button>
      </div>

      <el-table :data="cameras" v-loading="loadingCameras" style="width: 100%">
        <el-table-column prop="vmsCameraId" :label="t('vms.vmsCameraId')" width="120" />
        <el-table-column prop="displayName" :label="t('vms.displayName')" min-width="140" />
        <el-table-column prop="status" :label="t('vms.status')" width="90">
          <template #default="{ row }: { row: VmsCamera }">
            <el-tag :type="row.status === 'ONLINE' ? 'success' : 'danger'" size="small">
              {{ t(`vms.${row.status.toLowerCase()}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operations')" width="80">
          <template #default="{ row }: { row: VmsCamera }">
            <el-button v-if="canManage" size="small" type="danger" @click="handleCameraDelete(row.id)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 無選取 server 提示 -->
    <div v-else class="no-selection">
      <el-empty :description="t('common.select') + ' ' + t('vms.server')" />
    </div>

    <!-- Server Dialog -->
    <el-dialog v-model="serverDialogVisible" :title="serverDialogTitle" width="500px">
      <el-form :model="serverForm" label-width="100px">
        <el-form-item :label="t('vms.serverName')" required>
          <el-input v-model="serverForm.name" />
        </el-form-item>
        <el-form-item :label="t('vms.vmsType')" required>
          <el-select v-model="serverForm.vmsType" style="width: 100%">
            <el-option label="NX Witness" value="NX_WITNESS" />
            <el-option label="Milestone" value="MILESTONE" />
            <el-option label="Axxon" value="AXXON" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('vms.baseUrl')" required>
          <el-input v-model="serverForm.baseUrl" placeholder="http://vms-server:port" />
        </el-form-item>
        <el-form-item :label="t('vms.authType')">
          <el-select v-model="serverForm.authType" style="width: 100%">
            <el-option label="Basic" value="BASIC" />
            <el-option label="Token" value="TOKEN" />
            <el-option label="Certificate" value="CERT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="serverForm.authType === 'BASIC'" :label="t('vms.authUsername')">
          <el-input v-model="serverForm.authUsername" />
        </el-form-item>
        <el-form-item v-if="serverForm.authType === 'BASIC'" :label="t('vms.authPassword')">
          <el-input v-model="serverForm.authPassword" type="password" />
        </el-form-item>
        <el-form-item v-if="serverForm.authType === 'TOKEN'" :label="t('vms.apiToken')">
          <el-input v-model="serverForm.apiToken" type="password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="serverDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="savingServer" @click="handleServerSave">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Camera Dialog -->
    <el-dialog v-model="cameraDialogVisible" :title="t('vms.addCamera')" width="400px">
      <el-form :model="cameraForm" label-width="100px">
        <el-form-item :label="t('vms.vmsCameraId')" required>
          <el-input v-model="cameraForm.vmsCameraId" />
        </el-form-item>
        <el-form-item :label="t('vms.displayName')">
          <el-input v-model="cameraForm.displayName" />
        </el-form-item>
        <el-form-item :label="t('vms.deviceId')">
          <el-input-number v-model="cameraForm.deviceId" :min="1" :value-on-clear="undefined" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cameraDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="savingCamera" @click="handleCameraSave">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.vms-server-management {
  display: flex;
  gap: 16px;
  height: calc(100vh - 120px);
}
.server-panel {
  width: 65%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.camera-panel {
  width: 35%;
  display: flex;
  flex-direction: column;
}
.no-selection {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.panel-header h3 {
  font-size: 16px;
  margin: 0;
}
</style>
