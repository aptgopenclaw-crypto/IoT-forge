<script setup lang="ts">
/**
 * Tenant Authentication Configuration page.
 * Allows tenant admin to configure the authentication provider (LOCAL / LDAP / OIDC / SAML).
 */
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ShieldCheck, TestTube2, RotateCcw } from 'lucide-vue-next'
import { getAuthConfig, updateAuthConfig, deleteAuthConfig, testAuthConnection } from '@/api/authConfig'
import { useAuthStore } from '@/stores/authStore'
import {
  AUTH_TYPE_OPTIONS,
  LDAP_FIELDS,
  type AuthType,
  type TenantAuthConfigResponse,
  type TenantAuthConfigRequest,
  type LdapFieldMeta,
} from '@/types/authConfig'

const { t } = useI18n()
const route = useRoute()
const authStore = useAuthStore()

// [Platform/Tenant Separation 2.1.6] Prefer the tenantId carried by the route
// (canonical `/platform/tenants/:tenantId/auth-config`); fall back to the
// currently selected tenant so the legacy `/platform/auth-config` redirect and
// direct navigation by name without params keep working.
const currentTenantId = computed(() => {
  const fromRoute = route.params.tenantId
  if (typeof fromRoute === 'string' && fromRoute) return fromRoute
  return authStore.userInfo?.tenantId ?? ''
})

const config = ref<TenantAuthConfigResponse | null>(null)
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)

// Form state
const selectedAuthType = ref<AuthType>('LOCAL')
const fallbackLocal = ref(true)
const ldapForm = ref<Record<string, unknown>>({})

const isLocal = computed(() => selectedAuthType.value === 'LOCAL')
const isLdap = computed(() => selectedAuthType.value === 'LDAP')
const hasChanges = computed(() => {
  if (!config.value) return false
  if (selectedAuthType.value !== config.value.authType) return true
  if (fallbackLocal.value !== config.value.fallbackLocal) return true
  if (isLdap.value && Object.keys(ldapForm.value).length > 0) return true
  return false
})

