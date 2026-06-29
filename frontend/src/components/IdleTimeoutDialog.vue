<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Warning } from '@element-plus/icons-vue'

defineProps<{
  visible: boolean
  remainingSeconds: number
}>()

const emit = defineEmits<{
  continue: []
}>()

const { t } = useI18n()

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="t('idleTimeout.warningTitle')"
    width="400px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    align-center
  >
    <div class="idle-timeout-body">
      <el-icon :size="48" color="var(--el-color-warning)">
        <Warning />
      </el-icon>
      <p>{{ t('idleTimeout.warningMessage') }}</p>
      <p class="countdown">{{ formatTime(remainingSeconds) }}</p>
    </div>
    <template #footer>
      <el-button type="primary" @click="emit('continue')">
        {{ t('idleTimeout.continueBtn') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.idle-timeout-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
}

.countdown {
  font-size: 2rem;
  font-weight: bold;
  color: var(--el-color-danger);
  font-variant-numeric: tabular-nums;
}
</style>
