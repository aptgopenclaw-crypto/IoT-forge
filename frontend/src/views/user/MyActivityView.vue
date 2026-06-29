<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuditStore } from '@/stores/auditStore'
import AuditFilterBar from '@/components/audit/AuditFilterBar.vue'
import AuditTable from '@/components/audit/AuditTable.vue'
import type { AuditFilterModel } from '@/types/audit'
import dayjs from 'dayjs'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const auditStore = useAuditStore()

const currentFilters = ref<{ eventType?: string; startTimestamp?: string; endTimestamp?: string; sort?: string }>({
  startTimestamp: dayjs().subtract(7, 'day').startOf('day').toISOString(),
  endTimestamp: dayjs().endOf('day').toISOString(),
  sort: 'DESC',
})

function handleSearch(filters: AuditFilterModel) {
  currentFilters.value = {
    eventType: filters.eventType,
    startTimestamp: filters.startTimestamp,
    endTimestamp: filters.endTimestamp,
    sort: currentFilters.value.sort,
  }
  auditStore.myPagination.page = 0
  auditStore.fetchMyLogs(currentFilters.value)
}

function handleReset() {
  currentFilters.value = {
    startTimestamp: dayjs().subtract(7, 'day').startOf('day').toISOString(),
    endTimestamp: dayjs().endOf('day').toISOString(),
    sort: 'DESC',
  }
  auditStore.myPagination.page = 0
  auditStore.fetchMyLogs(currentFilters.value)
}

function handlePageChange(page: number) {
  auditStore.myPagination.page = page - 1
  auditStore.fetchMyLogs(currentFilters.value)
}

function handleSizeChange(size: number) {
  auditStore.myPagination.size = size
  auditStore.myPagination.page = 0
  auditStore.fetchMyLogs(currentFilters.value)
}

function handleSortChange(sortProps: { prop: string; order: string | null }) {
  if (sortProps.order) {
    currentFilters.value.sort = sortProps.order === 'ascending' ? 'ASC' : 'DESC'
  } else {
    currentFilters.value.sort = 'DESC'
  }
  auditStore.myPagination.page = 0
  auditStore.fetchMyLogs(currentFilters.value)
}

onMounted(() => {
  auditStore.fetchMyLogs(currentFilters.value)
})
</script>

<template>
  <div class="my-activity-page">
    <h1 class="page-title">{{ t('audit.myActivityTitle') }}</h1>

    <AuditFilterBar
      :show-event-type="true"
      @search="handleSearch"
      @reset="handleReset"
    />

    <AuditTable
      :data="auditStore.myLogs"
      :total="auditStore.myPagination.total"
      :loading="auditStore.loading"
      :page="auditStore.myPagination.page"
      :page-size="auditStore.myPagination.size"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
      @sort-change="handleSortChange"
    />
  </div>
</template>

<style scoped>
.my-activity-page {
  padding: 32px 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-heading);
  line-height: 1.15;
  letter-spacing: 0;
  margin: 0 0 24px 0;
}
</style>
