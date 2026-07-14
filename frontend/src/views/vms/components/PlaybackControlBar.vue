<script setup lang="ts">
import { ref } from 'vue'
import SpeedControl from './SpeedControl.vue'

const props = defineProps<{
  currentTime: number
  totalDuration: number
}>()

const emit = defineEmits<{
  (e: 'seek', timestamp: number): void
  (e: 'update:speed', speed: number): void
}>()

const speed = ref(1)
</script>

<template>
  <div class="playback-bar">
    <el-slider
      :model-value="totalDuration > 0 ? (currentTime / totalDuration) * 100 : 0"
      @update:model-value="emit('seek', (totalDuration * $event) / 100)"
      :max="100"
      :show-tooltip="false"
      class="timeline-slider"
    />
    <div class="bar-controls">
      <span class="time-display">{{ new Date(currentTime).toISOString().substr(11, 8) }}</span>
      <SpeedControl v-model="speed" @update:model-value="emit('update:speed', $event)" />
    </div>
  </div>
</template>

<style scoped>
.playback-bar { background: var(--el-bg-color); padding: 8px 16px; }
.timeline-slider { margin: 0 0 8px; }
.bar-controls { display: flex; justify-content: space-between; align-items: center; }
.time-display { font-size: 13px; font-family: monospace; }
</style>
