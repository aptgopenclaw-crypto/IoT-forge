<script setup lang="ts">
import { ref } from 'vue'
import { useVmsStore } from '@/stores/vmsStore'
import VmsCameraList from './components/VmsCameraList.vue'
import VmsStreamPanel from './components/VmsStreamPanel.vue'
import VmsTimeRangePicker from './components/VmsTimeRangePicker.vue'

const store = useVmsStore()
const startTime = ref('')
const endTime = ref('')

function onTimeRangeSelect(start: string, end: string) {
  startTime.value = start
  endTime.value = end
}
</script>

<template>
  <div class="vms-playback-view">
    <VmsCameraList />
    <div class="playback-main">
      <VmsTimeRangePicker @select="onTimeRangeSelect" />
      <VmsStreamPanel
        v-if="store.selectedCamera && startTime"
        :camera-id="store.selectedCamera.id"
        stream-type="playback"
        :start-time="startTime"
        :end-time="endTime"
      />
      <div v-else class="no-range-selected">
        <el-empty :description="$t('vms.selectTimeRangeHint')" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.vms-playback-view { display: flex; height: calc(100vh - 120px); }
.playback-main { flex: 1; display: flex; flex-direction: column; }
.no-range-selected { display: flex; align-items: center; justify-content: center; flex: 1; background: var(--el-bg-color-page); }
</style>
