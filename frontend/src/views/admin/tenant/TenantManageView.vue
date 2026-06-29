<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listTenants, createTenant, updateTenant, toggleTenantEnabled } from '@/api/tenant/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Pencil, Building2 } from 'lucide-vue-next'
import type { TenantDto, CreateTenantRequest, UpdateTenantRequest } from '@/types/tenant'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const tenants = ref<TenantDto[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogSaving = ref(false)
const editingTenant = ref<TenantDto | null>(null)

const form = ref({
  tenantCode: '',
  tenantName: '',
  deploymentMode: 'CLOUD',
  adminEmail: '',
  adminDisplayName: '',
  adminPassword: '',
})

onMounted(loadTenants)

async function loadTenants() {
  loading.value = true
  try {
    const res = await listTenants()
    tenants.value = res.body
  } catch {
    ElMessage.error(t('tenant.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editingTenant.value = null
  form.value = { tenantCode: '', tenantName: '', deploymentMode: 'CLOUD', adminEmail: '', adminDisplayName: '', adminPassword: '' }
  dialogVisible.value = true
}

function openEdit(tenant: TenantDto) {
  dialogMode.value = 'edit'
  editingTenant.value = tenant
  form.value = { tenantCode: tenant.tenantCode, tenantName: tenant.tenantName, deploymentMode: tenant.deploymentMode, adminEmail: '', adminDisplayName: '', adminPassword: '' }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.value.tenantName.trim()) {
    ElMessage.warning(t('tenant.errors.nameRequired'))
    return
  }
  dialogSaving.value = true
  try {
    if (dialogMode.value === 'create') {
      if (!form.value.tenantCode.trim()) {
        ElMessage.warning(t('tenant.errors.codeRequired'))
        dialogSaving.value = false
        return
      }
      const payload: CreateTenantRequest = {
        tenantCode: form.value.tenantCode,
        tenantName: form.value.tenantName,
        deploymentMode: form.value.deploymentMode,
      }
      if (form.value.adminEmail.trim()) {
        payload.adminEmail = form.value.adminEmail
        payload.adminDisplayName = form.value.adminDisplayName
        payload.adminPassword = form.value.adminPassword
      }
      await createTenant(payload)
      ElMessage.success(t('tenant.createSuccess'))
    } else {
      const payload: UpdateTenantRequest = {
        tenantName: form.value.tenantName,
        deploymentMode: form.value.deploymentMode,
      }
      await updateTenant(editingTenant.value!.tenantId, payload)
      ElMessage.success(t('tenant.updateSuccess'))
    }
    dialogVisible.value = false
    await loadTenants()
  } catch (err: unknown) {
    const code = (err as { response?: { data?: { errorCode?: string } } })?.response?.data?.errorCode
    if (code === '10023') ElMessage.error(t('tenant.errors.codeDuplicate'))
    else if (code === '20015') ElMessage.error(t('tenant.errors.adminEmailExists'))
    else ElMessage.error(dialogMode.value === 'create' ? t('tenant.createFailed') : t('tenant.updateFailed'))
  } finally {
    dialogSaving.value = false
  }
}

async function handleToggle(tenant: TenantDto) {
  const next = !tenant.enabled
  try {
    await ElMessageBox.confirm(
      next ? t('tenant.enableConfirm', { name: tenant.tenantName }) : t('tenant.disableConfirm', { name: tenant.tenantName }),
      t('common.confirm'),
      { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') },
    )
  } catch { return }

  try {
    await toggleTenantEnabled(tenant.tenantId, next)
    ElMessage.success(next ? t('tenant.enableSuccess') : t('tenant.disableSuccess'))
    await loadTenants()
  } catch {
    ElMessage.error(t('common.operationFailed'))
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-header">
        <div class="header-left">
          <Building2 :size="24" class="header-icon" />
          <div>
            <h1 class="page-title">{{ t('tenant.title') }}</h1>
            <p class="page-subtitle">{{ t('tenant.subtitle') }}</p>
          </div>
        </div>
        <el-button class="add-btn" @click="openCreate">
          <Plus :size="14" style="margin-right: 6px" />
          {{ t('tenant.addBtn') }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tenants" class="data-table">
        <el-table-column prop="tenantCode" :label="t('tenant.colCode')" width="150" />
        <el-table-column prop="tenantName" :label="t('tenant.colName')" min-width="200" />
        <el-table-column prop="deploymentMode" :label="t('tenant.colMode')" width="120" align="center" />
        <el-table-column :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="row.enabled ? 'status-success' : 'status-neutral'">
              {{ row.enabled ? t('common.enabled') : t('common.disabled') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="170" />
        <el-table-column :label="t('common.actions')" width="160" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button size="small" class="action-btn action-edit" @click="openEdit(row)">
                <Pencil :size="13" />
              </el-button>
              <el-switch
                :model-value="row.enabled"
                size="small"
                @change="handleToggle(row)"
              />
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('tenant.dialogCreateTitle') : t('tenant.dialogEditTitle')"
      width="520px"
      :close-on-click-modal="false"
    >
      <div class="dialog-form" role="form" :aria-label="dialogMode === 'create' ? t('tenant.dialogCreateTitle') : t('tenant.dialogEditTitle')">
        <div v-if="dialogMode === 'create'" class="form-row">
          <label class="form-label">{{ t('tenant.codeLabel') }} *</label>
          <el-input
            v-model="form.tenantCode"
            :placeholder="t('tenant.codePlaceholder')"
            maxlength="30"
            @input="form.tenantCode = form.tenantCode.toUpperCase().replace(/[^A-Z0-9_]/g, '')"
          />
        </div>
        <div class="form-row">
          <label class="form-label">{{ t('tenant.nameLabel') }} *</label>
          <el-input v-model="form.tenantName" :placeholder="t('tenant.namePlaceholder')" />
        </div>
        <div class="form-row">
          <label class="form-label">{{ t('tenant.modeLabel') }}</label>
          <el-select v-model="form.deploymentMode" style="width: 100%">
            <el-option value="CLOUD" label="CLOUD" />
            <el-option value="ON_PREMISE" label="ON_PREMISE" />
          </el-select>
        </div>

        <template v-if="dialogMode === 'create'">
          <el-divider>{{ t('tenant.adminSection') }}</el-divider>
          <p class="form-hint">{{ t('tenant.adminHint') }}</p>
          <div class="form-row">
            <label class="form-label">{{ t('tenant.adminEmail') }}</label>
            <el-input v-model="form.adminEmail" :placeholder="t('tenant.adminEmailPlaceholder')" />
          </div>
          <div class="form-row">
            <label class="form-label">{{ t('tenant.adminDisplayName') }}</label>
            <el-input v-model="form.adminDisplayName" :placeholder="t('tenant.adminDisplayNamePlaceholder')" />
          </div>
          <div class="form-row">
            <label class="form-label">{{ t('tenant.adminPassword') }}</label>
            <el-input v-model="form.adminPassword" type="password" show-password :placeholder="t('tenant.adminPasswordPlaceholder')" />
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="dialogSaving" @click="handleSubmit">
          {{ dialogMode === 'create' ? t('common.confirm') : t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container { padding: 32px 24px; }
.page-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 24px;
  box-shadow: var(--shadow-card);
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.header-icon { color: var(--color-primary); }
.page-title { font-size: 22px; font-weight: 600; margin: 0; color: var(--text-heading); }
.page-subtitle { font-size: 13px; color: var(--text-muted); margin: 2px 0 0; }
.add-btn {
  background: transparent;
  color: var(--text-primary);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  box-shadow: rgba(0, 0, 0, 0.03) 0px 7px 3px;
}
.add-btn:hover {
  opacity: 0.6;
}
.data-table { width: 100%; }
.action-btns { display: flex; align-items: center; gap: 8px; justify-content: center; }
.action-btn {
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  background: var(--bg-surface);
  cursor: pointer;
  padding: 4px 8px;
  display: flex;
  align-items: center;
  color: var(--text-body);
}
.dialog-form { display: flex; flex-direction: column; gap: 16px; }
.form-row { display: flex; flex-direction: column; gap: 6px; }
.form-label { font-size: 13px; font-weight: 500; color: var(--text-body); }
.form-hint { font-size: 12px; color: var(--text-muted); margin: -8px 0 0; }
</style>
