<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ConditionNode } from '@/types/telemetry'
import ConditionRow from './ConditionRow.vue'

const props = defineProps<{
  modelValue: ConditionNode
  fieldOptions: { value: string; label: string }[]
  deviceType: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: ConditionNode]
}>()

const { t } = useI18n()

/** 是否為分支節點（有 op + children） */
const isBranch = computed(() => !!props.modelValue.op)

/** children 保證為陣列 */
const children = computed(() => props.modelValue.children ?? [])

function updateOp(op: string) {
  // NOT 最多只能有 1 個 child；若超過則保留第一個
  let updated = children.value
  if (op === 'NOT' && updated.length > 1) {
    updated = [updated[0]]
  }
  emit('update:modelValue', { ...props.modelValue, op, children: updated })
}

function addCondition() {
  const child: ConditionNode = { field: '', operator: 'GT', value: 0 }
  emit('update:modelValue', {
    ...props.modelValue,
    children: [...children.value, child],
  })
}

function addGroup() {
  const child: ConditionNode = {
    op: 'AND',
    children: [{ field: '', operator: 'GT', value: 0 }],
  }
  emit('update:modelValue', {
    ...props.modelValue,
    children: [...children.value, child],
  })
}

function updateChild(index: number, child: ConditionNode) {
  const updated = [...children.value]
  updated[index] = child
  emit('update:modelValue', { ...props.modelValue, children: updated })
}

function removeChild(index: number) {
  const updated = [...children.value]
  updated.splice(index, 1)
  emit('update:modelValue', { ...props.modelValue, children: updated })
}

const logicOpOptions = [
  { value: 'AND', label: 'AND' },
  { value: 'OR', label: 'OR' },
  { value: 'NOT', label: 'NOT' },
]
</script>

<template>
  <div class="condition-editor">
    <!-- Group header: logic operator selector -->
    <div class="group-header">
      <el-select
        :model-value="modelValue.op || 'AND'"
        style="width: 100px"
        @update:model-value="updateOp"
      >
        <el-option
          v-for="opt in logicOpOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </div>

    <!-- Group children -->
    <div class="group-children">
      <template v-for="(child, idx) in children" :key="idx">
        <!-- Leaf node: render ConditionRow -->
        <div v-if="!child.op" class="child-item">
          <ConditionRow
            :model-value="child"
            :field-options="fieldOptions"
            :device-type="deviceType"
            @update:model-value="(val: ConditionNode) => updateChild(idx, val)"
            @remove="removeChild(idx)"
          />
        </div>

        <!-- Branch node: recursive ConditionEditor -->
        <div v-else class="child-item child-group">
          <ConditionEditor
            :model-value="child"
            :field-options="fieldOptions"
            :device-type="deviceType"
            @update:model-value="(val: ConditionNode) => updateChild(idx, val)"
          />
          <el-button
            circle
            size="small"
            type="danger"
            :title="t('common.delete')"
            class="remove-group-btn"
            @click="removeChild(idx)"
          >
            ✕
          </el-button>
        </div>
      </template>
    </div>

    <!-- Add buttons -->
    <div class="group-actions">
      <el-button size="small" @click="addCondition">{{ t('eventRule.addCondition') }}</el-button>
      <el-button size="small" @click="addGroup">{{ t('eventRule.addConditionGroup') }}</el-button>
    </div>
  </div>
</template>

<style scoped>
.condition-editor {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 8px 12px;
  background: var(--el-fill-color-lighter);
}

.group-header {
  margin-bottom: 6px;
}

.group-children {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.child-item {
  display: flex;
  align-items: flex-start;
  gap: 4px;
}

.child-group {
  position: relative;
  padding-right: 32px;
}

.remove-group-btn {
  flex-shrink: 0;
  margin-top: 6px;
}

.group-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed var(--el-border-color-extra-light);
}
</style>
