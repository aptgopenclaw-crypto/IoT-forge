<script setup lang="ts">
// [Phase 4 / 4.1.3 / ADR-004] App.vue is now layout-agnostic: the sidebar +
// top-bar chrome has moved into `layouts/TenantLayout.vue` (green tenant
// shell) and `layouts/PlatformLayout.vue` (dark platform shell). The router
// (see router/index.ts) nests each authenticated route under the matching
// layout, so App.vue only needs to render `<router-view />` plus globals:
// element-plus locale provider and the idle-timeout warning dialog.
import { computed } from 'vue'
import IdleTimeoutDialog from '@/components/IdleTimeoutDialog.vue'
import { useLocaleStore } from '@/stores/localeStore'
import { useIdleTimeout } from '@/composables/useIdleTimeout'
import zhTW from 'element-plus/es/locale/lang/zh-tw'
import zhCN from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'

const localeStore = useLocaleStore()
const { showWarning, remainingSeconds, continueSession } = useIdleTimeout()

const elLocale = computed(() => {
  if (localeStore.locale === 'zh-TW') return zhTW
  if (localeStore.locale === 'zh-CN') return zhCN
  return en
})
</script>

<template>
  <el-config-provider :locale="elLocale">
    <router-view />
    <IdleTimeoutDialog
      :visible="showWarning"
      :remaining-seconds="remainingSeconds"
      @continue="continueSession"
    />
  </el-config-provider>
</template>

<style>
/* Theme tokens defined in assets/styles/theme-tokens.css */

html, body, #app {
  margin: 0;
  padding: 0;
  background: var(--bg-base);
  color: var(--text-primary);
  font-family: 'Inter', sans-serif;
  min-height: 100vh;
  transition: background-color 200ms ease, color 200ms ease;
}
</style>
