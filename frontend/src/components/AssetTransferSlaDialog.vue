<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getApplicationSla } from '@/api/assetTransfer'
import type { WorkflowSlaDto } from '@/types/assetTransfer'

const props = defineProps<{
  applicationId: number | null
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const { t } = useI18n()

const sla = ref<WorkflowSlaDto | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

watch(
  () => props.visible,
  async (val) => {
    if (val && props.applicationId != null) {
      loading.value = true
      error.value = null
      sla.value = null
      try {
        const res = await getApplicationSla(props.applicationId)
        sla.value = res.body
      } catch {
        error.value = t('assetTransfer.loadFailed')
      } finally {
        loading.value = false
      }
    }
  },
)

function close() {
  emit('update:visible', false)
}

function formatDays(days: number | null): string {
  if (days == null) return t('assetTransfer.slaNotSet')
  return days.toFixed(2)
}

function formatDateTime(dt: string | null): string {
  if (!dt) return '-'
  const d = new Date(dt)
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="t('assetTransfer.slaDialogTitle')"
    width="740px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" style="min-height: 120px">
      <template v-if="!loading">
        <!-- 尚未送出 -->
        <el-empty v-if="!sla" :description="t('assetTransfer.slaNotStarted')" />

        <template v-else>
          <!-- 整體 KPI -->
          <el-descriptions :column="3" border size="small" class="sla-summary">
            <el-descriptions-item :label="t('assetTransfer.slaTotalDays')">
              <span v-if="sla.slaTotalDays != null">{{ sla.slaTotalDays }}</span>
              <el-tag v-else type="info" size="small">{{ t('assetTransfer.slaNotSet') }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('assetTransfer.slaActualDays')">
              {{ formatDays(sla.actualDays) }}
            </el-descriptions-item>
            <el-descriptions-item label="狀態">
              <el-tag :type="sla.overdue ? 'danger' : 'success'" size="small">
                {{ sla.overdue ? t('assetTransfer.slaOverdue') : t('assetTransfer.slaOnTime') }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <!-- 進度條（若有 SLA 總天數） -->
          <div v-if="sla.slaTotalDays != null && sla.actualDays != null" class="sla-progress">
            <el-progress
              :percentage="Math.min(Math.round((sla.actualDays / sla.slaTotalDays) * 100), 100)"
              :status="sla.overdue ? 'exception' : undefined"
              :stroke-width="10"
            />
            <span class="sla-progress-label">
              {{ sla.actualDays.toFixed(2) }} / {{ sla.slaTotalDays }} 天
            </span>
          </div>

          <!-- 各步驟明細 -->
          <div class="sla-steps-title">{{ t('assetTransfer.slaStepTitle') }}</div>
          <el-table :data="sla.steps" size="small" border>
            <el-table-column :label="t('assetTransfer.slaColStep')" prop="stepName" min-width="120" />
            <el-table-column :label="t('assetTransfer.slaColSla')" width="90" align="right">
              <template #default="{ row }">
                {{ row.slaDays != null ? row.slaDays : t('assetTransfer.slaNotSet') }}
              </template>
            </el-table-column>
            <el-table-column :label="t('assetTransfer.slaColActual')" width="100" align="right">
              <template #default="{ row }">
                <span v-if="row.completedAt != null">{{ formatDays(row.actualDays) }}</span>
                <el-tag v-else type="warning" size="small">{{ t('assetTransfer.slaInProgress') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('assetTransfer.slaColStatus')" width="90" align="center">
              <template #default="{ row }">
                <template v-if="row.completedAt != null">
                  <el-tag :type="row.overdue ? 'danger' : 'success'" size="small">
                    {{ row.overdue ? t('assetTransfer.slaOverdue') : t('assetTransfer.slaOnTime') }}
                  </el-tag>
                </template>
                <el-tag v-else type="info" size="small">-</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('assetTransfer.slaColEntered')" min-width="130">
              <template #default="{ row }">{{ formatDateTime(row.enteredAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('assetTransfer.slaColCompleted')" min-width="130">
              <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </template>
    </div>

    <template #footer>
      <el-button @click="close">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.sla-summary {
  margin-bottom: 16px;
}

.sla-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.sla-progress :deep(.el-progress) {
  flex: 1;
}

.sla-progress-label {
  white-space: nowrap;
  font-size: 13px;
  color: var(--text-secondary);
}

.sla-steps-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-heading);
  margin-bottom: 10px;
}
</style>
