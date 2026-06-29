import type { RouteRecordRaw } from 'vue-router'

export const publicRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
    meta: { isPublic: true },
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/user/ProfileView.vue'),
    meta: { isPublic: true },
  },
  {
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('@/views/user/ChangePasswordView.vue'),
    meta: { isPublic: true },
  },
  {
    path: '/my/activity',
    name: 'MyActivity',
    component: () => import('@/views/user/MyActivityView.vue'),
    meta: { isPublic: true, breadcrumb: '我的操作記錄' },
  },
  {
    path: '/announcements',
    name: 'Announcements',
    component: () => import('@/views/announcement/AnnouncementListView.vue'),
    meta: { isPublic: true, breadcrumb: '公告欄' },
  },
  {
    path: '/asset-transfer/create',
    name: 'AssetTransferCreate',
    component: () => import('@/views/assetTransfer/AssetTransferCreateView.vue'),
    meta: { isPublic: true, breadcrumb: '新增資產異動申請' },
  },
  {
    path: '/asset-transfer/pending',
    name: 'AssetTransferPending',
    component: () => import('@/views/assetTransfer/AssetTransferPendingView.vue'),
    meta: { isPublic: true, breadcrumb: '待審案件' },
  },
  {
    path: '/asset-transfer/my',
    name: 'AssetTransferMy',
    component: () => import('@/views/assetTransfer/AssetTransferPendingView.vue'),
    meta: { isPublic: true, breadcrumb: '我的申請', mode: 'my' },
  },
  {
    path: '/asset-transfer/:id',
    name: 'AssetTransferDetail',
    component: () => import('@/views/assetTransfer/AssetTransferDetailView.vue'),
    meta: { isPublic: true, breadcrumb: '申請詳情' },
    props: true,
  },
  {
    path: '/workflow-delegate/assign',
    name: 'WorkflowDelegateAssign',
    component: () => import('@/views/workflowDelegate/WorkflowDelegateView.vue'),
    meta: { isPublic: true, breadcrumb: '指派代理' },
  },
]
