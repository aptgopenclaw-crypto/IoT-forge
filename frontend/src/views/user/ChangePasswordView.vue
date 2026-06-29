<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { changePassword } from '@/api/user'
import { useAuthStore } from '@/stores/authStore'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Lock } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import PasswordRulesHint from '@/components/PasswordRulesHint.vue'
import { usePasswordPolicy } from '@/composables/usePasswordPolicy'

const { t } = useI18n()

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const { validatePassword } = usePasswordPolicy()

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules = computed<FormRules>(() => ({
  oldPassword: [
    { required: true, message: t('password.errors.oldPassRequired'), trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: t('password.errors.newPassRequired'), trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value && !validatePassword(value)) {
          callback(new Error(t('password.errors.newPassComplexity')))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: t('password.errors.confirmPassRequired'), trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.newPassword) {
          callback(new Error(t('password.errors.confirmPassMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}))

const errorCodeMessages = computed<Record<string, string>>(() => ({
  '10001': t('password.errors.10001'),
  '20003': t('password.errors.20003'),
  '20017': t('password.errors.20017'),
  '20019': t('password.errors.20019'),
}))

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success(t('password.updatedSuccess'))
    setTimeout(() => {
      authStore.doLogout()
    }, 1500)
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    const msg = (errorCode && errorCodeMessages.value[errorCode]) || t('password.updateFailed')
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-header">
        <h1 class="page-title">{{ t('password.title') }}</h1>
        <p class="page-subtitle">{{ t('password.subtitle') }}</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <el-form-item :label="t('password.oldPassLabel')" prop="oldPassword">
          <el-input
            v-model="form.oldPassword"
            type="password"
            :placeholder="t('password.oldPassPlaceholder')"
            show-password
          >
            <template #prefix>
              <Lock :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item :label="t('password.newPassLabel')" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            :placeholder="t('password.newPassPlaceholder')"
            show-password
          >
            <template #prefix>
              <Lock :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <PasswordRulesHint
          :password="form.newPassword"
          :email="authStore.userInfo?.email"
        />

        <el-form-item :label="t('password.confirmPassLabel')" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            :placeholder="t('password.confirmPassPlaceholder')"
            show-password
          >
            <template #prefix>
              <Lock :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item class="submit-row">
          <el-button class="cancel-btn" @click="router.back()">
            {{ t('common.cancel') }}
          </el-button>
          <el-button
            class="submit-btn"
            :loading="loading"
            native-type="submit"
          >
            {{ t('password.submitBtn') }}
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
  width: 480px;
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
</style>
