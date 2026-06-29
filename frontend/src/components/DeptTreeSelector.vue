<script setup lang="ts">
import { useDeptStore } from '@/stores/deptStore'

defineProps<{
  modelValue: number | null
  placeholder?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | null): void
}>()

const deptStore = useDeptStore()

function handleChange(val: number | null) {
  emit('update:modelValue', val ?? null)
}
</script>

<template>
  <el-tree-select
    :model-value="modelValue"
    :data="deptStore.deptOptions"
    :props="{ label: 'label', value: 'value', children: 'children' }"
    check-strictly
    :placeholder="placeholder ?? '請選擇部門'"
    :disabled="disabled"
    clearable
    filterable
    class="dept-tree-selector"
    @update:model-value="handleChange"
  />
</template>

<style scoped>
.dept-tree-selector {
  width: 100%;
}
</style>
