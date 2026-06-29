import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { TenantOption } from '@/types/auth'

export const useTenantStore = defineStore('tenant', () => {
  const currentTenantId = ref<string | null>(null)
  const tenantList = ref<TenantOption[]>([])
  const needsSelection = ref(false)

  function setTenant(tenantId: string) {
    currentTenantId.value = tenantId
  }

  function setTenantList(list: TenantOption[]) {
    tenantList.value = list
    needsSelection.value = list.length > 1
  }

  return { currentTenantId, tenantList, needsSelection, setTenant, setTenantList }
})
