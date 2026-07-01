<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getTelemetryHistory, getTelemetryStats } from '@/api/telemetry'
import { listDevices } from '@/api/device'
import type { DeviceResponse } from '@/types/device'
import type { TelemetryPointResponse, TelemetryFieldStats } from '@/types/telemetry'

const { t } = useI18n()

// ── Device selector ──
const devices = ref<DeviceResponse[]>([])
const selectedDeviceId = ref<number | null>(null)

async function loadDevices() {
  try {
    const res = await listDevices({ page: 0, size: 200 })
    if (res.errorCode === '00000') {
      devices.value = res.body.content
    }
  } catch {
    // handled by interceptor
  }
}

// ── Time range ──
const timeRange = ref<[Date, Date] | null>(null)

function getDefaultRange(): [Date, Date] {
  const now = new Date()
  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000)
  return [yesterday, now]
}

// ── History ──
const historyData = ref<TelemetryPointResponse[]>([])
const statsData = ref<TelemetryFieldStats[]>([])
const loading = ref(false)
const pagination = reactive({ page: 0, size: 100, total: 0 })

async function fetchHistory() {
  if (!selectedDeviceId.value) return
  loading.value = true
  try {
    const [from, to] = timeRange.value ?? getDefaultRange()
    const res = await getTelemetryHistory(selectedDeviceId.value, {
      from: from.toISOString(),
      to: to.toISOString(),
      page: pagination.page,
      size: pagination.size,
    })
    if (res.errorCode === '00000') {
      historyData.value = res.body.content
      pagination.total = res.body.totalElements
    }

    // fetch stats concurrently
    const sRes = await getTelemetryStats(selectedDeviceId.value, {
      from: from.toISOString(),
      to: to.toISOString(),
    })
    if (sRes.errorCode === '00000') {
      statsData.value = sRes.body
    }
  } catch {
    ElMessage.error(t('telemetry.fetchError'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 0
  fetchHistory()
}

function handlePageChange(page: number) {
  pagination.page = page - 1
  fetchHistory()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 0
  fetchHistory()
}

function formatTs(ts: string) {
  return new Date(ts).toLocaleString('zh-TW', { hour12: false })
}

function formatNum(n: number | undefined) {
  if (n === undefined || n === null) return '--'
  return n.toFixed(4)
}

// numeric field keys from the latest row
const numericFields = ref<string[]>([])

function deriveNumericFields(rows: TelemetryPointResponse[]) {
  if (!rows.length) return
  const sample = rows[0].values
  numericFields.value = Object.keys(sample).filter((k) => typeof sample[k] === 'number')
}

// watch historyData changes → derive fields
import { watch } from 'vue'
watch(historyData, (rows) => deriveNumericFields(rows))

onMounted(async () => {
  timeRange.value = getDefaultRange()
  await loadDevices()
})
</script>

<template>
  <div class="telemetry-history">
    <div class="page-header">
      <h2>{{ t('telemetry.historyTitle') }}</h2>
      <p class="subtitle">{{ t('telemetry.historySubtitle') }}</p>
    </div>

    <!-- Filter bar -->
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item :label="t('telemetry.selectDevice')">
          <el-select
            v-model="selectedDeviceId"
            :placeholder="t('telemetry.selectDevice')"
            filterable
            clearable
            style="width: 250px"
          >
            <el-option
              v-for="d in devices"
              :key="d.id"
              :label="`[${d.deviceType}] ${d.deviceCode}`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('telemetry.timeRange')">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            :range-separator="t('common.to')"
            :start-placeholder="t('common.startTime')"
            :end-placeholder="t('common.endTime')"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            style="width: 380px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :disabled="!selectedDeviceId" @click="handleSearch">
            {{ t('common.query') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-empty v-if="!selectedDeviceId" :description="t('telemetry.selectDeviceHint')" />

    <template v-else>
      <!-- Stats summary -->
      <el-card
        v-if="statsData.length"
        shadow="never"
        class="stats-card"
        :header="t('telemetry.statsTitle')"
      >
        <el-table :data="statsData" size="small" border>
          <el-table-column prop="field" :label="t('telemetry.field')" min-width="120" />
          <el-table-column prop="count" :label="t('telemetry.count')" width="80" />
          <el-table-column :label="t('telemetry.min')" width="100">
            <template #default="{ row }">{{ formatNum(row.min) }}</template>
          </el-table-column>
          <el-table-column :label="t('telemetry.max')" width="100">
            <template #default="{ row }">{{ formatNum(row.max) }}</template>
          </el-table-column>
          <el-table-column :label="t('telemetry.avg')" width="100">
            <template #default="{ row }">{{ formatNum(row.avg) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- History table -->
      <el-card shadow="never" :header="t('telemetry.historyTitle')">
        <el-table
          v-loading="loading"
          :data="historyData"
          border
          size="small"
          style="width: 100%"
        >
          <el-table-column
            prop="ts"
            :label="t('telemetry.timestamp')"
            width="180"
            :formatter="(row: TelemetryPointResponse) => formatTs(row.ts)"
          />
          <!-- Dynamic value columns from the first row -->
          <el-table-column
            v-for="field in numericFields"
            :key="field"
            :label="field"
            :min-width="100"
          >
            <template #default="{ row }">
              {{ row.values[field] ?? '--' }}
            </template>
          </el-table-column>
          <!-- Fallback: show raw JSON if no numeric fields derived yet -->
          <el-table-column
            v-if="!numericFields.length"
            :label="t('telemetry.values')"
          >
            <template #default="{ row }">
              <code>{{ JSON.stringify(row.values) }}</code>
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
          :page-sizes="[50, 100, 200, 500]"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.telemetry-history {
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

.stats-card {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
