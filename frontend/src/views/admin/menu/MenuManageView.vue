<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useMenuStore } from '@/stores/menuStore'
import { useAuthStore } from '@/stores/authStore'
import { deleteMenu, toggleMenuVisible } from '@/api/rbac'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Pencil, Trash2, Eye, EyeOff, LayoutList } from 'lucide-vue-next'
import MenuFormDialog from './MenuFormDialog.vue'
import type { MenuDto } from '@/types/rbac'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const menuStore = useMenuStore()
const authStore = useAuthStore()
const loading = ref(false)
const dialogVisible = ref(false)
const editingMenu = ref<MenuDto | null>(null)
const editParentId = ref<number | null>(null)

const treeData = computed(() => menuStore.menuTree)
const canWrite = computed(() => authStore.userInfo?.isSuperAdmin === true)

onMounted(() => {
  loadMenuTree()
})

async function loadMenuTree() {
  loading.value = true
  try {
    await menuStore.fetchMenuTree()
  } catch {
    ElMessage.error(t('menu.loadFailed'))
  } finally {
    loading.value = false
  }
}

function handleAddRoot() {
  editingMenu.value = null
  editParentId.value = null
  dialogVisible.value = true
}

function handleAddChild(parentMenu: MenuDto) {
  editingMenu.value = null
  editParentId.value = parentMenu.menuId
  dialogVisible.value = true
}

function handleEdit(menu: MenuDto) {
  editingMenu.value = menu
  editParentId.value = menu.parentId
  dialogVisible.value = true
}

async function handleToggleVisible(menu: MenuDto) {
  try {
    await toggleMenuVisible(menu.menuId, !menu.visible)
    ElMessage.success(menu.visible ? t('menu.hiddenSuccess') : t('menu.shownSuccess'))
    await loadMenuTree()
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    if (errorCode === '30005') {
      ElMessage.error(t('menu.notFound'))
    } else {
      ElMessage.error(t('common.operationFailed'))
    }
  }
}

