<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { LayoutDashboard } from 'lucide-vue-next'

const { t } = useI18n()
</script>

<template>
  <div class="page-container">
    <div class="page-content">
      <!-- Header -->
      <div class="page-header">
        <div class="header-left">
          <div class="header-icon"><LayoutDashboard :size="20" /></div>
          <div>
            <h2 class="header-title">{{ t('home.title') }}</h2>
            <p class="header-subtitle">{{ t('home.subtitle') }}</p>
          </div>
        </div>
      </div>

      <!-- ...已移除 workflow 卡片... -->
    </div>
  </div>
</template>

<style scoped>
.page-container { padding: 24px; height: 100%; overflow-y: auto; }
.page-content {
  width: 100%;
  margin: 0 auto;
  padding: 0 24px;
}

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.header-left { display: flex; align-items: center; gap: 12px; }
.header-icon {
  width: 40px; height: 40px;
  background: rgba(167, 139, 250, 0.1);
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  color: #a78bfa;
}

.dashboard-card {
  background: var(--bg-surface);
  border: 1px solid var(--bg-active);
  border-radius: 12px;
  overflow: hidden;
}

.card-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--bg-active);
}
.card-header-left { display: flex; align-items: center; gap: 8px; }
.card-icon { color: #a78bfa; }
.card-title { font-size: 15px; font-weight: 600; color: var(--text-heading); margin: 0; }

.badge {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 22px; height: 22px; padding: 0 6px;
  border-radius: 11px;
  background: rgba(255, 99, 99, 0.15); color: #FF6363;
  font-size: 12px; font-weight: 700;
}

.view-all-link {
  display: flex; align-items: center; gap: 4px;
  font-size: 13px; color: #a78bfa; text-decoration: none; font-weight: 500;
}
.view-all-link:hover { color: #c4b5fd; }

.card-body { min-height: 120px; }

.empty-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 48px 20px; color: var(--text-secondary);
}
.empty-icon { color: var(--text-secondary); opacity: 0.4; margin-bottom: 12px; }
.empty-state p { font-size: 14px; margin: 0; }

.code-text { color: #55b3ff; font-weight: 600; font-family: 'JetBrains Mono', monospace; font-size: 13px; }

.action-btn { padding: 4px 12px; min-width: auto; background: transparent; border-radius: 6px; }
.action-go {
  color: #a78bfa; border: 1px solid rgba(167, 139, 250, 0.25);
  display: inline-flex; align-items: center; gap: 4px; font-size: 12px;
}
.action-go:hover { background: rgba(167, 139, 250, 0.15); color: #c4b5fd; }
</style>
