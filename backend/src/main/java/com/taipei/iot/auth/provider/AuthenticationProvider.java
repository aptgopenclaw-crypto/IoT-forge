package com.taipei.iot.auth.provider;

/**
 * Strategy interface for pluggable authentication mechanisms. Each concrete provider
 * handles one {@link AuthType}.
 */
public interface AuthenticationProvider {

	/**
	 * @return the auth type this provider handles
	 */
	AuthType getType();

	/**
	 * Authenticate the user against this provider.
	 * @param request authentication input (identifier, credential, etc.)
	 * @param configJson the tenant's provider-specific configuration (decrypted); may be
	 * null for LOCAL
	 * @return result containing identity info for local user resolution
	 * @throws com.taipei.iot.common.exception.BusinessException on authentication failure
	 */
	AuthenticationResult authenticate(AuthenticationRequest request, String configJson);

	/**
	 * Test whether the configuration is valid and connectivity works. Used by the admin
	 * "Test Connection" UI.
	 * @param configJson provider config JSON to validate
	 * @return true if connection test succeeds
	 */
	default boolean testConnection(String configJson) {
		return true;
	}

}
