// ── VMS Types ──────────────────────────────────────────────────────────────

import type { BaseResponse, PageResponse } from '@/types/device'

export type { BaseResponse, PageResponse }

// ── VMS Server ─────────────────────────────────────────────────────────────

export type VmsType = 'NX_WITNESS' | 'MILESTONE' | 'AXXON'
export type VmsAuthType = 'BASIC' | 'TOKEN' | 'CERT'

export interface VmsServer {
  id: number
  name: string
  vmsType: VmsType
  baseUrl: string
  authType: VmsAuthType
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
}

// ── Camera ─────────────────────────────────────────────────────────────────

export type CameraStatus = 'ONLINE' | 'OFFLINE' | 'ERROR'

export interface VmsCamera {
  id: number
  serverId: number
  vmsCameraId: string
  displayName: string
  deviceId?: number
  deptId?: number
  status: CameraStatus
  rtspUrl?: string
}

export interface VmsCameraRequest {
  serverId: number
  vmsCameraId: string
  displayName?: string
  rtspUrl?: string
  deviceId?: number
  deptId?: number
}

// ── Stream ─────────────────────────────────────────────────────────────────

export interface CameraLiveResponse {
  cameraId: number
  displayName: string
  playUrl: string
  expiresAt: string
  status: CameraStatus
}

export interface CameraPlaybackResponse {
  cameraId: number
  displayName: string
  playUrl: string
  startTime: string
  endTime: string
  status: CameraStatus
}

// ── PTZ ────────────────────────────────────────────────────────────────────

export interface PtzCommand {
  direction: 'LEFT' | 'RIGHT' | 'UP' | 'DOWN' | 'ZOOM_IN' | 'ZOOM_OUT'
  speed?: number
  presetPoint?: number
}

// ── Event ──────────────────────────────────────────────────────────────────

export type VmsEventType =
  | 'MOTION_DETECT'
  | 'CAMERA_OFFLINE'
  | 'CAMERA_ONLINE'
  | 'VIDEO_LOST'
  | 'RECORDING_STARTED'
  | 'RECORDING_STOPPED'
  | 'UNKNOWN'

export interface VmsCameraEvent {
  id: number
  cameraId: number
  eventType: VmsEventType
  payload?: Record<string, unknown>
  occurredAt: string
}
