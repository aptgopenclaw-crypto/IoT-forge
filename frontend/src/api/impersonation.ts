import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'
import type {
  CreateImpersonationRequest,
  ImpersonationSessionDto,
  ImpersonationTokenResponse,
} from '@/types/impersonation'

/**
 * [Phase 4 / 4.1.6 + 4.1.9 / ADR-002] Platform impersonation API client.
 *
 * Backend: `com.taipei.iot.platform.impersonation.controller.PlatformImpersonationController`
 * (mounted at `/v1/platform/impersonations`, gated by
 * `@PreAuthorize("hasAuthority('PLATFORM_IMPERSONATE')")`).
 */

export const createImpersonation = (payload: CreateImpersonationRequest) =>
  axiosIns.post<unknown, BaseResponse<ImpersonationTokenResponse>>(
    '/platform/impersonations',
    payload,
  )

export const revokeImpersonation = (sessionId: string) =>
  axiosIns.delete<unknown, BaseResponse<void>>(
    `/platform/impersonations/${encodeURIComponent(sessionId)}`,
  )

export const listImpersonations = (status?: string) =>
  axiosIns.get<unknown, BaseResponse<ImpersonationSessionDto[]>>(
    '/platform/impersonations',
    status ? { params: { status } } : undefined,
  )
