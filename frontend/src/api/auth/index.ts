import axiosIns from '@/api/axios/axiosIns'
import type {
  BaseResponse,
  CaptchaResponse,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  LoginRequest,
  LoginResult,
  ResetPasswordRequest,
  SelectTenantRequest,
  SwitchTenantRequest,
  TokenResult,
  UserInfoDto,
} from '@/types/auth'

export const getCaptcha = () =>
  axiosIns.post<unknown, BaseResponse<CaptchaResponse>>('/noauth/captcha')

export const login = (payload: LoginRequest) =>
  axiosIns.post<unknown, BaseResponse<LoginResult>>('/noauth/login', payload)

export const selectTenant = (payload: SelectTenantRequest) =>
  axiosIns.post<unknown, BaseResponse<TokenResult>>('/auth/select-tenant', payload)

export const switchTenant = (payload: SwitchTenantRequest) =>
  axiosIns.post<unknown, BaseResponse<TokenResult>>('/auth/switch-tenant', payload)

export const refreshTokenApi = () =>
  axiosIns.post<unknown, BaseResponse<TokenResult>>('/noauth/token/refresh')

export const logout = () =>
  axiosIns.post<unknown, BaseResponse<void>>('/auth/logout')

export const getUserInfo = () =>
  axiosIns.get<unknown, BaseResponse<UserInfoDto>>('/auth/user/info')

export const changePassword = (payload: ChangePasswordRequest) =>
  axiosIns.post<unknown, BaseResponse<void>>('/auth/user/change-password', payload)

export const forgotPassword = (payload: ForgotPasswordRequest) =>
  axiosIns.post<unknown, BaseResponse<void>>('/noauth/user/forgot-password', payload)

export const resetPassword = (payload: ResetPasswordRequest) =>
  axiosIns.put<unknown, BaseResponse<void>>('/noauth/user/reset-password', payload)
