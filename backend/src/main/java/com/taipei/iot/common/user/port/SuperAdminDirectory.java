package com.taipei.iot.common.user.port;

import java.util.List;

/**
 * Port exposing the set of SUPER_ADMIN user IDs, owned by the {@code user} module. Lets
 * lower-layer modules (e.g. {@code audit}) exclude platform super-admins from
 * tenant-scoped views without depending upward on {@code user}.
 */
public interface SuperAdminDirectory {

	/**
	 * Return the user IDs of every SUPER_ADMIN account (global, not tenant-scoped).
	 * @return super-admin user IDs (never {@code null})
	 */
	List<String> getSuperAdminUserIds();

}
