<script setup lang="ts">
defineProps<{
  /** Title text */
  title: string
  /** Optional subtitle text */
  subtitle?: string
  /** Header icon color (only rendered when #icon slot is used) */
  iconColor?: string
}>()
</script>

<template>
  <div class="page-header">
    <div class="header-left">
      <span v-if="$slots.icon" class="header-icon" :style="{ color: iconColor }">
        <slot name="icon" />
      </span>
      <div>
        <h1 class="page-title">{{ title }}</h1>
        <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  display: flex;
  align-items: center;
  font-size: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: 0.2px;
  color: var(--text-heading);
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.6;
  letter-spacing: 0.2px;
  color: var(--text-secondary);
  margin: 0;
}

/* When there's no subtitle, remove bottom margin from title */
.page-title:last-child {
  margin-bottom: 0;
}
</style>
