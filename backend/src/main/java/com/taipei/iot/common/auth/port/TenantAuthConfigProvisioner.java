package com.taipei.iot.common.auth.port;

import com.taipei.iot.common.enums.AuthType;

/**
 * Port for seeding the default authentication configuration of a newly created tenant.
 * Implemented by the {@code auth} module so that the lower-layer {@code tenant} module
 * does not depend upward on {@code auth}.
 */
public interface TenantAuthConfigProvisioner {

	/**
	 * Seed the default {@code tenant_auth_config} row for a tenant. Persisted in system
	 * context.
	 * @param tenantId the tenant id
	 * @param authType the initial authentication type
	 */
	void seedDefaultAuthConfig(String tenantId, AuthType authType);

}
