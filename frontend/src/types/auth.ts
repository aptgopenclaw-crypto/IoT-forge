export interface BaseResponse<T> {
  errorCode: string
  errorMsg: string
  errorDetail: string
  timestamp: string
  body: T
}

export interface CaptchaResponse {
  captchaKey: string
  captchaImage: string
}

export interface LoginRequest {
  email: string
  password: string
  captcha: string
  captchaKey: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string | null
  needsSelection: boolean
  isSuperAdmin: boolean
  tenants: TenantOption[] | null
  /**
   * [Phase 3] When true, `accessToken` is a short-lived `password_change`
   * temporary token (not a regular access token). The UI must redirect to
   * the force-change-password page and call POST /noauth/user/force-change-password
   * with this token. The user is NOT logged in until that succeeds.
   */
  passwordChangeRequired?: boolean
}

export interface TenantOption {
  tenantId: string
  tenantCode: string
  tenantName: string
  roleName: string
  deptName: string | null
}

export interface SelectTenantRequest {
  tenantId: string
}

export interface SwitchTenantRequest {
  tenantId: string
}

export interface TokenResult {
  accessToken: string
  refreshToken: string
}

export interface UserInfoDto {
  userId: string
  email: string
  displayName: string
  tenantId: string
  tenantName: string
  roles: string[]
  deptId: string | null
  deptName: string | null
  permissions: string[]
  isSuperAdmin: boolean
  availableTenants: TenantOption[]
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface ForgotPasswordRequest {
  email: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}
