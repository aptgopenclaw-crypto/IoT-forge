<script setup lang="ts">
defineProps<{
  /** Layout variant: 'default' = plain content, 'card' = wrapped in card */
  variant?: 'default' | 'card'
  /** Center the card (for form pages like Profile/CreateUser) */
  centered?: boolean
  /** Max width constraint for content area */
  maxWidth?: string
}>()
</script>

<template>
  <div class="page-container" :class="{ centered }">
    <div v-if="variant === 'card'" class="page-card">
      <slot />
    </div>
    <div v-else class="page-content" :style="maxWidth ? { maxWidth, margin: '0 auto' } : undefined">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.page-container {
  padding: 32px 24px;
  min-height: 100vh;
  background-color: var(--bg-base);
}

.page-container.centered {
  display: flex;
  justify-content: center;
  padding: 40px 24px;
}

.page-card {
  background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 24px;
  box-shadow: var(--shadow-card);
  width: 100%;
}

.page-content {
  width: 100%;
}
</style>
