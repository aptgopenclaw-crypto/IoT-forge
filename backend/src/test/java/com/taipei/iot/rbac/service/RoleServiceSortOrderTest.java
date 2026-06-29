package com.taipei.iot.rbac.service;

import com.taipei.iot.auth.entity.RoleEntity;
import com.taipei.iot.auth.repository.RoleRepository;
import com.taipei.iot.rbac.dto.response.RoleDto;
import com.taipei.iot.rbac.repository.PermissionRepository;
import com.taipei.iot.rbac.repository.RolePermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * [RBAC N-11] 驗證 listRoles / listAssignableRoles 排序為 builtIn DESC, createTime DESC，
 * 使最近建立的角色排在前面（builtIn 角色仍最優先）。
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceSortOrderTest {

	@InjectMocks
	private RoleService roleService;

	@Mock
	private RoleRepository roleRepository;

	@Mock
	private RolePermissionRepository rolePermissionRepository;

	@Mock
	private PermissionRepository permissionRepository;

	@Test
	void listRoles_shouldCallCreateTimeDescMethod() {
		when(roleRepository.findAllByOrderByBuiltInDescCreateTimeDesc()).thenReturn(List.of());

		roleService.listRoles();

		verify(roleRepository).findAllByOrderByBuiltInDescCreateTimeDesc();
		verify(roleRepository, never()).findAllByOrderByBuiltInDescCodeAsc();
	}

	@Test
	void listRoles_shouldPreserveBuiltInFirstThenRecentFirst() {
		LocalDateTime now = LocalDateTime.now();
		List<RoleEntity> roles = List.of(
				RoleEntity.builder()
					.roleId("ROLE_ADMIN")
					.code("ADMIN")
					.name("Admin")
					.builtIn(true)
					.enabled(true)
					.createTime(now.minusDays(30))
					.build(),
				RoleEntity.builder()
					.roleId("r-new")
					.code("NEW_ROLE")
					.name("New Role")
					.builtIn(false)
					.enabled(true)
					.createTime(now.minusHours(1))
					.build(),
				RoleEntity.builder()
					.roleId("r-old")
					.code("OLD_ROLE")
					.name("Old Role")
					.builtIn(false)
					.enabled(true)
					.createTime(now.minusDays(7))
					.build());
		when(roleRepository.findAllByOrderByBuiltInDescCreateTimeDesc()).thenReturn(roles);

		List<RoleDto> result = roleService.listRoles();

		assertThat(result).hasSize(3);
		// builtIn first
		assertThat(result.get(0).getCode()).isEqualTo("ADMIN");
		// then most recently created
		assertThat(result.get(1).getCode()).isEqualTo("NEW_ROLE");
		assertThat(result.get(2).getCode()).isEqualTo("OLD_ROLE");
	}

	@Test
	void repositoryMethod_shouldExist() throws NoSuchMethodException {
		Method method = RoleRepository.class.getDeclaredMethod("findAllByOrderByBuiltInDescCreateTimeDesc");
		assertThat(method).isNotNull();
		assertThat(method.getReturnType()).isEqualTo(List.class);
	}

	@Test
	void oldCodeAscMethod_shouldStillExistForBackwardCompatibility() throws NoSuchMethodException {
		Method method = RoleRepository.class.getDeclaredMethod("findAllByOrderByBuiltInDescCodeAsc");
		assertThat(method).isNotNull();
	}

}
