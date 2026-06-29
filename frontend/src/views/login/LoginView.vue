<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Mail, Lock, ShieldCheck } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const authStore = useAuthStore()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaImage = ref('')

const form = reactive({
  email: '',
  password: '',
  captcha: '',
  captchaKey: '',
})

const rules = computed<FormRules>(() => ({
  email: [
    { required: true, message: t('login.errors.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('login.errors.emailFormat'), trigger: 'blur' },
  ],
  password: [
    { required: true, message: t('login.errors.passwordRequired'), trigger: 'blur' },
  ],
  captcha: [
    { required: true, message: t('login.errors.captchaRequired'), trigger: 'blur' },
  ],
}))

const errorCodeMessages = computed<Record<string, string>>(() => ({
  '10013': t('login.errors.10013'),
  '10002': t('login.errors.10002'),
  '10003': t('login.errors.10003'),
  '10007': t('login.errors.10007'),
  '10018': t('login.errors.10018'),
  '20005': t('login.errors.20005'),
  '10021': t('login.errors.10021'),
}))

async function refreshCaptcha() {
  try {
    const data = await authStore.doGetCaptcha()
    captchaImage.value = data.captchaImage
    form.captchaKey = data.captchaKey
    form.captcha = ''
  } catch {
    ElMessage.error(t('login.errors.captchaFetchFailed'))
  }
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.doLogin({
      email: form.email,
      password: form.password,
      captcha: form.captcha,
      captchaKey: form.captchaKey,
    })
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    const msg = (errorCode && errorCodeMessages.value[errorCode]) || t('login.errors.loginFailed')
    ElMessage.error(msg)
    await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1 class="login-title">{{ t('login.title') }}</h1>
        <p class="login-subtitle">{{ t('login.subtitle') }}</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="email">
          <el-input v-model="form.email" :placeholder="t('login.emailPlaceholder')">
            <template #prefix>
              <Mail :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t('login.passwordPlaceholder')"
            show-password
          >
            <template #prefix>
              <Lock :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <div class="forgot-password-row">
          <a class="forgot-password-link" @click="router.push('/forgot-password')">
            {{ t('login.forgotPassword') }}
          </a>
        </div>

        <el-form-item prop="captcha">
          <div class="captcha-row">
            <el-input v-model="form.captcha" :placeholder="t('login.captchaPlaceholder')">
              <template #prefix>
                <ShieldCheck :size="18" class="input-icon" />
              </template>
            </el-input>
            <img
              v-if="captchaImage"
              :src="captchaImage"
              class="captcha-image"
              alt="captcha"
              @click="refreshCaptcha"
            />
            <div v-else class="captcha-placeholder" @click="refreshCaptcha">
              {{ t('login.captchaLoading') }}
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            class="login-btn"
            :loading="loading"
            native-type="submit"
          >
            {{ t('login.submit') }}
          </el-button>
        </el-form-item>
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

.captcha-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.captcha-image {
  height: 40px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid var(--border-medium);
  flex-shrink: 0;
}

.captcha-placeholder {
  height: 40px;
  width: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  border: 1px solid var(--border-medium);
  background-color: var(--bg-base);
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  flex-shrink: 0;
}

.login-btn {
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

.forgot-password-row {
  text-align: right;
  margin-bottom: 16px;
}

.forgot-password-link {
  font-size: 13px;
  color: var(--text-link);
  cursor: pointer;
  text-decoration: none;
}

.forgot-password-link:hover {
  text-decoration: underline;
}

.login-btn:hover {
  background: var(--btn-primary-hover);
  color: var(--btn-primary-text);
}
</style>
