<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/authStore'
import { useDeptStore } from '@/stores/deptStore'
import { getApplication, approveApplication, rejectApplication, resubmitApplication, getRejectTargets, getApplicationHistory } from '@/api/assetTransfer'
import { useApiError } from '@/composables/useApiError'
import type { AssetTransferApplicationDto, AssetTransferStatus, RejectTargetOption, WorkflowStepLogDto, WorkflowAction } from '@/types/assetTransfer'

const props = defineProps<{ id: string }>()

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const deptStore = useDeptStore()
const { handleError } = useApiError()

// Ensure dept names are loaded
if (!deptStore.initialized) {
  deptStore.fetchDeptOptions()
}

const app = ref<AssetTransferApplicationDto | null>(null)
const loading = ref(false)
const acting = ref(false)

// Reject dialog
const rejectDialogVisible = ref(false)
const rejectComment = ref('')
const rejectTargetStep = ref('')
const rejectTargets = ref<RejectTargetOption[]>([])
const rejectTargetsLoading = ref(false)

// Signing trail
const history = ref<WorkflowStepLogDto[]>([])
const historyLoading = ref(false)

const currentUserId = computed(() => authStore.userInfo?.userId ?? '')

const canAct = computed(() => app.value?.canAct === true)

const canResubmit = computed(
  () =>
    app.value?.status === 'REJECTED' &&
    app.value?.applicantId === currentUserId.value,
)

/** Map status to el-steps active index (0-based) */
const stepActiveIndex = computed(() => {
  if (!app.value) return 0
  switch (app.value.status) {
    case 'DRAFT':
      return 0
    case 'PROCESSING':
      return 1
    case 'COMPLETED':
      return 4
    case 'REJECTED':
      return 4
    case 'CANCELLED':
      return 0
    default:
      return 0
  }
})

