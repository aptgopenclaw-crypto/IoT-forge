// =============================================================================
// AUTO-GENERATED — do not edit manually.
// Source: scripts/generate-contract.py
// Re-generate: python scripts/generate-contract.py --module setting
// =============================================================================
//
// Request / Response TypeScript interfaces for the `setting` module.
// These are derived from the Java @RequestBody and return types in the
// corresponding Spring Boot Controllers.
//
// Usage:
//   import type { UserCreateRequest, UserResponse } from '@/types/generated/setting.contracts';
// =============================================================================

// PUT /v1/auth/system-settings/{key}  (SystemSettingController.updateSetting)
export interface SystemSettingDto {
  settingKey: string;
  settingValue: string;
  description: string;
}
