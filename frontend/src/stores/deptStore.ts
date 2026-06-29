import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDeptOptions } from '@/api/dept'
import type { DeptOptionVO } from '@/types/dept'

function flattenToMap(nodes: DeptOptionVO[]): Map<number, string> {
  const map = new Map<number, string>()
  for (const node of nodes) {
    map.set(node.value, node.label)
    if (node.children?.length) {
      for (const [k, v] of flattenToMap(node.children)) {
        map.set(k, v)
      }
    }
  }
  return map
}

export const useDeptStore = defineStore('dept', () => {
  const deptOptions = ref<DeptOptionVO[]>([])
  const deptFlatMap = ref(new Map<number, string>())
  const initialized = ref(false)
  const loading = ref(false)

  async function fetchDeptOptions() {
    loading.value = true
    try {
      const res = await getDeptOptions()
      deptOptions.value = res.body
      deptFlatMap.value = flattenToMap(res.body)
      initialized.value = true
    } finally {
      loading.value = false
    }
  }

  function getDeptName(deptId: number | string | null | undefined): string {
    if (deptId == null) return ''
    const numId = typeof deptId === 'string' ? Number(deptId) : deptId
    return deptFlatMap.value.get(numId) ?? '未知部門'
  }

  return { deptOptions, deptFlatMap, initialized, loading, fetchDeptOptions, getDeptName }
})
