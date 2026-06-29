package com.taipei.iot.auth.provider;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Provider-agnostic authentication request.
 */
@Getter
@Builder
public class AuthenticationRequest {

	/** Login identifier (email for LOCAL/LDAP, null for OIDC/SAML) */
	private final String identifier;

	/** Credential (password for LOCAL/LDAP, null for OIDC/SAML) */
	private final String credential;

	/** Target tenant ID (may be null if not yet determined) */
	private final String tenantId;

	/** Provider-specific parameters (e.g. OIDC authorization code, SAML response) */
	private final Map<String, String> extra;

}
