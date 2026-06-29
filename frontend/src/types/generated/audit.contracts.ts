// =============================================================================
// AUTO-GENERATED — do not edit manually.
// Source: scripts/generate-contract.py
// Re-generate: python scripts/generate-contract.py --module audit
// =============================================================================
//
// Request / Response TypeScript interfaces for the `audit` module.
// These are derived from the Java @RequestBody and return types in the
// corresponding Spring Boot Controllers.
//
// Usage:
//   import type { UserCreateRequest, UserResponse } from '@/types/generated/audit.contracts';
// =============================================================================

// GET /v1/auth/audit/user/usage/history  (AuditController.getUserUsageHistory)
export interface PageResponse<T> {
  content: T[];
  total: number;
  page: number;
  size: number;
}
export interface UserEventLogDto {
  userEventLogPk: number;
  userId: string;
  username: string;
  userLabel: string;
  email: string;
  eventType: string;
  eventDesc: string;
  apiEndpoint: string;
  payload: string;
  errorCode: string;
  message: string;
  ipAddress: string;
  userAgent: string;
  executionTime: number;
  deptId: number;
  impersonatedBy: string;
  createTime: string;
}