async function load() {
  if (!currentTenantId.value) {
    ElMessage.error(t('common.operationFailed'))
    return
  }
  loading.value = true
  try {
    const res = await getAuthConfig(currentTenantId.value)
    config.value = res.body
    selectedAuthType.value = res.body.authType
    fallbackLocal.value = res.body.fallbackLocal
    if (res.body.config) {
      ldapForm.value = { ...res.body.config }
    } else {
      ldapForm.value = {}
    }
  } catch {
    ElMessage.error(t('common.operationFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(load)

watch(selectedAuthType, (newType) => {
  if (newType === 'LOCAL') {
    ldapForm.value = {}
  }
})

function buildRequest(): TenantAuthConfigRequest {
  return {
    authType: selectedAuthType.value,
    config: isLdap.value ? ldapForm.value : null,
    fallbackLocal: fallbackLocal.value,
  }
}

async function handleSave() {
  if (isLdap.value) {
    const missingRequired = LDAP_FIELDS.filter(
      f => f.required && !ldapForm.value[f.key],
    )
    if (missingRequired.length > 0) {
      ElMessage.warning(t('authConfig.errors.requiredFields'))
      return
    }
  }

  // Confirm when switching away from LOCAL or changing auth type
  if (selectedAuthType.value !== 'LOCAL' && selectedAuthType.value !== config.value?.authType) {
    try {
      await ElMessageBox.confirm(
        t('authConfig.switchConfirmMessage'),
        t('authConfig.switchConfirmTitle'),
        { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') },
      )
    } catch {
      return
    }
  }

  saving.value = true
  try {
    await updateAuthConfig(currentTenantId.value, buildRequest())
    await load()
    ElMessage.success(t('authConfig.saveSuccess'))
  } catch (err: unknown) {
    const e = err as { response?: { data?: { errorMsg?: string } } }
    ElMessage.error(e?.response?.data?.errorMsg || t('common.operationFailed'))
  } finally {
    saving.value = false
  }
}

async function handleReset() {
  try {
    await ElMessageBox.confirm(
      t('authConfig.resetConfirmMessage'),
      t('authConfig.resetConfirmTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }

  saving.value = true
  try {
    await deleteAuthConfig(currentTenantId.value)
    await load()
    ElMessage.success(t('authConfig.resetSuccess'))
  } catch {
    ElMessage.error(t('common.operationFailed'))
  } finally {
    saving.value = false
  }
}

async function handleTestConnection() {
  if (isLocal.value) {
    ElMessage.info(t('authConfig.testNotNeeded'))
    return
  }

  testing.value = true
  try {
    const res = await testAuthConnection(currentTenantId.value, buildRequest())
    if (res.body) {
      ElMessage.success(t('authConfig.testSuccess'))
    } else {
      ElMessage.error(t('authConfig.testFailed'))
    }
  } catch (err: unknown) {
    const e = err as { response?: { data?: { errorMsg?: string } } }
    ElMessage.error(e?.response?.data?.errorMsg || t('authConfig.testFailed'))
  } finally {
    testing.value = false
  }
}

function getLdapFieldValue(field: LdapFieldMeta): unknown {
  return ldapForm.value[field.key] ?? (field.type === 'boolean' ? false : '')
}

function setLdapFieldValue(field: LdapFieldMeta, value: unknown) {
  ldapForm.value[field.key] = value
}
</script>

<template>
  <div class="page-container">
    <div class="page-content">
      <div class="page-header">
        <div class="header-left">
          <h1 class="page-title">{{ t('authConfig.title') }}</h1>
          <p class="page-subtitle">{{ t('authConfig.subtitle') }}</p>
        </div>
        <div class="header-actions">
          <el-button
            v-if="config && config.authType !== 'LOCAL'"
            type="warning"
            :icon="RotateCcw"
            @click="handleReset"
          >
            {{ t('authConfig.resetToLocal') }}
          </el-button>
        </div>
      </div>

      <div v-loading="loading">
        <!-- Auth Type Selection -->
        <div class="section-card">
          <h2 class="section-title">
            <ShieldCheck :size="18" class="section-icon" />
            {{ t('authConfig.authTypeSection') }}
          </h2>
          <div class="auth-type-grid">
            <div
              v-for="option in AUTH_TYPE_OPTIONS"
              :key="option.value"
              class="auth-type-card"
              :class="{
                selected: selectedAuthType === option.value,
                disabled: !option.available,
              }"
              @click="option.available && (selectedAuthType = option.value)"
            >
              <div class="type-header">
                <el-radio
                  :model-value="selectedAuthType"
                  :value="option.value"
                  :disabled="!option.available"
                  @update:model-value="selectedAuthType = $event as AuthType"
                />
                <span class="type-label">{{ t(option.labelKey) }}</span>
                <el-tag v-if="!option.available" size="small" type="info">
                  {{ t('authConfig.comingSoon') }}
                </el-tag>
              </div>
              <p class="type-desc">{{ t(option.descriptionKey) }}</p>
            </div>
          </div>
        </div>

        <!-- LDAP Configuration -->
        <div v-if="isLdap" class="section-card">
          <h2 class="section-title">{{ t('authConfig.ldap.sectionTitle') }}</h2>
          <el-form label-position="top" class="ldap-form">
            <template v-for="field in LDAP_FIELDS" :key="field.key">
              <el-form-item
                :label="t(field.labelKey)"
                :required="field.required"
              >
                <el-switch
                  v-if="field.type === 'boolean'"
                  :model-value="getLdapFieldValue(field) as boolean"
                  @update:model-value="setLdapFieldValue(field, $event)"
                />
                <el-input-number
                  v-else-if="field.type === 'number'"
                  :model-value="(getLdapFieldValue(field) as number) || undefined"
                  @update:model-value="setLdapFieldValue(field, $event)"
                  :placeholder="field.placeholder"
                  :min="0"
                  controls-position="right"
                  style="width: 200px"
                />
                <el-input
                  v-else
                  :model-value="getLdapFieldValue(field) as string"
                  @update:model-value="setLdapFieldValue(field, $event)"
                  :placeholder="field.placeholder"
                  :type="field.type === 'password' ? 'password' : 'text'"
                  :show-password="field.type === 'password'"
                />
              </el-form-item>
            </template>
          </el-form>
        </div>

        <!-- Fallback Option -->
        <div v-if="!isLocal" class="section-card">
          <h2 class="section-title">{{ t('authConfig.fallbackSection') }}</h2>
          <div class="fallback-row">
            <el-switch v-model="fallbackLocal" />
            <div class="fallback-text">
              <span class="fallback-label">{{ t('authConfig.fallbackLabel') }}</span>
              <span class="fallback-desc">{{ t('authConfig.fallbackDesc') }}</span>
            </div>
          </div>
        </div>

        <!-- Actions -->
        <div class="action-bar">
          <el-button
            v-if="!isLocal"
            :loading="testing"
            @click="handleTestConnection"
          >
            <TestTube2 :size="14" class="btn-icon" />
            {{ t('authConfig.testConnection') }}
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            @click="handleSave"
          >
            {{ t('common.save') }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-content { max-width: 800px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.header-left { flex: 1; }
.page-title { font-size: 24px; font-weight: 600; margin: 0 0 4px 0; color: var(--text-heading); }
.page-subtitle { font-size: 13px; color: var(--text-body); margin: 0; }

.section-card { margin-bottom: 24px; background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: 8px; padding: 20px; }
.section-title { font-size: 16px; font-weight: 600; margin: 0 0 16px 0; color: var(--text-heading); display: flex; align-items: center; gap: 8px; }
.section-icon { color: var(--el-color-primary); }

.auth-type-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.auth-type-card { border: 2px solid var(--border-subtle); border-radius: 8px; padding: 16px; cursor: pointer; transition: all 0.2s; }
.auth-type-card.selected { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.auth-type-card.disabled { opacity: 0.5; cursor: not-allowed; }
.auth-type-card:not(.disabled):hover { border-color: var(--el-color-primary-light-3); }
.type-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.type-label { font-weight: 600; font-size: 14px; }
.type-desc { font-size: 12px; color: var(--text-muted, #666); margin: 0; line-height: 1.4; }

.ldap-form { display: grid; grid-template-columns: repeat(2, 1fr); gap: 0 16px; }
.ldap-form .el-form-item { margin-bottom: 16px; }

.fallback-row { display: flex; align-items: flex-start; gap: 12px; }
.fallback-text { display: flex; flex-direction: column; gap: 2px; }
.fallback-label { font-weight: 500; font-size: 14px; }
.fallback-desc { font-size: 12px; color: var(--text-muted, #666); }

.action-bar { display: flex; justify-content: flex-end; gap: 12px; padding-top: 8px; }
.btn-icon { margin-right: 4px; }
</style>
