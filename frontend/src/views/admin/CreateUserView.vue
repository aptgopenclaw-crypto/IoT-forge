<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { createUser } from '@/api/user'
import { listAssignableRoles } from '@/api/rbac'
import { getScopedDeptOptions } from '@/api/dept'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Mail, User, Phone, Lock, Shield } from 'lucide-vue-next'
import type { RoleDto } from '@/types/rbac'
import type { DeptOptionVO } from '@/types/dept'
import { useI18n } from 'vue-i18n'
import { usePasswordPolicy } from '@/composables/usePasswordPolicy'

const { t } = useI18n()

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const roles = ref<RoleDto[]>([])
const scopedDeptOptions = ref<DeptOptionVO[]>([])
const deptLocked = ref(false)

onMounted(async () => {
  const [rolesResult, deptResult] = await Promise.allSettled([
    listAssignableRoles(),
    getScopedDeptOptions(),
  ])

  if (rolesResult.status === 'fulfilled') {
    roles.value = rolesResult.value.body.filter((r: RoleDto) => r.enabled)
  }

  if (deptResult.status === 'fulfilled') {
    scopedDeptOptions.value = deptResult.value.body
    // 若只有一個部門可選，自動帶入並鎖定
    const flat = flattenDeptOptions(deptResult.value.body)
    if (flat.length === 1) {
      form.deptId = flat[0].value
      deptLocked.value = true
    }
  }
})

function flattenDeptOptions(nodes: DeptOptionVO[]): DeptOptionVO[] {
  const result: DeptOptionVO[] = []
  for (const node of nodes) {
    result.push(node)
    if (node.children?.length) {
      result.push(...flattenDeptOptions(node.children))
    }
  }
  return result
}

const isSuperAdmin = computed(() => authStore.userInfo?.isSuperAdmin ?? false)

const form = reactive({
  email: '',
  displayName: '',
  phone: '',
  initialPassword: '',
  tenantId: null as string | null,
  roleId: '',
  deptId: null as number | null,
  authType: 'LOCAL' as 'LOCAL' | 'LDAP',
})

const isLdap = computed(() => form.authType === 'LDAP')

const tenantIdRef = computed(() => form.tenantId ?? undefined)
const { validatePassword } = usePasswordPolicy(tenantIdRef)

const rules = computed<FormRules>(() => ({
  email: [
    { required: true, message: t('user.create.errors.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('user.create.errors.emailFormat'), trigger: 'blur' },
  ],
  displayName: [
    { required: true, message: t('user.create.errors.displayNameRequired'), trigger: 'blur' },
  ],
  initialPassword: isLdap.value
    ? []
    : [
        { required: true, message: t('user.create.errors.passRequired'), trigger: 'blur' },
        {
          validator: (_rule, value: string, callback) => {
            if (value && !validatePassword(value)) {
              callback(new Error(t('user.create.errors.passComplexity')))
            } else {
              callback()
            }
          },
          trigger: 'blur',
        },
      ],
  roleId: [
    { required: true, message: t('user.create.errors.roleRequired'), trigger: 'change' },
  ],
}))

const errorCodeMessages = computed<Record<string, string>>(() => ({
  '10010': t('user.create.errors.10010'),
  '20003': t('user.create.errors.20003'),
  '20015': t('user.create.errors.20015'),
}))

const availableTenants = computed(() => authStore.userInfo?.availableTenants ?? [])

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await createUser({
      email: form.email,
      displayName: form.displayName,
      phone: form.phone || undefined,
      initialPassword: isLdap.value ? undefined : form.initialPassword,
      tenantId: isSuperAdmin.value ? form.tenantId : null,
      roleId: form.roleId,
      deptId: form.deptId,
      authType: form.authType,
    })
    ElMessage.success(t('user.create.createdSuccess'))
    router.push('/admin/users')
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    const msg = (errorCode && errorCodeMessages.value[errorCode]) || t('user.create.createFailed')
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/admin/users')
}
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-header">
        <h1 class="page-title">{{ t('user.create.title') }}</h1>
        <p class="page-subtitle">{{ t('user.create.subtitle') }}</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <el-form-item :label="t('user.create.emailLabel')" prop="email">
          <el-input v-model="form.email" :placeholder="t('user.create.emailPlaceholder')">
            <template #prefix>
              <Mail :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item :label="t('user.create.displayNameLabel')" prop="displayName">
          <el-input v-model="form.displayName" :placeholder="t('user.create.displayNamePlaceholder')">
            <template #prefix>
              <User :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item :label="t('user.create.phoneLabel')">
          <el-input v-model="form.phone" :placeholder="t('user.create.phonePlaceholder')">
            <template #prefix>
              <Phone :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item :label="t('user.create.authTypeLabel')">
          <el-radio-group v-model="form.authType">
            <el-radio value="LOCAL">{{ t('user.create.authTypeLocal') }}</el-radio>
            <el-radio value="LDAP">{{ t('user.create.authTypeLdap') }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="!isLdap" :label="t('user.create.passLabel')" prop="initialPassword">
          <el-input
            v-model="form.initialPassword"
            type="password"
            :placeholder="t('user.create.passPlaceholder')"
            show-password
          >
            <template #prefix>
              <Lock :size="18" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item v-if="isSuperAdmin" :label="t('user.create.tenantLabel')">
          <el-select
            v-model="form.tenantId"
            :placeholder="t('user.create.tenantPlaceholder')"
            clearable
            class="full-width"
          >
            <el-option
              v-for="t in availableTenants"
              :key="t.tenantId"
              :label="t.tenantName"
              :value="t.tenantId"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('user.create.roleLabel')" prop="roleId">
          <el-select v-model="form.roleId" :placeholder="t('user.create.rolePlaceholder')" class="full-width">
            <template #prefix>
              <Shield :size="18" class="input-icon" />
            </template>
            <el-option
              v-for="role in roles"
              :key="role.roleId"
              :label="role.name"
              :value="role.roleId"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('user.create.deptLabel')">
          <el-tree-select
            v-model="form.deptId"
            :data="scopedDeptOptions"
            :props="{ label: 'label', value: 'value', children: 'children' }"
            check-strictly
            placeholder="請選擇部門"
            :disabled="deptLocked"
            clearable
            filterable
            class="full-width"
          />
        </el-form-item>

        <div class="button-row">
          <el-button class="cancel-btn" @click="goBack">{{ t('user.create.cancelBtn') }}</el-button>
          <el-button
            class="submit-btn"
            :loading="loading"
            native-type="submit"
          >
            {{ t('user.create.submitBtn') }}
          </el-button>
        </div>
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
  width: 560px;
  background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 40px 32px;
  box-shadow: var(--shadow-card);
  align-self: flex-start;
}

.page-header {
  margin-bottom: 24px;
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

.section-divider {
  border-top: 1px solid var(--bg-active);
  margin: 24px 0 20px;
  position: relative;
}

.divider-label {
  position: absolute;
  top: -10px;
  left: 0;
  background-color: var(--bg-surface);
  padding-right: 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.full-width {
  width: 100%;
}

.button-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}

.cancel-btn {
  background: transparent;
  color: var(--text-secondary);
  border: none;
  border-radius: 6px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.cancel-btn:hover {
  opacity: 0.6;
  color: var(--text-primary);
}

.submit-btn {
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
