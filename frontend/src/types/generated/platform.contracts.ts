// =============================================================================
// AUTO-GENERATED — do not edit manually.
// Source: scripts/generate-contract.py
// Re-generate: python scripts/generate-contract.py --module platform
// =============================================================================
//
// Request / Response TypeScript interfaces for the `platform` module.
// These are derived from the Java @RequestBody and return types in the
// corresponding Spring Boot Controllers.
//
// Usage:
//   import type { UserCreateRequest, UserResponse } from '@/types/generated/platform.contracts';
// =============================================================================

// GET /v1/platform/announcements/{id}  (PlatformAnnouncementController.getById)
export interface PlatformAnnouncementResponse {
  id: number;
  title: string;
  content: string;
  status: string;
  category: string;
  publishAt: string;
  expireAt: string;
  createdBy: string;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
}

// PUT /v1/platform/announcements/{id}  (PlatformAnnouncementController.update)
export interface PlatformAnnouncementRequest {
  title: string;
  content: string;
  status: string;
  category: string;
  publishAt: string;
  expireAt: string;
}
