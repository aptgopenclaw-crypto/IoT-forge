import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'
import type {
  AssetTransferApplicationDto,
  AssetTransferCreateRequest,
  AssetTransferActionRequest,
  AssetTransferRejectRequest,
  RejectTargetOption,
  WorkflowStepLogDto,
  WorkflowSlaDto,
} from '@/types/assetTransfer'

export const createApplication = (req: AssetTransferCreateRequest) =>
  axiosIns.post<unknown, BaseResponse<AssetTransferApplicationDto>>(
    '/auth/asset-transfer/create',
    req,
  )

export const createAndSubmitApplication = (req: AssetTransferCreateRequest) =>
  axiosIns.post<unknown, BaseResponse<AssetTransferApplicationDto>>(
    '/auth/asset-transfer/create-and-submit',
    req,
  )

export const submitApplication = (id: number) =>
  axiosIns.post<unknown, BaseResponse<AssetTransferApplicationDto>>(
    `/auth/asset-transfer/submit/${id}`,
  )

export const approveApplication = (id: number, req: AssetTransferActionRequest) =>
  axiosIns.post<unknown, BaseResponse<AssetTransferApplicationDto>>(
    `/auth/asset-transfer/approve/${id}`,
    req,
  )

export const rejectApplication = (id: number, req: AssetTransferRejectRequest) =>
  axiosIns.post<unknown, BaseResponse<AssetTransferApplicationDto>>(
    `/auth/asset-transfer/reject/${id}`,
    req,
  )

export const resubmitApplication = (id: number, req: AssetTransferActionRequest) =>
  axiosIns.post<unknown, BaseResponse<AssetTransferApplicationDto>>(
    `/auth/asset-transfer/resubmit/${id}`,
    req,
  )

export const getApplication = (id: number) =>
  axiosIns.get<unknown, BaseResponse<AssetTransferApplicationDto>>(`/auth/asset-transfer/${id}`)

export const getRejectTargets = (id: number) =>
  axiosIns.get<unknown, BaseResponse<RejectTargetOption[]>>(`/auth/asset-transfer/${id}/reject-targets`)

export const getMyApplications = () =>
  axiosIns.get<unknown, BaseResponse<AssetTransferApplicationDto[]>>('/auth/asset-transfer/my')

export const getPendingTasks = () =>
  axiosIns.get<unknown, BaseResponse<AssetTransferApplicationDto[]>>(
    '/auth/asset-transfer/pending',
  )

export const cancelApplication = (id: number, req: AssetTransferActionRequest) =>
  axiosIns.post<unknown, BaseResponse<AssetTransferApplicationDto>>(
    `/auth/asset-transfer/cancel/${id}`,
    req,
  )

export const getApplicationHistory = (id: number) =>
  axiosIns.get<unknown, BaseResponse<WorkflowStepLogDto[]>>(`/auth/asset-transfer/${id}/history`)

export const getApplicationSla = (id: number) =>
  axiosIns.get<unknown, BaseResponse<WorkflowSlaDto | null>>(`/auth/asset-transfer/${id}/sla`)
