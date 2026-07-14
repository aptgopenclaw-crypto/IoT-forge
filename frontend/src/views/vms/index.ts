import type { RouteRecordRaw } from 'vue-router'

export const vmsRoutes: RouteRecordRaw[] = [
  {
    path: '/vms/live',
    name: 'VmsLive',
    component: () => import('@/views/vms/VmsLiveView.vue'),
    meta: { title: 'vms.live' },
  },
  {
    path: '/vms/playback',
    name: 'VmsPlayback',
    component: () => import('@/views/vms/VmsPlaybackView.vue'),
    meta: { title: 'vms.playback' },
  },
  {
    path: '/vms/servers',
    name: 'VmsServers',
    component: () => import('@/views/vms/VmsServerManageView.vue'),
    meta: { title: 'vms.servers' },
  },
  {
    path: '/vms/cameras',
    name: 'VmsCameras',
    component: () => import('@/views/vms/VmsCameraManageView.vue'),
    meta: { title: 'vms.cameras' },
  },
  {
    path: '/vms/stream-logs',
    name: 'VmsStreamLogs',
    component: () => import('@/views/vms/VmsStreamLogView.vue'),
    meta: { title: 'vms.streamLogs' },
  },
]
