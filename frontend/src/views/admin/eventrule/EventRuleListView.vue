<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listEventRules,
  createEventRule,
  updateEventRule,
  toggleEventRule,
  deleteEventRule,
} from '@/api/eventrule'
import { listDeviceTypeNames } from '@/api/schema'
import type { EventRuleResponse, EventRuleRequest, TriggerMode, ActionType } from '@/types/telemetry'

const { t } = useI18n()

// ── Filter / table ──
const tableData = ref<EventRuleResponse[]>([])
const loading = ref(false)
const filterDeviceType = ref('')
const filterEnabled = ref<boolean | undefined>(undefined)
const pagination = reactive({ page: 0, size: 20, total: 0 })
const deviceTypeOptions = ref<{ value: string; label: string }[]>([])

async function loadDeviceTypeOptions() {
  try {
    const res = await listDeviceTypeNames()
    if (res.errorCode === '00000' && res.body) {
      deviceTypeOptions.value = res.body.map((t: string) => ({ value: t, label: t }))
    }
  } catch { /* no-op */ }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listEventRules({
      deviceType: filterDeviceType.value || undefined,
      enabled: filterEnabled.value,
      page: pagination.page,
      size: pagination.size,
    })
    if (res.errorCode === '00000') {
      tableData.value = res.body.content
      pagination.total = res.body.totalElements
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 0
  fetchList()
}

function handlePageChange(page: number) {
  pagination.page = page - 1
  fetchList()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 0
  fetchList()
}

// ── Dialog form ──
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)

const emptyForm = (): EventRuleRequest => ({
  ruleCode: '',
  name: '',
  deviceType: '',
  severity: 'WARNING',
  scope: null,
  condition: { field: '', operator: 'GT', value: 0 },
  triggerCfg: { mode: 'ON_MATCH', durationSec: 0, cooldownSec: 300 },
  actions: [{ type: 'NOTIFY', channels: ['IN_APP'] }],
})

const form = reactive<EventRuleRequest>(emptyForm())

function openCreate() {
  editingId.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: EventRuleResponse) {
  editingId.value = row.id
  Object.assign(form, {
    ruleCode: row.ruleCode,
    name: row.name,
    deviceType: row.deviceType,
    severity: row.severity,
    scope: row.scope ?? null,
    condition: JSON.parse(JSON.stringify(row.condition)),
    triggerCfg: { ...row.triggerCfg },
    actions: JSON.parse(JSON.stringify(row.actions)),
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.ruleCode || !form.name || !form.deviceType) {
    ElMessage.warning(t('common.requiredFields'))
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateEventRule(editingId.value, form)
    } else {
      await createEventRule(form)
    }
    ElMessage.success(t('common.saveSuccess'))
    dialogVisible.value = false
    fetchList()
  } catch { /* handled by interceptor */ } finally {
    saving.value = false
  }
}

async function handleToggle(row: EventRuleResponse) {
  try {
    await toggleEventRule(row.id, !row.enabled)
    ElMessage.success(t('common.saveSuccess'))
    fetchList()
  } catch { /* handled */ }
}

