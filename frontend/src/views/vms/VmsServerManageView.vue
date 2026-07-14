<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VmsServer, VmsServerRequest } from '@/types/vms'
import { listVmsServers, createVmsServer, updateVmsServer, deleteVmsServer, testVmsServerConnection } from '@/api/vms'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const servers = ref<VmsServer[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingServer = ref<VmsServer | null>(null)
const form = ref<VmsServerRequest>({
  name: '', vmsType: 'NX_WITNESS', baseUrl: '', authType: 'BASIC',
  authUsername: '', authPassword: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await listVmsServers()
    servers.value = res.body ?? []
  } finally { loading.value = false }
}

function openCreate() {
  editingServer.value = null
  form.value = { name: '', vmsType: 'NX_WITNESS', baseUrl: '', authType: 'BASIC', authUsername: '', authPassword: '' }
  dialogVisible.value = true
}

function openEdit(server: VmsServer) {
  editingServer.value = server
  form.value = { name: server.name, vmsType: server.vmsType, baseUrl: server.baseUrl,
    authType: server.authType, authUsername: server.authUsername }
  dialogVisible.value = true
}

async function handleSave() {
  if (editingServer.value) {
    await updateVmsServer(editingServer.value.id, form.value)
    ElMessage.success(t('common.updateSuccess'))
  } else {
    await createVmsServer(form.value)
    ElMessage.success(t('common.createSuccess'))
  }
  dialogVisible.value = false
  await fetchData()
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.warning'), { type: 'warning' })
  await deleteVmsServer(id)
  ElMessage.success(t('common.deleteSuccess'))
  await fetchData()
}

async function handleTestConnection(id: number) {
  try {
    await testVmsServerConnection(id)
    ElMessage.success(t('vms.connectionSuccess'))
  } catch {
    ElMessage.error(t('vms.connectionFailed'))
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="vms-server-manage">
    <div class="page-header">
      <h2>{{ t('vms.servers') }}</h2>
      <el-button type="primary" @click="openCreate">{{ t('common.add') }}</el-button>
    </div>
    <el-table :data="servers" v-loading="loading" stripe>
      <el-table-column prop="name" :label="t('common.name')" min-width="140" />
      <el-table-column prop="vmsType" :label="t('vms.vmsType')" width="120" />
      <el-table-column prop="baseUrl" :label="t('common.url')" min-width="200" />
      <el-table-column prop="isActive" :label="t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isActive ? 'success' : 'info'">{{ row.isActive ? 'Active' : 'Inactive' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button size="small" @click="handleTestConnection(row.id)">{{ t('vms.testConnection') }}</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingServer ? t('common.edit') : t('common.add')" width="500px">
      <el-form :model="form" label-position="top">
        <el-form-item :label="t('common.name')" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('vms.vmsType')" required>
          <el-select v-model="form.vmsType" style="width:100%">
            <el-option value="NX_WITNESS" label="NX Witness" />
            <el-option value="MILESTONE" label="Milestone" />
            <el-option value="AXXON" label="Axxon" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.url')" required>
          <el-input v-model="form.baseUrl" placeholder="http://192.168.1.100:7001" />
        </el-form-item>
        <el-form-item :label="t('common.username')">
          <el-input v-model="form.authUsername" />
        </el-form-item>
        <el-form-item :label="t('common.password')">
          <el-input v-model="form.authPassword" type="password" show-password />
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
</style>
