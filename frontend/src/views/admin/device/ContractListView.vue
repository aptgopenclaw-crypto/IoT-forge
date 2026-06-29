<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listContracts, createContract, updateContract } from '@/api/device'
import type { ContractRequest, ContractResponse } from '@/types/device'

const { t } = useI18n()

const tableData = ref<ContractResponse[]>([])
const loading = ref(false)
const filterStatus = ref('')
const keyword = ref('')
const pagination = reactive({ page: 0, size: 20, total: 0 })

const statusOptions = [
  { value: '', label: t('common.all') },
  { value: 'ACTIVE', label: t('contract.statusActive') },
  { value: 'EXPIRED', label: t('contract.statusExpired') },
  { value: 'TERMINATED', label: t('contract.statusTerminated') },
]

async function fetchList() {
  loading.value = true
  try {
    const res = await listContracts({
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

const getStatusType = (s: string) => {
  if (s === 'ACTIVE') return 'success'
  if (s === 'EXPIRED') return 'warning'
  if (s === 'TERMINATED') return 'danger'
  return 'info'
}

// ── Create / Edit Dialog ──
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref()

const form = reactive<ContractRequest>({
  contractCode: '',
  contractName: '',
  budgetYear: null,
  procurementNumber: '',
  contractorName: '',
  contractorContact: '',
  assetCategory: '',
  quantity: null,
  startDate: null,
  endDate: null,
  acceptanceDate: null,
  warrantyYears: null,
})

function resetForm() {
  form.contractCode = ''
  form.contractName = ''
  form.budgetYear = null
  form.procurementNumber = ''
  form.contractorName = ''
  form.contractorContact = ''
  form.assetCategory = ''
  form.quantity = null
  form.startDate = null
  form.endDate = null
  form.acceptanceDate = null
  form.warrantyYears = null
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: ContractResponse) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.contractCode = row.contractCode
  form.contractName = row.contractName
  form.budgetYear = row.budgetYear ?? null
  form.procurementNumber = row.procurementNumber ?? ''
  form.contractorName = row.contractorName ?? ''
  form.contractorContact = row.contractorContact ?? ''
  form.quantity = row.quantity ?? null
  form.startDate = row.startDate ?? null
  form.endDate = row.endDate ?? null
  form.acceptanceDate = null
  form.warrantyYears = null
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await createContract(form)
      ElMessage.success('Created')
    } else {
      await updateContract(editingId.value!, form)
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
        <h2>{{ t('contract.title') }}</h2>
        <p class="page-subtitle">{{ t('contract.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate">+ {{ t('contract.addBtn') }}</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filterStatus" :placeholder="t('contract.filterStatus')" clearable @change="handleSearch">
        <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-input v-model="keyword" :placeholder="t('contract.searchPlaceholder')" clearable @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-button type="primary" @click="handleSearch">{{ t('device.searchBtn') }}</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="contractCode" :label="t('contract.colCode')" width="140" />
      <el-table-column prop="contractName" :label="t('contract.colName')" min-width="220" />
      <el-table-column prop="contractorName" :label="t('contract.colContractor')" width="160" />
      <el-table-column :label="t('contract.colBudgetYear')" width="100">
        <template #default="{ row }">{{ row.budgetYear ?? '-' }}</template>
      </el-table-column>
      <el-table-column :label="t('contract.colStartDate')" width="110">
        <template #default="{ row }">{{ row.startDate ?? '-' }}</template>
      </el-table-column>
      <el-table-column :label="t('contract.colEndDate')" width="110">
        <template #default="{ row }">{{ row.endDate ?? '-' }}</template>
      </el-table-column>
      <el-table-column :label="t('contract.colWarrantyExpiry')" width="130">
        <template #default="{ row }">{{ row.warrantyExpiry ?? '-' }}</template>
      </el-table-column>
      <el-table-column :label="t('contract.colStatus')" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" fixed="right" width="180">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('contract.addBtn') : t('common.edit')"
      width="600px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Contract Code" required>
              <el-input v-model="form.contractCode" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Contract Name" required>
              <el-input v-model="form.contractName" maxlength="300" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Contractor Name">
              <el-input v-model="form.contractorName" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Contractor Contact">
              <el-input v-model="form.contractorContact" maxlength="200" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="Budget Year">
              <el-input-number v-model="form.budgetYear" :min="2000" :max="2100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Procurement No.">
              <el-input v-model="form.procurementNumber" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Quantity">
              <el-input-number v-model="form.quantity" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Start Date">
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="End Date">
              <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Asset Category">
              <el-input v-model="form.assetCategory" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Warranty (years)">
              <el-input-number v-model="form.warrantyYears" :min="0" :max="50" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Acceptance Date">
          <el-date-picker v-model="form.acceptanceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
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
.filter-bar .el-select {
  width: 160px;
}
.filter-bar .el-input {
  width: 240px;
}
</style>
