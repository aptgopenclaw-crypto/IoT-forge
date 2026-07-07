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

export interface WorkflowDefinitionItem {
  id: number
  code: string
  name: string
  version: number
  stepsJson: string
  enabled: boolean
  tenantId: string
  createdAt: string
  updatedAt: string
}

export interface WorkflowDefinitionRequest {
  code: string
  name: string
  stepsJson: string
  enabled: boolean
}

export const listWorkflowDefinitions = () =>
  axiosIns.get<unknown, BaseResponse<WorkflowDefinitionItem[]>>('/api/poc/workflow/definitions')

export const listWorkflowDefinitionsAdmin = () =>
  axiosIns.get<unknown, BaseResponse<WorkflowDefinitionItem[]>>('/auth/workflow/definitions')

export const createWorkflowDefinition = (payload: WorkflowDefinitionRequest) =>
  axiosIns.post<unknown, BaseResponse<WorkflowDefinitionItem>>('/auth/workflow/definitions', payload)

export const updateWorkflowDefinition = (id: number, payload: WorkflowDefinitionRequest) =>
  axiosIns.put<unknown, BaseResponse<WorkflowDefinitionItem>>(`/auth/workflow/definitions/${id}`, payload)

export const toggleWorkflowDefinitionEnabled = (id: number, enabled: boolean) =>
  axiosIns.patch<unknown, BaseResponse<void>>(`/auth/workflow/definitions/${id}/enabled`, null, { params: { enabled } })

export const deleteWorkflowDefinition = (id: number) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/auth/workflow/definitions/${id}`)
