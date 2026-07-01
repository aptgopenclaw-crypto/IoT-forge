<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getTelemetryLatest } from '@/api/telemetry'
import { listDevices } from '@/api/device'
import type { DeviceResponse } from '@/types/device'
import type { TelemetryLatestResponse } from '@/types/telemetry'

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

// ── Latest values ──
const latest = ref<TelemetryLatestResponse | null>(null)
const loading = ref(false)
const refreshing = ref(false)

const pollingState = reactive({ intervalId: 0 as ReturnType<typeof setInterval> | 0 })
const POLL_INTERVAL_MS = 15_000

async function fetchLatest(quiet = false) {
  if (!selectedDeviceId.value) return
  if (!quiet) loading.value = true
  refreshing.value = true
  try {
    const res = await getTelemetryLatest(selectedDeviceId.value)
    if (res.errorCode === '00000') {
      latest.value = res.body
    }
  } catch {
    if (!quiet) ElMessage.error(t('telemetry.fetchError'))
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function startPolling() {
  stopPolling()
  pollingState.intervalId = setInterval(() => fetchLatest(true), POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollingState.intervalId) {
    clearInterval(pollingState.intervalId)
    pollingState.intervalId = 0
  }
}

async function onDeviceChange() {
  latest.value = null
  stopPolling()
  await fetchLatest()
  startPolling()
}

function formatTs(ts: string | undefined) {
  if (!ts) return '--'
  return new Date(ts).toLocaleString('zh-TW', { hour12: false })
}

onMounted(async () => {
  await loadDevices()
})

onUnmounted(stopPolling)
</script>

<template>
  <div class="telemetry-live">
    <div class="page-header">
      <h2>{{ t('telemetry.liveTitle') }}</h2>
      <p class="subtitle">{{ t('telemetry.liveSubtitle') }}</p>
    </div>

    <!-- Device selector -->
    <el-card class="filter-card" shadow="never">
      <el-select
        v-model="selectedDeviceId"
        :placeholder="t('telemetry.selectDevice')"
        filterable
        clearable
        style="width: 300px"
        @change="onDeviceChange"
      >
        <el-option
          v-for="d in devices"
          :key="d.id"
          :label="`[${d.deviceType}] ${d.deviceCode}`"
          :value="d.id"
        />
      </el-select>
      <el-button
        :icon="'Refresh'"
        :loading="refreshing"
        :disabled="!selectedDeviceId"
        style="margin-left: 12px"
        @click="fetchLatest()"
      >
        {{ t('common.refresh') }}
      </el-button>
    </el-card>

    <!-- No device selected -->
    <el-empty v-if="!selectedDeviceId" :description="t('telemetry.selectDeviceHint')" />

    <!-- Loading -->
    <div v-else-if="loading" class="loading-wrap">
      <el-skeleton :rows="4" animated />
    </div>

    <!-- No data -->
    <el-empty
      v-else-if="!latest"
      :description="t('telemetry.noLatestData')"
    />

    <!-- Values grid -->
    <template v-else>
      <div class="latest-meta">
        <span>{{ t('telemetry.lastUpdated') }}：</span>
        <el-tag type="success">{{ formatTs(latest.ts) }}</el-tag>
        <el-tag type="info" style="margin-left: 8px">
          {{ t('telemetry.pollInterval', { sec: POLL_INTERVAL_MS / 1000 }) }}
        </el-tag>
      </div>

      <el-row :gutter="16" class="values-grid">
        <el-col
          v-for="(val, key) in latest.values"
          :key="key"
          :xs="12"
          :sm="8"
          :md="6"
          :lg="4"
        >
          <el-card class="value-card" shadow="hover">
            <div class="value-key">{{ key }}</div>
            <div class="value-num">{{ val }}</div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.telemetry-live {
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
  display: flex;
  align-items: center;
}

.loading-wrap {
  padding: 20px;
}

.latest-meta {
  margin-bottom: 16px;
  font-size: 13px;
}

.values-grid {
  margin-top: 8px;
}

.value-card {
  text-align: center;
  margin-bottom: 16px;
}

.value-key {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
  word-break: break-all;
}

.value-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-color-primary);
}
</style>
