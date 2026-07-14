// ── VMS Types ──────────────────────────────────────────────────────────────

import type { BaseResponse, PageResponse } from '@/types/device'

export type { BaseResponse, PageResponse }

// ── VMS Server ─────────────────────────────────────────────────────────────

export type VmsType = 'NX_WITNESS' | 'MILESTONE' | 'AXXON'
export type VmsAuthType = 'BASIC' | 'TOKEN' | 'CERT'
export type CameraStatus = 'ONLINE' | 'OFFLINE' | 'ERROR'

export interface VmsServer {
  id: number
  name: string
  vmsType: VmsType
  baseUrl: string
  authType: VmsAuthType
  authUsername?: string
  isActive: boolean
  createdAt: string
}

export interface VmsServerRequest {
  name: string
  vmsType: VmsType
  baseUrl: string
  authType?: VmsAuthType
  authUsername?: string
  authPassword?: string
  apiToken?: string
  isActive?: boolean
}

// ── Camera Mapping ─────────────────────────────────────────────────────────

export interface VmsCamera {
  id: number
  serverId: number
  serverName?: string
  vmsCameraId: string
  displayName?: string
  deptId?: number
  deptName?: string
  status: CameraStatus
  rtspUrl?: string
  createdAt?: string
}

export interface VmsCameraRequest {
  serverId: number
  vmsCameraId: string
  displayName?: string
  deptId?: number
  rtspUrl?: string
}

// ── Stream ─────────────────────────────────────────────────────────────────

export interface StreamCreateRequest {
  type: 'live' | 'playback'
  startTime?: string
  endTime?: string
}

export interface StreamCreateResponse {
  sessionToken: string
  expiresAt: string
  cameraId: number
  streamType: string
}

// ── Stream Log ─────────────────────────────────────────────────────────────

export interface VmsStreamLog {
  id: number
  userId: number
  userName?: string
  cameraId: number
  cameraName?: string
  streamType: string
  startedAt: string
  endedAt?: string
  durationSeconds?: number
  playbackStartTime?: string
  playbackEndTime?: string
}
