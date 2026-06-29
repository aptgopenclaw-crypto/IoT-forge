<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { updateOwnProfile } from '@/api/user'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Phone, Bell, Mail } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const pageLoading = ref(true)

const form = reactive({
  displayName: '',
  phone: '',
  notifySmsFlag: false,
  notifyEmailFlag: false,
  email: '',
})

const rules: FormRules = {
  displayName: [
    { required: true, message: () => t('profile.displayNameRequired'), trigger: 'blur' },
  ],
}

onMounted(async () => {
  try {
    if (!authStore.userInfo) {
      await authStore.getPermission()
    }
    if (authStore.userInfo) {
      form.email = authStore.userInfo.email
      form.displayName = authStore.userInfo.displayName || ''
      form.phone = (authStore.userInfo as Record<string, unknown>).phone as string || ''
      form.notifySmsFlag = (authStore.userInfo as Record<string, unknown>).notifySmsFlag as boolean ?? false
      form.notifyEmailFlag = (authStore.userInfo as Record<string, unknown>).notifyEmailFlag as boolean ?? false
    }
  } catch {
    ElMessage.error(t('profile.loadFailed'))
  } finally {
    pageLoading.value = false
  }
})

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await updateOwnProfile({
      displayName: form.displayName,
      phone: form.phone,
      notifySmsFlag: form.notifySmsFlag,
      notifyEmailFlag: form.notifyEmailFlag,
    })
    authStore.userInfo = res.body
    ElMessage.success(t('profile.updatedSuccess'))
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    const msgMap: Record<string, string> = { '10001': t('profile.errors.10001') }
    const msg = (errorCode && msgMap[errorCode]) || t('profile.updateFailed')
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-card" v-loading="pageLoading">
      <div class="page-header">
        <h1 class="page-title">{{ t('profile.title') }}</h1>
        <p class="page-subtitle">{{ t('profile.subtitle') }}</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <el-form-item :label="t('profile.emailLabel')">
          <el-input v-model="form.email" disabled>
            <template #prefix>
              <Mail :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item :label="t('profile.displayNameLabel')" prop="displayName">
          <el-input v-model="form.displayName" :placeholder="t('profile.displayNamePlaceholder')">
            <template #prefix>
              <User :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item :label="t('profile.phoneLabel')">
          <el-input v-model="form.phone" :placeholder="t('profile.phonePlaceholder')">
            <template #prefix>
              <Phone :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <div class="toggle-group">
          <div class="toggle-item">
            <div class="toggle-label">
              <Bell :size="16" class="toggle-icon" />
              <span>{{ t('profile.smsNotify') }}</span>
            </div>
            <el-switch v-model="form.notifySmsFlag" />
          </div>
          <div class="toggle-item">
            <div class="toggle-label">
              <Mail :size="16" class="toggle-icon" />
              <span>{{ t('profile.emailNotify') }}</span>
            </div>
            <el-switch v-model="form.notifyEmailFlag" />
          </div>
        </div>

        <el-form-item class="submit-row">
          <el-button class="cancel-btn" @click="router.back()">
            {{ t('common.cancel') }}
          </el-button>
          <el-button
            class="submit-btn"
            :loading="loading"
            native-type="submit"
          >
            {{ t('profile.submitBtn') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  justify-content: center;
  padding: 40px 24px;
  min-height: 100vh;
  background-color: var(--bg-base);
}

.page-card {
  width: 520px;
  background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 40px 32px;
  box-shadow: var(--shadow-card);
  align-self: flex-start;
}

.page-header {
  margin-bottom: 32px;
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
  line-height: 1.6;
  letter-spacing: 0.2px;
  color: var(--text-secondary);
  margin: 0;
}

.input-icon {
  color: var(--text-muted);
}

.toggle-group {
  margin-bottom: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toggle-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: var(--bg-base);
  border: 1px solid var(--border-medium);
  border-radius: 8px;
}

.toggle-label {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: 0.2px;
}

.toggle-icon {
  color: var(--text-muted);
}

.submit-row {
  margin-top: 8px;
}

.submit-row :deep(.el-form-item__content) {
  display: flex;
  gap: 12px;
}

.cancel-btn {
  flex: 1;
  border-radius: 86px;
  padding: 8px 24px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.submit-btn {
  flex: 1;
  background: var(--btn-primary-bg);
  color: var(--btn-primary-text);
  border: none;
  border-radius: 86px;
  padding: 8px 24px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
  transition: opacity 150ms ease;
}

.submit-btn:hover {
  background: var(--btn-primary-hover);
  color: var(--btn-primary-text);
}

/* Element Plus dark overrides */
:deep(.el-input__wrapper.is-disabled) {
  opacity: 0.35;
  cursor: not-allowed;
}

:deep(.el-switch.is-checked .el-switch__core) {
  background-color: #55b3ff;
  border-color: #55b3ff;
}
</style>
