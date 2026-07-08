import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/device'

// ── Device Template (Schema) ──────────────────────────────────────────
// 從 device 模組獨立為 schema 模組，提供設備資料格式定義。

export interface DeviceTemplateInfo {
  deviceType: string
  version: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

export const listDeviceTemplates = () =>
  axiosIns.get<unknown, BaseResponse<DeviceTemplateInfo[]>>('/auth/device-templates')

export const listDeviceTypeNames = () =>
  axiosIns.get<unknown, BaseResponse<string[]>>('/auth/device-templates/names')

export const getDeviceSchema = (deviceType: string) =>
  axiosIns.get<unknown, BaseResponse<Record<string, unknown>>>(`/auth/device-templates/${deviceType}/schema`)

export const getTelemetrySchema = (deviceType: string) =>
  axiosIns.get<unknown, BaseResponse<Record<string, unknown>>>(`/auth/device-templates/${deviceType}/schema/telemetry`)

export const updateDeviceSchema = (deviceType: string, schema: Record<string, unknown>) =>
  axiosIns.put<unknown, BaseResponse<Record<string, unknown>>>(`/auth/device-templates/${deviceType}/schema`, schema)

export const deleteDeviceTemplate = (deviceType: string) =>
  axiosIns.delete<unknown, BaseResponse<null>>(`/auth/device-templates/${deviceType}`)
