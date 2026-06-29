import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'
import type { TenantAuthConfigRequest, TenantAuthConfigResponse } from '@/types/authConfig'

/**
 * Tenant authentication-config API client.
 *
 * [Platform/Tenant Separation 2.1.5] Switched from the legacy
 * `/v1/auth/tenant-auth-config` routes (which resolved tenant from JWT) to the
 * canonical platform-scoped routes `/v1/platform/tenants/{tenantId}/auth-config[...]`.
 * Each call now requires the caller to pass the target `tenantId` explicitly.
 */

const base = (tenantId: string) =>
  `/platform/tenants/${encodeURIComponent(tenantId)}/auth-config`

/** Get the auth config for the given tenant */
export const getAuthConfig = (tenantId: string) =>
  axiosIns.get<unknown, BaseResponse<TenantAuthConfigResponse>>(base(tenantId))

/** Create or update auth config for the given tenant */
export const updateAuthConfig = (tenantId: string, payload: TenantAuthConfigRequest) =>
  axiosIns.put<unknown, BaseResponse<TenantAuthConfigResponse>>(base(tenantId), payload)

/** Delete auth config for the given tenant (revert to LOCAL) */
export const deleteAuthConfig = (tenantId: string) =>
  axiosIns.delete<unknown, BaseResponse<void>>(base(tenantId))

/** Test external IdP connection against the given tenant */
export const testAuthConnection = (tenantId: string, payload: TenantAuthConfigRequest) =>
  axiosIns.post<unknown, BaseResponse<boolean>>(`${base(tenantId)}/test-connection`, payload)
