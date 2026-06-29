<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Mail, ArrowLeft } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { forgotPassword } from '@/api/auth'

const { t } = useI18n()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const sent = ref(false)

const form = reactive({
  email: '',
})

const rules = computed<FormRules>(() => ({
  email: [
    { required: true, message: t('forgotPassword.errors.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('forgotPassword.errors.emailFormat'), trigger: 'blur' },
  ],
}))

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await forgotPassword({ email: form.email })
    sent.value = true
  } catch {
    ElMessage.error(t('forgotPassword.errors.sendFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1 class="login-title">{{ t('forgotPassword.title') }}</h1>
        <p class="login-subtitle">{{ t('forgotPassword.subtitle') }}</p>
      </div>

      <!-- Success state -->
      <div v-if="sent" class="sent-message">
        <p>{{ t('forgotPassword.sentMessage') }}</p>
        <el-button class="back-btn" @click="router.push('/login')">
          <ArrowLeft :size="16" style="margin-right: 6px;" />
          {{ t('forgotPassword.backToLogin') }}
        </el-button>
      </div>

      <!-- Form state -->
      <el-form
        v-else
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <el-form-item prop="email">
          <el-input v-model="form.email" :placeholder="t('forgotPassword.emailPlaceholder')">
            <template #prefix>
              <Mail :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item>
          <el-button
            class="submit-btn"
            :loading="loading"
            native-type="submit"
          >
            {{ t('forgotPassword.submit') }}
          </el-button>
        </el-form-item>

        <div class="back-row">
          <a class="back-link" @click="router.push('/login')">
            <ArrowLeft :size="14" style="margin-right: 4px; vertical-align: middle;" />
            {{ t('forgotPassword.backToLogin') }}
          </a>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: var(--bg-base);
}

.login-card {
  width: 420px;
  background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 40px 32px;
  box-shadow: var(--shadow-card);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-title {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.15;
  color: var(--text-heading);
  margin: 0 0 8px 0;
}

.login-subtitle {
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

.sent-message {
  text-align: center;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.8;
}

.submit-btn {
  width: 100%;
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

.back-btn {
  margin-top: 16px;
  background: transparent;
  color: var(--text-link);
  border: 1px solid var(--border-medium);
  border-radius: 86px;
  padding: 8px 24px;
  font-size: 14px;
}

.back-row {
  text-align: center;
  margin-top: 8px;
}

.back-link {
  font-size: 13px;
  color: var(--text-link);
  cursor: pointer;
  text-decoration: none;
}

.back-link:hover {
  text-decoration: underline;
}
</style>
