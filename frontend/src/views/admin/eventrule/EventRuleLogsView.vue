<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getAllTriggerLogs } from '@/api/eventrule'
import type { EventRuleTriggerLogResponse } from '@/types/telemetry'

const { t } = useI18n()

// ── Filter ──
const filterSeverity = ref('')
const timeRange = ref<[Date, Date] | null>(null)

function getDefaultRange(): [Date, Date] {
  const now = new Date()
  return [new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000), now]
}

// ── Table ──
const tableData = ref<EventRuleTriggerLogResponse[]>([])
const loading = ref(false)
const pagination = reactive({ page: 0, size: 20, total: 0 })

async function fetchList() {
  loading.value = true
  try {
    const [from, to] = timeRange.value ?? getDefaultRange()
    const res = await getAllTriggerLogs({
      from: from.toISOString(),
      to: to.toISOString(),
      severity: filterSeverity.value || undefined,
      page: pagination.page,
      size: pagination.size,
    })
    if (res.errorCode === '00000') {
      tableData.value = res.body.content
      pagination.total = res.body.totalElements
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 0
  fetchList()
}

function handlePageChange(page: number) {
  pagination.page = page - 1
  fetchList()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 0
  fetchList()
}

function formatTs(ts: string) {
  return new Date(ts).toLocaleString('zh-TW', { hour12: false })
}

function severityType(s: string | undefined) {
  return s === 'CRITICAL' ? 'danger' : s === 'WARNING' ? 'warning' : 'info'
}

onMounted(() => {
  timeRange.value = getDefaultRange()
  fetchList()
})
</script>

<template>
  <div class="event-rule-logs">
    <div class="page-header">
      <h2>{{ t('eventRule.logsTitle') }}</h2>
      <p class="subtitle">{{ t('eventRule.logsSubtitle') }}</p>
    </div>

    <!-- Filter bar -->
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item :label="t('telemetry.timeRange')">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            :range-separator="t('common.to')"
            :start-placeholder="t('common.startTime')"
            :end-placeholder="t('common.endTime')"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item :label="t('eventRule.severity')">
          <el-select
            v-model="filterSeverity"
            clearable
            :placeholder="t('common.all')"
            style="width: 120px"
          >
            <el-option value="INFO" label="INFO" />
            <el-option value="WARNING" label="WARNING" />
            <el-option value="CRITICAL" label="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ t('common.query') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-table v-loading="loading" :data="tableData" border size="small">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column :label="t('eventRule.ruleId')" width="160">
        <template #default="{ row }">
          <span :title="`ID: ${row.ruleId}`">{{ row.ruleName ?? row.ruleId }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('eventRule.deviceId')" width="160">
        <template #default="{ row }">
          <span :title="`ID: ${row.deviceId}`">{{ row.deviceName ?? row.deviceId }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('eventRule.triggeredAt')" width="180">
        <template #default="{ row }">{{ formatTs(row.triggeredAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('eventRule.severity')" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.severity" :type="severityType(row.severity)" size="small">
            {{ row.severity }}
          </el-tag>
          <span v-else>--</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('eventRule.matchedValues')" min-width="200">
        <template #default="{ row }">
          <code v-if="row.matchedValues" style="font-size: 12px">
            {{ JSON.stringify(row.matchedValues) }}
          </code>
          <span v-else>--</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="pagination.total > 0"
      class="pagination"
      background
      layout="total, sizes, prev, pager, next"
      :total="pagination.total"
      :page-size="pagination.size"
      :current-page="pagination.page + 1"
      :page-sizes="[20, 50, 100]"
      @size-change="handleSizeChange"
      @current-change="handlePageChange"
    />
  </div>
</template>

<style scoped>
.event-rule-logs {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.subtitle {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.filter-card {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
