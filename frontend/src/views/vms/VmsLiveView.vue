<script setup lang="ts">
import { useVmsStore } from '@/stores/vmsStore'
import VmsCameraList from './components/VmsCameraList.vue'
import VmsStreamPanel from './components/VmsStreamPanel.vue'

const store = useVmsStore()
</script>

<template>
  <div class="vms-live-view">
    <VmsCameraList />
    <VmsStreamPanel
      :camera-id="store.selectedCamera?.id ?? 0"
      stream-type="live"
    />
  </div>
</template>

<style scoped>
.vms-live-view { display: flex; height: calc(100vh - 120px); }
</style>
