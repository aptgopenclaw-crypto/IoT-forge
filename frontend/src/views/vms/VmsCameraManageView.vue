<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VmsCamera, VmsCameraRequest } from '@/types/vms'
import { listVmsCameras, createVmsCamera, updateVmsCamera, deleteVmsCamera, syncVmsCameras, listVmsServers } from '@/api/vms'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const cameras = ref<VmsCamera[]>([])
const servers = ref<{ id: number; name: string }[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingCamera = ref<VmsCamera | null>(null)
const form = ref<VmsCameraRequest>({ serverId: 0, vmsCameraId: '', displayName: '' })
const syncLoading = ref(false)
const syncServerId = ref<number | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const [camRes, srvRes] = await Promise.all([listVmsCameras(), listVmsServers()])
    cameras.value = camRes.body ?? []
    servers.value = (srvRes.body ?? []).map(s => ({ id: s.id, name: s.name }))
  } finally { loading.value = false }
}

function openCreate() {
  editingCamera.value = null
  form.value = { serverId: servers.value[0]?.id ?? 0, vmsCameraId: '', displayName: '' }
  dialogVisible.value = true
}

function openEdit(camera: VmsCamera) {
  editingCamera.value = camera
  form.value = { serverId: camera.serverId, vmsCameraId: camera.vmsCameraId, displayName: camera.displayName, deptId: camera.deptId, rtspUrl: camera.rtspUrl }
  dialogVisible.value = true
}

async function handleSave() {
  if (editingCamera.value) {
    await updateVmsCamera(editingCamera.value.id, form.value)
  } else {
    await createVmsCamera(form.value)
  }
  dialogVisible.value = false
  await fetchData()
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm(t('vms.deleteCameraConfirm', { name: '' }), t('common.warning'), { type: 'warning' })
  await deleteVmsCamera(id)
  await fetchData()
}

async function handleSync() {
  if (!syncServerId.value) return
  syncLoading.value = true
  try {
    await syncVmsCameras(syncServerId.value)
    ElMessage.success(t('vms.syncCameras'))
    await fetchData()
  } finally { syncLoading.value = false }
}

onMounted(fetchData)
</script>

<template>
  <div class="vms-camera-manage">
    <div class="page-header">
      <h2>{{ t('vms.cameraList') }}</h2>
      <div class="header-actions">
        <el-select v-if="servers.length" v-model="syncServerId" placeholder="Select server" style="width:180px;margin-right:8px">
          <el-option v-for="s in servers" :key="s.id" :value="s.id" :label="s.name" />
        </el-select>
        <el-button type="primary" size="small" @click="handleSync" :disabled="!syncServerId" :loading="syncLoading">
          {{ t('vms.syncCameras') }}
        </el-button>
        <el-button type="primary" @click="openCreate">{{ t('vms.addCamera') }}</el-button>
      </div>
    </div>

    <el-table :data="cameras" v-loading="loading" stripe>
      <el-table-column prop="displayName" :label="t('common.name')" min-width="160" />
      <el-table-column prop="vmsCameraId" label="NX Camera ID" min-width="240" />
      <el-table-column prop="serverName" :label="t('vms.server')" width="140" />
      <el-table-column prop="status" :label="t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ONLINE' ? 'success' : 'danger'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingCamera ? t('common.edit') : t('vms.addCamera')" width="500px">
      <el-form :model="form" label-position="top">
        <el-form-item :label="t('vms.server')" required>
          <el-select v-model="form.serverId" style="width:100%">
            <el-option v-for="s in servers" :key="s.id" :value="s.id" :label="s.name" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('vms.vmsCameraId')" required>
          <el-input v-model="form.vmsCameraId" :placeholder="t('vms.vmsCameraIdPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('vms.displayName')">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item :label="t('dept.title')">
          <el-input v-model="form.deptId" type="number" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header-actions { display: flex; align-items: center; }
</style>
