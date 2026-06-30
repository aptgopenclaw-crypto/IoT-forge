package com.taipei.iot.common.user.port;

import java.util.List;

/**
 * Port exposing department-membership queries owned by the {@code user} module, so that
 * the {@code dept} module can guard and clean up department deletion without depending
 * upward on {@code user}.
 */
public interface DeptMembershipGuard {

	/**
	 * Return the display names of active (non-deleted, enabled) users currently assigned
	 * to the given department. An empty list means the department has no blocking
	 * members.
	 * @param tenantId the tenant scope
	 * @param deptId the department being inspected
	 * @return display names of active members blocking deletion (never {@code null})
	 */
	List<String> findActiveMemberDisplayNames(String tenantId, Long deptId);

	/**
	 * Clear the department assignment of every user mapped to the given department, so
	 * the department can be deleted without violating foreign-key constraints.
	 * @param tenantId the tenant scope
	 * @param deptId the department being deleted
	 */
	void clearDeptAssignments(String tenantId, Long deptId);

}
