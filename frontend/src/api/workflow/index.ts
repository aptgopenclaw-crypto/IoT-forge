import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'

export interface DelegateSetRequest {
  delegateTo: string
  businessType: string
  effectiveFrom: string
  effectiveTo: string
}

export interface DelegateSettingDto {
  id: number
  delegateFor: string
  delegateTo: string
  businessType: string | null
  effectiveFrom: string
  effectiveTo: string
  createdAt: string
}

export const setDelegate = (payload: DelegateSetRequest) =>
  axiosIns.post<unknown, BaseResponse<DelegateSettingDto>>('/api/poc/workflow/delegate', payload)

export const listMyDelegates = () =>
  axiosIns.get<unknown, BaseResponse<DelegateSettingDto[]>>('/api/poc/workflow/delegate/my')
