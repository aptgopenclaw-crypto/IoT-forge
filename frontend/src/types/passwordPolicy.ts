/**
 * Effective password policy returned by both authenticated
 * (`GET /v1/auth/password-policy`) and public
 * (`GET /v1/noauth/password-policy/describe`) endpoints.
 *
 * Fields mirror backend `PasswordPolicyDto`. `describe` is the human-readable
 * rule list used by password input components to show real-time hints.
 */
export interface PasswordPolicyDto {
  minLength: number
  requireUppercase: boolean
  requireLowercase: boolean
  requireDigit: boolean
  requireSpecial: boolean
  historyCount: number

  // Phase 2
  maxLength: number
  minSpecialChars: number
  minDigits: number
  minUppercase: number
  minLowercase: number
  notContainsUsername: boolean

  // Phase 3
  expireDays: number
  forceChangeOnFirstLogin: boolean
  forceChangeOnAdminReset: boolean

  describe: string[]
}

export interface UpdatePasswordPolicyRequest {
  key: string
  value: string
}

/** Metadata for each settable policy key — drives the settings page UI. */
export interface PasswordPolicyKeyMeta {
  key: string
  type: 'INT' | 'BOOL'
  /** Hard-coded fallback default; shown when nothing else is set. */
  defaultValue: string
  /** Platform-imposed lower bound for INT keys; `null` for booleans. */
  platformFloor: number | null
  /** UI category for grouping rows. */
  category: 'basic' | 'advanced' | 'expiry'
  /** i18n key for the label text. */
  labelKey: string
}

/**
 * Static catalogue mirroring `PasswordPolicyKey` enum on the backend.
 * Kept in sync manually — backend is the source of truth for floors;
 * mismatches surface as 400 errors from the policy service.
 */
export const POLICY_KEYS: PasswordPolicyKeyMeta[] = [
  // Phase 1
  { key: 'password.min_length',         type: 'INT',  defaultValue: '8',    platformFloor: 8,    category: 'basic',    labelKey: 'passwordPolicy.keys.minLength' },
  { key: 'password.require_uppercase',  type: 'BOOL', defaultValue: 'true', platformFloor: null, category: 'basic',    labelKey: 'passwordPolicy.keys.requireUppercase' },
  { key: 'password.require_lowercase',  type: 'BOOL', defaultValue: 'true', platformFloor: null, category: 'basic',    labelKey: 'passwordPolicy.keys.requireLowercase' },
  { key: 'password.require_digit',      type: 'BOOL', defaultValue: 'true', platformFloor: null, category: 'basic',    labelKey: 'passwordPolicy.keys.requireDigit' },
  { key: 'password.require_special',    type: 'BOOL', defaultValue: 'true', platformFloor: null, category: 'basic',    labelKey: 'passwordPolicy.keys.requireSpecial' },
  { key: 'password.history_count',      type: 'INT',  defaultValue: '5',    platformFloor: 1,    category: 'basic',    labelKey: 'passwordPolicy.keys.historyCount' },
  // Phase 2
  { key: 'password.max_length',         type: 'INT',  defaultValue: '128',  platformFloor: 64,   category: 'advanced', labelKey: 'passwordPolicy.keys.maxLength' },
  { key: 'password.min_special_chars',  type: 'INT',  defaultValue: '1',    platformFloor: 1,    category: 'advanced', labelKey: 'passwordPolicy.keys.minSpecialChars' },
  { key: 'password.min_digits',         type: 'INT',  defaultValue: '1',    platformFloor: 1,    category: 'advanced', labelKey: 'passwordPolicy.keys.minDigits' },
  { key: 'password.min_uppercase',      type: 'INT',  defaultValue: '1',    platformFloor: 1,    category: 'advanced', labelKey: 'passwordPolicy.keys.minUppercase' },
  { key: 'password.min_lowercase',      type: 'INT',  defaultValue: '1',    platformFloor: 1,    category: 'advanced', labelKey: 'passwordPolicy.keys.minLowercase' },
  { key: 'password.not_contains_username', type: 'BOOL', defaultValue: 'true', platformFloor: null, category: 'advanced', labelKey: 'passwordPolicy.keys.notContainsUsername' },
  // Phase 3
  { key: 'password.expire_days',                 type: 'INT',  defaultValue: '90',   platformFloor: null, category: 'expiry', labelKey: 'passwordPolicy.keys.expireDays' },
  { key: 'password.force_change_on_first_login', type: 'BOOL', defaultValue: 'true', platformFloor: null, category: 'expiry', labelKey: 'passwordPolicy.keys.forceChangeOnFirstLogin' },
  { key: 'password.force_change_on_admin_reset', type: 'BOOL', defaultValue: 'true', platformFloor: null, category: 'expiry', labelKey: 'passwordPolicy.keys.forceChangeOnAdminReset' },
]

export interface ForceChangePasswordRequest {
  newPassword: string
}
