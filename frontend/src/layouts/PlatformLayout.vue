<script setup lang="ts">
/**
 * [Phase 4 / 4.1.1 / ADR-004] PlatformLayout
 *
 * 深色系版面，給 super_admin 操作平台層級事務（租戶管理、密碼策略、
 * 使用者-場域對應、代操）使用。
 *
 * - 品牌：固定顯示 "Platform"（i18n key `layout.platformBrand`），不顯示 tenant 名稱或切換器。
 * - Sidebar：直接綁定 `menuStore.sidebarMenus`；後端在 Phase 3.1.3 後已保證
 *   super_admin 取得的選單只會是 PLATFORM + PUBLIC scope，因此 UI 不需再過濾。
 * - 視覺：套用 `.platform-layout` 容器深色覆寫（不直接動全域 theme token，
 *   避免影響 TenantLayout / 共用元件）。
 *
 * 4.1.3 會由 router 將 `/platform/*` 路由掛在這個 layout 下；本檔本身只負責畫面骨架，
 * 不做 scope 守衛（4.1.5 由 router guard 處理）。
 */
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useMenuStore } from '@/stores/menuStore'
import { useAuthStore } from '@/stores/authStore'
import MenuNode from '@/components/MenuNode.vue'
import AppTopBar from '@/components/AppTopBar.vue'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const menuStore = useMenuStore()
const authStore = useAuthStore()

const sidebarMenus = computed(() =>
  [...menuStore.sidebarMenus].sort((a, b) => a.sortOrder - b.sortOrder),
)
const hasMenus = computed(() => sidebarMenus.value.length > 0)
const displayName = computed(() => authStore.userInfo?.displayName ?? '')
const activeIndex = computed(() => route.path)

function handleMenuSelect(index: string) {
  if (index && index !== route.path) {
    router.push(index)
  }
}
</script>

<template>
  <div class="platform-layout">
    <aside class="platform-sidebar" data-testid="platform-sidebar">
      <div class="platform-brand">
        <span class="brand-text">{{ t('layout.platformBrand') }}</span>
        <span class="brand-subtitle">{{ t('layout.platformSubtitle') }}</span>
      </div>

      <div v-if="displayName" class="platform-user">
        <div class="user-name">{{ displayName }}</div>
      </div>

      <div class="platform-divider" />

      <nav class="platform-nav">
        <el-menu
          v-if="hasMenus"
          :default-active="activeIndex"
          :collapse-transition="false"
          class="platform-el-menu"
          background-color="transparent"
          text-color="rgba(255,255,255,0.78)"
          active-text-color="#ffffff"
          @select="handleMenuSelect"
        >
          <MenuNode v-for="menu in sidebarMenus" :key="menu.menuId" :menu="menu" />
        </el-menu>
        <div v-else class="platform-empty">{{ t('layout.platformEmptyMenu') }}</div>
      </nav>
    </aside>

    <main class="platform-main">
      <AppTopBar />
      <div class="platform-content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.platform-layout {
  display: flex;
  min-height: 100vh;
  background: #0f172a; /* slate-900 base */
  color: #e2e8f0;      /* slate-200 default text */
}

.platform-sidebar {
  width: 240px;
  min-height: 100vh;
  background: #0b1220;          /* deeper than main */
  border-right: 1px solid #1e293b;
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
}

.platform-brand {
  padding: 18px 20px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-text {
  font-size: 20px;
  font-weight: 700;
  color: #f8fafc;
  letter-spacing: 0.4px;
}

.brand-subtitle {
  font-size: 12px;
  color: #94a3b8;
  letter-spacing: 0.2px;
}

.platform-user {
  padding: 4px 20px 8px;
}

.user-name {
  font-size: 14px;
  color: #cbd5e1;
}

.platform-divider {
  height: 1px;
  background: #1e293b;
  margin: 8px 16px;
}

.platform-nav {
  flex: 1;
  overflow-y: auto;
}

.platform-el-menu {
  border-right: none !important;
}

.platform-empty {
  padding: 16px 20px;
  font-size: 13px;
  color: #64748b;
}

.platform-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #0f172a;
}

.platform-content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  color: #e2e8f0;
}
</style>
