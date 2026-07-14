// ── VMS API ──────────────────────────────────────────────────────────────

import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse, PageResponse } from '@/types/device'
import type {
  VmsServer, VmsServerRequest,
  VmsCamera, VmsCameraRequest,
  StreamCreateRequest, StreamCreateResponse,
  VmsStreamLog,
} from '@/types/vms'

// ── VMS Server ──────────────────────────────────────────────────────────

export const listVmsServers = () =>
  axiosIns.get<unknown, BaseResponse<VmsServer[]>>('/auth/vms/servers')

export const getVmsServer = (id: number) =>
  axiosIns.get<unknown, BaseResponse<VmsServer>>(`/auth/vms/servers/${id}`)

export const createVmsServer = (payload: VmsServerRequest) =>
  axiosIns.post<unknown, BaseResponse<VmsServer>>('/auth/vms/servers', payload)

export const updateVmsServer = (id: number, payload: VmsServerRequest) =>
  axiosIns.put<unknown, BaseResponse<VmsServer>>(`/auth/vms/servers/${id}`, payload)

export const deleteVmsServer = (id: number) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/auth/vms/servers/${id}`)

export const testVmsServerConnection = (id: number) =>
  axiosIns.post<unknown, BaseResponse<void>>(`/auth/vms/servers/${id}/test-connection`)

// ── Camera Mapping ──────────────────────────────────────────────────────

export const listVmsCameras = () =>
  axiosIns.get<unknown, BaseResponse<VmsCamera[]>>('/auth/vms/cameras')

export const getVmsCamera = (id: number) =>
  axiosIns.get<unknown, BaseResponse<VmsCamera>>(`/auth/vms/cameras/${id}`)

export const createVmsCamera = (payload: VmsCameraRequest) =>
  axiosIns.post<unknown, BaseResponse<VmsCamera>>('/auth/vms/cameras', payload)

export const updateVmsCamera = (id: number, payload: VmsCameraRequest) =>
  axiosIns.put<unknown, BaseResponse<VmsCamera>>(`/auth/vms/cameras/${id}`, payload)

export const deleteVmsCamera = (id: number) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/auth/vms/cameras/${id}`)

export const syncVmsCameras = (serverId: number) =>
  axiosIns.post<unknown, BaseResponse<unknown[]>>(`/auth/vms/cameras/sync/${serverId}`)

// ── Streaming ───────────────────────────────────────────────────────────

export const createStream = (cameraId: number, payload: StreamCreateRequest) =>
  axiosIns.post<unknown, BaseResponse<StreamCreateResponse>>(`/auth/vms/${cameraId}/stream`, payload)

export const stopStream = (sessionToken: string) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/auth/vms/stream/${sessionToken}`)

// ── Stream Logs ─────────────────────────────────────────────────────────

export const queryStreamLogs = (params: {
  userId?: number
  cameraId?: number
  streamType?: string
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<VmsStreamLog>>>('/auth/vms/stream-logs', { params })
