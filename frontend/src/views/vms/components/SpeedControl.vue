<script setup lang="ts">
const props = defineProps<{ modelValue: number }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: number): void }>()

const speeds = [1, 2, 4, 8, -1, -2, -4, -8]
</script>

<template>
  <el-select :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)" size="small" style="width: 100px">
    <el-option v-for="s in speeds" :key="s" :value="s" :label="s > 0 ? `${s}x` : `${Math.abs(s)}x ${$t('vms.reverse')}`" />
  </el-select>
</template>
