<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listDeviceTemplates,
  listDeviceTypeNames,
  getDeviceSchema,
  updateDeviceSchema,
  deleteDeviceTemplate,
  type DeviceTemplateInfo,
} from '@/api/schema'

const { t } = useI18n()

// ── Table ──
const templates = ref<DeviceTemplateInfo[]>([])
const loading = ref(false)

async function fetchTemplates() {
  loading.value = true
  try {
    const res = await listDeviceTemplates()
    if (res.errorCode === '00000') {
      templates.value = res.body
    }
  } finally {
    loading.value = false
  }
}

// ── Create ──
const createDialogVisible = ref(false)
const newDeviceType = ref('')
const creating = ref(false)

async function handleCreate() {
  if (!newDeviceType.value.trim()) return
  creating.value = true
  try {
    await updateDeviceSchema(newDeviceType.value.trim(), {})
    ElMessage.success('模板已建立')
    createDialogVisible.value = false
    newDeviceType.value = ''
    await fetchTemplates()
  } catch {
    // handled by interceptor
  } finally {
    creating.value = false
  }
}

function openCreate() {
  newDeviceType.value = ''
  createDialogVisible.value = true
}

// ── Schema Editor ──
const editorDialogVisible = ref(false)
const editingType = ref('')
const schemaJson = ref('')
const saving = ref(false)

async function openEditor(deviceType: string) {
  editingType.value = deviceType
  try {
    const res = await getDeviceSchema(deviceType)
    if (res.errorCode === '00000') {
      schemaJson.value = JSON.stringify(res.body, null, 2)
    } else {
      schemaJson.value = '{}'
    }
  } catch {
    schemaJson.value = '{}'
  }
  editorDialogVisible.value = true
}

async function saveSchema() {
  saving.value = true
  try {
    const parsed = JSON.parse(schemaJson.value)
    await updateDeviceSchema(editingType.value, parsed)
    ElMessage.success('Schema 已儲存')
    editorDialogVisible.value = false
    await fetchTemplates()
  } catch (e) {
    if (e instanceof SyntaxError) {
      ElMessage.error('JSON 格式錯誤')
    }
  } finally {
    saving.value = false
  }
}

function formatJson() {
  try {
    schemaJson.value = JSON.stringify(JSON.parse(schemaJson.value), null, 2)
  } catch {
    ElMessage.error('JSON 格式錯誤')
  }
}

// ── Delete ──
async function handleDelete(deviceType: string) {
  try {
    await ElMessageBox.confirm(
      `確定刪除「${deviceType}」模板？`,
      '刪除確認',
      { confirmButtonText: '確定', cancelButtonText: '取消', type: 'warning' },
    )
    await deleteDeviceTemplate(deviceType)
    ElMessage.success('模板已刪除')
    await fetchTemplates()
  } catch (err: any) {
    // 使用者取消對話框不顯示錯誤
    if (err === 'cancel' || err === 'close') return
    // 顯示後端回傳的業務錯誤訊息
    const data = err?.response?.data
    const msg = data?.errorDetail || data?.errorMsg || err?.message || '操作失敗'
    ElMessage.error(msg)
  }
}

onMounted(fetchTemplates)
</script>

<template>
  <div class="page-container">
    <!-- Header -->
    <div class="page-header">
      <div>
        <h2>{{ t('deviceTemplate.title') }}</h2>
        <p class="page-subtitle">{{ t('deviceTemplate.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate">+ 新增模板</el-button>
      </div>
    </div>

    <!-- Table -->
    <el-table :data="templates" v-loading="loading" stripe>
      <el-table-column prop="deviceType" label="設備類型" width="180" />
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column prop="createdAt" label="建立時間" width="180">
        <template #default="{ row }">
          {{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新時間" width="180">
        <template #default="{ row }">
          {{ row.updatedAt ? new Date(row.updatedAt).toLocaleString() : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEditor(row.deviceType)">編輯 Schema</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row.deviceType)">刪除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create Dialog -->
    <el-dialog v-model="createDialogVisible" title="新增模板" width="400px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="設備類型代碼" required>
          <el-input v-model="newDeviceType" placeholder="例如：NEW_SENSOR" maxlength="30" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" :disabled="!newDeviceType.trim()" @click="handleCreate">建立</el-button>
      </template>
    </el-dialog>

    <!-- Schema Editor Dialog -->
    <el-dialog v-model="editorDialogVisible" :title="'編輯 Schema — ' + editingType" width="700px" destroy-on-close>
      <div class="editor-toolbar">
        <el-button @click="formatJson">格式化</el-button>
      </div>
      <el-input
        v-model="schemaJson"
        type="textarea"
        :rows="20"
        placeholder="{\n  &quot;type&quot;: &quot;object&quot;,\n  &quot;properties&quot;: {}\n}"
        class="json-editor"
      />
      <template #footer>
        <el-button @click="editorDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSchema">儲存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  gap: 8px;
}
.editor-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.json-editor :deep(textarea) {
  font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}
</style>
