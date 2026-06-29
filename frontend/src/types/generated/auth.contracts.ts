// =============================================================================
// AUTO-GENERATED — do not edit manually.
// Source: scripts/generate-contract.py
// Re-generate: python scripts/generate-contract.py --module auth
// =============================================================================
//
// Request / Response TypeScript interfaces for the `auth` module.
// These are derived from the Java @RequestBody and return types in the
// corresponding Spring Boot Controllers.
//
// Usage:
//   import type { UserCreateRequest, UserResponse } from '@/types/generated/auth.contracts';
// =============================================================================

// GET /v1/noauth/turnstile/config  (AuthController.getTurnstileConfig)
export interface TurnstileConfigResponse {
  enabled: boolean;
  siteKey: string;
}

// POST /v1/noauth/captcha  (AuthController.generateCaptcha)
export interface CaptchaResponse {
  captchaKey: string;
  captchaImage: string;
}

// POST /v1/noauth/login  (AuthController.login)
export interface LoginRequest {
  email: string;
  password: string;
  captcha: string;
  captchaKey: string;
  turnstileToken: string;
}
export interface LoginResult {
  accessToken: string;
  refreshToken: string;
  needsSelection: boolean;
  isSuperAdmin: boolean;
  tenants: TenantOption[];
  passwordChangeRequired: boolean;
}

// POST /v1/noauth/token/refresh  (AuthController.refreshToken)
export interface TokenResult {
  accessToken: string;
  refreshToken: string;
}

// POST /v1/noauth/user/forgot-password  (AuthController.forgotPassword)
export interface ForgotPasswordRequest {
  email: string;
}

// PUT /v1/noauth/user/reset-password  (AuthController.resetPassword)
export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

// POST /v1/auth/select-tenant  (AuthController.selectTenant)
export interface SelectTenantRequest {
  tenantId: string;
}

// POST /v1/auth/switch-tenant  (AuthController.switchTenant)
export interface SwitchTenantRequest {
  tenantId: string;
}

// GET /v1/auth/user/info  (AuthController.getUserInfo)
export interface UserInfoDto {
  userId: string;
  email: string;
  displayName: string;
  tenantId: string;
  tenantName: string;
  roles: string[];
  deptId: string;
  deptName: string;
  permissions: string[];
  isSuperAdmin: boolean;
  availableTenants: TenantOption[];
}

// GET /v1/auth/sessions  (AuthController.listMySessions)
export interface SessionDto {
  sessionId: string;
  tenantId: string;
  ipAddress: string;
  userAgent: string;
  issuedAt: string;
  lastSeenAt: string;
  expiresAt: string;
  current: boolean;
}

// GET /v1/noauth/password-policy/describe  (NoauthPasswordPolicyController.describe)
export interface PasswordPolicyDto {
  minLength: number;
  requireUppercase: boolean;
  requireLowercase: boolean;
  requireDigit: boolean;
  requireSpecial: boolean;
  historyCount: number;
  maxLength: number;
  minSpecialChars: number;
  minDigits: number;
  minUppercase: number;
  minLowercase: number;
  notContainsUsername: boolean;
  expireDays: number;
  forceChangeOnFirstLogin: boolean;
  forceChangeOnAdminReset: boolean;
  describe: string[];
}

// PUT /v1/auth/password-policy/tenant  (TenantPasswordPolicyController.updateTenantOverride)
export interface UpdatePasswordPolicyRequest {
  key: string;
  value: string;
}

// POST /v1/platform/tenants/{tenantId}/auth-config/test-connection  (PlatformTenantAuthConfigController.testConnection)
export interface TenantAuthConfigRequest {
  authType: AuthType;
  config: Record<string, unknown>;
  fallbackLocal: boolean;
}
