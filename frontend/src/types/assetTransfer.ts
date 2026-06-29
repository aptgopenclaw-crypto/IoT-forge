export type AssetTransferStatus = 'DRAFT' | 'PROCESSING' | 'COMPLETED' | 'REJECTED' | 'CANCELLED'

export interface AssetTransferApplicationDto {
  id: number
  applicationNo: string
  applicantId: string
  applicantName: string | null
  departmentId: number
  departmentName: string | null
  assetCode: string
  assetName: string
  transferType: string
  targetDepartmentId: number | null
  reason: string | null
  assetValue: number | null
  status: AssetTransferStatus
  currentAssignee: string | null
  currentAssigneeName: string | null
  createdAt: string
  createdBy: string | null
  updatedAt: string
  approvedAt: string | null
  approvedBy: string | null
  rejectReason: string | null
  canAct: boolean
}

export interface AssetTransferCreateRequest {
  assetCode: string
  assetName: string
  transferType: string
  departmentId: number
  targetDepartmentId?: number | null
  reason?: string | null
  assetValue?: number | null
}

export interface AssetTransferActionRequest {
  comment?: string | null
}

export interface AssetTransferRejectRequest {
  comment?: string | null
  targetStepId: string
}

export interface RejectTargetOption {
  stepId: string
  stepName: string
}

export type WorkflowAction = 'APPROVE' | 'REJECT' | 'RESUBMIT' | 'CANCEL'

export interface WorkflowStepLogDto {
  id: number
  stepId: string
  stepName: string
  assigneeUserId: string | null
  assigneeName: string | null
  action: WorkflowAction | null
  comment: string | null
  targetStepId: string | null
  enteredAt: string
  completedAt: string | null
}

export type WorkflowStatus = 'IN_PROGRESS' | 'COMPLETED' | 'REJECTED' | 'CANCELLED'

export interface WorkflowStepSlaDto {
  stepId: string
  stepName: string
  assigneeUserId: string | null
  action: WorkflowAction | null
  slaDays: number | null
  actualDays: number | null
  overdue: boolean
  enteredAt: string
  completedAt: string | null
}

export interface WorkflowSlaDto {
  instanceId: number
  businessId: string
  businessType: string
  status: WorkflowStatus
  createdAt: string
  completedAt: string | null
  slaTotalDays: number | null
  actualDays: number | null
  overdue: boolean
  steps: WorkflowStepSlaDto[]
}
