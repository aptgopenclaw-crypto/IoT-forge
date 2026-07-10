<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { UploadFilled, Document, CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'
import { importCameras } from '@/api/vms'
import type { ImportError, ImportResponse } from '@/types/device'

const { t } = useI18n()

const visible = defineModel<boolean>('visible', { default: false })
const emit = defineEmits<{ imported: [] }>()

type DialogState = 'select' | 'loading' | 'success' | 'error'
const state = ref<DialogState>('select')
const selectedFile = ref<File | null>(null)
const importResult = ref<ImportResponse | null>(null)

const canImport = computed(() => selectedFile.value !== null)
const fileName = computed(() => selectedFile.value?.name ?? '')
const fileSize = computed(() => {
  if (!selectedFile.value) return ''
  const bytes = selectedFile.value.size
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
})

function handleFileChange(file: File | null) {
  selectedFile.value = file
}

function reset() {
  state.value = 'select'
  selectedFile.value = null
  importResult.value = null
}

function handleClose() {
  visible.value = false
  reset()
}

async function handleImport() {
  if (!selectedFile.value) return
  state.value = 'loading'
  try {
    const res = await importCameras(selectedFile.value)
    if (res.errorCode === '00000' && res.body) {
      importResult.value = res.body
      state.value = res.body.errors.length > 0 ? 'error' : 'success'
      if (res.body.errors.length === 0) emit('imported')
    }
    else {
      state.value = 'error'
    }
  }
  catch (err: any) {
    importResult.value = {
      entityType: 'vms_camera',
      totalRows: 0,
      successCount: 0,
      errors: [{ row: 0, field: '', value: '', message: err?.message || t('import.importFailed') }],
    }
    state.value = 'error'
  }
}

function handleDone() {
  handleClose()
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="t('vms.importCameras')"
    width="600px"
    destroy-on-close
    @close="handleClose"
  >
    <template v-if="state === 'select'">
      <div class="import-dropzone">
        <el-upload
          drag
          :auto-upload="false"
          :show-file-list="false"
          accept=".xlsx,.csv"
          :on-change="(uploadFile) => handleFileChange(uploadFile.raw || null)"
        >
          <div v-if="!selectedFile">
            <el-icon :size="48" color="#909399"><UploadFilled /></el-icon>
            <p>{{ t('import.dropHint') }}</p>
            <p class="import-hint">{{ t('import.formatHint') }}</p>
          </div>
          <div v-else>
            <el-icon :size="48" color="#409eff"><Document /></el-icon>
            <p class="import-filename">{{ fileName }}</p>
            <p class="import-hint">{{ fileSize }}</p>
          </div>
        </el-upload>
      </div>
      <div class="import-hint-text">
        <p>{{ t('vms.importHint') }}</p>
      </div>
    </template>

    <template v-else-if="state === 'loading'">
      <div class="import-loading">
        <el-progress :percentage="100" :stroke-width="6" striped striped-flow />
        <p>{{ t('import.validating') }}</p>
      </div>
    </template>

    <template v-else-if="state === 'success' && importResult">
      <div class="import-result import-success">
        <el-icon :size="48" color="#67c23a"><CircleCheckFilled /></el-icon>
        <p class="import-result-text">{{ t('import.successMessage', { count: importResult.successCount }) }}</p>
      </div>
    </template>

    <template v-else-if="state === 'error' && importResult">
      <div class="import-result import-error">
        <el-icon :size="48" color="#f56c6c"><WarningFilled /></el-icon>
        <p class="import-result-text">{{ t('import.errorMessage', { count: importResult.errors.length }) }}</p>
        <div class="import-error-table">
          <el-table :data="importResult.errors" max-height="300" size="small">
            <el-table-column prop="row" :label="t('import.colRow')" width="60" />
            <el-table-column prop="field" :label="t('import.colField')" width="130" />
            <el-table-column prop="value" :label="t('import.colValue')" width="150">
              <template #default="{ row }">
                <el-tag v-if="!row.value" type="info" size="small">(empty)</el-tag>
                <span v-else>{{ row.value }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="message" :label="t('import.colMessage')" min-width="180" />
          </el-table>
        </div>
      </div>
    </template>

    <template #footer>
      <template v-if="state === 'select'">
        <el-button @click="handleClose">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!canImport" @click="handleImport">{{ t('import.startBtn') }}</el-button>
      </template>
      <template v-else-if="state === 'loading'">
        <el-button disabled>{{ t('import.importing') }}</el-button>
      </template>
      <template v-else-if="state === 'success'">
        <el-button type="primary" @click="handleDone">{{ t('common.confirm') }}</el-button>
      </template>
      <template v-else-if="state === 'error'">
        <el-button type="primary" @click="handleClose">{{ t('common.close') }}</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<style scoped>
.import-dropzone {
  text-align: center;
  padding: 16px 0;
}
.import-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
.import-hint-text {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  padding: 0 16px;
}
.import-filename {
  font-weight: 600;
  margin-top: 8px;
}
.import-loading {
  text-align: center;
  padding: 40px 0;
}
.import-loading p {
  margin-top: 16px;
  color: #909399;
}
.import-result {
  text-align: center;
  padding: 24px 0;
}
.import-result-text {
  margin-top: 12px;
  font-size: 16px;
}
.import-error-table {
  margin-top: 16px;
  text-align: left;
}
</style>
