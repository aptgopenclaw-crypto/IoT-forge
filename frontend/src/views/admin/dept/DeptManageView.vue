<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getDeptTree, createDept, updateDept, deleteDept } from '@/api/dept'
import { useDeptStore } from '@/stores/deptStore'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { DeptDto, CreateDeptRequest, UpdateDeptRequest } from '@/types/dept'
import { Plus, Pencil, Trash2, FolderTree } from 'lucide-vue-next'
import DeptTreeSelector from '@/components/DeptTreeSelector.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const deptStore = useDeptStore()
const loading = ref(false)
const treeData = ref<DeptDto[]>([])

// Dialog state
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => dialogMode.value === 'create' ? t('dept.dialogCreateTitle') : t('dept.dialogEditTitle'))
const dialogFormRef = ref<FormInstance>()
const dialogLoading = ref(false)

const dialogForm = ref({
  deptId: null as number | null,
  deptName: '',
  pid: null as number | null,
  deptSort: 0,
  status: 1,
})

const dialogRules = computed<FormRules>(() => ({
  deptName: [
    { required: true, message: t('dept.errors.deptNameRequired'), trigger: 'blur' },
    { max: 100, message: t('dept.errors.deptNameMaxLen'), trigger: 'blur' },
  ],
}))

const errorCodeMessages = computed<Record<string, string>>(() => ({
  '40001': t('dept.errors.40001'),
  '40002': t('dept.errors.40002'),
  '40003': t('dept.errors.40003'),
  '40004': t('dept.errors.40004'),
  '10010': t('dept.errors.10010'),
}))

