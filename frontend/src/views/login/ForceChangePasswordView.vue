<script setup lang="ts">
/**
 * Phase 3 force-change-password page. Reached when the login endpoint returns
 * `passwordChangeRequired = true`. Holds a short-lived `password_change`
 * temporary token in the auth store (NOT a regular session) and POSTs the new
 * password to /noauth/user/force-change-password. On success the backend
 * returns a normal LoginResult which we feed into authStore to continue the
 * standard post-login branching.
 */
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Lock } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/authStore'
import { forceChangePassword } from '@/api/passwordPolicy'
import PasswordRulesHint from '@/components/PasswordRulesHint.vue'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ newPassword: '', confirmPassword: '' })

onMounted(() => {
  // Guard: this page is only meaningful while we hold a password_change token.
  if (!authStore.passwordChangeToken) {
    router.replace('/login')
  }
})

const rules = computed<FormRules>(() => ({
  newPassword: [
    { required: true, message: t('forceChangePassword.errors.required'), trigger: 'blur' },
    { min: 8, message: t('forceChangePassword.errors.tooShort'), trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: t('forceChangePassword.errors.confirmRequired'), trigger: 'blur' },
    {
      validator: (_r, value: string, cb) => {
        if (value !== form.newPassword) cb(new Error(t('forceChangePassword.errors.mismatch')))
        else cb()
      },
      trigger: 'blur',
    },
  ],
}))

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!authStore.passwordChangeToken) {
    ElMessage.error(t('forceChangePassword.errors.tokenMissing'))
    router.replace('/login')
    return
  }
  loading.value = true
  try {
    const res = await forceChangePassword(authStore.passwordChangeToken, {
      newPassword: form.newPassword,
    })
    ElMessage.success(t('forceChangePassword.success'))
    await authStore.applyPostForceChangeLogin(res.body)
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string; errorMsg?: string } } }
    const errorCode = error?.response?.data?.errorCode
    // 20020 = FORCE_CHANGE_PASSWORD_TOKEN_INVALID — token expired or replayed.
    if (errorCode === '20020') {
      ElMessage.error(t('forceChangePassword.errors.tokenInvalid'))
      authStore.clearAuth()
      router.replace('/login')
      return
    }
    ElMessage.error(error?.response?.data?.errorMsg || t('forceChangePassword.errors.generic'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1 class="login-title">{{ t('forceChangePassword.title') }}</h1>
        <p class="login-subtitle">{{ t('forceChangePassword.subtitle') }}</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <el-form-item prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            :placeholder="t('forceChangePassword.newPasswordPlaceholder')"
            show-password
          >
            <template #prefix><Lock :size="18" /></template>
          </el-input>
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            :placeholder="t('forceChangePassword.confirmPlaceholder')"
            show-password
          >
            <template #prefix><Lock :size="18" /></template>
          </el-input>
        </el-form-item>

        <PasswordRulesHint :password="form.newPassword" />

        <el-form-item style="margin-top: 16px;">
          <el-button
            class="submit-btn"
            type="primary"
            :loading="loading"
            native-type="submit"
          >
            {{ t('forceChangePassword.submit') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex; justify-content: center; align-items: center;
  min-height: 100vh; background-color: var(--bg-base);
}
.login-card {
  width: 440px; background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle); border-radius: 12px;
  padding: 40px 32px; box-shadow: var(--shadow-card);
}
.login-header { text-align: center; margin-bottom: 24px; }
.login-title { font-size: 24px; font-weight: 600; margin: 0 0 8px 0; color: var(--text-heading); }
.login-subtitle { font-size: 13px; color: var(--text-body); margin: 0; }
.submit-btn { width: 100%; }
</style>