async function handleDelete(row: EventRuleResponse) {
  try {
    await ElMessageBox.confirm(t('common.confirmDelete'), t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await deleteEventRule(row.id)
    ElMessage.success(t('common.deleted'))
    fetchList()
  } catch { /* cancelled */ }
}

const triggerModeOptions: { value: TriggerMode; label: string }[] = [
  { value: 'ON_MATCH', label: 'ON_MATCH' },
  { value: 'FOR_DURATION', label: 'FOR_DURATION' },
  { value: 'ON_CHANGE', label: 'ON_CHANGE' },
]

const severityOptions = ['INFO', 'WARNING', 'CRITICAL']

const operatorOptions = ['GT', 'LT', 'EQ', 'GTE', 'LTE', 'NEQ']

const actionTypeOptions: { value: ActionType; label: string }[] = [
  { value: 'NOTIFY', label: 'NOTIFY' },
  { value: 'WEBHOOK', label: 'WEBHOOK' },
]

function severityType(s: string) {
  return s === 'CRITICAL' ? 'danger' : s === 'WARNING' ? 'warning' : 'info'
}

onMounted(() => {
  loadDeviceTypeOptions()
  fetchList()
})
</script>

<template>
  <div class="event-rule-list">
    <div class="page-header">
      <h2>{{ t('eventRule.listTitle') }}</h2>
      <p class="subtitle">{{ t('eventRule.listSubtitle') }}</p>
    </div>

    <!-- Filter bar -->
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item :label="t('eventRule.deviceType')">
          <el-select
            v-model="filterDeviceType"
            clearable
            :placeholder="t('common.all')"
            style="width: 160px"
          >
            <el-option
              v-for="opt in deviceTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select
            v-model="filterEnabled"
            clearable
            :placeholder="t('common.all')"
            style="width: 120px"
          >
            <el-option :value="true" :label="t('common.enabled')" />
            <el-option :value="false" :label="t('common.disabled')" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleSearch">{{ t('common.query') }}</el-button>
          <el-button type="primary" @click="openCreate">{{ t('common.add') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-table v-loading="loading" :data="tableData" border size="small">
      <el-table-column prop="ruleCode" :label="t('eventRule.ruleCode')" width="160" />
      <el-table-column prop="name" :label="t('eventRule.name')" min-width="160" />
      <el-table-column prop="deviceType" :label="t('eventRule.deviceType')" width="140" />
      <el-table-column :label="t('eventRule.severity')" width="100">
        <template #default="{ row }">
          <el-tag :type="severityType(row.severity)" size="small">{{ row.severity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="triggerCfg.mode" :label="t('eventRule.triggerMode')" width="130" />
      <el-table-column :label="t('common.status')" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? t('common.enabled') : t('common.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button
            size="small"
            :type="row.enabled ? 'warning' : 'success'"
            @click="handleToggle(row)"
          >
            {{ row.enabled ? t('common.disabled') : t('common.enabled') }}
          </el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="pagination.total > 0"
      class="pagination"
      background
      layout="total, sizes, prev, pager, next"
      :total="pagination.total"
      :page-size="pagination.size"
      :current-page="pagination.page + 1"
      :page-sizes="[10, 20, 50]"
      @size-change="handleSizeChange"
      @current-change="handlePageChange"
    />

    <!-- Create / Edit dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('common.edit') : t('common.add')"
      width="640px"
    >
      <el-form :model="form" label-width="120px" size="default">
        <el-form-item :label="t('eventRule.ruleCode')" required>
          <el-input v-model="form.ruleCode" :disabled="!!editingId" maxlength="50" />
        </el-form-item>
        <el-form-item :label="t('eventRule.name')" required>
          <el-input v-model="form.name" maxlength="200" />
        </el-form-item>
        <el-form-item :label="t('eventRule.deviceType')" required>
          <el-select v-model="form.deviceType" filterable style="width: 100%">
            <el-option
              v-for="opt in deviceTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('eventRule.severity')">
          <el-select v-model="form.severity" style="width: 160px">
            <el-option v-for="s in severityOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-divider>{{ t('eventRule.conditionSection') }}</el-divider>
        <el-form-item :label="t('eventRule.condField')">
          <el-input v-model="form.condition.field" />
        </el-form-item>
        <el-form-item :label="t('eventRule.condOperator')">
          <el-select v-model="form.condition.operator" style="width: 120px">
            <el-option v-for="op in operatorOptions" :key="op" :label="op" :value="op" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('eventRule.condValue')">
          <el-input v-model="form.condition.value" />
        </el-form-item>
        <el-divider>{{ t('eventRule.triggerSection') }}</el-divider>
        <el-form-item :label="t('eventRule.triggerMode')">
          <el-select v-model="form.triggerCfg.mode" style="width: 180px">
            <el-option
              v-for="opt in triggerModeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="form.triggerCfg.mode === 'FOR_DURATION'"
          :label="t('eventRule.durationSec')"
        >
          <el-input-number v-model="form.triggerCfg.durationSec" :min="1" :max="86400" />
        </el-form-item>
        <el-form-item :label="t('eventRule.cooldownSec')">
          <el-input-number v-model="form.triggerCfg.cooldownSec" :min="0" :max="86400" />
        </el-form-item>
        <el-divider>{{ t('eventRule.actionsSection') }}</el-divider>
        <el-form-item
          v-for="(action, idx) in form.actions"
          :key="idx"
          :label="`${t('eventRule.action')} ${idx + 1}`"
        >
          <el-select v-model="action.type" style="width: 160px">
            <el-option
              v-for="opt in actionTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.event-rule-list {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.subtitle {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.filter-card {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
