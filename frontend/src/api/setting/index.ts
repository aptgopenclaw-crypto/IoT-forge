import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'

export interface SystemSettingDto {
  settingKey: string
  settingValue: string
  description: string
}

export const listSettings = () =>
  axiosIns.get<unknown, BaseResponse<SystemSettingDto[]>>('/auth/system-settings')

export const updateSetting = (key: string, value: string) =>
  axiosIns.put<unknown, BaseResponse<SystemSettingDto>>(`/auth/system-settings/${key}`, null, {
    params: { value },
  })

export const getIdleTimeout = () =>
  axiosIns.get<unknown, BaseResponse<number>>('/auth/system-settings/idle-timeout')

export const updateIdleTimeout = (minutes: number) =>
  axiosIns.put<unknown, BaseResponse<number>>('/auth/system-settings/idle-timeout', null, {
    params: { minutes },
  })

export const idleLogout = () =>
  axiosIns.post<unknown, BaseResponse<void>>('/auth/idle-logout')
