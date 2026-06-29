<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/authStore'
import { listUsers } from '@/api/user'
import { setDelegate, listMyDelegates } from '@/api/workflow'
import type { DelegateSettingDto } from '@/api/workflow'
import type { UserListItemDto } from '@/types/user'

const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const deptUsers = ref<UserListItemDto[]>([])
const loadingUsers = ref(false)
const delegateRecords = ref<DelegateSettingDto[]>([])
const loadingRecords = ref(false)

const form = reactive({
  delegateTo: '',
  businessType: 'ASSET_TRANSFER',
  effectiveFrom: '',
  effectiveTo: '',
})

const rules: FormRules = {
  delegateTo: [{ required: true, message: '請選擇代理人', trigger: 'change' }],
  businessType: [{ required: true, message: '請選擇業務類型', trigger: 'change' }],
  effectiveFrom: [{ required: true, message: '請選擇生效日期', trigger: 'change' }],
  effectiveTo: [{ required: true, message: '請選擇截止日期', trigger: 'change' }],
}

const userMap = computed(() => {
  const map = new Map<string, string>()
  deptUsers.value.forEach((u) => map.set(u.userId, u.displayName))
  return map
})

const today = new Date().toISOString().slice(0, 10)

function delegateStatus(record: DelegateSettingDto) {
  if (record.effectiveTo < today) return { label: '已過期', type: 'info' as const }
  if (record.effectiveFrom > today) return { label: '未生效', type: 'warning' as const }
  return { label: '生效中', type: 'success' as const }
}

function businessTypeLabel(type: string | null) {
  if (type === 'ASSET_TRANSFER') return '資產異動'
  return type ?? '—'
}

async function loadRecords() {
  loadingRecords.value = true
  try {
    const res = await listMyDelegates()
    delegateRecords.value = res.body ?? []
  } catch {
    // silent — table simply stays empty
  } finally {
    loadingRecords.value = false
  }
}

onMounted(async () => {
  const deptId = authStore.userInfo?.deptId ? Number(authStore.userInfo.deptId) : null
  if (!deptId) return
  loadingUsers.value = true
  try {
    const res = await listUsers({ deptId, size: 200 })
    const currentUserId = authStore.userInfo?.userId
    deptUsers.value = res.body.content.filter((u) => u.userId !== currentUserId)
  } catch {
    ElMessage.error('無法載入部門成員')
  } finally {
    loadingUsers.value = false
  }
  await loadRecords()
})

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await setDelegate({
      delegateTo: form.delegateTo,
      businessType: form.businessType,
      effectiveFrom: form.effectiveFrom,
      effectiveTo: form.effectiveTo,
    })
    ElMessage.success('代理設定已儲存')
    formRef.value.resetFields()
    await loadRecords()
  } catch {
    ElMessage.error('儲存失敗，請稍後再試')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">指派代理</h2>
    <el-card class="form-card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        label-position="right"
      >
        <el-form-item label="代理人" prop="delegateTo">
          <el-select
            v-model="form.delegateTo"
            placeholder="請選擇代理人"
            :loading="loadingUsers"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="user in deptUsers"
              :key="user.userId"
              :label="`${user.displayName}（${user.email}）`"
              :value="user.userId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="業務類型" prop="businessType">
          <el-select v-model="form.businessType" placeholder="請選擇業務類型" style="width: 100%">
            <el-option label="資產異動" value="ASSET_TRANSFER" />
          </el-select>
        </el-form-item>

        <el-form-item label="生效日期" prop="effectiveFrom">
          <el-date-picker
            v-model="form.effectiveFrom"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="選擇生效日期"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="截止日期" prop="effectiveTo">
          <el-date-picker
            v-model="form.effectiveTo"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="選擇截止日期"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">儲存</el-button>
          <el-button @click="formRef?.resetFields()">重設</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <template #header>
        <span class="card-header-title">代理設定記錄</span>
      </template>
      <el-table :data="delegateRecords" v-loading="loadingRecords" empty-text="尚無代理設定記錄">
        <el-table-column label="代理人" min-width="140">
          <template #default="{ row }">
            {{ userMap.get(row.delegateTo) ?? row.delegateTo }}
          </template>
        </el-table-column>
        <el-table-column label="業務類型" width="120">
          <template #default="{ row }">
            {{ businessTypeLabel(row.businessType) }}
          </template>
        </el-table-column>
        <el-table-column prop="effectiveFrom" label="生效日期" width="120" />
        <el-table-column prop="effectiveTo" label="截止日期" width="120" />
        <el-table-column label="狀態" width="100">
          <template #default="{ row }">
            <el-tag :type="delegateStatus(row).type" size="small">
              {{ delegateStatus(row).label }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  padding: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 16px;
}

.form-card {
  max-width: 600px;
}

.table-card {
  margin-top: 24px;
  max-width: 800px;
}

.card-header-title {
  font-weight: 600;
}
</style>

