// =============================================================================
// AUTO-GENERATED — do not edit manually.
// Source: scripts/generate-contract.py
// Re-generate: python scripts/generate-contract.py --module dept
// =============================================================================
//
// Request / Response TypeScript interfaces for the `dept` module.
// These are derived from the Java @RequestBody and return types in the
// corresponding Spring Boot Controllers.
//
// Usage:
//   import type { UserCreateRequest, UserResponse } from '@/types/generated/dept.contracts';
// =============================================================================

// GET /v1/auth/dept/list  (DeptController.getDeptTree)
export interface DeptDto {
  id: number;
  pid: number;
  deptName: string;
  deptSort: number;
  status: number;
  hierarchyPath: string;
  createBy: string;
  updateBy: string;
  createTime: string;
  updateTime: string;
  children: DeptDto[];
}

// GET /v1/auth/dept/options  (DeptController.getDeptOptions)
export interface DeptOptionVO {
  value: number;
  pid: number;
  label: string;
  children: DeptOptionVO[];
}
