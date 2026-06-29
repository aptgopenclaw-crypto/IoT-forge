<script setup lang="ts">
/**
 * [Phase 4 / 4.1.2 / ADR-004] TenantLayout
 *
 * 既有「綠色系」租戶版面的抽離版本：sidebar (AppSidebar) + 頂條 (AppTopBar) + router-view。
 * 目前 `App.vue` 仍以 `showSidebar` 旗標內嵌相同結構；4.1.3 router 重整後，
 * 登入後 TENANT / IMPERSONATION scope 路由會掛在這個 layout 之下，
 * 屆時 `App.vue` 只保留 router-view 與全域 dialog / config-provider。
 *
 * 本檔不做 scope 守衛（4.1.5 由 router guard 處理）。
 * [Phase 4 / 4.1.6] 頂部掛載 ImpersonationBanner — 條件渲染，
 * 只有當 access token 為 IMPERSONATION scope 時才出現（無 token 時 0 DOM）。
 */
import AppSidebar from '@/components/AppSidebar.vue'
import AppTopBar from '@/components/AppTopBar.vue'
import ImpersonationBanner from '@/components/ImpersonationBanner.vue'
</script>

<template>
  <div class="tenant-layout" data-testid="tenant-layout">
    <AppSidebar />
    <main class="tenant-main">
      <ImpersonationBanner />
      <AppTopBar />
      <div class="tenant-content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.tenant-layout {
  display: flex;
  min-height: 100vh;
  background: var(--bg-base);
  color: var(--text-primary);
}

.tenant-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.tenant-content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}
</style>
