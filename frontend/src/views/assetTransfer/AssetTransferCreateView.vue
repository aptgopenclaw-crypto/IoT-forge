<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useDeptStore } from '@/stores/deptStore'
import DeptTreeSelector from '@/components/DeptTreeSelector.vue'
import { createApplication, createAndSubmitApplication } from '@/api/assetTransfer'
import { useApiError } from '@/composables/useApiError'
import type { AssetTransferCreateRequest } from '@/types/assetTransfer'

const { t } = useI18n()
const router = useRouter()
const deptStore = useDeptStore()
const { handleError } = useApiError()

// Ensure dept options are loaded
if (!deptStore.deptOptions.length) {
  deptStore.fetchDeptTree()
}

const formRef = ref<FormInstance>()
const submitting = ref(false)
const saving = ref(false)

const form = reactive<AssetTransferCreateRequest>({
  assetCode: '',
  assetName: '',
  transferType: '',
  departmentId: 0,
  targetDepartmentId: null,
  reason: '',
  assetValue: null,
})

const TRANSFER_TYPES = ['INTERNAL', 'EXTERNAL', 'DISPOSAL', 'RETURN'] as const

const rules: FormRules = {
  assetCode: [{ required: true, message: t('assetTransfer.placeholderAssetCode'), trigger: 'blur' }],
  assetName: [{ required: true, message: t('assetTransfer.placeholderAssetName'), trigger: 'blur' }],
  transferType: [{ required: true, message: t('assetTransfer.fieldTransferType'), trigger: 'change' }],
  departmentId: [
    {
      required: true,
      validator: (_rule: unknown, value: number, callback: (err?: Error) => void) => {
        if (!value) callback(new Error(t('assetTransfer.fieldDept')))
        else callback()
      },
      trigger: 'change',
    },
  ],
}

async function handleSaveDraft() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await createApplication({ ...form })
    ElMessage.success(t('assetTransfer.createdSuccess'))
    router.push('/asset-transfer/my')
  } catch (err) {
    handleError(err, {
      '90005': t('common.deptNotFound'),
    }, t('assetTransfer.loadFailed'))
  } finally {
    saving.value = false
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createAndSubmitApplication({ ...form })
    ElMessage.success(t('assetTransfer.submittedSuccess'))
    router.push('/asset-transfer/my')
  } catch (err) {
    handleError(err, {
      '90005': t('common.deptNotFound'),
    }, t('assetTransfer.loadFailed'))
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  router.back()
}
</script>

<template>
  <div class="page-container">
    <div class="page-content">
      <div class="page-header">
        <div>
          <h1 class="page-title">{{ t('assetTransfer.createTitle') }}</h1>
          <p class="page-subtitle">{{ t('assetTransfer.createSubtitle') }}</p>
        </div>
      </div>

      <div class="form-card">
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="transfer-form">
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item :label="t('assetTransfer.fieldAssetCode')" prop="assetCode">
                <el-input
                  v-model="form.assetCode"
                  :placeholder="t('assetTransfer.placeholderAssetCode')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('assetTransfer.fieldAssetName')" prop="assetName">
                <el-input
                  v-model="form.assetName"
                  :placeholder="t('assetTransfer.placeholderAssetName')"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item :label="t('assetTransfer.fieldTransferType')" prop="transferType">
                <el-select v-model="form.transferType" style="width: 100%">
                  <el-option
                    v-for="type in TRANSFER_TYPES"
                    :key="type"
                    :label="t(`assetTransfer.transferType${type.charAt(0) + type.slice(1).toLowerCase()}`)"
                    :value="type"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('assetTransfer.fieldAssetValue')" prop="assetValue">
                <el-input-number
                  v-model="form.assetValue"
                  :min="0"
                  :precision="2"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item :label="t('assetTransfer.fieldDept')" prop="departmentId">
                <DeptTreeSelector v-model="form.departmentId" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('assetTransfer.fieldTargetDept')" prop="targetDepartmentId">
                <DeptTreeSelector v-model="form.targetDepartmentId" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item :label="t('assetTransfer.fieldReason')" prop="reason">
            <el-input
              v-model="form.reason"
              type="textarea"
              :rows="3"
              :placeholder="t('assetTransfer.placeholderReason')"
            />
          </el-form-item>

          <div class="form-actions">
            <el-button @click="handleCancel">{{ t('common.cancel') }}</el-button>
            <el-button :loading="saving" @click="handleSaveDraft">
              {{ t('assetTransfer.btnSaveDraft') }}
            </el-button>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">
              {{ t('assetTransfer.btnSubmit') }}
            </el-button>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  padding: 32px 24px;
  min-height: 100vh;
  background-color: var(--bg-base);
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-heading);
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.form-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  padding: 32px;
  max-width: 900px;
}

.transfer-form :deep(.el-form-item__label) {
  color: var(--text-primary);
  font-weight: 500;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}
</style>
