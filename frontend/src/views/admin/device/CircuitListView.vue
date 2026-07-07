<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCircuits, createCircuit, updateCircuit, deleteCircuit } from '@/api/device'
import type { CircuitRequest, CircuitResponse } from '@/types/device'

const { t } = useI18n()

const tableData = ref<CircuitResponse[]>([])
const loading = ref(false)
const keyword = ref('')
const pagination = reactive({ page: 0, size: 20, total: 0 })

async function fetchList() {
  loading.value = true
  try {
    const res = await listCircuits({
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

async function handleDelete(row: CircuitResponse) {
  try {
    await ElMessageBox.confirm('Delete this circuit?', 'Confirm', {
      confirmButtonText: 'Confirm',
      cancelButtonText: 'Cancel',
      type: 'warning',
    })
    await deleteCircuit(row.id)
    ElMessage.success('Deleted')
    fetchList()
  } catch {
    // cancelled
  }
}

// ── Create / Edit Dialog ──
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref()

const form = reactive<CircuitRequest>({
  circuitNumber: '',
  circuitName: '',
  taipowerAccount: '',
  usageType: '',
  panelBoxDeviceId: null,
})

function resetForm() {
  form.circuitNumber = ''
  form.circuitName = ''
  form.taipowerAccount = ''
  form.usageType = ''
  form.panelBoxDeviceId = null
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: CircuitResponse) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.circuitNumber = row.circuitNumber
  form.circuitName = row.circuitName ?? ''
  form.taipowerAccount = row.taipowerAccount ?? ''
  form.usageType = row.usageType ?? ''
  form.panelBoxDeviceId = row.panelBoxDeviceId ?? null
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await createCircuit(form)
      ElMessage.success('Created')
    } else {
      await updateCircuit(editingId.value!, form)
      ElMessage.success('Updated')
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    // error handled by interceptor
  } finally {
    saving.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>{{ t('circuit.title') }}</h2>
        <p class="page-subtitle">{{ t('circuit.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate">+ {{ t('circuit.addBtn') }}</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="keyword" :placeholder="t('circuit.searchPlaceholder')" clearable @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-button type="primary" @click="handleSearch">{{ t('device.searchBtn') }}</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="circuitNumber" :label="t('circuit.colNumber')" width="140" />
      <el-table-column prop="circuitName" :label="t('circuit.colName')" min-width="200" />
      <el-table-column prop="taipowerAccount" :label="t('circuit.colTaipower')" width="140" />
      <el-table-column prop="usageType" :label="t('circuit.colUsageType')" width="120" />
      <el-table-column :label="t('circuit.colStatus')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" fixed="right" width="180">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('circuit.addBtn') : t('common.edit')"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" label-position="top">
        <el-form-item :label="t('circuit.colNumber')" required>
          <el-input v-model="form.circuitNumber" maxlength="50" />
        </el-form-item>
        <el-form-item :label="t('circuit.colName')">
          <el-input v-model="form.circuitName" maxlength="200" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('circuit.colTaipower')">
              <el-input v-model="form.taipowerAccount" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('circuit.colUsageType')">
              <el-input v-model="form.usageType" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('circuit.panelBoxDeviceId')">
          <el-input-number v-model="form.panelBoxDeviceId" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

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
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-bar .el-input {
  width: 300px;
}
</style>
