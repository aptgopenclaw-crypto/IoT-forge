<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RotateCcw } from 'lucide-vue-next'
import dayjs from 'dayjs'
import type { AuditFilterModel } from '@/types/audit'
import { getAuditCategories } from '@/api/audit'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const categoryOptions = ref<string[]>([])

onMounted(async () => {
  try {
    const res = await getAuditCategories()
    categoryOptions.value = res.body
  } catch {
    categoryOptions.value = []
  }
})

const props = defineProps<{
  showUserName?: boolean
  showEventDesc?: boolean
  showEventType?: boolean
}>()

const emit = defineEmits<{
  search: [filters: AuditFilterModel]
  reset: []
}>()

const defaultStart = dayjs().subtract(7, 'day').startOf('day').toISOString()
const defaultEnd = dayjs().endOf('day').toISOString()

const filters = reactive<AuditFilterModel>({
  userName: '',
  eventDesc: '',
  eventType: '',
  startTimestamp: defaultStart,
  endTimestamp: defaultEnd,
})

const dateRange = ref<[string, string]>([defaultStart, defaultEnd])

function onDateRangeChange(val: [string, string] | null) {
  if (val) {
    const start = dayjs(val[0])
    const end = dayjs(val[1])
    const diffDays = end.diff(start, 'day')
    if (diffDays > 90) {
      ElMessage.warning(t('audit.dateRangeWarning'))
      return
    }
    filters.startTimestamp = start.toISOString()
    filters.endTimestamp = end.toISOString()
  } else {
    filters.startTimestamp = undefined
    filters.endTimestamp = undefined
  }
}

function handleSearch() {
  emit('search', { ...filters })
}

function handleReset() {
  filters.userName = ''
  filters.eventDesc = ''
  filters.eventType = ''
  filters.startTimestamp = defaultStart
  filters.endTimestamp = defaultEnd
  dateRange[0] = defaultStart
  dateRange[1] = defaultEnd
  emit('reset')
}
</script>

<template>
  <div class="audit-filter-bar">
    <el-form :inline="true" @submit.prevent="handleSearch" class="filter-form">
      <el-form-item v-if="props.showUserName" :label="t('audit.filterUser')">
        <el-input
          v-model="filters.userName"
          :placeholder="t('audit.filterUserPlaceholder')"
          clearable
          maxlength="200"
          class="filter-input"
        />
      </el-form-item>

      <el-form-item v-if="props.showEventDesc" :label="t('audit.filterEventDesc')">
        <el-select
          v-model="filters.eventDesc"
          :placeholder="t('audit.filterEventDescPlaceholder')"
          clearable
          class="filter-input"
        >
          <el-option v-for="cat in categoryOptions" :key="cat" :label="cat" :value="cat" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="props.showEventType" :label="t('audit.filterEventType')">
        <el-input
          v-model="filters.eventType"
          :placeholder="t('audit.filterEventTypePlaceholder')"
          clearable
          maxlength="50"
          class="filter-input"
        />
      </el-form-item>

      <el-form-item :label="t('audit.filterDateRange')">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          :range-separator="t('audit.filterDateSeparator')"
          :start-placeholder="t('audit.filterDateStart')"
          :end-placeholder="t('audit.filterDateEnd')"
          value-format="YYYY-MM-DDTHH:mm:ssZ"
          class="filter-date-picker"
          @change="onDateRangeChange"
        />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" native-type="submit" :icon="Search">
          {{ t('audit.searchBtn') }}
        </el-button>
        <el-button @click="handleReset" :icon="RotateCcw">
          {{ t('audit.resetBtn') }}
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.audit-filter-bar {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 20px 24px;
  margin-bottom: 20px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
}

.filter-input {
  width: 180px;
}

.filter-date-picker {
  width: 360px;
}

:deep(.el-form-item) {
  margin-bottom: 0;
}

:deep(.el-form-item__label) {
  color: var(--text-label);
  font-size: 12px;
  font-weight: 500;
}

:deep(.el-input__wrapper),
:deep(.el-date-editor) {
  background: var(--bg-base);
  border: 1px solid var(--border-medium);
  box-shadow: none;
}

:deep(.el-input__inner) {
  color: var(--text-primary);
}

:deep(.el-range-input) {
  color: var(--text-primary);
  background: transparent;
}

:deep(.el-range-separator) {
  color: var(--text-secondary);
}
</style>
