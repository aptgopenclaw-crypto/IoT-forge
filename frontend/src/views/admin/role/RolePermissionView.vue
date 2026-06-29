<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  listRoles,
  getRolePermissions,
  listPermissions,
  createRole,
  updateRole,
  toggleRoleEnabled,
  assignRolePermissions,
} from '@/api/rbac'
import { ElMessage } from 'element-plus'
import { Shield, ChevronRight, Plus, Pencil } from 'lucide-vue-next'
import type { RoleDto, PermissionDto } from '@/types/rbac'
import { useI18n } from 'vue-i18n'

const { t, te } = useI18n()

// 將 roleId 對應到 i18n 顯示名稱；無對應條目時回退至後端的 role.name
const localizedRoleName = (role: { roleId?: string; name: string }) => {
  const key = role.roleId ? `role.displayNames.${role.roleId}` : ''
  return key && te(key) ? t(key) : role.name
}

// ---- Role list ----
const roles = ref<RoleDto[]>([])
const loading = ref(false)
const selectedRole = ref<RoleDto | null>(null)

// ---- Permission panel ----
const allPermissions = ref<PermissionDto[]>([])
const checkedPermIds = ref<Set<string>>(new Set())
const originalPermIds = ref<Set<string>>(new Set())
const permLoading = ref(false)
const saving = ref(false)

// ---- Role dialog ----
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const roleForm = ref({
  code: '',
  name: '',
  description: '',
  enabled: true,
  dataScope: 'ALL',
})
const dialogSaving = ref(false)

const permsDirty = computed(() => {
  if (!selectedRole.value) return false
  if (checkedPermIds.value.size !== originalPermIds.value.size) return true
  for (const id of checkedPermIds.value) {
    if (!originalPermIds.value.has(id)) return true
  }
  return false
})

// Group all permissions by groupName
const groupedAllPermissions = computed(() => {
  const map = new Map<string, PermissionDto[]>()
  for (const p of allPermissions.value) {
    const group = p.groupName || t('common.other')
    if (!map.has(group)) map.set(group, [])
    map.get(group)!.push(p)
  }
  return map
})

onMounted(async () => {
  await Promise.all([loadRoles(), loadAllPermissions()])
})

async function loadRoles() {
  loading.value = true
  try {
    const res = await listRoles()
    roles.value = (res.body as RoleDto[]).filter(r => r.code !== 'SUPER_ADMIN')
  } catch {
    ElMessage.error(t('role.loadRolesFailed'))
  } finally {
    loading.value = false
  }
}

async function loadAllPermissions() {
  try {
    const res = await listPermissions()
    allPermissions.value = res.body
  } catch {
    ElMessage.error(t('role.loadAllPermsFailed'))
  }
}

async function handleSelectRole(role: RoleDto) {
  selectedRole.value = role
  permLoading.value = true
  try {
    const res = await getRolePermissions(role.roleId)
    const ids = new Set(res.body.permissions.map((p: PermissionDto) => p.permissionId))
    checkedPermIds.value = new Set(ids)
    originalPermIds.value = new Set(ids)
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    if (errorCode === '30003') {
      ElMessage.error(t('role.roleNotFound'))
    } else {
      ElMessage.error(t('role.loadPermsFailed'))
    }
    checkedPermIds.value = new Set()
    originalPermIds.value = new Set()
  } finally {
    permLoading.value = false
  }
}

function togglePermission(permId: string) {
  const next = new Set(checkedPermIds.value)
  if (next.has(permId)) {
    next.delete(permId)
  } else {
    next.add(permId)
  }
  checkedPermIds.value = next
}

async function handleSavePermissions() {
  if (!selectedRole.value) return
  saving.value = true
  try {
    await assignRolePermissions(selectedRole.value.roleId, {
      permissionIds: Array.from(checkedPermIds.value),
    })
    originalPermIds.value = new Set(checkedPermIds.value)
    ElMessage.success(t('role.savePermsSuccess'))
  } catch {
    ElMessage.error(t('role.savePermsFailed'))
  } finally {
    saving.value = false
  }
}

async function handleToggleEnabled(role: RoleDto) {
  const newEnabled = !role.enabled
  const msg = newEnabled ? t('role.enableSuccess') : t('role.disableSuccess')
  try {
    await toggleRoleEnabled(role.roleId, newEnabled)
    role.enabled = newEnabled
    ElMessage.success(msg)
  } catch {
    ElMessage.error(t('role.toggleFailed'))
  }
}

