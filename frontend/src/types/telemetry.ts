// ── Telemetry Types ────────────────────────────────────────────────────────

import type { BaseResponse, PageResponse } from '@/types/device'

export type { BaseResponse, PageResponse }

// GET /v1/auth/telemetry/devices/{id}/history
export interface TelemetryPointResponse {
  ts: string // ISO-8601 instant
  values: Record<string, unknown>
}

// GET /v1/auth/telemetry/devices/{id}/latest
export interface TelemetryLatestResponse {
  deviceId: number
  ts: string
  values: Record<string, unknown>
}

// GET /v1/auth/telemetry/devices/{id}/stats
export interface TelemetryFieldStats {
  field: string
  count: number
  min: number
  max: number
  avg: number
}

// ── Event-Rule Types ───────────────────────────────────────────────────────

export type TriggerMode = 'ON_MATCH' | 'FOR_DURATION' | 'ON_CHANGE'
export type ActionType = 'NOTIFY' | 'WEBHOOK' | 'DEVICE_CMD' | 'WORKFLOW'
export type ConditionOperator =
  | 'GT'
  | 'LT'
  | 'EQ'
  | 'GTE'
  | 'LTE'
  | 'NEQ'
  | 'BETWEEN'
  | 'CONTAINS'

export interface ConditionNode {
  op?: string // AND | OR | NOT (branch)
  children?: ConditionNode[]
  field?: string // leaf
  operator?: ConditionOperator // leaf
  value?: unknown // leaf
}

export interface TriggerConfig {
  mode: TriggerMode
  durationSec: number
  cooldownSec: number
}

export interface RecipientConfig {
  roleCodes?: string[]
  userIds?: string[]
}

export interface ActionConfig {
  type: ActionType
  channels?: string[]
  recipients?: RecipientConfig
  template?: string
}

export interface RuleScope {
  deviceType?: string
  deviceIds?: number[]
}

export interface EventRuleRequest {
  ruleCode: string
  name: string
  deviceType: string
  severity: string
  scope?: RuleScope | null
  condition: ConditionNode
  triggerCfg: TriggerConfig
  actions: ActionConfig[]
}

export interface EventRuleResponse {
  id: number
  tenantId: string
  ruleCode: string
  name: string
  deviceType: string
  enabled: boolean
  severity: string
  scope?: RuleScope | null
  condition: ConditionNode
  triggerCfg: TriggerConfig
  actions: ActionConfig[]
  createTime?: string
  updateTime?: string
}

export interface EventRuleTriggerLogResponse {
  id: number
  tenantId: string
  ruleId: number
  deviceId: number
  triggeredAt: string
  severity?: string
  matchedValues?: Record<string, unknown>
  actionResult?: Record<string, unknown>
}
