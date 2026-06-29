<script setup lang="ts">
import { ref, computed } from 'vue'
import { ChevronDown, ChevronUp } from 'lucide-vue-next'

const props = defineProps<{
  payload: string
}>()

const expanded = ref(false)
const TRUNCATE_LENGTH = 120

const isLong = computed(() => props.payload && props.payload.length > TRUNCATE_LENGTH)

const displayText = computed(() => {
  if (!props.payload) return '（無）'
  if (expanded.value || !isLong.value) return props.payload
  return props.payload.slice(0, TRUNCATE_LENGTH) + '...'
})
</script>

<template>
  <div class="payload-detail">
    <div class="payload-label">Payload</div>
    <pre class="payload-content">{{ displayText }}</pre>
    <button v-if="isLong" class="toggle-btn" @click="expanded = !expanded">
      <template v-if="expanded">
        <ChevronUp :size="14" /> 收起
      </template>
      <template v-else>
        <ChevronDown :size="14" /> 展開完整內容
      </template>
    </button>
  </div>
</template>

<style scoped>
.payload-detail {
  padding: 12px 20px;
  background: var(--bg-base);
  border-radius: 8px;
  margin: 8px 0;
}

.payload-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  margin-bottom: 8px;
}

.payload-content {
  font-family: 'GeistMono', 'JetBrains Mono', monospace;
  font-size: 13px;
  font-weight: 400;
  line-height: 1.6;
  color: var(--text-label);
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

.toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 4px 8px;
  background: transparent;
  border: none;
  color: #55b3ff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border-radius: 4px;
  transition: opacity 150ms ease;
}

.toggle-btn:hover {
  opacity: 0.6;
}
</style>
