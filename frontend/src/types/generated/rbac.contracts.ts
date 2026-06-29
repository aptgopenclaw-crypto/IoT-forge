// =============================================================================
// AUTO-GENERATED — do not edit manually.
// Source: scripts/generate-contract.py
// Re-generate: python scripts/generate-contract.py --module rbac
// =============================================================================
//
// Request / Response TypeScript interfaces for the `rbac` module.
// These are derived from the Java @RequestBody and return types in the
// corresponding Spring Boot Controllers.
//
// Usage:
//   import type { UserCreateRequest, UserResponse } from '@/types/generated/rbac.contracts';
// =============================================================================

// GET /v1/auth/menus/tree  (MenuController.getMenuTree)
export interface MenuDto {
  menuId: number;
  parentId: number;
  name: string;
  menuType: string;
  routeName: string;
  routePath: string;
  component: string;
  permissionCode: string;
  icon: string;
  sortOrder: number;
  visible: boolean;
  keepAlive: boolean;
  redirect: string;
  scope: string;
  children: MenuDto[];
}

// GET /v1/auth/menus/my  (MenuController.getMyMenus)
export interface UserMenuDto {
  menuId: number;
  parentId: number;
  name: string;
  menuType: string;
  routeName: string;
  routePath: string;
  component: string;
  icon: string;
  sortOrder: number;
  redirect: string;
  scope: string;
  children: UserMenuDto[];
}

// GET /v1/auth/roles/assignable  (RoleController.listAssignableRoles)
export interface RoleDto {
  roleId: string;
  code: string;
  name: string;
  description: string;
  builtIn: boolean;
  enabled: boolean;
  dataScope: string;
}

// PUT /v1/auth/roles/{roleId}  (RoleController.updateRole)
export interface UpdateRoleRequest {
  name: string;
  description: string;
  enabled: boolean;
  dataScope: string;
}

// GET /v1/auth/roles/{roleId}/permissions  (RoleController.getRolePermissions)
export interface RolePermissionListDto {
  roleId: string;
  roleCode: string;
  permissions: PermissionDto[];
}

// PUT /v1/auth/roles/{roleId}/permissions  (RoleController.assignPermissions)
export interface AssignRolePermissionsRequest {
  permissionIds: string[];
}
