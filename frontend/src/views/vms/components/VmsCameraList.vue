<script setup lang="ts">
import { onMounted } from 'vue'
import { useVmsStore } from '@/stores/vmsStore'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const store = useVmsStore()

onMounted(() => {
  if (store.cameras.length === 0) store.fetchCameras()
})

function select(camera: any) {
  store.selectCamera(camera)
}
</script>

<template>
  <div class="vms-camera-list">
    <div class="list-header">
      <h3>{{ t('vms.cameraList') }}</h3>
      <el-tag size="small" type="success">{{ store.onlineCameras.length }}/{{ store.cameras.length }}</el-tag>
    </div>

    <div v-if="store.loading" v-loading="true" class="loading-placeholder" />
    <div v-else-if="store.cameras.length === 0" class="empty-state">
      <el-empty :description="t('common.noData')" />
    </div>
    <el-scrollbar v-else class="camera-scroll">
      <div
        v-for="cam in store.cameras"
        :key="cam.id"
        class="camera-item"
        :class="{ selected: store.selectedCamera?.id === cam.id }"
        @click="select(cam)"
      >
        <span class="status-dot" :class="cam.status.toLowerCase()" />
        <span class="camera-name">{{ cam.displayName || cam.vmsCameraId }}</span>
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped>
.vms-camera-list { width: 260px; border-right: 1px solid var(--el-border-color-light); display: flex; flex-direction: column; }
.list-header { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; border-bottom: 1px solid var(--el-border-color-lighter); }
.list-header h3 { margin: 0; font-size: 14px; }
.loading-placeholder { height: 200px; }
.empty-state { padding: 40px 16px; }
.camera-scroll { flex: 1; }
.camera-item { display: flex; align-items: center; padding: 10px 16px; cursor: pointer; transition: background 0.15s; }
.camera-item:hover { background: var(--el-fill-color-light); }
.camera-item.selected { background: var(--el-color-primary-light-9); }
.status-dot { width: 8px; height: 8px; border-radius: 50%; margin-right: 10px; flex-shrink: 0; }
.status-dot.online { background: #67c23a; }
.status-dot.offline { background: #909399; }
.status-dot.error { background: #f56c6c; }
.camera-name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
