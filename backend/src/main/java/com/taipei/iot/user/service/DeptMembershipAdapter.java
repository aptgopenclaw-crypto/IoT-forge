package com.taipei.iot.user.service;

import com.taipei.iot.common.user.port.DeptMembershipGuard;
import com.taipei.iot.user.entity.UserEntity;
import com.taipei.iot.user.repository.UserRepository;
import com.taipei.iot.user.repository.UserTenantMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code user}-module adapter for {@link DeptMembershipGuard}. Owns the user-data queries
 * that the {@code dept} module needs when deleting a department.
 */
@Component
@RequiredArgsConstructor
public class DeptMembershipAdapter implements DeptMembershipGuard {

	private final UserTenantMappingRepository userTenantMappingRepository;

	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	public List<String> findActiveMemberDisplayNames(String tenantId, Long deptId) {
		List<String> memberUserIds = userTenantMappingRepository.findByTenantIdAndDeptIdAndEnabledTrue(tenantId, deptId)
			.stream()
			.map(m -> m.getUserId())
			.collect(Collectors.toList());
		if (memberUserIds.isEmpty()) {
			return List.of();
		}
		return userRepository.findAllById(memberUserIds)
			.stream()
			.filter(u -> !u.getDeleted())
			.map(UserEntity::getDisplayName)
			.collect(Collectors.toList());
	}

	@Override
	@Transactional
	public void clearDeptAssignments(String tenantId, Long deptId) {
		userTenantMappingRepository.clearDeptIdByTenantIdAndDeptId(tenantId, deptId);
	}

}
