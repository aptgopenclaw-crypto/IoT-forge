<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { VmsStreamLog } from '@/types/vms'
import { queryStreamLogs } from '@/api/vms'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const logs = ref<VmsStreamLog[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const filter = ref({ userId: undefined as number | undefined, cameraId: undefined as number | undefined, streamType: undefined as string | undefined })

async function fetchData() {
  loading.value = true
  try {
    const res = await queryStreamLogs({ ...filter.value, page: page.value, size: pageSize.value })
    logs.value = res.body?.content ?? []
    total.value = res.body?.totalElements ?? 0
  } finally { loading.value = false }
}

onMounted(fetchData)
</script>

<template>
  <div class="vms-stream-log">
    <div class="page-header">
      <h2>{{ t('vms.streamLogs') }}</h2>
    </div>

    <el-card class="filter-bar" shadow="never">
      <el-form :model="filter" inline>
        <el-form-item :label="t('common.type')">
          <el-select v-model="filter.streamType" clearable style="width:140px">
            <el-option value="LIVE" :label="t('vms.liveStream')" />
            <el-option value="PLAYBACK" :label="t('vms.playbackStream')" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">{{ t('common.query') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="logs" v-loading="loading" stripe style="margin-top:12px">
      <el-table-column prop="userName" :label="t('common.user')" width="120" />
      <el-table-column prop="cameraName" :label="t('vms.camera')" min-width="160" />
      <el-table-column prop="streamType" :label="t('common.type')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.streamType === 'LIVE' ? 'primary' : 'warning'" size="small">{{ row.streamType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startedAt" :label="t('common.startTime')" width="180" />
      <el-table-column prop="endedAt" :label="t('common.endTime')" width="180" />
      <el-table-column prop="durationSeconds" :label="t('common.duration')" width="100">
        <template #default="{ row }">{{ row.durationSeconds ? `${row.durationSeconds}s` : '-' }}</template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
        :total="total" layout="prev, pager, next" @current-change="fetchData" />
    </div>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.filter-bar { margin-bottom: 8px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
