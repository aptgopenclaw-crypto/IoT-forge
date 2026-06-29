<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { listSettings, updateSetting, type SystemSettingDto } from '@/api/setting'
import { Pencil } from 'lucide-vue-next'

const { t } = useI18n()

const settings = ref<SystemSettingDto[]>([])
const loading = ref(false)

// ---- Edit dialog ----
const editDialogVisible = ref(false)
const editingRow = ref<SystemSettingDto | null>(null)
const editValue = ref('')
const editBoolValue = ref(false)
const editNumValue = ref(0)
const saving = ref(false)

type SettingType = 'boolean' | 'number' | 'string'

function detectType(value: string): SettingType {
  if (value === 'true' || value === 'false') return 'boolean'
  if (/^\d+$/.test(value)) return 'number'
  return 'string'
}

const editType = computed<SettingType>(() => {
  if (!editingRow.value) return 'string'
  return detectType(editingRow.value.settingValue)
})

onMounted(async () => {
  await loadSettings()
})

async function loadSettings() {
  loading.value = true
  try {
    const res = await listSettings()
    settings.value = res.body
  } catch {
    ElMessage.error(t('common.operationFailed'))
  } finally {
    loading.value = false
  }
}

function openEdit(row: SystemSettingDto) {
  editingRow.value = row
  const type = detectType(row.settingValue)
  if (type === 'boolean') {
    editBoolValue.value = row.settingValue === 'true'
  } else if (type === 'number') {
    editNumValue.value = parseInt(row.settingValue, 10)
  } else {
    editValue.value = row.settingValue
  }
  editDialogVisible.value = true
}

function getSubmitValue(): string {
  if (editType.value === 'boolean') return String(editBoolValue.value)
  if (editType.value === 'number') return String(editNumValue.value)
  return editValue.value
}

async function handleSaveEdit() {
  if (!editingRow.value) return
  saving.value = true
  try {
    const res = await updateSetting(editingRow.value.settingKey, getSubmitValue())
    const idx = settings.value.findIndex(s => s.settingKey === res.body.settingKey)
    if (idx >= 0) settings.value[idx] = res.body
    editDialogVisible.value = false
    ElMessage.success(t('setting.saveSuccess'))
  } catch {
    ElMessage.error(t('common.operationFailed'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-content">
      <div class="page-header">
        <div>
          <h1 class="page-title">{{ t('setting.title') }}</h1>
          <p class="page-subtitle">{{ t('setting.subtitle') }}</p>
        </div>
      </div>

      <div class="table-card" v-loading="loading">
        <el-table :data="settings" style="width: 100%" row-class-name="table-row">
          <el-table-column :label="t('setting.colDescription')" min-width="280">
            <template #default="{ row }">
              {{ t(`setting.keys.${row.settingKey}`, row.description) }}
            </template>
          </el-table-column>
          <el-table-column prop="settingValue" :label="t('setting.colValue')" width="180">
            <template #default="{ row }">
              <el-tag v-if="detectType(row.settingValue) === 'boolean'" :type="row.settingValue === 'true' ? 'success' : 'info'" size="small">
                {{ row.settingValue === 'true' ? t('setting.enabled') : t('setting.disabled') }}
              </el-tag>
              <span v-else>{{ row.settingValue }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('setting.colAction')" width="100" align="center" fixed="right">
            <template #default="{ row }">
              <div class="action-group">
                <el-button class="action-btn" size="small" @click="openEdit(row)">
                  <Pencil :size="14" />
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- Edit Dialog -->
    <el-dialog
      v-model="editDialogVisible"
      :title="t('setting.editDialogTitle')"
      width="420px"
      :close-on-click-modal="false"
    >
      <div class="edit-form" role="form" :aria-label="t('setting.editDialogTitle')">
        <div class="form-row">
          <label class="form-label">{{ t('setting.colDescription') }}</label>
          <div class="form-value-readonly">{{ editingRow ? t(`setting.keys.${editingRow.settingKey}`, editingRow.description) : '' }}</div>
        </div>
        <div class="form-row">
          <label class="form-label">{{ t('setting.colValue') }}</label>
          <!-- Boolean: switch -->
          <el-switch
            v-if="editType === 'boolean'"
            v-model="editBoolValue"
            :active-text="t('setting.enabled')"
            :inactive-text="t('setting.disabled')"
          />
          <!-- Numeric: input number -->
          <el-input-number
            v-else-if="editType === 'number'"
            v-model="editNumValue"
            :min="0"
            :max="9999"
            controls-position="right"
          />
          <!-- String: text input -->
          <el-input v-else v-model="editValue" maxlength="500" show-word-limit />
        </div>
      </div>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveEdit">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-content {
}

.table-card {
  background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 0;
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

/* Action button — same as UserListView */
.action-group {
  display: flex;
  gap: 6px;
  justify-content: center;
}

.action-btn {
  border: 1px solid var(--border-light);
  background: transparent;
  color: var(--text-secondary);
  padding: 4px 8px;
  border-radius: 6px;
}
.action-btn:hover {
  background: var(--bg-hover);
  color: #55b3ff;
  border-color: #55b3ff;
}

/* Edit dialog form */
.edit-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.form-value-readonly {
  font-size: 14px;
  color: var(--text-primary);
  padding: 8px 0;
}
</style>
