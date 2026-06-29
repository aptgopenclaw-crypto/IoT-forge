<script setup lang="ts">
/**
 * Tenant-level password policy settings (TENANT_ADMIN / SUPER_ADMIN).
 *
 * Shows the *effective* merged policy and lets the admin either override a key
 * (with the platform floor enforced) or "use platform default" (DELETE the
 * override row). The "is customised?" badge is driven by the raw overrides map.
 */
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Pencil, RotateCcw } from 'lucide-vue-next'
import {
  deleteTenantOverride,
  getEffectivePolicy,
  getTenantOverrides,
  updateTenantOverride,
} from '@/api/passwordPolicy'
import { POLICY_KEYS, type PasswordPolicyDto, type PasswordPolicyKeyMeta } from '@/types/passwordPolicy'

const { t } = useI18n()

const effective = ref<PasswordPolicyDto | null>(null)
const overrides = ref<Record<string, string>>({})
const loading = ref(false)
const saving = ref(false)

const editDialogVisible = ref(false)
const editingKey = ref<PasswordPolicyKeyMeta | null>(null)
const editValue = ref<string | number>('')
const editBoolValue = ref(false)

const categoryGroups = computed(() => {
  const map: Record<string, PasswordPolicyKeyMeta[]> = { basic: [], advanced: [], expiry: [] }
  POLICY_KEYS.forEach(k => map[k.category].push(k))
  return map
})

