/**
 * [Phase 4 / 4.1.9 / ADR-002] Frontend types mirroring the backend
 * `com.taipei.iot.platform.impersonation.dto.*` DTOs.
 */

export interface CreateImpersonationRequest {
  tenantId: string
  reason: string
  /** Duration in minutes; backend enforces 1..60. */
  durationMinutes: number
}

export interface ImpersonationTokenResponse {
  accessToken: string
  sessionId: string
  targetTenantId: string
  /** ISO-8601 local date-time string from the backend (no zone). */
  expiresAt: string
  /** Always `"IMPERSONATION"`. */
  scope: string
}

/** Server-side session status — keep in sync with backend enum. */
export type ImpersonationStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED'

export interface ImpersonationSessionDto {
  id: string
  operatorUserId: string
  targetTenantId: string
  targetTenantName: string
  reason: string
  status: ImpersonationStatus | string
  startedAt: string
  expiresAt: string
  revokedAt: string | null
}
