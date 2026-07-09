<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { listCameras, getPlayback } from '@/api/vms'
import type { VmsCamera, CameraPlaybackResponse } from '@/types/vms'

const { t } = useI18n()

// ── Camera selector ──
const cameras = ref<VmsCamera[]>([])
const selectedCameraId = ref<number | null>(null)
const loadingCameras = ref(false)

// ── Time range ──
const timeRange = ref<[Date, Date] | null>(null)

// ── Playback ──
const playbackInfo = ref<CameraPlaybackResponse | null>(null)
const loadingPlayback = ref(false)
const playbackError = ref('')

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

// 預設時間範圍：過去 1 小時
function getDefaultRange(): [Date, Date] {
  const now = new Date()
  return [new Date(now.getTime() - 60 * 60 * 1000), now]
}

async function handlePlay() {
  if (!selectedCameraId.value) return

  const [start, end] = timeRange.value ?? getDefaultRange()
  playbackError.value = ''
  playbackInfo.value = null
  loadingPlayback.value = true

  try {
    const res = await getPlayback(selectedCameraId.value, start.toISOString(), end.toISOString())
    if (res.errorCode === '00000') {
      playbackInfo.value = res.body
    } else {
      playbackError.value = t('vms.connectionFailed')
    }
  } catch {
    playbackError.value = t('vms.connectionFailed')
  } finally {
    loadingPlayback.value = false
  }
}

function handleStop() {
  playbackInfo.value = null
}

// 初始載入攝影機列表
loadCameras()
</script>

<template>
  <div class="vms-playback-view">
    <el-card class="controls">
      <el-form :inline="true">
        <el-form-item :label="t('vms.camera')">
          <el-select
            v-model="selectedCameraId"
            :placeholder="t('common.select')"
            style="width: 240px"
            filterable
          >
            <el-option
              v-for="cam in cameras"
              :key="cam.id"
              :label="cam.displayName"
              :value="cam.id"
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
            style="width: 400px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :disabled="!selectedCameraId" :loading="loadingPlayback" @click="handlePlay">
            <el-icon><VideoPlay /></el-icon>
            {{ t('vms.play') }}
          </el-button>
          <el-button v-if="playbackInfo" @click="handleStop">
            {{ t('vms.stop') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="playback-area">
      <div v-if="!playbackInfo && !playbackError && !loadingPlayback" class="placeholder">
        <el-empty :description="t('vms.noCameraSelected')" />
      </div>

      <div v-if="loadingPlayback" class="loading">
        <span>{{ t('common.loading') }}...</span>
      </div>

      <div v-if="playbackError" class="error">
        <el-result status="error" :title="playbackError" />
        <el-button @click="handlePlay">{{ t('common.retry') }}</el-button>
      </div>

      <div v-if="playbackInfo" class="player-wrapper">
        <div class="playback-info">
          <span class="camera-name">{{ playbackInfo.displayName }}</span>
          <span class="time-range">
            {{ new Date(playbackInfo.startTime).toLocaleString() }}
            ~
            {{ new Date(playbackInfo.endTime).toLocaleString() }}
          </span>
          <el-tag :type="playbackInfo.status === 'ONLINE' ? 'success' : 'danger'" size="small">
            {{ t(`vms.${playbackInfo.status.toLowerCase()}`) }}
          </el-tag>
        </div>
        <!-- WebRTC 回放播放器: 嵌入 ZLMediaKit 內建 webrtcplayer 頁面 -->
        <div class="player">
          <iframe
            :src="playbackInfo.playUrl"
            class="playback-iframe"
            allow="autoplay; microphone; camera; display-capture"
            referrerpolicy="origin"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.vms-playback-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.controls {
  flex-shrink: 0;
}
.playback-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}
.placeholder, .loading, .error {
  text-align: center;
}
.player-wrapper {
  width: 100%;
  max-width: 960px;
}
.playback-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.camera-name {
  font-size: 18px;
  font-weight: 600;
}
.time-range {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.player {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}
.playback-iframe {
  width: 100%;
  height: 100%;
  border: none;
}
</style>
