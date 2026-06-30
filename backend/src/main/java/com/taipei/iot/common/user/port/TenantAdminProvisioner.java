package com.taipei.iot.common.user.port;

/**
 * Port for provisioning the initial administrator account of a newly created tenant.
 * Implemented by the {@code user} module so that the lower-layer {@code tenant} module
 * does not depend upward on {@code user}.
 */
public interface TenantAdminProvisioner {

	/**
	 * Provision the initial admin user for a tenant. Validates the password against the
	 * tenant password policy, creates the user and the user-tenant mapping (saved in
	 * system context). No-op when email or password is blank.
	 * @param spec the admin account specification
	 */
	void provisionTenantAdmin(TenantAdminSpec spec);

	/**
	 * Specification for the initial tenant administrator.
	 */
	record TenantAdminSpec(String tenantId, String email, String rawPassword, String displayName) {
	}

}
