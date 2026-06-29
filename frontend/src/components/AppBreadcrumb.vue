<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useMenuStore } from '@/stores/menuStore'

const route = useRoute()
const menuStore = useMenuStore()

const crumbs = computed(() => menuStore.getBreadcrumbs(route.name as string))
</script>

<template>
  <el-breadcrumb separator="/" class="app-breadcrumb">
    <el-breadcrumb-item :to="{ path: '/' }">首頁</el-breadcrumb-item>
    <el-breadcrumb-item
      v-for="crumb in crumbs"
      :key="crumb.label"
      :to="crumb.path ? { path: crumb.path } : undefined"
    >
      {{ crumb.label }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<style scoped>
.app-breadcrumb {
  padding: 12px 24px;
}

.app-breadcrumb :deep(.el-breadcrumb__inner) {
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.2px;
  color: var(--text-secondary);
}

.app-breadcrumb :deep(.el-breadcrumb__inner a),
.app-breadcrumb :deep(.el-breadcrumb__inner.is-link) {
  color: var(--text-secondary);
  font-weight: 500;
  transition: color 150ms ease;
}

.app-breadcrumb :deep(.el-breadcrumb__inner a:hover),
.app-breadcrumb :deep(.el-breadcrumb__inner.is-link:hover) {
  color: var(--text-primary);
}

.app-breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--text-primary);
}

.app-breadcrumb :deep(.el-breadcrumb__separator) {
  color: var(--text-muted);
}
</style>
