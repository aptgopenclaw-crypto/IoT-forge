<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{ (e: 'select', start: string, end: string): void }>()
const startTime = ref('')
const endTime = ref('')
const presets = [
  { label: '最近 1 小時', getRange: () => { const e = new Date(); return [new Date(e.getTime() - 3600000).toISOString().slice(0, 16), e.toISOString().slice(0, 16)] } },
  { label: '最近 6 小時', getRange: () => { const e = new Date(); return [new Date(e.getTime() - 21600000).toISOString().slice(0, 16), e.toISOString().slice(0, 16)] } },
  { label: '今天', getRange: () => { const now = new Date(); const s = new Date(now.getFullYear(), now.getMonth(), now.getDate()); return [s.toISOString().slice(0, 16), now.toISOString().slice(0, 16)] } },
]

function applyPreset(preset: typeof presets[0]) {
  const [s, e] = preset.getRange()
  startTime.value = s
  endTime.value = e
  emit('select', s, e)
}
</script>

<template>
  <div class="time-range-picker">
    <div class="presets">
      <el-button v-for="p in presets" :key="p.label" size="small" @click="applyPreset(p)">{{ p.label }}</el-button>
    </div>
    <div class="custom-range">
      <el-date-picker v-model="startTime" type="datetime" placeholder="開始時間" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm" />
      <span class="separator">~</span>
      <el-date-picker v-model="endTime" type="datetime" placeholder="結束時間" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm" />
      <el-button type="primary" size="small" @click="emit('select', startTime, endTime)" :disabled="!startTime || !endTime">
        {{ $t('common.query') }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.time-range-picker { padding: 12px 16px; border-bottom: 1px solid var(--el-border-color-lighter); }
.presets { display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.custom-range { display: flex; align-items: center; gap: 8px; }
.separator { color: var(--el-text-color-secondary); }
</style>