async function handleDelete(menu: MenuDto) {
  try {
    await ElMessageBox.confirm(
      t('menu.deleteConfirm', { name: menu.name }),
      t('menu.deleteTitle'),
      {
        confirmButtonText: t('menu.confirmDelete'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await deleteMenu(menu.menuId)
    ElMessage.success(t('menu.deletedSuccess'))
    await loadMenuTree()
  } catch (err: unknown) {
    const error = err as { response?: { data?: { errorCode?: string } } }
    const errorCode = error?.response?.data?.errorCode
    if (errorCode === '30006') {
      ElMessage.error(t('menu.deleteHasChildren'))
    } else if (errorCode === '30005') {
      ElMessage.error(t('menu.notFound'))
    } else {
      ElMessage.error(t('menu.deleteFailed'))
    }
  }
}

function handleDialogClose() {
  dialogVisible.value = false
  editingMenu.value = null
  editParentId.value = null
}

async function handleDialogSaved() {
  handleDialogClose()
  await loadMenuTree()
}

function menuTypeLabel(type: string) {
  switch (type) {
    case 'DIRECTORY': return t('menu.typeDirectory')
    case 'PAGE': return t('menu.typePage')
    case 'BUTTON': return t('menu.typeButton')
    default: return type
  }
}

function menuTypeBadgeClass(type: string) {
  switch (type) {
    case 'DIRECTORY': return 'status-info'
    case 'PAGE': return 'status-success'
    case 'BUTTON': return 'status-neutral'
    default: return 'status-neutral'
  }
}

function getVisibleType(visible: boolean) {
  return visible ? 'success' : 'danger'
}

function getVisibleLabel(visible: boolean) {
  return visible ? t('menu.visibleYes') : t('menu.visibleNo')
}
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <!-- Header -->
      <div class="page-header">
        <div class="header-left">
          <LayoutList :size="24" class="header-icon" />
          <div>
            <h1 class="page-title">{{ t('menu.title') }}</h1>
            <p class="page-subtitle">{{ t('menu.subtitle') }}</p>
          </div>
        </div>
        <el-button v-if="canWrite" class="add-btn" @click="handleAddRoot">
          <Plus :size="14" style="margin-right: 6px" />
          {{ t('menu.addRootBtn') }}
        </el-button>
      </div>

      <!-- Tree Table -->
      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="menuId"
        :tree-props="{ children: 'children' }"
        :default-expand-all="true"
        class="menu-table"
      >
        <el-table-column prop="name" :label="t('menu.colName')" min-width="220">
          <template #default="{ row }">
            <span class="menu-name">{{ row.name }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="menuType" :label="t('menu.colType')" width="100" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="menuTypeBadgeClass(row.menuType)">
              {{ menuTypeLabel(row.menuType) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="routePath" :label="t('menu.colRoutePath')" min-width="160">
          <template #default="{ row }">
            <span class="mono-text">{{ row.routePath ?? '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="permissionCode" :label="t('menu.colPermCode')" min-width="150">
          <template #default="{ row }">
            <span v-if="row.permissionCode" class="perm-badge">{{ row.permissionCode }}</span>
            <span v-else class="mono-text">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="visible" :label="t('menu.colVisible')" width="90" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="'status-' + getVisibleType(row.visible)">
              {{ getVisibleLabel(row.visible) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="sortOrder" :label="t('menu.colSort')" width="80" align="center" />

        <el-table-column :label="t('menu.colActions')" width="200" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <template v-if="canWrite">
                <el-button class="action-btn action-add" size="small" :title="t('menu.addChildTooltip')" @click="handleAddChild(row)">
                  <Plus :size="14" />
                </el-button>
                <el-button class="action-btn action-edit" size="small" :title="t('menu.editTooltip')" @click="handleEdit(row)">
                  <Pencil :size="14" />
                </el-button>
                <el-button class="action-btn action-visible" size="small" :title="row.visible ? t('menu.hideTooltip') : t('menu.showTooltip')" @click="handleToggleVisible(row)">
                  <Eye v-if="row.visible" :size="14" />
                  <EyeOff v-else :size="14" />
                </el-button>
                <el-button class="action-btn action-delete" size="small" :title="t('menu.deleteTooltip')" @click="handleDelete(row)">
                  <Trash2 :size="14" />
                </el-button>
              </template>
              <span v-else class="read-only-hint">{{ t('common.readOnly') }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <MenuFormDialog
      :visible="dialogVisible"
      :menu="editingMenu"
      :parent-id="editParentId"
      :menu-tree="treeData"
      @close="handleDialogClose"
      @saved="handleDialogSaved"
    />
  </div>
</template>

<style scoped>
.page-container {
  padding: 32px 24px;
  min-height: 100vh;
  background-color: var(--bg-base);
}

.page-card {
  background-color: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 24px;
  box-shadow: var(--shadow-card);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  color: #55b3ff;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: 0.2px;
  color: var(--text-heading);
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.6;
  letter-spacing: 0.2px;
  color: var(--text-secondary);
  margin: 0;
}

.add-btn {
  background: transparent;
  color: var(--text-primary);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
  display: flex;
  align-items: center;
  box-shadow: rgba(0, 0, 0, 0.03) 0px 7px 3px;
}

.add-btn:hover {
  opacity: 0.6;
}

/* Tree Table */
.menu-table {
  --el-table-bg-color: var(--bg-surface);
  --el-table-tr-bg-color: var(--bg-surface);
  --el-table-header-bg-color: var(--bg-surface);
  --el-table-header-text-color: var(--text-secondary);
  --el-table-text-color: var(--text-primary);
  --el-table-border-color: var(--bg-active);
  --el-table-row-hover-bg-color: var(--bg-hover-subtle);
  --el-fill-color-lighter: var(--bg-surface);
}

.menu-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: 0.2px;
}

.mono-text {
  font-family: 'GeistMono', 'JetBrains Mono', monospace;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-muted);
  letter-spacing: 0.2px;
}

.perm-badge {
  font-family: 'GeistMono', 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(255, 99, 99, 0.15);
  color: #FF6363;
}

/* Status Badge */
.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.status-success {
  background: rgba(95, 201, 146, 0.15);
  color: #5fc992;
}

.status-danger {
  background: rgba(255, 99, 99, 0.15);
  color: #FF6363;
}

.status-info {
  background: rgba(85, 179, 255, 0.15);
  color: #55b3ff;
}

.status-neutral {
  background: rgba(156, 156, 157, 0.1);
  color: var(--text-secondary);
}

/* Action Buttons */
.action-btns {
  display: flex;
  gap: 6px;
  justify-content: center;
}

.read-only-hint {
  font-size: 12px;
  color: var(--text-muted);
  font-style: italic;
}

.action-btn {
  padding: 4px 8px;
  min-width: auto;
  background: transparent;
  border-radius: 6px;
}

.action-add {
  color: #55b3ff;
  border: 1px solid rgba(85, 179, 255, 0.2);
}

.action-add:hover {
  background: rgba(85, 179, 255, 0.15);
  color: #55b3ff;
}

.action-edit {
  color: #ffbc33;
  border: 1px solid rgba(255, 188, 51, 0.2);
}

.action-edit:hover {
  background: rgba(255, 188, 51, 0.15);
  color: #ffbc33;
}

.action-visible {
  color: #5fc992;
  border: 1px solid rgba(95, 201, 146, 0.2);
}

.action-visible:hover {
  background: rgba(95, 201, 146, 0.15);
  color: #5fc992;
}

.action-delete {
  color: #FF6363;
  border: 1px solid rgba(255, 99, 99, 0.2);
}

.action-delete:hover {
  background: rgba(255, 99, 99, 0.15);
  color: #FF6363;
}

/* Element Plus overrides */
:deep(.el-table__expand-icon) {
  color: var(--text-secondary);
}
</style>
