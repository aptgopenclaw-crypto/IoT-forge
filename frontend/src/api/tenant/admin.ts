import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'
import type { TenantDto, CreateTenantRequest, UpdateTenantRequest } from '@/types/tenant'

// [Platform/Tenant Separation 2.1.5] Migrated from `/v1/admin/tenants/**`
// to the canonical `/v1/platform/tenants/**` path.

export const listTenants = () =>
  axiosIns.get<unknown, BaseResponse<TenantDto[]>>('/platform/tenants')

export const createTenant = (payload: CreateTenantRequest) =>
  axiosIns.post<unknown, BaseResponse<TenantDto>>('/platform/tenants', payload)

export const updateTenant = (tenantId: string, payload: UpdateTenantRequest) =>
  axiosIns.put<unknown, BaseResponse<TenantDto>>(`/platform/tenants/${encodeURIComponent(tenantId)}`, payload)

export const toggleTenantEnabled = (tenantId: string, enabled: boolean) =>
  axiosIns.patch<unknown, BaseResponse<void>>(
    `/platform/tenants/${encodeURIComponent(tenantId)}/enabled`,
    null,
    { params: { enabled } },
  )
