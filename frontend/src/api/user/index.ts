import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse, UserInfoDto } from '@/types/auth'
import type { PageResponse } from '@/types/common'
import type {
  UpdateOwnProfileRequest,
  UserChangePasswordRequest,
  UserListQuery,
  CreateUserRequest,
  UpdateUserRequest,
  AddTenantRoleRequest,
  UserListItemDto,
  UserTenantMappingDto,
} from '@/types/user'

// ===== Self-service =====

export const updateOwnProfile = (payload: UpdateOwnProfileRequest) =>
  axiosIns.put<unknown, BaseResponse<UserInfoDto>>('/auth/user/my', payload)

export const changePassword = (payload: UserChangePasswordRequest) =>
  axiosIns.post<unknown, BaseResponse<void>>('/auth/user/change-password', payload)

// ===== Admin =====

export const listUsers = (params: UserListQuery) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<UserListItemDto>>>('/auth/users', { params })

export const getUser = (userId: string) =>
  axiosIns.get<unknown, BaseResponse<UserListItemDto>>(`/auth/users/${encodeURIComponent(userId)}`)

export const createUser = (payload: CreateUserRequest) =>
  axiosIns.post<unknown, BaseResponse<UserListItemDto>>('/auth/users', payload)

export const updateUser = (userId: string, payload: UpdateUserRequest) =>
  axiosIns.put<unknown, BaseResponse<UserListItemDto>>(`/auth/users/${encodeURIComponent(userId)}`, payload)

export const disableUser = (userId: string) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/auth/users/${encodeURIComponent(userId)}`)

export const softDeleteUser = (userId: string) =>
  axiosIns.patch<unknown, BaseResponse<void>>(`/auth/users/${encodeURIComponent(userId)}/soft-delete`)

export const getUserTenantRoles = (userId: string) =>
  // [Platform/Tenant Separation 2.1.5] migrated to /v1/platform/users/{userId}/tenant-roles
  axiosIns.get<unknown, BaseResponse<UserTenantMappingDto[]>>(`/platform/users/${encodeURIComponent(userId)}/tenant-roles`)

export const addTenantRole = (userId: string, payload: AddTenantRoleRequest) =>
  axiosIns.post<unknown, BaseResponse<UserTenantMappingDto>>(`/platform/users/${encodeURIComponent(userId)}/tenant-roles`, payload)

export const removeTenantRole = (userId: string, mappingId: number) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/platform/users/${encodeURIComponent(userId)}/tenant-roles/${mappingId}`)
