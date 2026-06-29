<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { ChevronDown } from 'lucide-vue-next'

const authStore = useAuthStore()

const tenants = computed(() => authStore.userInfo?.availableTenants ?? [])
const currentTenantId = computed(() => authStore.userInfo?.tenantId ?? '')
const currentTenantName = computed(() => authStore.userInfo?.tenantName ?? '')

function handleSwitch(tenantId: string | number | object) {
  authStore.doSwitchTenant(String(tenantId))
}
</script>

<template>
  <el-dropdown v-if="tenants.length > 1" @command="handleSwitch">
    <span class="tenant-switcher-trigger">
      {{ currentTenantName }}
      <ChevronDown :size="14" class="trigger-icon" />
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="t in tenants"
          :key="t.tenantId"
          :command="t.tenantId"
          :disabled="t.tenantId === currentTenantId"
        >
          {{ t.tenantName }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.tenant-switcher-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.2px;
  color: var(--text-primary);
  padding: 4px 8px;
  border-radius: 6px;
  transition: opacity 150ms ease;
}

.tenant-switcher-trigger:hover {
  opacity: 0.7;
}

.trigger-icon {
  color: var(--text-secondary);
}
</style>