function openCreateDialog() {
  dialogMode.value = 'create'
  roleForm.value = { code: '', name: '', description: '', enabled: true, dataScope: 'ALL' }
  dialogVisible.value = true
}

function openEditDialog(role: RoleDto) {
  dialogMode.value = 'edit'
  roleForm.value = {
    code: role.code,
    name: role.name,
    description: role.description || '',
    enabled: role.enabled,
    dataScope: role.dataScope || 'ALL',
  }
  dialogVisible.value = true
}

async function handleDialogSubmit() {
  if (!roleForm.value.name.trim()) {
    ElMessage.warning(t('role.errors.nameRequired'))
    return
  }
  dialogSaving.value = true
  try {
    if (dialogMode.value === 'create') {
      if (!roleForm.value.code.trim()) {
        ElMessage.warning(t('role.errors.codeRequired'))
        dialogSaving.value = false
        return
      }
      await createRole({
        code: roleForm.value.code,
        name: roleForm.value.name,
        description: roleForm.value.description,
        dataScope: roleForm.value.dataScope,
      })
      ElMessage.success(t('role.createdSuccess'))
    } else {
      if (!selectedRole.value) return
      await updateRole(selectedRole.value.roleId, {
        name: roleForm.value.name,
        description: roleForm.value.description,
        enabled: roleForm.value.enabled,
        dataScope: roleForm.value.dataScope,
      })
      ElMessage.success(t('role.updatedSuccess'))
    }
    dialogVisible.value = false
    await loadRoles()
    // Reselect if editing current
    if (dialogMode.value === 'edit' && selectedRole.value) {
      const updated = roles.value.find((r) => r.roleId === selectedRole.value!.roleId)
      if (updated) selectedRole.value = updated
    }
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    if (errorCode && t(`role.errors.${errorCode}`) !== `role.errors.${errorCode}`) {
      ElMessage.error(t(`role.errors.${errorCode}`))
    } else {
      ElMessage.error(dialogMode.value === 'create' ? t('role.createFailed') : t('role.updateFailed'))
    }
  } finally {
    dialogSaving.value = false
  }
}
</script>

