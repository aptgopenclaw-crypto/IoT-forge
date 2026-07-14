// ── VMS Store ────────────────────────────────────────────────────────────

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { VmsCamera } from '@/types/vms'
import { listVmsCameras } from '@/api/vms'

export const useVmsStore = defineStore('vms', () => {
  const cameras = ref<VmsCamera[]>([])
  const selectedCamera = ref<VmsCamera | null>(null)
  const loading = ref(false)

  const onlineCameras = computed(() =>
    cameras.value.filter(c => c.status === 'ONLINE')
  )

  async function fetchCameras() {
    loading.value = true
    try {
      const res = await listVmsCameras()
      cameras.value = res.body ?? []
    } finally {
      loading.value = false
    }
  }

  function selectCamera(camera: VmsCamera | null) {
    selectedCamera.value = camera
  }

  return { cameras, selectedCamera, onlineCameras, loading, fetchCameras, selectCamera }
})
