import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse, PageResponse } from '@/types/device'
import type {
  TelemetryPointResponse,
  TelemetryLatestResponse,
  TelemetryFieldStats,
} from '@/types/telemetry'

// ── Telemetry Query ────────────────────────────────────────────────────────

export const getTelemetryHistory = (
  deviceId: number,
  params: {
    from?: string
    to?: string
    page?: number
    size?: number
  },
) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<TelemetryPointResponse>>>(
    `/auth/telemetry/devices/${deviceId}/history`,
    { params },
  )

export const getTelemetryLatest = (deviceId: number) =>
  axiosIns.get<unknown, BaseResponse<TelemetryLatestResponse>>(
    `/auth/telemetry/devices/${deviceId}/latest`,
  )

export const getTelemetryStats = (
  deviceId: number,
  params: { from?: string; to?: string; fields?: string[] },
) =>
  axiosIns.get<unknown, BaseResponse<TelemetryFieldStats[]>>(
    `/auth/telemetry/devices/${deviceId}/stats`,
    { params },
  )
