<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ConditionNode, ConditionOperator } from '@/types/telemetry'

const props = defineProps<{
  modelValue: ConditionNode
  fieldOptions: { value: string; label: string }[]
  deviceType: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: ConditionNode]
  'remove': []
}>()

const { t } = useI18n()

const operatorOptions: { value: ConditionOperator; label: string }[] = [
  { value: 'GT', label: '>' },
  { value: 'GTE', label: '≥' },
  { value: 'LT', label: '<' },
  { value: 'LTE', label: '≤' },
  { value: 'EQ', label: '=' },
  { value: 'NEQ', label: '≠' },
  { value: 'BETWEEN', label: 'BETWEEN' },
]

const isBetween = computed(() => props.modelValue.operator === 'BETWEEN')

/** 目前 value 若為陣列則視為 BETWEEN 的 [min, max] */
const betweenMin = computed({
  get: () => {
    if (Array.isArray(props.modelValue.value) && props.modelValue.value.length >= 2) {
      return props.modelValue.value[0] as number
    }
    return 0
  },
  set: (val: number) => {
    const max = Array.isArray(props.modelValue.value) ? (props.modelValue.value[1] as number) : 100
    updateValue([val, max])
  },
})

const betweenMax = computed({
  get: () => {
    if (Array.isArray(props.modelValue.value) && props.modelValue.value.length >= 2) {
      return props.modelValue.value[1] as number
    }
    return 100
  },
  set: (val: number) => {
    const min = Array.isArray(props.modelValue.value) ? (props.modelValue.value[0] as number) : 0
    updateValue([min, val])
  },
})

function updateField(field: string) {
  emit('update:modelValue', { ...props.modelValue, field })
}

function updateOperator(operator: ConditionOperator) {
  const val = operator === 'BETWEEN' ? [0, 100] : props.modelValue.value ?? 0
  emit('update:modelValue', { ...props.modelValue, operator, value: val })
}

function updateValue(value: unknown) {
  emit('update:modelValue', { ...props.modelValue, value })
}
</script>

<template>
  <div class="condition-row">
    <el-select
      :model-value="modelValue.field"
      filterable
      :placeholder="t('eventRule.selectFieldHint')"
      style="width: 160px"
      @update:model-value="updateField"
    >
      <template #empty>
        <span class="field-empty-hint">{{ t('eventRule.noFieldsHint') }}</span>
      </template>
      <el-option
        v-for="opt in fieldOptions"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>

    <el-select
      :model-value="modelValue.operator"
      style="width: 100px"
      @update:model-value="updateOperator"
    >
      <el-option
        v-for="op in operatorOptions"
        :key="op.value"
        :label="op.label"
        :value="op.value"
      />
    </el-select>

    <!-- BETWEEN: two number inputs for min / max -->
    <template v-if="isBetween">
      <el-input-number
        :model-value="betweenMin"
        style="width: 140px"
        :placeholder="t('eventRule.betweenMin')"
        controls-position="right"
        @update:model-value="(v: number) => (betweenMin = v)"
      />
      <span class="between-separator">~</span>
      <el-input-number
        :model-value="betweenMax"
        style="width: 140px"
        :placeholder="t('eventRule.betweenMax')"
        controls-position="right"
        @update:model-value="(v: number) => (betweenMax = v)"
      />
    </template>

    <!-- Other operators: single value input -->
    <el-input
 v-else :model-value="modelValue.value as string | number" style="width: 140px" @update:model-value="updateValue"
    />

    <el-button
      circle
      size="small"
      type="danger"
      :title="t('common.delete')"
      @click="emit('remove')"
    >
      ✕
    </el-button>
  </div>
</template>

<style scoped>
.condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.between-separator {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.field-empty-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  padding: 4px 8px;
}
</style>
