<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { createMenu, updateMenu, listPermissions } from '@/api/rbac'
import { ElMessage } from 'element-plus'
import type { MenuDto, CreateMenuRequest, UpdateMenuRequest, PermissionDto } from '@/types/rbac'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
  menu: MenuDto | null
  parentId: number | null
  menuTree: MenuDto[]
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const permissions = ref<PermissionDto[]>([])

const isEdit = computed(() => !!props.menu)
const dialogTitle = computed(() => (isEdit.value ? '編輯選單' : '新增選單'))

const menuTypeOptions = [
  { label: '目錄', value: 'DIRECTORY' },
  { label: '頁面', value: 'PAGE' },
  { label: '按鈕', value: 'BUTTON' },
]

const scopeOptions = [
  { label: '租戶內（TENANT）', value: 'TENANT' },
  { label: '平台專用（PLATFORM）', value: 'PLATFORM' },
  { label: '公開（PUBLIC）', value: 'PUBLIC' },
]

const form = ref({
  name: '',
  menuType: 'PAGE' as string,
  parentId: null as number | null,
  routeName: '',
  routePath: '',
  component: '',
  permissionCode: '',
  icon: '',
  sortOrder: 0,
  visible: true,
  keepAlive: false,
  redirect: '',
  scope: 'TENANT' as 'PLATFORM' | 'TENANT' | 'PUBLIC',
})

