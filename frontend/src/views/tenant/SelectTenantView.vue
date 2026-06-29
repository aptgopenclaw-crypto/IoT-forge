<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useTenantStore } from '@/stores/tenantStore'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Building2, User, Briefcase } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const authStore = useAuthStore()
const tenantStore = useTenantStore()
const router = useRouter()
const loading = ref<string | null>(null)

let timer: ReturnType<typeof setTimeout> | null = null

async function handleSelect(tenantId: string) {
  loading.value = tenantId
  try {
    await authStore.doSelectTenant(tenantId)
  } catch {
    ElMessage.error(t('tenant.selectFailed'))
    loading.value = null
  }
}

onMounted(() => {
  if (!tenantStore.tenantList.length) {
    router.push('/login')
    return
  }
  timer = setTimeout(() => {
    ElMessage.warning(t('tenant.expiredWarning'))
    authStore.clearAuth()
    router.push('/login')
  }, 5 * 60 * 1000)
})

onUnmounted(() => {
  if (timer) clearTimeout(timer)
})
</script>

<template>
  <div class="select-tenant-container">
    <div class="select-tenant-content">
      <h1 class="page-title">{{ t('tenant.selectTitle') }}</h1>
      <p class="page-subtitle">{{ t('tenant.selectSubtitle') }}</p>

      <div class="tenant-grid">
        <div
          v-for="t in tenantStore.tenantList"
          :key="t.tenantId"
          class="tenant-card"
          :class="{ 'is-loading': loading === t.tenantId }"
          @click="handleSelect(t.tenantId)"
        >
          <div class="tenant-icon">
            <Building2 :size="24" />
          </div>
          <h3 class="tenant-name">{{ t.tenantName }}</h3>
          <div class="tenant-meta">
            <span class="meta-item">
              <User :size="14" />
              {{ t.roleName }}
            </span>
            <span v-if="t.deptName" class="meta-item">
              <Briefcase :size="14" />
              {{ t.deptName }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.select-tenant-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: var(--bg-base);
  padding: 40px 20px;
}

.select-tenant-content {
  text-align: center;
  max-width: 800px;
  width: 100%;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.15;
  color: var(--text-heading);
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.2px;
  color: var(--text-secondary);
  margin: 0 0 32px 0;
}

.tenant-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: center;
}

.tenant-card {
  width: 260px;
  background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 24px;
  cursor: pointer;
  transition: border-color 150ms ease;
  box-shadow: var(--shadow-card);
}

.tenant-card:hover {
  border-color: var(--border-strong);
}

.tenant-card.is-loading {
  opacity: 0.6;
  pointer-events: none;
}

.tenant-icon {
  color: #55b3ff;
  margin-bottom: 12px;
}

.tenant-name {
  font-size: 18px;
  font-weight: 500;
  line-height: 1.25;
  letter-spacing: 0.2px;
  color: var(--text-primary);
  margin: 0 0 12px 0;
}

.tenant-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.2px;
  color: var(--text-secondary);
}
</style>
