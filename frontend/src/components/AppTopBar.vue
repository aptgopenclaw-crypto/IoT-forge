<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMenuStore } from '@/stores/menuStore'
import { useLocaleStore } from '@/stores/localeStore'
import { useThemeStore } from '@/stores/themeStore'
import { useAuthStore } from '@/stores/authStore'
import { useI18n } from 'vue-i18n'
import { Sun, Moon, LogOut, KeyRound, UserCog, ChevronDown } from 'lucide-vue-next'
import NotificationBell from '@/components/NotificationBell.vue'
import type { LocaleKey } from '@/i18n'

const route = useRoute()
const router = useRouter()
const menuStore = useMenuStore()
const localeStore = useLocaleStore()
const themeStore = useThemeStore()
const authStore = useAuthStore()
const { t } = useI18n()

const displayName = computed(() => authStore.userInfo?.displayName ?? '')

const crumbs = computed(() => menuStore.getBreadcrumbs(route.name as string))

const localeOptions: { value: LocaleKey; label: string }[] = [
  { value: 'zh-TW', label: '繁體中文' },
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en', label: 'English' },
]

const currentLocaleLabel = computed(
  () => localeOptions.find(o => o.value === localeStore.locale)?.label ?? '',
)

async function handleLogout() {
  await authStore.doLogout()
  router.push('/login')
}

function handleUserCommand(command: string) {
  if (command === 'profile') router.push('/profile')
  else if (command === 'changePassword') router.push('/change-password')
  else if (command === 'logout') handleLogout()
}
</script>

<template>
  <div class="app-topbar">
    <!-- Breadcrumb left -->
    <el-breadcrumb separator="/" class="topbar-breadcrumb">
      <el-breadcrumb-item :to="{ path: '/' }">{{ t('nav.home') }}</el-breadcrumb-item>
      <el-breadcrumb-item
        v-for="crumb in crumbs"
        :key="crumb.label"
        :to="crumb.path ? { path: crumb.path } : undefined"
      >
        {{ crumb.label }}
      </el-breadcrumb-item>
    </el-breadcrumb>

    <!-- Right actions -->
    <div class="topbar-actions">
      <!-- Notification Bell -->
      <NotificationBell />

      <!-- Theme toggle -->
      <el-tooltip :content="themeStore.isDark ? t('nav.lightMode') : t('nav.darkMode')" placement="bottom">
        <button class="icon-btn" @click="themeStore.toggleTheme">
          <Sun v-if="themeStore.isDark" :size="16" />
          <Moon v-else :size="16" />
        </button>
      </el-tooltip>

      <!-- Logout -->
      <el-dropdown trigger="click" @command="handleUserCommand">
        <button class="user-btn">
          <UserCog :size="15" />
          <span class="user-btn-name">{{ displayName }}</span>
          <ChevronDown :size="11" />
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><UserCog :size="14" /></el-icon>
              {{ t('nav.profile') }}
            </el-dropdown-item>
            <el-dropdown-item command="changePassword">
              <el-icon><KeyRound :size="14" /></el-icon>
              {{ t('nav.changePassword') }}
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><LogOut :size="14" /></el-icon>
              {{ t('nav.logout') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <div class="topbar-divider" />

      <!-- Language switcher -->
      <el-dropdown @command="(val: LocaleKey) => localeStore.setLocale(val)" trigger="click">
        <button class="lang-btn">
          <span class="lang-icon">🌐</span>
          <span class="lang-label">{{ currentLocaleLabel }}</span>
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none" class="lang-chevron">
            <path d="M2 4l4 4 4-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="opt in localeOptions"
              :key="opt.value"
              :command="opt.value"
              :class="{ 'is-active': localeStore.locale === opt.value }"
            >
              {{ opt.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped>
.app-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 24px;
  border-bottom: 1px solid var(--border-divider);
  background-color: var(--bg-surface);
}

.topbar-breadcrumb :deep(.el-breadcrumb__inner) {
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.2px;
  color: var(--text-secondary);
}

.topbar-breadcrumb :deep(.el-breadcrumb__inner a),
.topbar-breadcrumb :deep(.el-breadcrumb__inner.is-link) {
  color: var(--text-secondary);
  font-weight: 500;
  transition: color 150ms ease;
}

.topbar-breadcrumb :deep(.el-breadcrumb__inner a:hover),
.topbar-breadcrumb :deep(.el-breadcrumb__inner.is-link:hover) {
  color: var(--text-primary);
}

.topbar-breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--text-primary);
}

.topbar-breadcrumb :deep(.el-breadcrumb__separator) {
  color: var(--text-muted);
}

.lang-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: 1px solid var(--border-medium);
  border-radius: 8px;
  padding: 5px 12px;
  cursor: pointer;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.2px;
  transition: border-color 150ms ease, background 150ms ease;
  outline: none;
}

.lang-btn:hover {
  border-color: var(--border-strong);
  background: var(--bg-hover);
}

.lang-icon {
  font-size: 14px;
  line-height: 1;
}

.lang-chevron {
  color: var(--text-muted);
  transition: transform 150ms ease;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.topbar-divider {
  width: 1px;
  height: 18px;
  background: var(--border-divider);
  margin: 0 6px;
}

.user-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: 1px solid var(--border-medium);
  border-radius: 8px;
  padding: 5px 10px;
  cursor: pointer;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
  transition: border-color 150ms ease, background 150ms ease;
  outline: none;
  max-width: 160px;
}

.user-btn:hover {
  border-color: var(--border-strong);
  background: var(--bg-hover);
}

.user-btn-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-secondary);
  transition: background 150ms ease, color 150ms ease;
  outline: none;
}

.icon-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

:deep(.el-dropdown-menu__item.is-active) {
  color: var(--text-heading);
  font-weight: 600;
}
</style>
