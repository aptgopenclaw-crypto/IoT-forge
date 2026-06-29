<script setup lang="ts">
/**
 * [Phase 4 / 4.1.9 / ADR-002] Platform-side impersonation management page.
 *
 * Two panels:
 *   1. "New session" form — pick tenant, fill reason, choose duration; on
 *      success swaps the operator's PLATFORM token for the freshly-minted
 *      IMPERSONATION token and reloads into the tenant shell.
 *   2. "My sessions" history — operator's own active/expired/revoked
 *      sessions, with an inline revoke action for ACTIVE rows.
 *
 * Mounted under PlatformLayout (dark theme). Backend is gated by
 * `PLATFORM_IMPERSONATE`; no super-admin check is needed client-side because
 * the route already carries `requiresScope: 'PLATFORM'`.
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { ShieldAlert, RefreshCw, UserCog } from 'lucide-vue-next'
import { listTenants } from '@/api/tenant/admin'
import {
  createImpersonation,
  listImpersonations,
  revokeImpersonation,
} from '@/api/impersonation'
import { useAuthStore } from '@/stores/authStore'
import type { TenantDto } from '@/types/tenant'
import type {
  CreateImpersonationRequest,
  ImpersonationSessionDto,
} from '@/types/impersonation'

const { t } = useI18n()
const authStore = useAuthStore()

// ── State ────────────────────────────────────────────────────────────────
const tenants = ref<TenantDto[]>([])
const sessions = ref<ImpersonationSessionDto[]>([])
const loading = ref(false)
const submitting = ref(false)
const statusFilter = ref<'' | 'ACTIVE' | 'EXPIRED' | 'REVOKED'>('')

const form = ref<CreateImpersonationRequest>({
  tenantId: '',
  reason: '',
  durationMinutes: 15,
})

// ── Derived ──────────────────────────────────────────────────────────────
const filteredSessions = computed(() =>
  statusFilter.value
    ? sessions.value.filter((s) => s.status === statusFilter.value)
    : sessions.value,
)

const statusLabel = (status: string): string => {
  switch (status) {
    case 'ACTIVE':
      return t('impersonationManage.statusActive')
    case 'EXPIRED':
      return t('impersonationManage.statusExpired')
    case 'REVOKED':
      return t('impersonationManage.statusRevoked')
    default:
      return status
  }
}

const statusTagType = (
  status: string,
): 'success' | 'info' | 'warning' | 'danger' | 'primary' => {
  if (status === 'ACTIVE') return 'success'
  if (status === 'EXPIRED') return 'info'
  if (status === 'REVOKED') return 'warning'
  return 'primary'
}

const formatDateTime = (raw: string | null | undefined): string => {
  if (!raw) return '—'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  return d.toLocaleString()
}

// ── Lifecycle ────────────────────────────────────────────────────────────
onMounted(async () => {
  await Promise.all([loadTenants(), loadSessions()])
})

async function loadTenants() {
  try {
    const res = await listTenants()
    tenants.value = res.body.filter((tn) => tn.enabled !== false)
  } catch {
    // Non-blocking: form just shows empty select.
  }
}

async function loadSessions() {
  loading.value = true
  try {
    const res = await listImpersonations(statusFilter.value || undefined)
    sessions.value = res.body
  } catch {
    ElMessage.error(t('impersonationManage.loadFailed'))
  } finally {
    loading.value = false
  }
}

// ── Actions ──────────────────────────────────────────────────────────────
async function handleCreate() {
  if (!form.value.tenantId) {
    ElMessage.warning(t('impersonationManage.errors.tenantRequired'))
    return
  }
  if (!form.value.reason.trim()) {
    ElMessage.warning(t('impersonationManage.errors.reasonRequired'))
    return
  }
  if (
    !Number.isInteger(form.value.durationMinutes) ||
    form.value.durationMinutes < 1 ||
    form.value.durationMinutes > 60
  ) {
    ElMessage.warning(t('impersonationManage.errors.durationRange'))
    return
  }

  submitting.value = true
  try {
    const res = await createImpersonation({
      tenantId: form.value.tenantId,
      reason: form.value.reason.trim(),
      durationMinutes: form.value.durationMinutes,
    })
    ElMessage.success(t('impersonationManage.createSuccess'))
    // Swap PLATFORM token → IMPERSONATION token and reload into TenantLayout.
    authStore.applyImpersonationToken(res.body.accessToken)
  } catch {
    ElMessage.error(t('impersonationManage.createFailed'))
  } finally {
    submitting.value = false
  }
}

async function handleRevoke(session: ImpersonationSessionDto) {
  try {
    await ElMessageBox.confirm(
      t('impersonationManage.revokeConfirmMessage'),
      t('impersonationManage.revokeConfirmTitle'),
      {
        type: 'warning',
        confirmButtonText: t('impersonationManage.actionRevoke'),
        cancelButtonText: t('impersonation.endConfirmCancel'),
      },
    )
  } catch {
    return
  }
  try {
    await revokeImpersonation(session.id)
    ElMessage.success(t('impersonationManage.revokeSuccess'))
    await loadSessions()
  } catch {
    ElMessage.error(t('impersonationManage.revokeFailed'))
  }
}
</script>

<template>
  <div class="impersonation-manage" data-testid="impersonation-manage-view">
    <header class="page-header">
      <div class="page-header__icon">
        <ShieldAlert :size="22" />
      </div>
      <div>
        <h1>{{ t('impersonationManage.title') }}</h1>
        <p>{{ t('impersonationManage.subtitle') }}</p>
      </div>
    </header>

    <section class="panel" data-testid="impersonation-create-panel">
      <div class="panel__title">
        <UserCog :size="16" />
        <span>{{ t('impersonationManage.newSessionTitle') }}</span>
      </div>
      <el-form class="impersonation-form" label-position="top" @submit.prevent>
        <el-form-item :label="t('impersonationManage.fieldTenant')" required>
          <el-select
            v-model="form.tenantId"
            :placeholder="t('impersonationManage.fieldTenantPlaceholder')"
            filterable
            data-testid="impersonation-tenant-select"
          >
            <el-option
              v-for="tn in tenants"
              :key="tn.tenantId"
              :label="`${tn.tenantName} (${tn.tenantCode})`"
              :value="tn.tenantId"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('impersonationManage.fieldReason')" required>
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            :maxlength="500"
            show-word-limit
            :placeholder="t('impersonationManage.fieldReasonPlaceholder')"
            data-testid="impersonation-reason-input"
          />
        </el-form-item>

        <el-form-item :label="t('impersonationManage.fieldDuration')" required>
          <el-input-number
            v-model="form.durationMinutes"
            :min="1"
            :max="60"
            :step="1"
            data-testid="impersonation-duration-input"
          />
          <span class="hint">{{ t('impersonationManage.fieldDurationHint') }}</span>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="submitting"
            data-testid="impersonation-submit-btn"
            @click="handleCreate"
          >
            {{
              submitting
                ? t('impersonationManage.submitting')
                : t('impersonationManage.submit')
            }}
          </el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="panel" data-testid="impersonation-history-panel">
      <div class="panel__title panel__title--row">
        <span>{{ t('impersonationManage.historyTitle') }}</span>
        <div class="panel__actions">
          <el-select
            v-model="statusFilter"
            size="small"
            data-testid="impersonation-status-filter"
            @change="loadSessions"
          >
            <el-option :label="t('impersonationManage.filterAll')" value="" />
            <el-option :label="t('impersonationManage.filterActive')" value="ACTIVE" />
            <el-option :label="t('impersonationManage.filterExpired')" value="EXPIRED" />
            <el-option :label="t('impersonationManage.filterRevoked')" value="REVOKED" />
          </el-select>
          <el-button
            size="small"
            :loading="loading"
            data-testid="impersonation-refresh-btn"
            @click="loadSessions"
          >
            <RefreshCw :size="14" style="margin-right: 4px" />
            {{ t('impersonationManage.refresh') }}
          </el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredSessions"
        :empty-text="t('impersonationManage.empty')"
        data-testid="impersonation-history-table"
        style="width: 100%"
      >
        <el-table-column prop="targetTenantName" :label="t('impersonationManage.colTenant')" />
        <el-table-column prop="reason" :label="t('impersonationManage.colReason')" show-overflow-tooltip />
        <el-table-column :label="t('impersonationManage.colStatus')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{
              statusLabel(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('impersonationManage.colStartedAt')" width="180">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('impersonationManage.colExpiresAt')" width="180">
          <template #default="{ row }">{{ formatDateTime(row.expiresAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('impersonationManage.colRevokedAt')" width="180">
          <template #default="{ row }">{{ formatDateTime(row.revokedAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('impersonationManage.colActions')" width="120">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ACTIVE'"
              type="danger"
              size="small"
              link
              data-testid="impersonation-row-revoke-btn"
              @click="handleRevoke(row)"
            >
              {{ t('impersonationManage.actionRevoke') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.impersonation-manage {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: flex;
  gap: 16px;
  align-items: center;
}

.page-header__icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--bg-secondary, #1f2937);
  color: var(--accent, #f87171);
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-header h1 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary, #f9fafb);
}

.page-header p {
  margin: 4px 0 0;
  color: var(--text-secondary, #9ca3af);
  font-size: 13px;
}

.panel {
  background: var(--bg-elevated, #111827);
  border: 1px solid var(--border-subtle, #1f2937);
  border-radius: 10px;
  padding: 20px;
}

.panel__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
  color: var(--text-primary, #f9fafb);
  margin-bottom: 16px;
}

.panel__title--row {
  justify-content: space-between;
}

.panel__actions {
  display: flex;
  gap: 8px;
}

.impersonation-form .hint {
  margin-left: 12px;
  color: var(--text-secondary, #9ca3af);
  font-size: 12px;
}
</style>