<template>
  <div class="role-permission-page">
    <div class="page-header">
      <h1 class="page-title">{{ t('role.title') }}</h1>
    </div>

    <div class="content-grid">
      <!-- Role List -->
      <div class="card role-list-card">
        <div class="card-header">
          <h2 class="card-title">{{ t('role.cardRoles') }}</h2>
          <button class="add-btn" @click="openCreateDialog">
            <Plus :size="14" />
            {{ t('role.addBtn') }}
          </button>
        </div>
        <div v-if="loading" class="loading-state">{{ t('common.loading') }}</div>
        <div v-else class="role-list">
          <div
            v-for="role in roles"
            :key="role.roleId"
            :class="['role-item', { active: selectedRole?.roleId === role.roleId }]"
            @click="handleSelectRole(role)"
          >
            <div class="role-icon">
              <Shield :size="16" />
            </div>
            <div class="role-info">
              <div class="role-name-row">
                <span class="role-name">{{ localizedRoleName(role) }}</span>
                <span v-if="role.builtIn" class="badge badge-builtin">{{ t('role.builtInBadge') }}</span>
                <span v-if="!role.enabled" class="badge badge-disabled">{{ t('role.disabledBadge') }}</span>
              </div>
              <span class="role-code">{{ role.code }}</span>
            </div>
            <div class="role-actions" @click.stop>
              <button
                v-if="!role.builtIn"
                class="icon-btn"
                :title="t('common.edit')"
                @click="selectedRole = role; openEditDialog(role)"
              >
                <Pencil :size="13" />
              </button>
              <el-switch
                v-if="!role.builtIn"
                :model-value="role.enabled"
                size="small"
                @change="handleToggleEnabled(role)"
              />
            </div>
            <ChevronRight :size="14" class="role-arrow" />
          </div>
        </div>
      </div>

      <!-- Permission Detail -->
      <div class="card perm-detail-card">
        <div class="card-header">
          <h2 class="card-title">
            {{ selectedRole ? t('role.permTitle', { code: selectedRole.code }) : t('role.permSelectHint') }}
          </h2>
          <button
            v-if="selectedRole && permsDirty"
            class="save-btn"
            :disabled="saving"
            @click="handleSavePermissions"
          >
            {{ saving ? t('common.saving') : t('role.savePerms') }}
          </button>
        </div>

        <div v-if="!selectedRole" class="empty-state">
          {{ t('role.permClickHint') }}
        </div>
        <div v-else-if="permLoading" class="loading-state">{{ t('common.loading') }}</div>
        <div v-else-if="!allPermissions.length" class="empty-state">
          {{ t('role.noPerms') }}
        </div>
        <div v-else class="perm-groups">
          <div v-for="[groupName, perms] in groupedAllPermissions" :key="groupName" class="perm-group">
            <h3 class="group-title">{{ groupName }}</h3>
            <div class="perm-items">
              <div v-for="p in perms" :key="p.permissionId" class="perm-item">
                <el-checkbox
                  :model-value="checkedPermIds.has(p.permissionId)"
                  @change="togglePermission(p.permissionId)"
                />
                <span class="perm-name">{{ p.name }}</span>
                <span class="perm-code">{{ p.code }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('role.dialogCreateTitle') : t('role.dialogEditTitle')"
      width="480px"
      :close-on-click-modal="false"
    >
      <div class="dialog-form" role="form" :aria-label="dialogMode === 'create' ? t('role.dialogCreateTitle') : t('role.dialogEditTitle')">
        <div v-if="dialogMode === 'create'" class="form-row">
          <label class="form-label">{{ t('role.codeLabel') }}</label>
          <el-input v-model="roleForm.code" :placeholder="t('role.codePlaceholder')" />
        </div>
        <div class="form-row">
          <label class="form-label">{{ t('role.nameLabel') }}</label>
          <el-input v-model="roleForm.name" :placeholder="t('role.namePlaceholder')" />
        </div>
        <div class="form-row">
          <label class="form-label">{{ t('role.descLabel') }}</label>
          <el-input
            v-model="roleForm.description"
            type="textarea"
            :rows="3"
            :placeholder="t('role.descPlaceholder')"
          />
        </div>
        <div class="form-row">
          <label class="form-label">{{ t('role.dataScopeLabel') }}</label>
          <el-select v-model="roleForm.dataScope" style="width: 100%">
            <el-option value="ALL" :label="t('role.dataScopeAll')" />
            <el-option value="THIS_LEVEL" :label="t('role.dataScopeThisLevel')" />
            <el-option value="THIS_LEVEL_AND_BELOW" :label="t('role.dataScopeThisAndBelow')" />
          </el-select>
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="dialogSaving" @click="handleDialogSubmit">
          {{ dialogMode === 'create' ? t('common.confirm') : t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.role-permission-page {
  padding: 32px 24px;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.15;
  color: var(--text-heading);
  margin: 0;
}

.content-grid {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 20px;
}

.card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: var(--shadow-card);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.card-title {
  font-size: 18px;
  font-weight: 500;
  color: var(--text-heading);
  letter-spacing: 0.2px;
  line-height: 1.25;
  margin: 0;
}

.add-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid var(--border-light);
  background: transparent;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 150ms ease;
}
.add-btn:hover {
  background: var(--bg-hover);
}

.save-btn {
  padding: 6px 16px;
  border-radius: 6px;
  border: none;
  background: #55b3ff;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 150ms ease;
}
.save-btn:hover {
  opacity: 0.9;
}
.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 40px 0;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.2px;
}

/* Role List */
.role-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.role-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 150ms ease;
}

.role-item:hover {
  background: var(--bg-hover);
}

.role-item.active {
  background: var(--bg-active);
  border-left: 2px solid #FF6363;
}

.role-icon {
  color: var(--text-secondary);
  display: flex;
  align-items: center;
}

.role-item.active .role-icon {
  color: #55b3ff;
}

.role-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.role-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.role-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: 0.2px;
}

.role-code {
  font-family: 'GeistMono', 'JetBrains Mono', monospace;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-muted);
  letter-spacing: 0.2px;
}

.badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.3px;
  text-transform: uppercase;
}
.badge-builtin {
  background: rgba(85, 179, 255, 0.15);
  color: #55b3ff;
}
.badge-disabled {
  background: rgba(255, 99, 99, 0.15);
  color: #FF6363;
}

.role-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 150ms ease;
}
.icon-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.role-arrow {
  color: var(--text-muted);
}

/* Permission Groups */
.perm-groups {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.group-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  margin: 0 0 8px 0;
}

.perm-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.perm-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.perm-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: 0.2px;
}

.perm-code {
  font-family: 'GeistMono', 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 400;
  color: var(--text-muted);
  letter-spacing: 0.2px;
}

/* Checkbox override */
:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: #55b3ff;
  border-color: #55b3ff;
}

/* Dialog form */
.dialog-form {
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
  letter-spacing: 0.2px;
}
</style>
