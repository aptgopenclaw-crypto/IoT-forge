<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyApplications, getPendingTasks, cancelApplication } from '@/api/assetTransfer'
import { useApiError } from '@/composables/useApiError'
import type { AssetTransferApplicationDto, AssetTransferStatus } from '@/types/assetTransfer'
import AssetTransferSlaDialog from '@/components/AssetTransferSlaDialog.vue'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const { handleError } = useApiError()

/** When meta.mode === 'my' we fetch the user's own applications, otherwise pending tasks */
const isMyMode = computed(() => route.meta?.mode === 'my')

const items = ref<AssetTransferApplicationDto[]>([])
const loading = ref(false)

const slaDialogVisible = ref(false)
const slaTargetId = ref<number | null>(null)

function openSlaDialog(row: AssetTransferApplicationDto) {
  slaTargetId.value = row.id
  slaDialogVisible.value = true
}

const pageTitle = computed(() =>
  isMyMode.value ? t('assetTransfer.myTitle') : t('assetTransfer.pendingTitle'),
)
const pageSubtitle = computed(() =>
  isMyMode.value ? t('assetTransfer.mySubtitle') : t('assetTransfer.pendingSubtitle'),
)

onMounted(() => loadData())

// 同一元件在 /asset-transfer/pending ↔ /asset-transfer/my 之間切換時
// 不會重新 mount，需要 watch isMyMode 重新拉資料。
watch(isMyMode, () => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = isMyMode.value ? await getMyApplications() : await getPendingTasks()
    items.value = res.body
  } catch (err) {
    handleError(err, {}, t('assetTransfer.loadFailed'))
  } finally {
    loading.value = false
  }
}

function goCreate() {
  router.push('/asset-transfer/create')
}

function goDetail(row: AssetTransferApplicationDto) {
  router.push(`/asset-transfer/${row.id}`)
}

async function handleCancel(row: AssetTransferApplicationDto) {
  try {
    await ElMessageBox.confirm(t('assetTransfer.cancelConfirmMsg'), t('assetTransfer.cancelConfirmTitle'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await cancelApplication(row.id, { comment: null })
    ElMessage.success(t('assetTransfer.cancelledSuccess'))
    await loadData()
  } catch {
    // 使用者取消對話框或 API 錯誤，不做任何事
  }
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`
}

const STATUS_TAG_TYPE: Record<AssetTransferStatus, 'info' | 'warning' | 'success' | 'danger'> = {
  DRAFT: 'info',
  PROCESSING: 'warning',
  COMPLETED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'info',
}

function getStatusType(status: AssetTransferStatus) {
  return STATUS_TAG_TYPE[status] ?? 'info'
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

function getTransferTypeLabel(type: string) {
  const map: Record<string, string> = {
    INTERNAL: t('assetTransfer.transferTypeInternal'),
    EXTERNAL: t('assetTransfer.transferTypeExternal'),
    DISPOSAL: t('assetTransfer.transferTypeDisposal'),
    RETURN: t('assetTransfer.transferTypeReturn'),
  }
  return map[type] ?? type
}
</script>

<template>
  <div class="page-container">
    <div class="page-content">
      <div class="page-header">
        <div>
          <h1 class="page-title">{{ pageTitle }}</h1>
          <p class="page-subtitle">{{ pageSubtitle }}</p>
        </div>
        <el-button v-if="isMyMode" class="create-btn" @click="goCreate">
          + {{ t('assetTransfer.createTitle') }}
        </el-button>
      </div>

      <div class="table-card" v-loading="loading">
        <el-table :data="items" style="width: 100%" row-class-name="table-row">
          <el-table-column prop="applicationNo" :label="t('assetTransfer.colAppNo')" min-width="160" />
          <el-table-column prop="assetCode" :label="t('assetTransfer.colAssetCode')" min-width="120" />
          <el-table-column prop="assetName" :label="t('assetTransfer.colAssetName')" min-width="160" />
          <el-table-column :label="t('assetTransfer.colTransferType')" min-width="120">
            <template #default="{ row }">
              {{ getTransferTypeLabel(row.transferType) }}
            </template>
          </el-table-column>
          <el-table-column prop="departmentName" :label="t('assetTransfer.colDept')" min-width="120">
            <template #default="{ row }">
              {{ row.departmentName || '-' }}
            </template>
          </el-table-column>
          <el-table-column :label="t('assetTransfer.colStatus')" width="110">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ getStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="applicantName" :label="t('assetTransfer.colApplicant')" min-width="100">
            <template #default="{ row }">
              {{ row.applicantName || row.applicantId }}
            </template>
          </el-table-column>
          <el-table-column :label="t('assetTransfer.colCreatedAt')" min-width="110">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('assetTransfer.colActions')" width="220" fixed="right">
            <template #default="{ row }">
              <el-button size="small" class="action-btn" @click="goDetail(row)">
                {{ t('assetTransfer.btnViewDetail') }}
              </el-button>
              <el-button
                v-if="isMyMode && row.status !== 'DRAFT'"
                size="small"
                class="action-btn"
                @click="openSlaDialog(row)"
              >
                {{ t('assetTransfer.btnSla') }}
              </el-button>
              <el-button
                v-if="isMyMode && row.status === 'PROCESSING'"
                size="small"
                type="danger"
                plain
                class="action-btn"
                @click="handleCancel(row)"
              >
                {{ t('assetTransfer.btnCancel') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="!loading && items.length === 0" class="empty-hint">
          {{ t('assetTransfer.noData') }}
        </div>
      </div>
    </div>
  </div>

  <AssetTransferSlaDialog
    v-model:visible="slaDialogVisible"
    :application-id="slaTargetId"
  />
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

.create-btn {
  background: var(--btn-primary-bg);
  color: var(--btn-primary-text);
  border: none;
  border-radius: 86px;
  padding: 8px 24px;
  font-size: 14px;
  font-weight: 600;
}

.create-btn:hover {
  background: var(--btn-primary-hover);
  color: var(--btn-primary-text);
}

.table-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  overflow: hidden;
}

.action-btn {
  background: transparent;
  color: var(--text-primary);
  border: 1px solid var(--border-light);
  border-radius: 6px;
}

.empty-hint {
  text-align: center;
  padding: 48px;
  color: var(--text-secondary);
  font-size: 14px;
}
</style>
