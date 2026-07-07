import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse, PageResponse } from '@/types/device'
import type {
  DeviceRequest,
  DeviceResponse,
  DeviceStatsResponse,
  WorkOrderRequest,
  WorkOrderResponse,
  CircuitRequest,
  CircuitResponse,
  ContractRequest,
  ContractResponse,
  ImportError,
  ImportResponse,
} from '@/types/device'

// ── Device ──────────────────────────────────────────────────────────────

export const listDevices = (params: {
  deviceType?: string
  status?: string
  keyword?: string
  page?: number
  size?: number
}) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<DeviceResponse>>>('/auth/devices', { params })

export const getDevice = (id: number) =>
  axiosIns.get<unknown, BaseResponse<DeviceResponse>>(`/auth/devices/${id}`)

export const createDevice = (data: DeviceRequest) =>
  axiosIns.post<unknown, BaseResponse<DeviceResponse>>('/auth/devices', data)

export const updateDevice = (id: number, data: DeviceRequest) =>
  axiosIns.put<unknown, BaseResponse<DeviceResponse>>(`/auth/devices/${id}`, data)

export const deleteDevice = (id: number) =>
  axiosIns.delete<unknown, BaseResponse<null>>(`/auth/devices/${id}`)

export const decommissionDevice = (id: number) =>
  axiosIns.post<unknown, BaseResponse<null>>(`/auth/devices/${id}/decommission`)

export const getDeviceTree = (id: number) =>
  axiosIns.get<unknown, BaseResponse<DeviceResponse>>(`/auth/devices/tree/${id}`)

export const getDeviceStats = () =>
  axiosIns.get<unknown, BaseResponse<DeviceStatsResponse>>('/auth/devices/stats')

// ── Work Order ──────────────────────────────────────────────────────────

export const myTasksWorkOrders = (params: { page?: number; size?: number }) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<WorkOrderResponse>>>('/auth/work-orders/my-tasks', { params })

export const listWorkOrders = (params: {
  deviceId?: number
  status?: string
  sourceType?: string
  keyword?: string
  page?: number
  size?: number
}) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<WorkOrderResponse>>>('/auth/work-orders', { params })

export const getWorkOrder = (id: number) =>
  axiosIns.get<unknown, BaseResponse<WorkOrderResponse>>(`/auth/work-orders/${id}`)

export const createWorkOrder = (data: WorkOrderRequest) =>
  axiosIns.post<unknown, BaseResponse<WorkOrderResponse>>('/auth/work-orders', data)

export const assignWorkOrder = (id: number, assigneeUserId: string) =>
  axiosIns.post<unknown, BaseResponse<WorkOrderResponse>>(`/auth/work-orders/${id}/assign`, { assigneeUserId })

export const startWorkOrder = (id: number, latitude?: number | null, longitude?: number | null) =>
  axiosIns.post<unknown, BaseResponse<WorkOrderResponse>>(`/auth/work-orders/${id}/start`, { latitude, longitude })

export const completeWorkOrder = (id: number, data: { remark?: string; faultCause?: string; repairCost?: number }) =>
  axiosIns.post<unknown, BaseResponse<WorkOrderResponse>>(`/auth/work-orders/${id}/complete`, data)

export const approveWorkOrder = (id: number, reviewerId: string) =>
  axiosIns.post<unknown, BaseResponse<WorkOrderResponse>>(`/auth/work-orders/${id}/approve`, { reviewerId })

export const rejectWorkOrder = (id: number, reviewerId: string, reason: string) =>
  axiosIns.post<unknown, BaseResponse<WorkOrderResponse>>(`/auth/work-orders/${id}/reject`, { reviewerId, reason })

export const closeWorkOrder = (id: number, closedBy: string) =>
  axiosIns.post<unknown, BaseResponse<null>>(`/auth/work-orders/${id}/close`, { closedBy })

export const getWorkOrderTimeline = (id: number) =>
  axiosIns.get<unknown, BaseResponse<Array<{ action: string; note?: string; createdAt: string }>>>(`/auth/work-orders/${id}/timeline`)

// ── Circuit ─────────────────────────────────────────────────────────────

export const listCircuits = (params: { keyword?: string; page?: number; size?: number }) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<CircuitResponse>>>('/auth/circuits', { params })

export const getCircuit = (id: number) =>
  axiosIns.get<unknown, BaseResponse<CircuitResponse>>(`/auth/circuits/${id}`)

export const createCircuit = (data: CircuitRequest) =>
  axiosIns.post<unknown, BaseResponse<CircuitResponse>>('/auth/circuits', data)

export const updateCircuit = (id: number, data: CircuitRequest) =>
  axiosIns.put<unknown, BaseResponse<CircuitResponse>>(`/auth/circuits/${id}`, data)

export const deleteCircuit = (id: number) =>
  axiosIns.delete<unknown, BaseResponse<null>>(`/auth/circuits/${id}`)

// ── Contract ────────────────────────────────────────────────────────────

export const listContracts = (params: { status?: string; keyword?: string; page?: number; size?: number }) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<ContractResponse>>>('/auth/contracts', { params })

export const getContract = (id: number) =>
  axiosIns.get<unknown, BaseResponse<ContractResponse>>(`/auth/contracts/${id}`)

export const createContract = (data: ContractRequest) =>
  axiosIns.post<unknown, BaseResponse<ContractResponse>>('/auth/contracts', data)

export const updateContract = (id: number, data: ContractRequest) =>
  axiosIns.put<unknown, BaseResponse<ContractResponse>>(`/auth/contracts/${id}`, data)

// ── Import ────────────────────────────────────────────────────────────

export const importDevices = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return axiosIns.post<unknown, BaseResponse<ImportResponse>>(
    '/auth/devices/import',
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  )
}

export const downloadImportTemplate = (format: 'xlsx' | 'csv' = 'xlsx') => {
  return axiosIns.get('/auth/devices/import/template', {
    params: { format },
    responseType: 'blob',
  })
}

export const downloadErrorReport = (payload: {
  originalFileName: string
  headers: string[]
  rows: string[][]
  errors: ImportError[]
}) => {
  return axiosIns.post('/auth/devices/import/error-report', payload, {
    responseType: 'blob',
  })
}

// ── Circuit Import ────────────────────────────────────────────────────

export const importCircuits = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return axiosIns.post<unknown, BaseResponse<ImportResponse>>(
    '/auth/circuits/import',
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  )
}

export const downloadCircuitImportTemplate = (format: 'xlsx' | 'csv' = 'xlsx') => {
  return axiosIns.get('/auth/circuits/import/template', {
    params: { format },
    responseType: 'blob',
  })
}

export const downloadCircuitErrorReport = (payload: {
  originalFileName: string
  headers: string[]
  rows: string[][]
  errors: ImportError[]
}) => {
  return axiosIns.post('/auth/circuits/import/error-report', payload, {
    responseType: 'blob',
  })
}
