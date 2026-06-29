<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/userStore'
import { useAuthStore } from '@/stores/authStore'
import { addTenantRole, removeTenantRole } from '@/api/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Trash2, Globe, Shield, Building2 } from 'lucide-vue-next'
import DeptTreeSelector from '@/components/DeptTreeSelector.vue'
import type { UserTenantMappingDto } from '@/types/user'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  userId: string
}>()

const userStore = useUserStore()
const authStore = useAuthStore()
const loading = ref(false)
const dialogVisible = ref(false)

const newMapping = ref({
  tenantId: null as string | null,
  roleId: '',
  deptId: null as number | null,
})

const availableTenants = authStore.userInfo?.availableTenants ?? []
const isSuperAdmin = authStore.userInfo?.isSuperAdmin ?? false

onMounted(() => {
  loadTenantRoles()
})

async function loadTenantRoles() {
  loading.value = true
  try {
    await userStore.fetchTenantRoles(props.userId)
  } catch {
    ElMessage.error(t('tenantRole.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openAddDialog() {
  newMapping.value = { tenantId: null, roleId: '', deptId: null as number | null }
  dialogVisible.value = true
}

async function handleAdd() {
  if (!newMapping.value.roleId) {
    ElMessage.warning(t('tenantRole.roleIdRequired'))
    return
  }

  try {
    await addTenantRole(props.userId, {
      tenantId: isSuperAdmin ? newMapping.value.tenantId : null,
      roleId: newMapping.value.roleId,
      deptId: newMapping.value.deptId || null,
    })
    ElMessage.success(t('tenantRole.addedSuccess'))
    dialogVisible.value = false
    await loadTenantRoles()
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    if (errorCode === '10010') {
      ElMessage.error(t('tenantRole.errors.10010'))
    } else if (errorCode === '20005') {
      ElMessage.error(t('tenantRole.errors.20005'))
    } else {
      ElMessage.error(t('tenantRole.addFailed'))
    }
  }
}

async function handleRemove(mapping: UserTenantMappingDto) {
  try {
    await ElMessageBox.confirm(
      t('tenantRole.removeConfirm', { name: mapping.tenantName }),
      t('tenantRole.removeTitle'),
      {
        confirmButtonText: t('tenantRole.confirmRemove'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await removeTenantRole(props.userId, mapping.mappingId)
    ElMessage.success(t('tenantRole.removedSuccess'))
    await loadTenantRoles()
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    if (errorCode === '20018') {
      ElMessage.error(t('tenantRole.errors.20018'))
    } else if (errorCode === '10010') {
      ElMessage.error(t('tenantRole.errors.10010'))
    } else {
      ElMessage.error(t('tenantRole.removeFailed'))
    }
  }
}

function getEnabledType(enabled: boolean) {
  return enabled ? 'success' : 'danger'
}

function getEnabledLabel(enabled: boolean) {
  return enabled ? t('tenantRole.statusEnabled') : t('tenantRole.statusDisabled')
}
</script>

<template>
  <div class="tenant-role-card" v-loading="loading">
    <div class="card-header">
      <div>
        <h2 class="card-title">{{ t('tenantRole.cardTitle') }}</h2>
        <p class="card-subtitle">{{ t('tenantRole.cardSubtitle') }}</p>
      </div>
      <el-button class="add-btn" @click="openAddDialog">
        <Plus :size="14" style="margin-right: 6px" />
        {{ t('tenantRole.addBtn') }}
      </el-button>
    </div>

    <div v-if="userStore.tenantRoles.length === 0" class="empty-state">
      {{ t('tenantRole.empty') }}
    </div>

    <div v-else class="mapping-list">
      <div
        v-for="mapping in userStore.tenantRoles"
        :key="mapping.mappingId"
        class="mapping-item"
      >
        <div class="mapping-info">
          <div class="mapping-primary">
            <Globe :size="14" class="mapping-icon" />
            <span class="mapping-tenant">{{ mapping.tenantName }}</span>
            <span class="status-badge" :class="'status-' + getEnabledType(mapping.enabled)">
              {{ getEnabledLabel(mapping.enabled) }}
            </span>
          </div>
          <div class="mapping-secondary">
            <span class="mapping-detail">
              <Shield :size="12" class="detail-icon" />
              {{ mapping.roleName }}
            </span>
            <span v-if="mapping.deptName" class="mapping-detail">
              <Building2 :size="12" class="detail-icon" />
              {{ mapping.deptName }}
            </span>
          </div>
        </div>
        <el-button
          class="remove-btn"
          size="small"
          @click="handleRemove(mapping)"
        >
          <Trash2 :size="14" />
        </el-button>
      </div>
    </div>

    <!-- Add dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="t('tenantRole.addDialogTitle')"
      width="420px"
      :close-on-click-modal="false"
      class="dark-dialog"
    >
      <div class="dialog-form">
        <div v-if="isSuperAdmin" class="dialog-field">
          <label class="dialog-label">{{ t('tenantRole.tenantLabel') }}</label>
          <el-select
            v-model="newMapping.tenantId"
            :placeholder="t('tenantRole.tenantPlaceholder')"
            clearable
            class="full-width"
          >
            <el-option
              v-for="t in availableTenants"
              :key="t.tenantId"
              :label="t.tenantName"
              :value="t.tenantId"
            />
          </el-select>
        </div>

        <div class="dialog-field">
          <label class="dialog-label">{{ t('tenantRole.roleIdLabel') }}</label>
          <el-input v-model="newMapping.roleId" :placeholder="t('tenantRole.roleIdPlaceholder')" />
        </div>

        <div class="dialog-field">
          <label class="dialog-label">{{ t('tenantRole.deptLabel') }}</label>
          <DeptTreeSelector v-model="newMapping.deptId" :placeholder="t('tenantRole.deptPlaceholder')" />
        </div>
      </div>

      <template #footer>
        <el-button class="cancel-btn" @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button class="submit-btn" @click="handleAdd">{{ t('tenantRole.addBtn') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tenant-role-card {
  background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 32px;
  box-shadow: var(--shadow-card);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.card-title {
  font-size: 18px;
  font-weight: 500;
  line-height: 1.25;
  letter-spacing: 0.2px;
  color: var(--text-heading);
  margin: 0 0 4px 0;
}

.card-subtitle {
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
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.3px;
  display: flex;
  align-items: center;
  box-shadow: rgba(0, 0, 0, 0.03) 0px 7px 3px;
}

.add-btn:hover {
  opacity: 0.6;
}

.empty-state {
  text-align: center;
  padding: 32px;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.2px;
}

.mapping-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mapping-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: var(--bg-base);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
}

.mapping-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mapping-primary {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mapping-icon {
  color: #55b3ff;
}

.mapping-tenant {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: 0.2px;
}

.mapping-secondary {
  display: flex;
  gap: 16px;
  padding-left: 22px;
}

.mapping-detail {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
  letter-spacing: 0.2px;
}

.detail-icon {
  color: var(--text-muted);
}

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

.remove-btn {
  background: transparent;
  color: #FF6363;
  border: 1px solid rgba(255, 99, 99, 0.2);
  border-radius: 6px;
  padding: 4px 8px;
  min-width: auto;
}

.remove-btn:hover {
  background: rgba(255, 99, 99, 0.15);
  color: #FF6363;
}

/* Dialog */
.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dialog-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dialog-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-label);
  letter-spacing: 0.2px;
}

.full-width {
  width: 100%;
}

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
:deep(.el-input__wrapper) {
  background-color: var(--bg-base);
  border: 1px solid var(--border-medium);
  border-radius: 8px;
  box-shadow: none;
}

:deep(.el-input__wrapper:hover) {
  border-color: var(--border-strong);
}

:deep(.el-input__wrapper.is-focus) {
  border-color: rgba(85, 179, 255, 0.5);
  box-shadow: 0 0 0 3px rgba(85, 179, 255, 0.15);
}

:deep(.el-input__inner) {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.2px;
}

:deep(.el-input__inner::placeholder) {
  color: var(--text-muted);
}

:deep(.el-select .el-input__wrapper) {
  background-color: var(--bg-base);
  border: 1px solid var(--border-medium);
  box-shadow: none;
}

:deep(.el-dialog) {
  background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
}

:deep(.el-dialog__header) {
  padding: 20px 24px 0;
}

:deep(.el-dialog__title) {
  color: var(--text-heading);
  font-size: 18px;
  font-weight: 500;
}

:deep(.el-dialog__body) {
  padding: 20px 24px;
}

:deep(.el-dialog__footer) {
  padding: 0 24px 20px;
}
</style>
