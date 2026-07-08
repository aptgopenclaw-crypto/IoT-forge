<script setup lang="ts">
import { computed, ref, reactive, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/authStore'
import {
  listEventRules,
  createEventRule,
  updateEventRule,
  toggleEventRule,
  deleteEventRule,
} from '@/api/eventrule'
import { listDeviceTypeNames, getTelemetrySchema, getDeviceSchema } from '@/api/schema'
import type { EventRuleResponse, EventRuleRequest, TriggerMode, ActionType, ConditionNode } from '@/types/telemetry'
import ConditionEditor from './ConditionEditor.vue'

const { t } = useI18n()
const authStore = useAuthStore()
const canManageRules = computed(() => authStore.userInfo?.permissions.includes('EVENT_RULE_MANAGE') ?? false)

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

// ── Schema field options (for condition field dropdown) ──
const fieldOptions = ref<{ value: string; label: string }[]>([])
const loadingFields = ref(false)

/**
 * 從 telemetry schema 中萃取所有欄位名稱。
 * 支援兩種格式：
 *   1. JSON Schema 格式：{ "type": "object", "properties": { "fieldName": {...} } }
 *   2. fields 陣列格式：{ "fields": [{ "key": "fieldName", "type": "number" }, ...] }
 */
function extractFieldNames(schema: Record<string, unknown>): string[] {
  // 格式 1: JSON Schema properties
  const props = schema.properties as Record<string, unknown> | undefined
  if (props && typeof props === 'object') {
    return Object.keys(props)
  }

  // 格式 2: fields 陣列
  const fields = schema.fields as Array<Record<string, unknown>> | undefined
  if (Array.isArray(fields)) {
    return fields.map((f) => String(f.key ?? '')).filter(Boolean)
  }

  return []
}

async function loadFieldOptions(deviceType: string) {
  if (!deviceType) {
    fieldOptions.value = []
    return
  }
  loadingFields.value = true
  try {
    // 優先從 telemetry schema 萃取欄位（巢狀於 schema.telemetry 下）
    let names = extractFieldNames((await getTelemetrySchema(deviceType)).body as Record<string, unknown>)

    // 若 telemetry 段為空，退回讀取完整 schema（可能為 flat fields 格式）
    if (names.length === 0) {
      names = extractFieldNames((await getDeviceSchema(deviceType)).body as Record<string, unknown>)
    }

    fieldOptions.value = names.map((k) => ({ value: k, label: k }))
  } catch {
    fieldOptions.value = []
  } finally {
    loadingFields.value = false
  }
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
  condition: { op: 'AND', children: [{ field: '', operator: 'GT', value: 0 }] },
  triggerCfg: { mode: 'ON_MATCH', durationSec: 0, cooldownSec: 300 },
  actions: [{ type: 'NOTIFY', channels: ['IN_APP'] }],
})

const form = reactive<EventRuleRequest>(emptyForm())

// Watch deviceType changes to refresh field options
watch(() => form.deviceType, (newVal) => {
  loadFieldOptions(newVal)
})

/** 若 condition 為葉節點（無 op），包裝為群組格式 */
function normalizeCondition(cond: ConditionNode): ConditionNode {
  if (cond.op) return cond // already a branch
  return { op: 'AND', children: [cond] }
}

/** 設備類型變更時，清除條件並重新載入欄位選項 */
function onDeviceTypeChange(deviceType: string) {
  form.condition = { op: 'AND', children: [{ field: '', operator: 'GT', value: 0 }] }
  loadFieldOptions(deviceType)
}

function generateRuleCode(): string {
  const rand = Math.random().toString(16).substring(2, 10).toUpperCase()
  return `RULE_${rand}`
}

function openCreate() {
  editingId.value = null
  Object.assign(form, emptyForm())
  form.ruleCode = generateRuleCode()
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
    condition: normalizeCondition(JSON.parse(JSON.stringify(row.condition))),
    triggerCfg: { ...row.triggerCfg },
    actions: JSON.parse(JSON.stringify(row.actions)),
  })
  // 確保欄位選項已載入（即使 deviceType 與上次開啟對話框時相同）
  loadFieldOptions(row.deviceType)
  dialogVisible.value = true
}

/** 遞迴清除空葉節點（無 field 或無 operator），並移除空 children */
function cleanCondition(node: ConditionNode): ConditionNode | null {
  if (node.op) {
    // 分支節點：遞迴清理 children
    const cleaned = (node.children ?? [])
      .map((c) => cleanCondition(c))
      .filter((c): c is ConditionNode => c !== null)
    if (cleaned.length === 0) return null
    return { ...node, children: cleaned }
  }
  // 葉節點：至少要有 field 和 operator
  if (!node.field || !node.operator) return null
  return node
}

async function handleSave() {
  if (!form.ruleCode || !form.name || !form.deviceType) {
    ElMessage.warning(t('common.requiredFields'))
    return
  }

  // 清理並驗證條件
  const cleaned = cleanCondition(form.condition)
  if (!cleaned) {
    ElMessage.warning(t('eventRule.conditionRequired'))
    return
  }
  form.condition = cleaned

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
          <el-button v-if="canManageRules" type="primary" @click="openCreate">{{ t('common.add') }}</el-button>
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
      <el-table-column v-if="canManageRules" :label="t('common.actions')" width="200" fixed="right">
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
          <el-input v-model="form.ruleCode" disabled maxlength="50" />
        </el-form-item>
        <el-form-item :label="t('eventRule.name')" required>
          <el-input v-model="form.name" maxlength="200" />
        </el-form-item>
        <el-form-item :label="t('eventRule.deviceType')" required>
          <el-select v-model="form.deviceType" filterable style="width: 100%" @change="onDeviceTypeChange">
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
        <el-form-item label-width="0">
          <ConditionEditor
            v-if="form.deviceType"
            v-model="form.condition"
            :field-options="fieldOptions"
            :device-type="form.deviceType"
          />
          <span v-else class="condition-placeholder">{{ t('eventRule.selectDeviceTypeHint') }}</span>
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

.condition-placeholder {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
</style>