const stepStatus = computed(() => {
  if (app.value?.status === 'COMPLETED') return 'success'
  if (app.value?.status === 'REJECTED') return 'error'
  return 'process'
})

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getApplication(Number(props.id))
    app.value = res.body
    // 若申請已有流程實例，一併載入簽核軌跡
    if (app.value.status !== 'DRAFT') {
      loadHistory()
    }
  } catch (err) {
    handleError(err, {}, t('assetTransfer.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await getApplicationHistory(Number(props.id))
    history.value = res.body
  } catch (err) {
    // 簽核軌跡載入失敗不阻塞主要畫面，僅靜默處理
    history.value = []
  } finally {
    historyLoading.value = false
  }
}

function getActionLabel(action: WorkflowAction | null) {
  const map: Record<string, string> = {
    APPROVE: t('assetTransfer.actionApprove'),
    REJECT: t('assetTransfer.actionReject'),
    RESUBMIT: t('assetTransfer.actionResubmit'),
    CANCEL: t('assetTransfer.actionCancel'),
  }
  return action ? (map[action] ?? action) : '-'
}

function getActionTagType(action: WorkflowAction | null) {
  const map: Record<string, 'success' | 'danger' | 'warning' | 'info'> = {
    APPROVE: 'success',
    REJECT: 'danger',
    RESUBMIT: 'warning',
    CANCEL: 'info',
  }
  return action ? (map[action] ?? 'info') : 'info'
}

async function handleApprove() {
  try {
    await ElMessageBox.confirm(
      t('assetTransfer.approveConfirmMsg'),
      t('assetTransfer.approveConfirmTitle'),
      { confirmButtonText: t('assetTransfer.btnApprove'), cancelButtonText: t('common.cancel'), type: 'warning' },
    )
  } catch {
    return
  }
  acting.value = true
  try {
    const res = await approveApplication(Number(props.id), {})
    app.value = res.body
    ElMessage.success(t('assetTransfer.approvedSuccess'))
  } catch (err) {
    handleError(err, {
      '91001': t('assetTransfer.errorPermissionDenied'),
      '91002': t('assetTransfer.errorNotFound'),
      '91003': t('assetTransfer.errorInvalidStatus'),
    }, t('assetTransfer.loadFailed'))
  } finally {
    acting.value = false
  }
}

function openRejectDialog() {
  rejectComment.value = ''
  rejectTargetStep.value = ''
  rejectTargets.value = []
  rejectDialogVisible.value = true
  rejectTargetsLoading.value = true
  getRejectTargets(Number(props.id))
    .then((res) => {
      rejectTargets.value = res.body
      if (res.body.length === 1) {
        rejectTargetStep.value = res.body[0].stepId
      }
    })
    .catch((err) => handleError(err, {}, t('assetTransfer.loadFailed')))
    .finally(() => {
      rejectTargetsLoading.value = false
    })
}

async function handleRejectConfirm() {
  if (!rejectTargetStep.value.trim()) {
    ElMessage.warning(t('assetTransfer.targetStepPlaceholder'))
    return
  }
  acting.value = true
  rejectDialogVisible.value = false
  try {
    const res = await rejectApplication(Number(props.id), {
      comment: rejectComment.value || null,
      targetStepId: rejectTargetStep.value.trim(),
    })
    app.value = res.body
    ElMessage.success(t('assetTransfer.rejectedSuccess'))
  } catch (err) {
    handleError(err, {
      '91001': t('assetTransfer.errorPermissionDenied'),
      '91002': t('assetTransfer.errorNotFound'),
      '91003': t('assetTransfer.errorInvalidStatus'),
    }, t('assetTransfer.loadFailed'))
  } finally {
    acting.value = false
  }
}

async function handleResubmit() {
  acting.value = true
  try {
    const res = await resubmitApplication(Number(props.id), {})
    app.value = res.body
    ElMessage.success(t('assetTransfer.submittedSuccess'))
  } catch (err) {
    handleError(err, {
      '91001': t('assetTransfer.errorPermissionDenied'),
      '91002': t('assetTransfer.errorNotFound'),
      '91004': t('assetTransfer.errorWorkflowNotStarted'),
    }, t('assetTransfer.loadFailed'))
  } finally {
    acting.value = false
  }
}

function getStatusLabel(status: AssetTransferStatus) {
  const map: Record<AssetTransferStatus, string> = {
    DRAFT: t('assetTransfer.statusDraft'),
    PROCESSING: t('assetTransfer.statusPending'),
    COMPLETED: t('assetTransfer.statusApproved'),
    REJECTED: t('assetTransfer.statusRejected'),
    CANCELLED: t('assetTransfer.statusCancelled'),
  }
  return map[status] ?? status
}

const STATUS_TAG_TYPE: Record<AssetTransferStatus, 'info' | 'warning' | 'success' | 'danger'> = {
  DRAFT: 'info',
  PROCESSING: 'warning',
  COMPLETED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'info',
}

function getTransferTypeLabel(type: string) {
  const map: Record<string, string> = {
    INTERNAL: t('assetTransfer.transferTypeInternal'),
    EXTERNAL: t('assetTransfer.transferTypeExternal'),
    DISPOSAL: t('assetTransfer.transferTypeDisposal'),
    RETURN: t('assetTransfer.transferTypeReturn'),
  }
  return map[type] ?? type
}

function formatDate(dateStr: string | null) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
</script>

<template>
  <div class="page-container">
    <div class="page-content">
      <!-- Header -->
      <div class="page-header">
        <div>
          <h1 class="page-title">{{ t('assetTransfer.detailTitle') }}</h1>
          <p class="page-subtitle">{{ t('assetTransfer.detailSubtitle') }}</p>
        </div>
        <el-button @click="router.back()">{{ t('common.back') }}</el-button>
      </div>

      <el-skeleton :rows="6" :loading="loading" animated>
        <template #default>
          <div v-if="app">
            <!-- Workflow progress -->
            <div class="detail-card">
              <h3 class="card-title">{{ t('assetTransfer.stepProgress') }}</h3>
              <el-steps
                :active="stepActiveIndex"
                :finish-status="stepStatus"
                class="workflow-steps"
              >
                <el-step :title="t('assetTransfer.stepDraft')" />
                <el-step :title="t('assetTransfer.stepDeptAdmin')" />
                <el-step :title="t('assetTransfer.stepPropertyMgr')" />
                <el-step :title="t('assetTransfer.stepDone')" />
              </el-steps>
            </div>

            <!-- Basic info -->
            <div class="detail-card">
              <div class="info-grid">
                <div class="info-row">
                  <span class="info-label">{{ t('assetTransfer.colAppNo') }}</span>
                  <span class="info-value mono">{{ app.applicationNo }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">{{ t('assetTransfer.colStatus') }}</span>
                  <span class="info-value">
                    <el-tag :type="STATUS_TAG_TYPE[app.status]" size="small">
                      {{ getStatusLabel(app.status) }}
                    </el-tag>
                  </span>
                </div>
                <div class="info-row">
                  <span class="info-label">{{ t('assetTransfer.colAssetCode') }}</span>
                  <span class="info-value mono">{{ app.assetCode }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">{{ t('assetTransfer.colAssetName') }}</span>
                  <span class="info-value">{{ app.assetName }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">{{ t('assetTransfer.colTransferType') }}</span>
                  <span class="info-value">{{ getTransferTypeLabel(app.transferType) }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">{{ t('assetTransfer.colDept') }}</span>
                  <span class="info-value">{{ app.departmentName || deptStore.getDeptName(app.departmentId) }}</span>
                </div>
                <div v-if="app.targetDepartmentId" class="info-row">
                  <span class="info-label">{{ t('assetTransfer.colTargetDept') }}</span>
                  <span class="info-value">{{ deptStore.getDeptName(app.targetDepartmentId) }}</span>
                </div>
                <div v-if="app.assetValue !== null" class="info-row">
                  <span class="info-label">{{ t('assetTransfer.fieldAssetValue') }}</span>
                  <span class="info-value">{{ app.assetValue?.toLocaleString() }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">{{ t('assetTransfer.colApplicant') }}</span>
                  <span class="info-value">{{ app.applicantName || app.applicantId }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">{{ t('assetTransfer.colCreatedAt') }}</span>
                  <span class="info-value">{{ formatDate(app.createdAt) }}</span>
                </div>
                <div v-if="app.reason" class="info-row info-row-full">
                  <span class="info-label">{{ t('assetTransfer.fieldReason') }}</span>
                  <span class="info-value">{{ app.reason }}</span>
                </div>
                <div v-if="app.rejectReason" class="info-row info-row-full">
                  <span class="info-label">{{ t('assetTransfer.rejectReasonLabel') }}</span>
                  <span class="info-value info-value-danger">{{ app.rejectReason }}</span>
                </div>
                <div v-if="app.currentAssignee" class="info-row">
                  <span class="info-label">{{ t('assetTransfer.currentAssigneeLabel') }}</span>
                  <span class="info-value">{{ app.currentAssigneeName || app.currentAssignee }}</span>
                </div>
              </div>
            </div>

            <!-- Signing trail -->
            <div v-if="app.status !== 'DRAFT'" class="detail-card">
              <h3 class="card-title">{{ t('assetTransfer.signingTrail') }}</h3>
              <el-skeleton :rows="3" :loading="historyLoading" animated>
                <template #default>
                  <div v-if="history.length > 0" class="signing-trail">
                    <div
                      v-for="(item, idx) in history"
                      :key="item.id"
                      class="trail-item"
                    >
                      <div class="trail-timeline">
                        <div
                          :class="['trail-dot', getActionTagType(item.action)]"
                        />
                        <div
                          v-if="idx < history.length - 1"
                          class="trail-line"
                        />
                      </div>
                      <div class="trail-content">
                        <div class="trail-header">
                          <span class="trail-step-name">{{ item.stepName }}</span>
                          <el-tag
                            :type="getActionTagType(item.action)"
                            size="small"
                            effect="plain"
                          >
                            {{ getActionLabel(item.action) }}
                          </el-tag>
                        </div>
                        <div class="trail-meta">
                          <span v-if="item.assigneeName" class="trail-assignee">
                            {{ item.assigneeName }}
                          </span>
                          <span class="trail-time">{{ formatDate(item.completedAt || item.enteredAt) }}</span>
                        </div>
                        <div v-if="item.comment" class="trail-comment">
                          {{ item.comment }}
                        </div>
                      </div>
                    </div>
                  </div>
                  <div v-else class="trail-empty">
                    {{ t('assetTransfer.signingTrailEmpty') }}
                  </div>
                </template>
              </el-skeleton>
            </div>

            <!-- Action buttons -->
            <div v-if="canAct || canResubmit" class="action-card">
              <template v-if="canAct">
                <el-button
                  type="success"
                  :loading="acting"
                  @click="handleApprove"
                >
                  {{ t('assetTransfer.btnApprove') }}
                </el-button>
                <el-button
                  type="danger"
                  :loading="acting"
                  @click="openRejectDialog"
                >
                  {{ t('assetTransfer.btnReject') }}
                </el-button>
              </template>
              <template v-if="canResubmit">
                <el-button
                  type="primary"
                  :loading="acting"
                  @click="handleResubmit"
                >
                  {{ t('assetTransfer.btnResubmit') }}
                </el-button>
              </template>
            </div>
          </div>
        </template>
      </el-skeleton>
    </div>

    <!-- Reject Dialog -->
    <el-dialog
      v-model="rejectDialogVisible"
      :title="t('assetTransfer.rejectDialogTitle')"
      width="480px"
    >
      <el-form label-position="top">
        <el-form-item :label="t('assetTransfer.targetStepLabel')" required>
          <el-select
            v-model="rejectTargetStep"
            :loading="rejectTargetsLoading"
            :placeholder="t('assetTransfer.targetStepPlaceholder')"
            style="width: 100%"
          >
            <el-option
              v-for="opt in rejectTargets"
              :key="opt.stepId"
              :label="opt.stepName"
              :value="opt.stepId"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('assetTransfer.commentLabel')">
          <el-input
            v-model="rejectComment"
            type="textarea"
            :rows="3"
            :placeholder="t('assetTransfer.commentPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="danger" @click="handleRejectConfirm">{{ t('assetTransfer.btnReject') }}</el-button>
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

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-heading);
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.detail-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 16px;
  max-width: 860px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-heading);
  margin: 0 0 20px 0;
}

.workflow-steps {
  padding: 0 8px;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 32px;
}

.info-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-row-full {
  grid-column: span 2;
}

.info-label {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.info-value {
  font-size: 14px;
  color: var(--text-primary);
}

.info-value.mono {
  font-family: monospace;
  font-size: 13px;
}

.info-value-danger {
  color: var(--el-color-danger);
}

.action-card {
  display: flex;
  gap: 12px;
  max-width: 860px;
  padding: 16px 0;
}

/* ── Signing Trail ──────────────────────────────────────────────── */

.signing-trail {
  padding: 4px 0;
}

.trail-item {
  display: flex;
  gap: 16px;
  min-height: 56px;
}

.trail-timeline {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 12px;
  flex-shrink: 0;
}

.trail-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 4px;
}

.trail-dot.success {
  background-color: var(--el-color-success);
}

.trail-dot.danger {
  background-color: var(--el-color-danger);
}

.trail-dot.warning {
  background-color: var(--el-color-warning);
}

.trail-dot.info {
  background-color: var(--el-color-info);
}

.trail-line {
  width: 2px;
  flex: 1;
  background-color: var(--border-light, #e4e7ed);
  margin: 4px 0;
}

.trail-content {
  flex: 1;
  padding-bottom: 20px;
}

.trail-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.trail-step-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.trail-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.trail-assignee::before {
  content: '@';
  margin-right: 2px;
  opacity: 0.6;
}

.trail-comment {
  margin-top: 6px;
  padding: 8px 12px;
  background: var(--bg-base, #f5f7fa);
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.5;
}

.trail-empty {
  text-align: center;
  padding: 24px 0;
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
