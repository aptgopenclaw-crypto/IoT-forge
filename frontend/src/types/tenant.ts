export interface TenantDto {
  tenantId: string
  tenantCode: string
  tenantName: string
  deploymentMode: string
  enabled: boolean
  createTime: string | null
}

export interface CreateTenantRequest {
  tenantCode: string
  tenantName: string
  deploymentMode: string
  adminEmail?: string
  adminDisplayName?: string
  adminPassword?: string
}

export interface UpdateTenantRequest {
  tenantName: string
  deploymentMode?: string
}
