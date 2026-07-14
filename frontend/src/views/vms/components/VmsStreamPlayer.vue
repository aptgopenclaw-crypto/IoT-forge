<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, shallowRef } from 'vue'
import Hls from 'hls.js'
import { createStream, stopStream } from '@/api/vms'
import { useAuthStore } from '@/stores/authStore'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  cameraId: number
  streamType: 'live' | 'playback'
  speed?: number
  startTime?: string
  endTime?: string
}>()

const emit = defineEmits<{
  (e: 'timeUpdate', time: number): void
  (e: 'playbackEnded'): void
  (e: 'error', msg: string): void
}>()

const { t } = useI18n()
const authStore = useAuthStore()
const videoRef = ref<HTMLVideoElement | null>(null)
const hlsInstance = shallowRef<Hls | null>(null)
const sessionToken = ref<string>('')
const playing = ref(false)
const currentSpeed = ref(1)
const errorMsg = ref('')
const loading = ref(false)
const streamBaseUrl = import.meta.env.VITE_API_BASE_URL || ''
const videoRef = ref<HTMLVideoElement | null>(null)
const hlsInstance = shallowRef<Hls | null>(null)
const sessionToken = ref<string>('')
const playing = ref(false)
const errorMsg = ref('')
const loading = ref(false)

let retryCount = 0
const MAX_RETRIES = 3

async function initStream() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await createStream(props.cameraId, {
      type: props.streamType,
      startTime: props.startTime,
      endTime: props.endTime,
    })
    sessionToken.value = res.body!.sessionToken
    loadHls()
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.errorMsg || t('vms.connectionFailed')
    emit('error', errorMsg.value)
  } finally {
    loading.value = false
  }
}

function getStreamUrl(path: string): string {
  return `${streamBaseUrl}/v1/auth/vms/stream/${sessionToken.value}${path}`
}

function loadHls() {
  if (!videoRef.value || !sessionToken.value) return

  if (hlsInstance.value) {
    hlsInstance.value.destroy()
  }

  const config = props.streamType === 'live'
    ? { lowLatencyMode: true, backBufferLength: 30 }
    : { maxBufferLength: 240, backBufferLength: 60 }

  const hls = new Hls({
    ...config,
    xhrSetup: (xhr) => {
      xhr.setRequestHeader('Authorization', `Bearer ${authStore.accessToken}`)
    },
    fetchSetup: (context) => {
      context.headers = {
        ...context.headers,
        'Authorization': `Bearer ${authStore.accessToken}`,
      }
      return new Request(context.url, context)
    },
  })

  hls.loadSource(getStreamUrl('/master.m3u8'))
  hls.attachMedia(videoRef.value)

  hls.on(Hls.Events.MANIFEST_PARSED, () => {
    playing.value = true
    videoRef.value?.play()
  })

  hls.on(Hls.Events.ERROR, (_event: any, data: any) => {
    if (data.fatal) {
      retryCount++
      if (retryCount <= MAX_RETRIES) {
        hls.recoverMediaError()
      } else {
        errorMsg.value = t('vms.connectionFailed')
        emit('error', errorMsg.value)
      }
    }
  })

  hlsInstance.value = hls
}

async function changeSpeed(speed: number) {
  currentSpeed.value = speed
  if (speed === 1) {
    // Reload normal speed playlist
    if (hlsInstance.value && sessionToken.value) {
      hlsInstance.value.loadSource(getStreamUrl('/master.m3u8?' + Date.now()))
    }
  } else {
    // Trickplay
    if (hlsInstance.value && sessionToken.value) {
      hlsInstance.value.loadSource(getStreamUrl(`/trickplay?speed=${speed}&_=${Date.now()}`))
    }
  }
}

function seekTo(timestamp: number) {
  if (hlsInstance.value && sessionToken.value) {
    hlsInstance.value.loadSource(getStreamUrl(`/master.m3u8?pos=${timestamp}&_=${Date.now()}`))
  }
}

async function destroyStream() {
  if (sessionToken.value) {
    try { await stopStream(sessionToken.value) } catch { /* ignore */ }
    sessionToken.value = ''
  }
  if (hlsInstance.value) {
    hlsInstance.value.destroy()
    hlsInstance.value = null
  }
  playing.value = false
}

watch(() => props.cameraId, () => {
  retryCount = 0
  destroyStream().then(() => initStream())
})

watch(() => props.speed, (newSpeed) => {
  if (newSpeed && newSpeed !== currentSpeed.value) {
    changeSpeed(newSpeed)
  }
})

onMounted(() => initStream())
onBeforeUnmount(() => destroyStream())
</script>

<template>
  <div class="vms-stream-player" :class="{ 'has-error': errorMsg }">
    <div v-if="loading" class="player-overlay">
      <el-icon class="is-loading" :size="32"><i-ep-Loading /></el-icon>
      <span>{{ t('common.loading') }}</span>
    </div>

    <div v-else-if="errorMsg" class="player-overlay error">
      <el-icon :size="32" color="#f56c6c"><i-ep-WarningFilled /></el-icon>
      <span>{{ errorMsg }}</span>
      <el-button type="primary" size="small" @click="initStream" style="margin-top:8px">
        {{ t('common.retry') }}
      </el-button>
    </div>

    <video v-else ref="videoRef" autoplay muted playsinline class="video-element" />
  </div>
</template>

<style scoped>
.vms-stream-player { position: relative; width: 100%; background: #000; aspect-ratio: 16/9; display: flex; align-items: center; justify-content: center; }
.video-element { width: 100%; height: 100%; object-fit: contain; }
.player-overlay { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #fff; }
.player-overlay.error { color: #f56c6c; }
</style>
