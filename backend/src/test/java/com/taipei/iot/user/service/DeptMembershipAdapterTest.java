package com.taipei.iot.user.service;

import com.taipei.iot.user.entity.UserEntity;
import com.taipei.iot.user.entity.UserTenantMappingEntity;
import com.taipei.iot.user.repository.UserRepository;
import com.taipei.iot.user.repository.UserTenantMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeptMembershipAdapterTest {

	@InjectMocks
	private DeptMembershipAdapter adapter;

	@Mock
	private UserTenantMappingRepository userTenantMappingRepository;

	@Mock
	private UserRepository userRepository;

	private UserTenantMappingEntity mapping(String userId) {
		return UserTenantMappingEntity.builder()
			.userId(userId)
			.tenantId("TENANT_A")
			.roleId("ROLE_ADMIN")
			.deptId(2L)
			.enabled(true)
			.build();
	}

	@Test
	void findActiveMemberDisplayNames_noMappings_returnsEmptyWithoutQueryingUsers() {
		when(userTenantMappingRepository.findByTenantIdAndDeptIdAndEnabledTrue("TENANT_A", 2L))
			.thenReturn(Collections.emptyList());

		List<String> result = adapter.findActiveMemberDisplayNames("TENANT_A", 2L);

		assertThat(result).isEmpty();
		verify(userRepository, never()).findAllById(org.mockito.ArgumentMatchers.anyList());
	}

	@Test
	void findActiveMemberDisplayNames_activeUser_returnsDisplayName() {
		when(userTenantMappingRepository.findByTenantIdAndDeptIdAndEnabledTrue("TENANT_A", 2L))
			.thenReturn(List.of(mapping("user-admin-001")));
		UserEntity user = UserEntity.builder()
			.userId("user-admin-001")
			.displayName("測試用戶")
			.email("test@example.com")
			.passwordHash("hash")
			.build();
		when(userRepository.findAllById(List.of("user-admin-001"))).thenReturn(List.of(user));

		List<String> result = adapter.findActiveMemberDisplayNames("TENANT_A", 2L);

		assertThat(result).containsExactly("測試用戶");
	}

	@Test
	void findActiveMemberDisplayNames_deletedUser_isFilteredOut() {
		when(userTenantMappingRepository.findByTenantIdAndDeptIdAndEnabledTrue("TENANT_A", 2L))
			.thenReturn(List.of(mapping("user-viewer-001")));
		UserEntity deleted = UserEntity.builder()
			.userId("user-viewer-001")
			.displayName("Viewer User")
			.email("viewer@test.com")
			.passwordHash("hash")
			.deleted(true)
			.build();
		when(userRepository.findAllById(List.of("user-viewer-001"))).thenReturn(List.of(deleted));

		List<String> result = adapter.findActiveMemberDisplayNames("TENANT_A", 2L);

		assertThat(result).isEmpty();
	}

	@Test
	void clearDeptAssignments_delegatesToMappingRepository() {
		adapter.clearDeptAssignments("TENANT_A", 2L);

		verify(userTenantMappingRepository).clearDeptIdByTenantIdAndDeptId("TENANT_A", 2L);
	}

}
