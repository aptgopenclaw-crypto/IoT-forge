<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMenuStore } from '@/stores/menuStore'
import { useAuthStore } from '@/stores/authStore'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
import {
  Menu as MenuIcon,
  Home,
  UserCog,
  KeyRound,
} from 'lucide-vue-next'
import MenuNode from '@/components/MenuNode.vue'
import TenantSwitcher from '@/components/TenantSwitcher.vue'

const router = useRouter()
const route = useRoute()
const menuStore = useMenuStore()
const authStore = useAuthStore()

const collapsed = ref(false)
const sidebarMenus = computed(() =>
  [...menuStore.sidebarMenus].sort((a, b) => a.sortOrder - b.sortOrder),
)

// Determine if we should show fallback publicRoutes
const showFallbackMenus = computed(
  () => menuStore.initialized && menuStore.userMenus.length === 0,
)

// Active menu index for el-menu (routePath of current route)
const activeIndex = computed(() => route.path)

function handleMenuSelect(index: string) {
  if (index && index !== route.path) {
    router.push(index)
  }
}

const displayName = computed(() => authStore.userInfo?.displayName ?? '')
const tenantName = computed(() => authStore.userInfo?.tenantName ?? '')
const availableTenants = computed(() => authStore.userInfo?.availableTenants ?? [])
</script>

<template>
  <aside :class="['app-sidebar', { collapsed }]">
    <!-- Brand Header -->
    <div class="sidebar-header">
      <div class="brand" v-if="!collapsed">
        <TenantSwitcher v-if="availableTenants.length > 1" />
        <span class="brand-text" v-else>{{ tenantName || 'CCMS' }}</span>
      </div>
      <button class="collapse-btn" @click="collapsed = !collapsed">
        <MenuIcon :size="18" />
      </button>
    </div>

    <!-- User Info -->
    <div v-if="!collapsed" class="user-section">
      <div class="user-name">{{ displayName }}</div>
    </div>

    <div class="sidebar-divider" />

    <!-- Dynamic Menu (backend-driven) -->
    <nav class="sidebar-nav">
      <el-menu
        :default-active="activeIndex"
        :collapse="collapsed"
        :collapse-transition="false"
        class="sidebar-el-menu"
        @select="handleMenuSelect"
      >
        <!-- Backend-driven menus -->
        <template v-if="!showFallbackMenus">
          <MenuNode
            v-for="menu in sidebarMenus"
            :key="menu.menuId"
            :menu="menu"
          />
        </template>
        <!-- Fallback: publicRoutes only -->
        <template v-else>
          <el-menu-item index="/" @click="router.push('/')">
            <el-icon><Home /></el-icon>
            <template #title><span>{{ t('nav.home') }}</span></template>
          </el-menu-item>
          <el-menu-item index="/profile" @click="router.push('/profile')">
            <el-icon><UserCog /></el-icon>
            <template #title><span>{{ t('nav.profile') }}</span></template>
          </el-menu-item>
          <el-menu-item index="/change-password" @click="router.push('/change-password')">
            <el-icon><KeyRound /></el-icon>
            <template #title><span>{{ t('nav.changePassword') }}</span></template>
          </el-menu-item>
        </template>
      </el-menu>
    </nav>
  </aside>
</template>

<style scoped>
.app-sidebar {
  width: 240px;
  min-height: 100vh;
  background: var(--bg-base);
  border-right: 1px solid var(--border-divider);
  display: flex;
  flex-direction: column;
  transition: width 200ms ease;
  overflow-x: hidden;
}

.app-sidebar.collapsed {
  width: 56px;
}

/* Header */
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 8px;
}

.brand-text {
  font-size: 18px;
  font-weight: 600;
  color: #FF6363;
  letter-spacing: 0.3px;
}

.collapse-btn {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 150ms ease;
}

.collapse-btn:hover {
  opacity: 0.6;
  color: var(--text-primary);
}

/* User Section */
.user-section {
  padding: 8px 16px;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: 0.2px;
}

.user-tenant {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-muted);
  letter-spacing: 0.2px;
}

.sidebar-divider {
  height: 1px;
  background: var(--border-divider);
  margin: 8px 16px;
}

/* Navigation */
.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE/Edge */
}

.sidebar-nav::-webkit-scrollbar {
  display: none; /* Chrome/Safari/Opera */
}

.sidebar-el-menu {
  border-right: none !important;
  --el-menu-bg-color: var(--bg-base);
  --el-menu-hover-bg-color: var(--bg-hover);
  --el-menu-text-color: var(--text-secondary);
  --el-menu-active-color: var(--text-primary);
  --el-menu-item-height: 40px;
}

.sidebar-el-menu :deep(.el-menu-item) {
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.3px;
  height: 40px;
  line-height: 40px;
}

.sidebar-el-menu :deep(.el-menu-item.is-active) {
  background: var(--bg-active) !important;
  border-left: 2px solid #FF6363;
}

.sidebar-el-menu :deep(.el-menu-item.is-active .el-icon) {
  color: #55b3ff;
}

.sidebar-el-menu :deep(.el-menu-item:hover) {
  background: var(--bg-hover) !important;
}

.sidebar-el-menu :deep(.el-sub-menu__title) {
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.3px;
  height: 40px;
  line-height: 40px;
}

.sidebar-el-menu :deep(.el-sub-menu__title:hover) {
  background: var(--bg-hover) !important;
}

/* Footer */
.sidebar-footer {
  padding: 8px;
  flex-shrink: 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 150ms ease;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  letter-spacing: 0.3px;
}

.nav-item:hover {
  background: var(--bg-hover);
}

.nav-icon {
  color: var(--text-secondary);
  flex-shrink: 0;
}

.nav-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
