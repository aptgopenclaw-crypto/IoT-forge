import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'
import type {
  RoleDto,
  RolePermissionListDto,
  PermissionDto,
  MenuDto,
  UserMenuDto,
  CreateMenuRequest,
  UpdateMenuRequest,
  CreateRoleRequest,
  UpdateRoleRequest,
  AssignRolePermissionsRequest,
} from '@/types/rbac'

// ===== Role =====

export const listRoles = () =>
  axiosIns.get<unknown, BaseResponse<RoleDto[]>>('/auth/roles')

export const listAssignableRoles = () =>
  axiosIns.get<unknown, BaseResponse<RoleDto[]>>('/auth/roles/assignable')

export const createRole = (payload: CreateRoleRequest) =>
  axiosIns.post<unknown, BaseResponse<RoleDto>>('/auth/roles', payload)

export const updateRole = (roleId: string, payload: UpdateRoleRequest) =>
  axiosIns.put<unknown, BaseResponse<RoleDto>>(`/auth/roles/${encodeURIComponent(roleId)}`, payload)

export const toggleRoleEnabled = (roleId: string, enabled: boolean) =>
  axiosIns.patch<unknown, BaseResponse<void>>(`/auth/roles/${encodeURIComponent(roleId)}/enabled`, null, { params: { enabled } })

export const getRolePermissions = (roleId: string) =>
  axiosIns.get<unknown, BaseResponse<RolePermissionListDto>>(`/auth/roles/${encodeURIComponent(roleId)}/permissions`)

export const assignRolePermissions = (roleId: string, payload: AssignRolePermissionsRequest) =>
  axiosIns.put<unknown, BaseResponse<RolePermissionListDto>>(`/auth/roles/${encodeURIComponent(roleId)}/permissions`, payload)

// ===== Permission =====

export const listPermissions = () =>
  axiosIns.get<unknown, BaseResponse<PermissionDto[]>>('/auth/permissions')

// ===== Menu =====

export const getMenuTree = () =>
  axiosIns.get<unknown, BaseResponse<MenuDto[]>>('/auth/menus/tree')

export const getMyMenus = () =>
  axiosIns.get<unknown, BaseResponse<UserMenuDto[]>>('/auth/menus/my')

export const createMenu = (payload: CreateMenuRequest) =>
  axiosIns.post<unknown, BaseResponse<MenuDto>>('/auth/menus', payload)

export const updateMenu = (payload: UpdateMenuRequest) =>
  axiosIns.put<unknown, BaseResponse<MenuDto>>('/auth/menus', payload)

export const deleteMenu = (menuId: number) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/auth/menus/${menuId}`)

export const toggleMenuVisible = (menuId: number, visible: boolean) =>
  axiosIns.patch<unknown, BaseResponse<void>>(`/auth/menus/${menuId}/visible`, null, { params: { visible } })
