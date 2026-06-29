// ── Base Response Types ──────────────────────────────────────────────────

export interface BaseResponse<T> {
  errorCode: string
  errorMsg: string
  errorDetail?: string
  timestamp: number
  body: T
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

// ── Device ───────────────────────────────────────────────────────────────

export interface DeviceRequest {
  deviceType: string
  deviceCode: string
  deviceName?: string
  twd97X?: number | null
  twd97Y?: number | null
  lng?: number | null
  lat?: number | null
  elevation?: number | null
  deptId?: number | null
  contractId?: number | null
  propertyOwner?: string
  installedAt?: string | null
  parentDeviceId?: number | null
  mountPosition?: string
  connectivityType?: string
  circuitId?: number | null
  attributes?: Record<string, unknown>
}

export interface DeviceResponse {
  id: number
  deviceType: string
  deviceCode: string
  deviceName?: string
  twd97X?: number | null
  twd97Y?: number | null
  lng?: number | null
  lat?: number | null
  elevation?: number | null
  deptId?: number | null
  deptName?: string
  contractId?: number | null
  contractCode?: string
  propertyOwner?: string
  status: string
  installedAt?: string | null
  decommissionedAt?: string | null
  parentDeviceId?: number | null
  parentDeviceCode?: string
  mountPosition?: string
  connectivityType?: string
  lastHeartbeatAt?: string | null
  circuitId?: number | null
  circuitNumber?: string
  childrenCount: number
  children?: DeviceResponse[]
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface DeviceStatsResponse {
  totalDevices: number
  byType: Record<string, number>
  byStatus: Record<string, number>
  onlineRate: number
  openFaults: number
}

// ── Work Order ───────────────────────────────────────────────────────────

export interface WorkOrderRequest {
  deviceId?: number | null
  deviceCode?: string
  circuitId?: number | null
  orderType: string
  sourceType: string
  priority?: string
  reporterName?: string
  reporterContact?: string
  description?: string
}

export interface WorkOrderLogEntry {
  action: string
  operatorName?: string
  latitude?: number | null
  longitude?: number | null
  note?: string
  createdAt: string
}

export interface WorkOrderResponse {
  id: number
  deviceId?: number | null
  deviceCode?: string
  deviceName?: string
  circuitId?: number | null
  orderType: string
  sourceType: string
  status: string
  priority?: string
  reporterName?: string
  reporterContact?: string
  reportedAt?: string
  description?: string
  assignedTo?: string
  assignedToName?: string
  assignedAt?: string
  startedAt?: string
  completedAt?: string
  completionRemark?: string
  faultCause?: string
  repairCost?: number | null
  reviewerId?: string
  reviewedAt?: string
  rejectReason?: string
  closedAt?: string
  timeline?: WorkOrderLogEntry[]
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

// ── Circuit ──────────────────────────────────────────────────────────────

export interface CircuitRequest {
  panelBoxDeviceId?: number | null
  circuitNumber: string
  circuitName?: string
  taipowerAccount?: string
  usageType?: string
}

export interface CircuitResponse {
  id: number
  panelBoxDeviceId?: number | null
  circuitNumber: string
  circuitName?: string
  taipowerAccount?: string
  usageType?: string
  status: string
  createdAt?: string
}

// ── Contract ─────────────────────────────────────────────────────────────

export interface ContractRequest {
  contractCode: string
  contractName: string
  budgetYear?: number | null
  procurementNumber?: string
  contractorName?: string
  contractorContact?: string
  assetCategory?: string
  quantity?: number | null
  startDate?: string | null
  endDate?: string | null
  acceptanceDate?: string | null
  warrantyYears?: number | null
}

export interface ContractResponse {
  id: number
  contractCode: string
  contractName: string
  budgetYear?: number | null
  procurementNumber?: string
  contractorName?: string
  contractorContact?: string
  quantity?: number | null
  startDate?: string | null
  endDate?: string | null
  acceptanceDate?: string | null
  warrantyExpiry?: string | null
  status: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}
