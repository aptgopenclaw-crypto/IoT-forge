<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listCameras, listCameraEvents } from '@/api/vms'
import type { VmsCamera, VmsCameraEvent, VmsEventType } from '@/types/vms'

const { t } = useI18n()

// ── Filters ──
const cameras = ref<VmsCamera[]>([])
const selectedCameraId = ref<number | null>(null)
const filterEventType = ref<VmsEventType | ''>('')
const timeRange = ref<[Date, Date] | null>(null)

function getDefaultRange(): [Date, Date] {
  const now = new Date()
  return [new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000), now]
}

// ── Table ──
const events = ref<VmsCameraEvent[]>([])
const loading = ref(false)
const pagination = reactive({ page: 0, size: 20, total: 0 })

// ── Event type options ──
const eventTypeOptions = [
  { value: '', label: t('common.all') },
  { value: 'MOTION_DETECT', label: t('vms.motionDetect') },
  { value: 'CAMERA_OFFLINE', label: t('vms.cameraOffline') },
  { value: 'CAMERA_ONLINE', label: t('vms.cameraOnline') },
  { value: 'VIDEO_LOST', label: t('vms.videoLost') },
]

async function loadCameras() {
  try {
    const res = await listCameras()
    if (res.errorCode === '00000') {
      cameras.value = res.body
    }
  } catch { /* no-op */ }
}

async function fetchEvents() {
  if (!selectedCameraId.value) return
  loading.value = true
  try {
    const [from, to] = timeRange.value ?? getDefaultRange()
    const res = await listCameraEvents(selectedCameraId.value, {
      startTime: from.toISOString(),
      endTime: to.toISOString(),
      page: pagination.page,
    })
    if (res.errorCode === '00000') {
      events.value = res.body.content
      pagination.total = res.body.totalElements
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 0
  fetchEvents()
}

function handlePageChange(page: number) {
  pagination.page = page - 1
  fetchEvents()
}

function formatEventType(type: string): string {
  const opt = eventTypeOptions.find(o => o.value === type)
  return opt ? opt.label : type
}

onMounted(loadCameras)
</script>

<template>
  <div class="vms-event-logs">
    <!-- Filters -->
    <el-card class="filters">
      <el-form :inline="true">
        <el-form-item :label="t('vms.camera')">
          <el-select
            v-model="selectedCameraId"
            :placeholder="t('common.select')"
            style="width: 200px"
            filterable
            clearable
            @change="fetchEvents"
          >
            <el-option
              v-for="cam in cameras"
              :key="cam.id"
              :label="cam.displayName"
              :value="cam.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('vms.eventType')">
          <el-select v-model="filterEventType" style="width: 140px" @change="handleSearch">
            <el-option
              v-for="opt in eventTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('vms.startTime')">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            :range-separator="t('common.to')"
            :start-placeholder="t('vms.startTime')"
            :end-placeholder="t('vms.endTime')"
            :default-value="getDefaultRange()"
            format="YYYY-MM-DD HH:mm:ss"
            style="width: 380px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            {{ t('common.search') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Event table -->
    <el-card class="table-card">
      <el-table
        :data="events"
        v-loading="loading"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="id" :label="t('common.id')" width="70" />
        <el-table-column :label="t('vms.eventType')" width="140">
          <template #default="{ row }: { row: VmsCameraEvent }">
            <el-tag :type="row.eventType === 'CAMERA_OFFLINE' || row.eventType === 'VIDEO_LOST' ? 'danger' : 'info'" size="small">
              {{ formatEventType(row.eventType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('vms.payload')" min-width="200">
          <template #default="{ row }: { row: VmsCameraEvent }">
            <code class="payload-json">{{ row.payload ? JSON.stringify(row.payload) : '-' }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="occurredAt" :label="t('vms.occurredAt')" width="180">
          <template #default="{ row }: { row: VmsCameraEvent }">
            {{ new Date(row.occurredAt).toLocaleString() }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper" v-if="pagination.total > pagination.size">
        <el-pagination
          :current-page="pagination.page + 1"
          :page-size="pagination.size"
          :total="pagination.total"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.vms-event-logs {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.filters {
  flex-shrink: 0;
}
.table-card {
  flex: 1;
}
.payload-json {
  font-size: 12px;
  word-break: break-all;
  max-height: 48px;
  overflow: hidden;
  display: block;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