async function loadTree() {
  loading.value = true
  try {
    const res = await getDeptTree()
    treeData.value = res.body
  } catch {
    ElMessage.error(t('dept.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreateDialog(parentId: number | null = null) {
  dialogMode.value = 'create'
  dialogForm.value = {
    deptId: null,
    deptName: '',
    pid: parentId,
    deptSort: 0,
    status: 1,
  }
  dialogVisible.value = true
}

function openEditDialog(row: DeptDto) {
  dialogMode.value = 'edit'
  dialogForm.value = {
    deptId: row.id,
    deptName: row.deptName,
    pid: row.pid,
    deptSort: row.deptSort ?? 0,
    status: row.status,
  }
  dialogVisible.value = true
}

async function handleDialogSubmit() {
  const valid = await dialogFormRef.value?.validate().catch(() => false)
  if (!valid) return

  dialogLoading.value = true
  try {
    if (dialogMode.value === 'create') {
      const payload: CreateDeptRequest = {
        deptName: dialogForm.value.deptName,
        pid: dialogForm.value.pid,
        deptSort: dialogForm.value.deptSort,
      }
      await createDept(payload)
      ElMessage.success(t('dept.createdSuccess'))
    } else {
      const payload: UpdateDeptRequest = {
        deptId: dialogForm.value.deptId!,
        deptName: dialogForm.value.deptName,
        deptSort: dialogForm.value.deptSort,
        status: dialogForm.value.status,
      }
      await updateDept(payload)
      ElMessage.success(t('dept.updatedSuccess'))
    }
    dialogVisible.value = false
    await loadTree()
    await deptStore.fetchDeptOptions()
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    const msg = (errorCode && errorCodeMessages.value[errorCode]) || t('common.operationFailed')
    ElMessage.error(msg)
  } finally {
    dialogLoading.value = false
  }
}

async function handleDelete(row: DeptDto) {
  try {
    await ElMessageBox.confirm(
      t('dept.deleteConfirm', { name: row.deptName }),
      t('dept.deleteTitle'),
      {
        confirmButtonText: t('dept.confirmDelete'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )
  } catch {
    return
  }

  loading.value = true
  try {
    await deleteDept(row.id)
    ElMessage.success(t('dept.deletedSuccess'))
    await loadTree()
    await deptStore.fetchDeptOptions()
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    const msg = (errorCode && errorCodeMessages.value[errorCode]) || t('dept.deleteFailed')
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

function getStatusLabel(status: number) {
  return status === 1 ? t('dept.statusEnabled') : t('dept.statusDisabled')
}

function getStatusType(status: number) {
  return status === 1 ? 'success' : 'danger'
}

onMounted(() => {
  loadTree()
})
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <!-- Header -->
      <div class="page-header">
        <div class="header-left">
          <FolderTree :size="24" class="header-icon" />
          <div>
            <h1 class="page-title">{{ t('dept.title') }}</h1>
            <p class="page-subtitle">{{ t('dept.subtitle') }}</p>
          </div>
        </div>
        <el-button class="add-btn" @click="openCreateDialog(null)">
          <Plus :size="14" style="margin-right: 6px" />
          {{ t('dept.addBtn') }}
        </el-button>
      </div>

      <!-- Tree Table -->
      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children' }"
        :default-expand-all="true"
        class="dept-table"
      >
        <el-table-column prop="deptName" :label="t('dept.colName')" min-width="240">
          <template #default="{ row }">
            <span class="dept-name">{{ row.deptName }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="deptSort" :label="t('dept.colSort')" width="80" align="center" />

        <el-table-column prop="status" :label="t('dept.colStatus')" width="100" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="'status-' + getStatusType(row.status)">
              {{ getStatusLabel(row.status) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" :label="t('dept.colCreateTime')" width="180">
          <template #default="{ row }">
            <span class="time-text">{{ row.createTime ?? '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column :label="t('dept.colActions')" width="200" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button class="action-btn action-add" size="small" @click="openCreateDialog(row.id)">
                <Plus :size="14" />
              </el-button>
              <el-button class="action-btn action-edit" size="small" @click="openEditDialog(row)">
                <Pencil :size="14" />
              </el-button>
              <el-button class="action-btn action-delete" size="small" @click="handleDelete(row)">
                <Trash2 :size="14" />
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Create / Edit Dialog  -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="480px"
      :close-on-click-modal="false"
      class="dark-dialog"
    >
      <el-form
        ref="dialogFormRef"
        :model="dialogForm"
        :rules="dialogRules"
        label-position="top"
        @submit.prevent="handleDialogSubmit"
      >
        <el-form-item :label="t('dept.parentDeptLabel')">
          <DeptTreeSelector v-model="dialogForm.pid" :placeholder="t('dept.parentDeptPlaceholder')" />
        </el-form-item>

        <el-form-item :label="t('dept.deptNameLabel')" prop="deptName">
          <el-input v-model="dialogForm.deptName" :placeholder="t('dept.deptNamePlaceholder')" />
        </el-form-item>

        <el-form-item :label="t('dept.sortLabel')">
          <el-input-number v-model="dialogForm.deptSort" :min="0" :max="999" />
        </el-form-item>

        <el-form-item v-if="dialogMode === 'edit'" :label="t('dept.statusLabel')">
          <el-radio-group v-model="dialogForm.status">
            <el-radio :value="1">{{ t('dept.statusEnabled') }}</el-radio>
            <el-radio :value="0">{{ t('dept.statusDisabled') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button class="cancel-btn" @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          class="submit-btn"
          :loading="dialogLoading"
          @click="handleDialogSubmit"
        >
          {{ dialogMode === 'create' ? t('dept.confirmCreate') : t('dept.confirmEdit') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  padding: 32px 24px;
  min-height: 100vh;
  background-color: var(--bg-base);
}

.page-card {
  background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 24px;
  box-shadow: var(--shadow-card);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  color: #55b3ff;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: 0.2px;
  color: var(--text-heading);
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.6;
  letter-spacing: 0.2px;
  color: var(--text-secondary);
  margin: 0;
}

.add-btn {
  background: transparent;
  color: var(--text-primary);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
  display: flex;
  align-items: center;
  box-shadow: rgba(0, 0, 0, 0.03) 0px 7px 3px;
}

.add-btn:hover {
  opacity: 0.6;
}

/* Tree Table */
.dept-table {
  --el-table-bg-color: var(--bg-surface);
  --el-table-tr-bg-color: var(--bg-surface);
  --el-table-header-bg-color: var(--bg-surface);
  --el-table-header-text-color: var(--text-secondary);
  --el-table-text-color: var(--text-primary);
  --el-table-border-color: var(--bg-active);
  --el-table-row-hover-bg-color: var(--bg-hover-subtle);
  --el-fill-color-lighter: var(--bg-surface);
}

.dept-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: 0.2px;
}

.time-text {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
  letter-spacing: 0.2px;
}

/* Status Badge */
.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.status-success {
  background: rgba(95, 201, 146, 0.15);
  color: #5fc992;
}

.status-danger {
  background: rgba(255, 99, 99, 0.15);
  color: #FF6363;
}

/* Action Buttons */
.action-btns {
  display: flex;
  gap: 6px;
  justify-content: center;
}

.action-btn {
  padding: 4px 8px;
  min-width: auto;
  background: transparent;
  border-radius: 6px;
}

.action-add {
  color: #55b3ff;
  border: 1px solid rgba(85, 179, 255, 0.2);
}

.action-add:hover {
  background: rgba(85, 179, 255, 0.15);
  color: #55b3ff;
}

.action-edit {
  color: #ffbc33;
  border: 1px solid rgba(255, 188, 51, 0.2);
}

.action-edit:hover {
  background: rgba(255, 188, 51, 0.15);
  color: #ffbc33;
}

.action-delete {
  color: #FF6363;
  border: 1px solid rgba(255, 99, 99, 0.2);
}

.action-delete:hover {
  background: rgba(255, 99, 99, 0.15);
  color: #FF6363;
}

/* Dialog Buttons */
.cancel-btn {
  background: transparent;
  color: var(--text-secondary);
  border: none;
  border-radius: 6px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.cancel-btn:hover {
  opacity: 0.6;
  color: var(--text-primary);
}

.submit-btn {
  background: var(--btn-primary-bg);
  color: var(--btn-primary-text);
  border: none;
  border-radius: 86px;
  padding: 8px 24px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
  transition: opacity 150ms ease;
}

.submit-btn:hover {
  background: var(--btn-primary-hover);
  color: var(--btn-primary-text);
}

/* Element Plus dark overrides */
:deep(.el-table__expand-icon) {
  color: var(--text-secondary);
}

:deep(.el-input-number) {
  width: 100%;
}

:deep(.el-radio__label) {
  color: var(--text-label);
}

:deep(.el-radio__input.is-checked .el-radio__inner) {
  background-color: #55b3ff;
  border-color: #55b3ff;
}

:deep(.el-radio__input.is-checked + .el-radio__label) {
  color: var(--text-primary);
}
</style>
