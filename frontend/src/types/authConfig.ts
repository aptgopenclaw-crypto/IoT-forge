export type AuthType = 'LOCAL' | 'LDAP' | 'OIDC' | 'SAML'

export interface TenantAuthConfigResponse {
  id: number | null
  tenantId: string
  authType: AuthType
  enabled: boolean
  /** Sanitized config (secrets replaced with "***") */
  config: Record<string, unknown> | null
  fallbackLocal: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface TenantAuthConfigRequest {
  authType: AuthType
  config: Record<string, unknown> | null
  fallbackLocal?: boolean
}

/** LDAP-specific configuration fields */
export interface LdapConfig {
  url: string
  baseDn: string
  bindDn: string
  bindPassword: string
  userSearchFilter: string
  userSearchBase: string
  groupSearchBase?: string
  groupSearchFilter?: string
  useSsl: boolean
  connectTimeout: number
  readTimeout: number
}

/** OIDC-specific configuration fields (Phase C) */
export interface OidcConfig {
  issuerUrl: string
  clientId: string
  clientSecret: string
  scopes: string
  redirectUri: string
}

/** Auth type display metadata */
export interface AuthTypeOption {
  value: AuthType
  labelKey: string
  descriptionKey: string
  available: boolean
}

export const AUTH_TYPE_OPTIONS: AuthTypeOption[] = [
  {
    value: 'LOCAL',
    labelKey: 'authConfig.types.local',
    descriptionKey: 'authConfig.types.localDesc',
    available: true,
  },
  {
    value: 'LDAP',
    labelKey: 'authConfig.types.ldap',
    descriptionKey: 'authConfig.types.ldapDesc',
    available: true,
  },
  {
    value: 'OIDC',
    labelKey: 'authConfig.types.oidc',
    descriptionKey: 'authConfig.types.oidcDesc',
    available: false,
  },
  {
    value: 'SAML',
    labelKey: 'authConfig.types.saml',
    descriptionKey: 'authConfig.types.samlDesc',
    available: false,
  },
]

/** LDAP config field metadata for form rendering */
export interface LdapFieldMeta {
  key: keyof LdapConfig
  labelKey: string
  type: 'text' | 'password' | 'number' | 'boolean'
  required: boolean
  placeholder?: string
}

export const LDAP_FIELDS: LdapFieldMeta[] = [
  { key: 'url', labelKey: 'authConfig.ldap.url', type: 'text', required: true, placeholder: 'ldap://host:389' },
  { key: 'baseDn', labelKey: 'authConfig.ldap.baseDn', type: 'text', required: true, placeholder: 'dc=example,dc=com' },
  { key: 'bindDn', labelKey: 'authConfig.ldap.bindDn', type: 'text', required: true, placeholder: 'cn=admin,dc=example,dc=com' },
  { key: 'bindPassword', labelKey: 'authConfig.ldap.bindPassword', type: 'password', required: true },
  { key: 'userSearchFilter', labelKey: 'authConfig.ldap.userSearchFilter', type: 'text', required: true, placeholder: '(mail={0})' },
  { key: 'userSearchBase', labelKey: 'authConfig.ldap.userSearchBase', type: 'text', required: true, placeholder: 'ou=users,dc=example,dc=com' },
  { key: 'groupSearchBase', labelKey: 'authConfig.ldap.groupSearchBase', type: 'text', required: false, placeholder: 'ou=groups,dc=example,dc=com' },
  { key: 'groupSearchFilter', labelKey: 'authConfig.ldap.groupSearchFilter', type: 'text', required: false, placeholder: '(member={0})' },
  { key: 'useSsl', labelKey: 'authConfig.ldap.useSsl', type: 'boolean', required: false },
  { key: 'connectTimeout', labelKey: 'authConfig.ldap.connectTimeout', type: 'number', required: false, placeholder: '5000' },
  { key: 'readTimeout', labelKey: 'authConfig.ldap.readTimeout', type: 'number', required: false, placeholder: '10000' },
]