async function load() {
  loading.value = true
  try {
    const [effRes, ovRes] = await Promise.all([getEffectivePolicy(), getTenantOverrides()])
    effective.value = effRes.body
    overrides.value = ovRes.body || {}
  } catch {
    ElMessage.error(t('common.operationFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(load)

const FIELD_BY_KEY: Record<string, keyof PasswordPolicyDto> = {
  'password.min_length': 'minLength',
  'password.require_uppercase': 'requireUppercase',
  'password.require_lowercase': 'requireLowercase',
  'password.require_digit': 'requireDigit',
  'password.require_special': 'requireSpecial',
  'password.history_count': 'historyCount',
  'password.max_length': 'maxLength',
  'password.min_special_chars': 'minSpecialChars',
  'password.min_digits': 'minDigits',
  'password.min_uppercase': 'minUppercase',
  'password.min_lowercase': 'minLowercase',
  'password.not_contains_username': 'notContainsUsername',
  'password.expire_days': 'expireDays',
  'password.force_change_on_first_login': 'forceChangeOnFirstLogin',
  'password.force_change_on_admin_reset': 'forceChangeOnAdminReset',
}

function effectiveValue(meta: PasswordPolicyKeyMeta): string {
  const p = effective.value
  if (!p) return meta.defaultValue
  const field = FIELD_BY_KEY[meta.key]
  const raw = p[field]
  return String(raw)
}

function displayValue(meta: PasswordPolicyKeyMeta): string {
  const v = effectiveValue(meta)
  if (meta.type === 'BOOL') return v === 'true' ? t('common.yes') : t('common.no')
  return v
}

function isOverridden(meta: PasswordPolicyKeyMeta): boolean {
  return Object.prototype.hasOwnProperty.call(overrides.value, meta.key)
}

function openEdit(meta: PasswordPolicyKeyMeta) {
  editingKey.value = meta
  const current = effectiveValue(meta)
  editValue.value = current
  editBoolValue.value = current === 'true'
  editDialogVisible.value = true
}

async function handleSave() {
  if (!editingKey.value) return
  const meta = editingKey.value
  const value = meta.type === 'BOOL' ? String(editBoolValue.value) : editValue.value

  if (meta.type === 'INT' && meta.platformFloor !== null) {
    const n = Number(value)
    if (!Number.isFinite(n) || n < meta.platformFloor) {
      ElMessage.error(t('passwordPolicy.errors.belowFloor', { floor: meta.platformFloor }))
      return
    }
  }

  saving.value = true
  try {
    await updateTenantOverride({ key: meta.key, value })
    await load()
    editDialogVisible.value = false
    ElMessage.success(t('passwordPolicy.saveSuccess'))
  } catch (err: unknown) {
    const e = err as { response?: { data?: { errorMsg?: string } } }
    ElMessage.error(e?.response?.data?.errorMsg || t('common.operationFailed'))
  } finally {
    saving.value = false
  }
}

async function handleReset(meta: PasswordPolicyKeyMeta) {
  saving.value = true
  try {
    await deleteTenantOverride(meta.key)
    await load()
    ElMessage.success(t('passwordPolicy.resetSuccess'))
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
        <h1 class="page-title">{{ t('passwordPolicy.tenant.title') }}</h1>
        <p class="page-subtitle">{{ t('passwordPolicy.tenant.subtitle') }}</p>
      </div>

      <div v-loading="loading">
        <div v-for="(group, cat) in categoryGroups" :key="cat" class="group-card">
          <h2 class="group-title">{{ t(`passwordPolicy.categories.${cat}`) }}</h2>
          <el-table :data="group" style="width: 100%">
            <el-table-column :label="t('passwordPolicy.colKey')" min-width="280">
              <template #default="{ row }">
                <div class="key-cell">
                  <div class="key-label">
                    {{ t(row.labelKey) }}
                    <el-tag v-if="isOverridden(row)" size="small" type="warning" effect="plain" class="custom-tag">
                      {{ t('passwordPolicy.customized') }}
                    </el-tag>
                  </div>
                  <div class="key-id">{{ row.key }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column :label="t('passwordPolicy.colValue')" width="120">
              <template #default="{ row }">{{ displayValue(row) }}</template>
            </el-table-column>
            <el-table-column :label="t('passwordPolicy.colFloor')" width="120">
              <template #default="{ row }">
                <span v-if="row.platformFloor !== null">≥ {{ row.platformFloor }}</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="160" align="center">
              <template #default="{ row }">
                <el-button size="small" @click="openEdit(row)">
                  <Pencil :size="14" />
                </el-button>
                <el-button
                  v-if="isOverridden(row)"
                  size="small"
                  type="warning"
                  @click="handleReset(row)"
                  :title="t('passwordPolicy.useDefault')"
                >
                  <RotateCcw :size="14" />
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="editDialogVisible"
      :title="t('passwordPolicy.editDialogTitle')"
      width="420px"
      :close-on-click-modal="false"
    >
      <div v-if="editingKey" class="edit-form" role="form" :aria-label="t('passwordPolicy.editDialogTitle')">
        <div class="form-row">
          <label class="form-label">{{ t('passwordPolicy.colKey') }}</label>
          <div>{{ t(editingKey.labelKey) }}</div>
          <div class="key-id">{{ editingKey.key }}</div>
        </div>
        <div class="form-row" v-if="editingKey.platformFloor !== null">
          <label class="form-label">{{ t('passwordPolicy.colFloor') }}</label>
          <div>{{ t('passwordPolicy.floorHint', { floor: editingKey.platformFloor }) }}</div>
        </div>
        <div class="form-row">
          <label class="form-label">{{ t('passwordPolicy.colValue') }}</label>
          <el-input-number
            v-if="editingKey.type === 'INT'"
            v-model="editValue"
            :min="editingKey.platformFloor ?? 0"
            controls-position="right"
          />
          <el-switch v-else v-model="editBoolValue" />
        </div>
      </div>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 600; margin: 0 0 4px 0; color: var(--text-heading); }
.page-subtitle { font-size: 13px; color: var(--text-body); margin: 0; }
.group-card { margin-bottom: 24px; background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: 8px; padding: 16px; }
.group-title { font-size: 16px; font-weight: 600; margin: 0 0 12px 0; color: var(--text-heading); }
.key-cell .key-label { font-weight: 500; }
.key-cell .key-id, .key-id { font-family: monospace; font-size: 11px; color: var(--text-muted, #888); }
.custom-tag { margin-left: 6px; }
.muted { color: var(--text-muted, #888); }
.edit-form { display: flex; flex-direction: column; gap: 12px; }
.form-row { display: flex; flex-direction: column; gap: 4px; }
.form-label { font-size: 12px; color: var(--text-muted, #666); }
</style>
