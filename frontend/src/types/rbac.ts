import type { BaseResponse } from '@/types/auth'

// ===== Role =====

export interface RoleDto {
  roleId: string
  code: string
  name: string
  description: string
  builtIn: boolean
  enabled: boolean
  dataScope: string
}

export interface CreateRoleRequest {
  code: string
  name: string
  description: string
  dataScope: string
}

export interface UpdateRoleRequest {
  name: string
  description: string
  enabled: boolean
  dataScope: string
}

export interface AssignRolePermissionsRequest {
  permissionIds: string[]
}

// ===== Permission =====

export interface PermissionDto {
  permissionId: string
  code: string
  name: string
  groupName: string
}

export interface RolePermissionListDto {
  roleId: string
  roleCode: string
  permissions: PermissionDto[]
}

// ===== Menu =====

export interface MenuDto {
  menuId: number
  parentId: number | null
  name: string
  menuType: string
  routeName: string | null
  routePath: string | null
  component: string | null
  permissionCode: string | null
  icon: string | null
  sortOrder: number
  visible: boolean
  keepAlive: boolean
  redirect: string | null
  scope: MenuScope
  children: MenuDto[]
}

export type MenuScope = 'PLATFORM' | 'TENANT' | 'PUBLIC'

export interface UserMenuDto {
  menuId: number
  parentId: number | null
  name: string
  menuType: string
  routeName: string | null
  routePath: string | null
  component: string | null
  icon: string | null
  sortOrder: number
  redirect: string | null
  scope: MenuScope
  children: UserMenuDto[]
}

// ===== Request =====

export interface CreateMenuRequest {
  parentId: number | null
  name: string
  menuType: string
  routeName: string | null
  routePath: string | null
  component: string | null
  permissionCode: string | null
  icon: string | null
  sortOrder: number
  visible: boolean
  keepAlive: boolean
  redirect: string | null
  scope: MenuScope
}

export interface UpdateMenuRequest extends CreateMenuRequest {
  menuId: number
}

// ===== Breadcrumb =====

export interface BreadcrumbItem {
  label: string
  path: string | null  // null = not clickable (DIRECTORY)
}

// Re-export for convenience
export type { BaseResponse }
