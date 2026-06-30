package com.taipei.iot.common.enums;

/**
 * Supported authentication provider types.
 */
public enum AuthType {

	LOCAL, LDAP, OIDC, SAML;

	public static AuthType fromString(String value) {
		if (value == null || value.isBlank()) {
			return LOCAL;
		}
		return valueOf(value.toUpperCase());
	}

}
