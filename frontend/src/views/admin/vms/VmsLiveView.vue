<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listCameras, getLiveStream } from '@/api/vms'
import type { VmsCamera, CameraLiveResponse } from '@/types/vms'

const { t } = useI18n()

// ── Camera tree ──
const cameras = ref<VmsCamera[]>([])
const selectedCameraId = ref<number | null>(null)
const loadingCameras = ref(false)

// ── Stream player ──
const streamInfo = ref<CameraLiveResponse | null>(null)
const loadingStream = ref(false)
const streamError = ref('')

let refreshTimer: ReturnType<typeof setInterval> | null = null

async function loadCameras() {
  loadingCameras.value = true
  try {
    const res = await listCameras()
    if (res.errorCode === '00000') {
      cameras.value = res.body
    }
  } finally {
    loadingCameras.value = false
  }
}

async function onCameraSelect(cameraId: number) {
  selectedCameraId.value = cameraId
  streamError.value = ''
  await fetchStream(cameraId)
  startAutoRefresh()
}

async function fetchStream(cameraId: number) {
  loadingStream.value = true
  streamInfo.value = null
  try {
    const res = await getLiveStream(cameraId)
    if (res.errorCode === '00000') {
      streamInfo.value = res.body
    } else {
      streamError.value = t('vms.connectionFailed')
    }
  } catch {
    streamError.value = t('vms.connectionFailed')
  } finally {
    loadingStream.value = false
  }
}

function startAutoRefresh() {
  stopAutoRefresh()
  // 每 30 秒檢查串流是否即將過期
  refreshTimer = setInterval(async () => {
    if (selectedCameraId.value && streamInfo.value) {
      const expiresAt = new Date(streamInfo.value.expiresAt).getTime()
      if (Date.now() > expiresAt - 15000) {
        await fetchStream(selectedCameraId.value)
      }
    }
  }, 30000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(loadCameras)
onUnmounted(stopAutoRefresh)
</script>

<template>
  <div class="vms-live-view">
    <div class="camera-sidebar">
      <h3>{{ t('vms.live') }}</h3>
      <el-tree
        :data="cameras"
        :props="{ label: 'displayName', value: 'id' }"
        :filter-node-method="(value: string, data: VmsCamera) => data.displayName?.includes(value)"
        node-key="id"
        :highlight-current="true"
        @node-click="(data: VmsCamera) => onCameraSelect(data.id)"
      />
    </div>

    <div class="stream-area">
      <div v-if="!selectedCameraId" class="placeholder">
        <el-empty :description="t('vms.noCameraSelected')" />
      </div>

      <div v-else-if="loadingStream" class="loading">
        <span>{{ t('common.loading') }}...</span>
      </div>

      <div v-else-if="streamError" class="error">
        <el-result status="error" :title="streamError" />
        <el-button @click="fetchStream(selectedCameraId)">{{ t('common.retry') }}</el-button>
      </div>

      <div v-else-if="streamInfo" class="player-wrapper">
        <div class="camera-info">
          <span class="camera-name">{{ streamInfo.displayName }}</span>
          <el-tag :type="streamInfo.status === 'ONLINE' ? 'success' : 'danger'" size="small">
            {{ t(`vms.${streamInfo.status.toLowerCase()}`) }}
          </el-tag>
        </div>
        <!-- WebRTC 播放器: 嵌入 ZLMediaKit 內建 webrtcplayer 頁面 -->
        <div class="player">
          <iframe
            :src="streamInfo.playUrl"
            class="stream-iframe"
            allow="autoplay; microphone; camera; display-capture"
            referrerpolicy="origin"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.vms-live-view {
  display: flex;
  height: calc(100vh - 120px);
  gap: 16px;
}
.camera-sidebar {
  width: 280px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-light);
  padding-right: 16px;
  overflow-y: auto;
}
.camera-sidebar h3 {
  margin-bottom: 12px;
  font-size: 16px;
}
.stream-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.placeholder, .loading, .error {
  text-align: center;
}
.player-wrapper {
  width: 100%;
  max-width: 960px;
}
.camera-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.camera-name {
  font-size: 18px;
  font-weight: 600;
}
.player {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}
.stream-iframe {
  width: 100%;
  height: 100%;
  border: none;
}
.unsupported {
  padding: 40px;
}
</style>
