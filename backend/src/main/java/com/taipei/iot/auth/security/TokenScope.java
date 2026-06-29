package com.taipei.iot.auth.security;

/**
 * [Platform/Tenant Separation ADR-007] JWT token scope.
 *
 * <p>
 * Encoded into the {@code scope} claim of every access / temporary token, read by
 * {@code ScopeEnforcementFilter} (Phase 1.1.2) to ensure the request path prefix matches
 * the token's authority scope:
 *
 * <ul>
 * <li>{@link #PLATFORM} → super_admin tokens (tenantId is null); allowed on
 * {@code /v1/platform/**} and {@code /v1/noauth/**} only.</li>
 * <li>{@link #TENANT} → regular tenant user tokens (tenantId required); allowed on
 * {@code /v1/auth/**} and {@code /v1/noauth/**}.</li>
 * <li>{@link #IMPERSONATION} → tokens issued by the Impersonation API (Phase 1.1.6) for
 * super_admin operating inside a target tenant on its behalf; treated like
 * {@link #TENANT} for routing but audited separately via
 * {@code impersonated_by_user_id}.</li>
 * </ul>
 *
 * <p>
 * Legacy tokens issued before Phase 1 do not carry this claim and are treated as
 * {@link #TENANT} during the transition window (ADR-007).
 */
public enum TokenScope {

	PLATFORM, TENANT, IMPERSONATION;

	/**
	 * Parse a string claim value, returning {@code null} if the value is absent or
	 * unrecognised. Callers decide the legacy default.
	 */
	public static TokenScope fromClaim(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return TokenScope.valueOf(value);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
