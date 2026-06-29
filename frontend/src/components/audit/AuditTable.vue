<script setup lang="ts">
import { computed } from 'vue'
import { formatDateTime } from '@/utils/datetime'
import type { UserEventLogDto } from '@/types/audit'
import PayloadDetail from '@/components/audit/PayloadDetail.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  data: UserEventLogDto[]
  total: number
  loading: boolean
  page: number
  pageSize: number
}>()

const emit = defineEmits<{
  pageChange: [page: number]
  sizeChange: [size: number]
  sortChange: [props: { prop: string; order: string | null }]
}>()

const pageSizes = [20, 50, 100]

const currentPageDisplay = computed(() => props.page + 1)

function handleSortChange(sortProps: { prop: string; order: string | null }) {
  emit('sortChange', sortProps)
}

function handleCurrentChange(val: number) {
  emit('pageChange', val)
}

function handleSizeChange(val: number) {
  emit('sizeChange', val)
}
</script>

<template>
  <div class="audit-table-wrapper">
    <el-table
      :data="data"
      v-loading="loading"
      row-key="userEventLogPk"
      class="audit-table"
      @sort-change="handleSortChange"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <PayloadDetail :payload="row.payload" />
        </template>
      </el-table-column>
      <el-table-column prop="username" :label="t('audit.colUsername')" min-width="140" show-overflow-tooltip />
      <el-table-column prop="userLabel" :label="t('audit.colUser')" min-width="100" show-overflow-tooltip />
      <el-table-column prop="eventType" :label="t('audit.colEventType')" min-width="120" show-overflow-tooltip />
      <el-table-column prop="eventDesc" :label="t('audit.colEventDesc')" min-width="100" show-overflow-tooltip />
      <el-table-column prop="apiEndpoint" :label="t('audit.colApi')" min-width="180" show-overflow-tooltip />
      <el-table-column prop="errorCode" :label="t('audit.colResultCode')" width="90" align="center">
        <template #default="{ row }">
          <span :class="row.errorCode === '00000' ? 'code-success' : 'code-error'">
            {{ row.errorCode }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" :label="t('audit.colIp')" width="130" show-overflow-tooltip />
      <el-table-column prop="impersonatedBy" :label="t('audit.colImpersonator')" width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.impersonatedBy">
            <el-tag type="warning" size="small" effect="dark">{{ t('audit.impersonatedBadge') }}</el-tag>
            <span style="margin-left:6px">{{ row.impersonatedBy }}</span>
          </span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="executionTime" :label="t('audit.colExecTime')" width="90" align="right" sortable="custom" />
      <el-table-column prop="createTime" :label="t('audit.colTime')" min-width="160" sortable="custom">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        :current-page="currentPageDisplay"
        :page-size="pageSize"
        :page-sizes="pageSizes"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
    </div>

    <div v-if="!loading && data.length === 0" class="empty-state">
      {{ t('audit.tableEmpty') }}
    </div>
  </div>
</template>

<style scoped>
.audit-table-wrapper {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 20px 24px;
}

.code-success {
  color: #5fc992;
  font-family: 'GeistMono', 'JetBrains Mono', monospace;
  font-size: 13px;
}

.code-error {
  color: #FF6363;
  font-family: 'GeistMono', 'JetBrains Mono', monospace;
  font-size: 13px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.empty-state {
  text-align: center;
  padding: 40px 0;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
}

:deep(.el-table) {
  --el-table-bg-color: var(--bg-surface);
  --el-table-tr-bg-color: var(--bg-surface);
  --el-table-header-bg-color: var(--bg-surface);
  --el-table-row-hover-bg-color: var(--bg-hover-subtle);
  --el-table-border-color: var(--bg-active);
  --el-table-text-color: var(--text-primary);
  --el-table-header-text-color: var(--text-secondary);
}

:deep(.el-table th.el-table__cell) {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

:deep(.el-table td.el-table__cell) {
  font-size: 14px;
  font-weight: 500;
  border-bottom: 1px solid var(--bg-active);
}

:deep(.el-pagination) {
  --el-pagination-bg-color: var(--bg-surface);
  --el-pagination-text-color: var(--text-label);
  --el-pagination-button-bg-color: var(--bg-surface);
  --el-pagination-hover-color: #55b3ff;
}
</style>
