// =============================================================================
// AUTO-GENERATED — do not edit manually.
// Source: scripts/generate-contract.py
// Re-generate: python scripts/generate-contract.py --module tenant
// =============================================================================
//
// Request / Response TypeScript interfaces for the `tenant` module.
// These are derived from the Java @RequestBody and return types in the
// corresponding Spring Boot Controllers.
//
// Usage:
//   import type { UserCreateRequest, UserResponse } from '@/types/generated/tenant.contracts';
// =============================================================================

// PUT /v1/platform/tenants/{tenantId}  (TenantAdminController.updateTenant)
export interface UpdateTenantRequest {
  tenantName: string;
  deploymentMode: string;
}
export interface TenantDto {
  tenantId: string;
  tenantCode: string;
  tenantName: string;
  deploymentMode: string;
  enabled: boolean;
  createTime: string;
}
