<script setup lang="ts">
import { ref } from 'vue'
import VmsStreamPlayer from './VmsStreamPlayer.vue'
import PlaybackControlBar from './PlaybackControlBar.vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  cameraId: number
  streamType: 'live' | 'playback'
  startTime?: string
  endTime?: string
}>()

const { t } = useI18n()
const currentTime = ref(0)

function onTimeUpdate(time: number) { currentTime.value = time }
function onSeek(timestamp: number) { /* StreamPlayer handles via prop change */ }
</script>

<template>
  <div class="vms-stream-panel">
    <div v-if="!cameraId" class="no-camera-selected">
      <el-empty :description="t('vms.noCameraSelected')" />
    </div>
    <template v-else>
      <VmsStreamPlayer
        :camera-id="cameraId"
        :stream-type="streamType"
        :start-time="startTime"
        :end-time="endTime"
        @time-update="onTimeUpdate"
      />
      <PlaybackControlBar
        v-if="streamType === 'playback'"
        :current-time="currentTime"
        :total-duration="0"
        @seek="onSeek"
      />
    </template>
  </div>
</template>

<style scoped>
.vms-stream-panel { flex: 1; display: flex; flex-direction: column; background: #000; }
.no-camera-selected { display: flex; align-items: center; justify-content: center; height: 100%; background: var(--el-bg-color-page); }
</style>