const rules: FormRules = {
  name: [{ required: true, message: '請輸入選單名稱', trigger: 'blur' }],
  menuType: [{ required: true, message: '請選擇選單類型', trigger: 'change' }],
  redirect: [
    {
      validator: (_rule, value: string, callback) => {
        if (!value) return callback()
        if (!/^\/[a-zA-Z0-9\-_/]*$/.test(value)) {
          return callback(new Error('Redirect 必須為 / 開頭的內部路徑'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

// Flatten tree for parentId selector
function flattenTree(nodes: MenuDto[], result: { menuId: number; name: string; depth: number }[] = [], depth = 0) {
  for (const node of nodes) {
    result.push({ menuId: node.menuId, name: node.name, depth })
    if (node.children?.length) {
      flattenTree(node.children, result, depth + 1)
    }
  }
  return result
}

const flatMenuOptions = computed(() => flattenTree(props.menuTree))

watch(
  () => props.visible,
  (val) => {
    if (val) {
      if (props.menu) {
        form.value = {
          name: props.menu.name,
          menuType: props.menu.menuType,
          parentId: props.menu.parentId,
          routeName: props.menu.routeName ?? '',
          routePath: props.menu.routePath ?? '',
          component: props.menu.component ?? '',
          permissionCode: props.menu.permissionCode ?? '',
          icon: props.menu.icon ?? '',
          sortOrder: props.menu.sortOrder,
          visible: props.menu.visible,
          keepAlive: props.menu.keepAlive,
          redirect: props.menu.redirect ?? '',
          scope: props.menu.scope ?? 'TENANT',
        }
      } else {
        form.value = {
          name: '',
          menuType: 'PAGE',
          parentId: props.parentId,
          routeName: '',
          routePath: '',
          component: '',
          permissionCode: '',
          icon: '',
          sortOrder: 0,
          visible: true,
          keepAlive: false,
          redirect: '',
          scope: 'TENANT',
        }
      }
      loadPermissions()
    }
  },
)

async function loadPermissions() {
  try {
    const res = await listPermissions()
    permissions.value = res.body
  } catch {
    // non-blocking
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && props.menu) {
      const payload: UpdateMenuRequest = {
        menuId: props.menu.menuId,
        name: form.value.name,
        menuType: form.value.menuType,
        parentId: form.value.parentId,
        routeName: form.value.routeName || null,
        routePath: form.value.routePath || null,
        component: form.value.component || null,
        permissionCode: form.value.permissionCode || null,
        icon: form.value.icon || null,
        sortOrder: form.value.sortOrder,
        visible: form.value.visible,
        keepAlive: form.value.keepAlive,
        redirect: form.value.redirect || null,
        scope: form.value.scope,
      }
      await updateMenu(payload)
      ElMessage.success(t('menu.form.updatedSuccess'))
    } else {
      const payload: CreateMenuRequest = {
        name: form.value.name,
        menuType: form.value.menuType,
        parentId: form.value.parentId,
        routeName: form.value.routeName || null,
        routePath: form.value.routePath || null,
        component: form.value.component || null,
        permissionCode: form.value.permissionCode || null,
        icon: form.value.icon || null,
        sortOrder: form.value.sortOrder,
        visible: form.value.visible,
        keepAlive: form.value.keepAlive,
        redirect: form.value.redirect || null,
        scope: form.value.scope,
      }
      await createMenu(payload)
      ElMessage.success(t('menu.form.createdSuccess'))
    }
    emit('saved')
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    if (errorCode === '30005') {
      ElMessage.error(t('menu.notFound'))
    } else {
      ElMessage.error(t('common.operationFailed'))
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="600px"
    :close-on-click-modal="false"
    class="menu-form-dialog"
    @close="emit('close')"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :aria-label="dialogTitle"
      label-width="100px"
      @submit.prevent="handleSubmit"
    >
      <el-form-item :label="t('menu.form.nameLabel')" prop="name">
        <el-input v-model="form.name" :placeholder="t('menu.form.namePlaceholder')" />
      </el-form-item>

      <el-form-item :label="t('menu.form.typeLabel')" prop="menuType">
        <el-select v-model="form.menuType" :placeholder="t('menu.form.typePlaceholder')">
          <el-option
            v-for="opt in menuTypeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="t('menu.form.parentLabel')">
        <el-select v-model="form.parentId" clearable :placeholder="t('menu.form.parentPlaceholder')">
          <el-option
            v-for="opt in flatMenuOptions"
            :key="opt.menuId"
            :label="'─'.repeat(opt.depth) + ' ' + opt.name"
            :value="opt.menuId"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-if="form.menuType !== 'BUTTON'" :label="t('menu.form.routeNameLabel')">
        <el-input v-model="form.routeName" :placeholder="t('menu.form.routeNamePlaceholder')" />
      </el-form-item>

      <el-form-item v-if="form.menuType !== 'BUTTON'" :label="t('menu.form.routePathLabel')">
        <el-input v-model="form.routePath" :placeholder="t('menu.form.routePathPlaceholder')" />
      </el-form-item>

      <el-form-item v-if="form.menuType === 'PAGE'" :label="t('menu.form.componentLabel')">
        <el-input v-model="form.component" :placeholder="t('menu.form.componentPlaceholder')" />
      </el-form-item>

      <el-form-item :label="t('menu.form.permCodeLabel')">
        <el-select v-model="form.permissionCode" clearable :placeholder="t('menu.form.permCodePlaceholder')">
          <el-option
            v-for="p in permissions"
            :key="p.code"
            :label="`${p.name} (${p.code})`"
            :value="p.code"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="t('menu.form.iconLabel')">
        <el-input v-model="form.icon" :placeholder="t('menu.form.iconPlaceholder')" />
      </el-form-item>

      <el-form-item :label="t('menu.form.sortLabel')">
        <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
      </el-form-item>

      <el-form-item :label="t('menu.form.visibleLabel')">
        <el-switch v-model="form.visible" />
      </el-form-item>

      <el-form-item label="適用範圍" prop="scope">
        <el-select v-model="form.scope">
          <el-option
            v-for="opt in scopeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-if="form.menuType === 'PAGE'" :label="t('menu.form.keepAliveLabel')">
        <el-switch v-model="form.keepAlive" />
      </el-form-item>

      <el-form-item v-if="form.menuType === 'DIRECTORY'" :label="t('menu.form.redirectLabel')" prop="redirect">
        <el-input v-model="form.redirect" :placeholder="t('menu.form.redirectPlaceholder')" />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <button class="btn-outlined" @click="emit('close')">{{ t('common.cancel') }}</button>
        <button class="btn-primary-pill" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? t('menu.form.saving') : t('menu.form.saveBtn') }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn-primary-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--btn-primary-bg);
  color: var(--btn-primary-text);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
  padding: 8px 24px;
  border-radius: 86px;
  border: none;
  cursor: pointer;
  transition: opacity 150ms ease;
}

.btn-primary-pill:hover {
  background: var(--btn-primary-hover);
}

.btn-primary-pill:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.btn-outlined {
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
  padding: 8px 16px;
  border-radius: 6px;
  border: 1px solid var(--border-light);
  cursor: pointer;
  transition: opacity 150ms ease;
}

.btn-outlined:hover {
  opacity: 0.6;
}

/* Dialog dark theme overrides */
:deep(.el-dialog) {
  background: var(--bg-surface);
  border: 1px solid var(--border-medium);
  border-radius: 16px;
  box-shadow: rgba(0, 0, 0, 0.5) 0px 0px 0px 2px, rgba(255, 255, 255, 0.19) 0px 0px 14px;
}

:deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 500;
  color: var(--text-heading);
  letter-spacing: 0.2px;
}

:deep(.el-dialog__headerbtn .el-dialog__close) {
  color: var(--text-secondary);
}

:deep(.el-input__inner),
:deep(.el-select .el-input__inner),
:deep(.el-textarea__inner) {
  background: var(--bg-base);
  color: var(--text-primary);
  border: 1px solid var(--border-medium);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.2px;
}

:deep(.el-input__wrapper),
:deep(.el-select .el-input__wrapper) {
  background: var(--bg-base);
  box-shadow: 0 0 0 1px var(--border-medium) inset;
}

:deep(.el-input-number) {
  width: 160px;
}
</style>
