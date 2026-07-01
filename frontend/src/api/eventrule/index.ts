import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse, PageResponse } from '@/types/device'
import type {
  EventRuleRequest,
  EventRuleResponse,
  EventRuleTriggerLogResponse,
} from '@/types/telemetry'

// ── Event Rules ────────────────────────────────────────────────────────────

export const listEventRules = (params: {
  deviceType?: string
  enabled?: boolean
  page?: number
  size?: number
}) => axiosIns.get<unknown, BaseResponse<PageResponse<EventRuleResponse>>>('/auth/event-rules', { params })

export const getEventRule = (id: number) =>
  axiosIns.get<unknown, BaseResponse<EventRuleResponse>>(`/auth/event-rules/${id}`)

export const createEventRule = (data: EventRuleRequest) =>
  axiosIns.post<unknown, BaseResponse<EventRuleResponse>>('/auth/event-rules', data)

export const updateEventRule = (id: number, data: EventRuleRequest) =>
  axiosIns.put<unknown, BaseResponse<EventRuleResponse>>(`/auth/event-rules/${id}`, data)

export const toggleEventRule = (id: number, enabled: boolean) =>
  axiosIns.patch<unknown, BaseResponse<EventRuleResponse>>(`/auth/event-rules/${id}/enabled`, {
    enabled,
  })

export const deleteEventRule = (id: number) =>
  axiosIns.delete<unknown, BaseResponse<null>>(`/auth/event-rules/${id}`)

// ── Trigger Logs ───────────────────────────────────────────────────────────

export const getEventRuleLogs = (
  ruleId: number,
  params: { from?: string; to?: string; page?: number; size?: number },
) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<EventRuleTriggerLogResponse>>>(
    `/auth/event-rules/${ruleId}/logs`,
    { params },
  )

export const getAllTriggerLogs = (params: {
  from?: string
  to?: string
  severity?: string
  page?: number
  size?: number
}) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<EventRuleTriggerLogResponse>>>(
    '/auth/event-rules/logs',
    { params },
  )
