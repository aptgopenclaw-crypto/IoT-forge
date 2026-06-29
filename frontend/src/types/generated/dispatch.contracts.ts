// =============================================================================
// AUTO-GENERATED — do not edit manually.
// Source: scripts/generate-contract.py
// Re-generate: python scripts/generate-contract.py --module dispatch
// =============================================================================

// GET /v1/auth/work-orders/my-tasks  (WorkOrderController.myTasks)
export interface WorkOrderResponse {
  id: number;
  deviceId: number;
  deviceCode: string;
  deviceName: string;
  circuitId: number;
  orderType: string;
  sourceType: string;
  status: string;
  priority: string;
  reporterName: string;
  reporterContact: string;
  reportedAt: string;
  description: string;
  assignedTo: string;
  assignedToName: string;
  assignedAt: string;
  startedAt: string;
  completedAt: string;
  completionRemark: string;
  faultCause: string;
  repairCost: number;
  reviewerId: string;
  reviewedAt: string;
  rejectReason: string;
  closedAt: string;
  timeline: string[];
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface WorkOrderRequest {
  deviceId: number;
  deviceCode: string;
  circuitId: number;
  orderType: string;
  sourceType: string;
  priority: string;
  reporterName: string;
  reporterContact: string;
  description: string;
  locationSnapshot: Record<string, unknown>;
}
