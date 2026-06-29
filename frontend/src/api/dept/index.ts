import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse } from '@/types/auth'
import type { DeptDto, DeptOptionVO, CreateDeptRequest, UpdateDeptRequest } from '@/types/dept'

export const getDeptTree = () =>
  axiosIns.get<unknown, BaseResponse<DeptDto[]>>('/auth/dept/list')

export const getDeptOptions = () =>
  axiosIns.get<unknown, BaseResponse<DeptOptionVO[]>>('/auth/dept/options')

export const getScopedDeptOptions = () =>
  axiosIns.get<unknown, BaseResponse<DeptOptionVO[]>>('/auth/dept/scope-options')

export const getDeptById = (deptId: number) =>
  axiosIns.get<unknown, BaseResponse<DeptDto>>(`/auth/dept/${deptId}`)

export const createDept = (payload: CreateDeptRequest) =>
  axiosIns.post<unknown, BaseResponse<DeptDto>>('/auth/dept', payload)

export const updateDept = (payload: UpdateDeptRequest) =>
  axiosIns.put<unknown, BaseResponse<DeptDto>>('/auth/dept', payload)

export const deleteDept = (deptId: number) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/auth/dept/${deptId}`)
