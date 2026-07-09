import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/device'
import type {
  CameraLiveResponse,
  CameraPlaybackResponse,
  PtzCommand,
  VmsCamera,
  VmsCameraEvent,
  VmsCameraRequest,
  VmsServer,
  VmsServerRequest,
} from '@/types/vms'

// ── Servers ────────────────────────────────────────────────────────────────

export const listServers = () =>
  axiosIns.get<unknown, BaseResponse<VmsServer[]>>('/auth/vms/servers')

export const getServer = (id: number) =>
  axiosIns.get<unknown, BaseResponse<VmsServer>>(`/auth/vms/servers/${id}`)

export const createServer = (data: VmsServerRequest) =>
  axiosIns.post<unknown, BaseResponse<VmsServer>>('/auth/vms/servers', data)

export const updateServer = (id: number, data: VmsServerRequest) =>
  axiosIns.put<unknown, BaseResponse<VmsServer>>(`/auth/vms/servers/${id}`, data)

export const deleteServer = (id: number) =>
  axiosIns.delete<unknown, BaseResponse<null>>(`/auth/vms/servers/${id}`)

export const testServerConnection = (id: number) =>
  axiosIns.post<unknown, BaseResponse<VmsServer>>(`/auth/vms/servers/${id}/test-connection`)

// ── Cameras ────────────────────────────────────────────────────────────────

export const listCameras = (params?: { serverId?: number }) =>
  axiosIns.get<unknown, BaseResponse<VmsCamera[]>>('/auth/vms/cameras', { params })

export const listCamerasAdmin = (params?: { serverId?: number }) =>
  axiosIns.get<unknown, BaseResponse<VmsCamera[]>>('/auth/vms/cameras/admin', { params })

export const createCamera = (data: VmsCameraRequest) =>
  axiosIns.post<unknown, BaseResponse<VmsCamera>>('/auth/vms/cameras', data)

export const deleteCamera = (id: number) =>
  axiosIns.delete<unknown, BaseResponse<null>>(`/auth/vms/cameras/${id}`)

// ── Stream ─────────────────────────────────────────────────────────────────

export const getLiveStream = (cameraId: number) =>
  axiosIns.get<unknown, BaseResponse<CameraLiveResponse>>(`/auth/vms/cameras/${cameraId}/live`)

export const getPlayback = (cameraId: number, startTime: string, endTime: string) =>
  axiosIns.get<unknown, BaseResponse<CameraPlaybackResponse>>(
    `/auth/vms/cameras/${cameraId}/playback`,
    { params: { startTime, endTime } },
  )

// ── PTZ ────────────────────────────────────────────────────────────────────

export const controlPtz = (cameraId: number, command: PtzCommand) =>
  axiosIns.post<unknown, BaseResponse<null>>(`/auth/vms/cameras/${cameraId}/ptz`, command)

// ── Events ─────────────────────────────────────────────────────────────────

export const listCameraEvents = (
  cameraId: number,
  params?: { startTime?: string; endTime?: string; page?: number },
) =>
  axiosIns.get<unknown, BaseResponse<{ content: VmsCameraEvent[] }>>(
    `/auth/vms/cameras/${cameraId}/events`,
    { params },
  )
