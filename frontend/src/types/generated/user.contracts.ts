// =============================================================================
// AUTO-GENERATED — do not edit manually.
// Source: scripts/generate-contract.py
// Re-generate: python scripts/generate-contract.py --module user
// =============================================================================
//
// Request / Response TypeScript interfaces for the `user` module.
// These are derived from the Java @RequestBody and return types in the
// corresponding Spring Boot Controllers.
//
// Usage:
//   import type { UserCreateRequest, UserResponse } from '@/types/generated/user.contracts';
// =============================================================================

// GET /v1/auth/users/{userId}  (UserAdminController.getUser)
export interface UserListItemDto {
  userId: string;
  email: string;
  displayName: string;
  phone: string;
  enabled: boolean;
  locked: boolean;
  deleted: boolean;
  roleId: string;
  roleCode: string;
  roleName: string;
  deptId: number;
  deptName: string;
  mappingId: number;
  mappingEnabled: boolean;
}

// PUT /v1/auth/users/{userId}  (UserAdminController.updateUser)
export interface UpdateUserRequest {
  displayName: string;
  phone: string;
  roleId: string;
  deptId: number;
}

// GET /v1/auth/users/{userId}/tenant-roles  (UserAdminController.getUserTenantMappings)
export interface UserTenantMappingDto {
  mappingId: number;
  tenantId: string;
  tenantName: string;
  roleId: string;
  roleCode: string;
  roleName: string;
  deptId: number;
  deptName: string;
  enabled: boolean;
}

// POST /v1/auth/users/{userId}/tenant-roles  (UserAdminController.addTenantRole)
export interface AddTenantRoleRequest {
  tenantId: string;
  roleId: string;
  deptId: number;
}

// PUT /v1/auth/user/my  (UserSelfController.updateOwnProfile)
export interface UpdateOwnProfileRequest {
  displayName: string;
  phone: string;
  notifySmsFlag: boolean;
  notifyEmailFlag: boolean;
}
export interface UserEntity {
  userId: ;
  email: ;
  passwordHash: ;
  displayName: ;
  phone: ;
  enabled: ;
  locked: ;
  lockedAt: ;
  loginFailCount: ;
  isSuperAdmin: ;
  lastLoginAt: ;
  deleted: ;
  deletedAt: ;
  createTime: ;
  updateTime: ;
  notifyEmailFlag: ;
  notifySmsFlag: ;
  passwordChangedAt: ;
  forceChangePassword: ;
  authType: ;
  externalId: ;
}

// POST /v1/auth/user/change-password  (UserSelfController.changePassword)
export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}
