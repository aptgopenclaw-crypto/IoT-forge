package com.taipei.iot.auth.provider;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Result returned by an {@link AuthenticationProvider} after successful authentication.
 */
@Getter
@Builder
public class AuthenticationResult {

	/** Local user_id if the user already exists in the system; null for auto-provision */
	private final String localUserId;

	/** External IdP unique identifier (LDAP DN / OIDC sub / SAML nameId) */
	private final String externalId;

	/** User email (for matching or provisioning) */
	private final String email;

	/** Display name from IdP (for provisioning) */
	private final String displayName;

	/** Additional claims from IdP (group memberships, etc.) for role mapping */
	private final Map<String, Object> claims;

}
