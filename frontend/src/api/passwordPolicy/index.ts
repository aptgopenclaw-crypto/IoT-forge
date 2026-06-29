import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'
import type {
  ForceChangePasswordRequest,
  PasswordPolicyDto,
  UpdatePasswordPolicyRequest,
} from '@/types/passwordPolicy'

// ── Tenant (TENANT_ADMIN / SUPER_ADMIN) ─────────────────────────────────────

/** Effective merged policy (tenant override ∪ platform default). */
export const getEffectivePolicy = () =>
  axiosIns.get<unknown, BaseResponse<PasswordPolicyDto>>('/auth/password-policy')

/** Raw tenant-only overrides (for "is this key customised?" indicator). */
export const getTenantOverrides = () =>
  axiosIns.get<unknown, BaseResponse<Record<string, string>>>('/auth/password-policy/tenant')

export const updateTenantOverride = (payload: UpdatePasswordPolicyRequest) =>
  axiosIns.put<unknown, BaseResponse<void>>('/auth/password-policy/tenant', payload)

export const deleteTenantOverride = (key: string) =>
  axiosIns.delete<unknown, BaseResponse<void>>(
    `/auth/password-policy/tenant/${encodeURIComponent(key)}`,
  )

// ── Platform (SUPER_ADMIN) ──────────────────────────────────────────────────

export const getPlatformDefaults = () =>
  axiosIns.get<unknown, BaseResponse<Record<string, string>>>('/platform/password-policy')

export const updatePlatformDefault = (payload: UpdatePasswordPolicyRequest) =>
  axiosIns.put<unknown, BaseResponse<void>>('/platform/password-policy', payload)

// ── Public describe (no auth) ───────────────────────────────────────────────

/**
 * Fetch the rule list for the login/reset/change-password pages. When `tenantId`
 * is omitted the backend returns the platform default — appropriate for login
 * where the tenant isn't known until after authentication.
 */
export const describePolicy = (tenantId?: string) =>
  axiosIns.get<unknown, BaseResponse<PasswordPolicyDto>>('/noauth/password-policy/describe', {
    params: tenantId ? { tenantId } : undefined,
  })

// ── Force-change-password (Phase 3) ─────────────────────────────────────────

/**
 * Submit the new password using the short-lived `password_change` temporary
 * token returned by the login endpoint when `passwordChangeRequired = true`.
 * The token is sent via Authorization header — pass it through the {@link
 * axiosIns} `Authorization` override below.
 */
export const forceChangePassword = (
  passwordChangeToken: string,
  payload: ForceChangePasswordRequest,
) =>
  axiosIns.post<unknown, BaseResponse<import('@/types/auth').LoginResult>>(
    '/noauth/user/force-change-password',
    payload,
    {
      headers: {
        Authorization: `Bearer ${passwordChangeToken}`,
      },
    },
  )
