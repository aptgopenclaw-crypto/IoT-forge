// =============================================================================
// AUTO-GENERATED — do not edit manually.
// Source: scripts/generate-contract.py
// Re-generate: python scripts/generate-contract.py --module workflow
// =============================================================================
//
// Request / Response TypeScript interfaces for the `workflow` module.
// These are derived from the Java @RequestBody and return types in the
// corresponding Spring Boot Controllers.
//
// Usage:
//   import type { UserCreateRequest, UserResponse } from '@/types/generated/workflow.contracts';
// =============================================================================

// POST /v1/api/poc/workflow/start  (WorkflowPocController.start)
export interface WorkflowInstanceEntity {
  id: number;
  tenantId: string;
  workflowDefId: number;
  businessId: string;
  businessType: string;
  currentStepId: string;
  status: WorkflowStatus;
  contextJson: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string;
}

// GET /v1/api/poc/workflow/history/{id}  (WorkflowPocController.getHistory)
export interface WorkflowStepLogEntity {
  id: number;
  tenantId: string;
  workflowInstanceId: number;
  stepId: string;
  stepName: string;
  assigneeUserId: string;
  action: WorkflowAction;
  comment: string;
  targetStepId: string;
  enteredAt: string;
  completedAt: string;
}

// POST /v1/api/poc/workflow/delegate  (WorkflowPocController.setDelegate)
export interface DelegateSettingEntity {
  id: number;
  tenantId: string;
  delegateFor: string;
  delegateTo: string;
  businessType: string;
  effectiveFrom: string;
  effectiveTo: string;
  createdAt: string;
  updatedAt: string;
}
