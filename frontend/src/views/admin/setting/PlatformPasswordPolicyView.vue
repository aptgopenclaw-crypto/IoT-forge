<script setup lang="ts">
/**
 * Platform-level password policy settings (SUPER_ADMIN only).
 *
 * Reads/writes the rows under the `__PLATFORM__` sentinel. Each value here acts
 * as a *floor* — tenant admins may not weaken numeric INT keys below it
 * (enforced server-side in PasswordPolicyService).
 */
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Pencil } from 'lucide-vue-next'
import { getPlatformDefaults, updatePlatformDefault } from '@/api/passwordPolicy'
import { POLICY_KEYS, type PasswordPolicyKeyMeta } from '@/types/passwordPolicy'

const { t } = useI18n()

const rawValues = ref<Record<string, string>>({})
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
    const res = await getPlatformDefaults()
    rawValues.value = res.body || {}
  } catch {
    ElMessage.error(t('common.operationFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** Resolved value: raw row → fallback to hard-coded default in metadata. */
function valueOf(meta: PasswordPolicyKeyMeta): string {
  return rawValues.value[meta.key] ?? meta.defaultValue
}

function displayValue(meta: PasswordPolicyKeyMeta): string {
  const v = valueOf(meta)
  if (meta.type === 'BOOL') return v === 'true' ? t('common.yes') : t('common.no')
  return v
}

function openEdit(meta: PasswordPolicyKeyMeta) {
  editingKey.value = meta
  const current = valueOf(meta)
  editValue.value = current
  editBoolValue.value = current === 'true'
  editDialogVisible.value = true
}

async function handleSave() {
  if (!editingKey.value) return
  const meta = editingKey.value
  const value = meta.type === 'BOOL' ? String(editBoolValue.value) : editValue.value

  // Client-side floor check (server re-checks); avoids a round-trip for typos.
  if (meta.type === 'INT' && meta.platformFloor !== null) {
    const n = Number(value)
    if (!Number.isFinite(n) || n < meta.platformFloor) {
      ElMessage.error(t('passwordPolicy.errors.belowFloor', { floor: meta.platformFloor }))
      return
    }
  }

  saving.value = true
  try {
    await updatePlatformDefault({ key: meta.key, value })
    rawValues.value = { ...rawValues.value, [meta.key]: value }
    editDialogVisible.value = false
    ElMessage.success(t('passwordPolicy.saveSuccess'))
  } catch (err: unknown) {
    const e = err as { response?: { data?: { errorMsg?: string } } }
    ElMessage.error(e?.response?.data?.errorMsg || t('common.operationFailed'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-content">
      <div class="page-header">
        <h1 class="page-title">{{ t('passwordPolicy.platform.title') }}</h1>
        <p class="page-subtitle">{{ t('passwordPolicy.platform.subtitle') }}</p>
      </div>

      <div v-loading="loading">
        <div v-for="(group, cat) in categoryGroups" :key="cat" class="group-card">
          <h2 class="group-title">{{ t(`passwordPolicy.categories.${cat}`) }}</h2>
          <el-table :data="group" style="width: 100%">
            <el-table-column :label="t('passwordPolicy.colKey')" min-width="280">
              <template #default="{ row }">
                <div class="key-cell">
                  <div class="key-label">{{ t(row.labelKey) }}</div>
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
            <el-table-column :label="t('common.actions')" width="100" align="center">
              <template #default="{ row }">
                <el-button size="small" @click="openEdit(row)">
                  <Pencil :size="14" />
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
          <div>≥ {{ editingKey.platformFloor }}</div>
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
.muted { color: var(--text-muted, #888); }
.edit-form { display: flex; flex-direction: column; gap: 12px; }
.form-row { display: flex; flex-direction: column; gap: 4px; }
.form-label { font-size: 12px; color: var(--text-muted, #666); }
</style>
