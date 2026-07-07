<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  listWorkflowDefinitionsAdmin,
  createWorkflowDefinition,
  updateWorkflowDefinition,
  toggleWorkflowDefinitionEnabled,
  deleteWorkflowDefinition,
} from '@/api/workflow'
import type { WorkflowDefinitionItem, WorkflowDefinitionRequest } from '@/api/workflow'

const { t } = useI18n()

const tableData = ref<WorkflowDefinitionItem[]>([])
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const res = await listWorkflowDefinitionsAdmin()
    if (res.errorCode === '00000') tableData.value = res.body
  } finally {
    loading.value = false
  }
}

// ── Dialog ──
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = reactive<WorkflowDefinitionRequest>({
  code: '',
  name: '',
  stepsJson: '',
  enabled: true,
})

const STEPS_TEMPLATE = JSON.stringify(
  {
    initial_step: 'step_1',
    steps: [
      { id: 'step_1', name: '第一步', type: 'normal', role_code: 'ROLE_DEPT_USER', next: 'step_end', sla_days: 3, reject_target: null },
      { id: 'step_end', name: '結案', type: 'end', role_code: null, next: null, sla_days: null, reject_target: null },
    ],
  },
  null,
  2,
)

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  form.code = ''
  form.name = ''
  form.stepsJson = STEPS_TEMPLATE
  form.enabled = true
  dialogVisible.value = true
}

function openEdit(row: WorkflowDefinitionItem) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  form.stepsJson = formatJson(row.stepsJson)
  form.enabled = row.enabled
  dialogVisible.value = true
}

function formatJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function validateJson(raw: string): boolean {
  try {
    JSON.parse(raw)
    return true
  } catch {
    return false
  }
}

async function handleSave() {
  if (!form.code.trim() || !form.name.trim() || !form.stepsJson.trim()) {
    ElMessage.warning(t('common.requiredFields'))
    return
  }
  if (!validateJson(form.stepsJson)) {
    ElMessage.error(t('workflowDef.errors.stepsInvalidJson'))
    return
  }
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await createWorkflowDefinition(form)
      ElMessage.success(t('workflowDef.createdSuccess'))
    } else {
      await updateWorkflowDefinition(editingId.value!, form)
      ElMessage.success(t('workflowDef.updatedSuccess'))
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    saving.value = false
  }
}

async function handleToggle(row: WorkflowDefinitionItem) {
  try {
    await toggleWorkflowDefinitionEnabled(row.id, !row.enabled)
    ElMessage.success(t('workflowDef.toggleEnabledSuccess'))
    fetchList()
  } catch { /* error handled */ }
}

async function handleDelete(row: WorkflowDefinitionItem) {
  try {
    await ElMessageBox.confirm(
      t('workflowDef.deleteConfirm', { name: row.name }),
      t('common.warning'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' },
    )
    await deleteWorkflowDefinition(row.id)
    ElMessage.success(t('workflowDef.deletedSuccess'))
    fetchList()
  } catch { /* cancelled */ }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>{{ t('workflowDef.title') }}</h2>
        <p class="page-subtitle">{{ t('workflowDef.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate">+ {{ t('workflowDef.addBtn') }}</el-button>
      </div>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="code" :label="t('workflowDef.colCode')" width="200" />
      <el-table-column prop="name" :label="t('workflowDef.colName')" min-width="180" />
      <el-table-column prop="version" :label="t('workflowDef.colVersion')" width="80" align="center" />
      <el-table-column prop="tenantId" :label="t('workflowDef.colTenant')" width="160" />
      <el-table-column :label="t('workflowDef.colEnabled')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? t('workflowDef.enabledYes') : t('workflowDef.enabledNo') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('workflowDef.colUpdatedAt')" width="170">
        <template #default="{ row }">
          {{ row.updatedAt ? new Date(row.updatedAt).toLocaleString() : '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" fixed="right" width="200">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button link size="small" @click="handleToggle(row)">
            {{ row.enabled ? t('common.disable') : t('common.enable') }}
          </el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('workflowDef.dialogCreateTitle') : t('workflowDef.dialogEditTitle')"
      width="700px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('workflowDef.codeLabel')" required>
              <el-input
                v-model="form.code"
                :placeholder="t('workflowDef.codePlaceholder')"
                :disabled="dialogMode === 'edit'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('workflowDef.nameLabel')" required>
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('workflowDef.stepsLabel')" required>
          <p class="json-hint">{{ t('workflowDef.jsonHint') }}</p>
          <el-input
            v-model="form.stepsJson"
            type="textarea"
            :rows="18"
            style="font-family: monospace; font-size: 12px"
          />
        </el-form-item>
        <el-form-item :label="t('workflowDef.enabledLabel')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.header-actions { display: flex; gap: 8px; }
.json-hint {
  font-size: 12px;
  color: #909399;
  margin: 0 0 6px;
}
</style>
