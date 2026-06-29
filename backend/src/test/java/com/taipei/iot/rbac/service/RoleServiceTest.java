package com.taipei.iot.rbac.service;

import com.taipei.iot.auth.entity.RoleEntity;
import com.taipei.iot.auth.repository.RoleRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.rbac.dto.request.AssignRolePermissionsRequest;
import com.taipei.iot.rbac.dto.request.CreateRoleRequest;
import com.taipei.iot.rbac.dto.request.UpdateRoleRequest;
import com.taipei.iot.rbac.dto.response.RoleDto;
import com.taipei.iot.rbac.dto.response.RolePermissionListDto;
import com.taipei.iot.rbac.entity.PermissionEntity;
import com.taipei.iot.rbac.entity.RolePermissionEntity;
import com.taipei.iot.rbac.repository.PermissionRepository;
import com.taipei.iot.rbac.repository.RolePermissionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

	@InjectMocks
	private RoleService roleService;

	@Mock
	private RoleRepository roleRepository;

	@Mock
	private RolePermissionRepository rolePermissionRepository;

	@Mock
	private PermissionRepository permissionRepository;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	private void mockSuperAdmin() {
		var auth = new UsernamePasswordAuthenticationToken("super-admin", null,
				List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	@Test
	void listRoles_shouldReturn5RolesExcludingSuperAdmin() {
		List<RoleEntity> roles = List.of(
				RoleEntity.builder()
					.roleId("ROLE_SUPER_ADMIN")
					.code("SUPER_ADMIN")
					.name("Super Admin")
					.builtIn(true)
					.enabled(true)
					.build(),
				RoleEntity.builder()
					.roleId("ROLE_ADMIN")
					.code("ADMIN")
					.name("Admin")
					.builtIn(true)
					.enabled(true)
					.build(),
				RoleEntity.builder()
					.roleId("ROLE_OPERATOR")
					.code("OPERATOR")
					.name("Operator")
					.builtIn(true)
					.enabled(true)
					.build(),
				RoleEntity.builder()
					.roleId("ROLE_VIEWER")
					.code("VIEWER")
					.name("Viewer")
					.builtIn(true)
					.enabled(true)
					.build(),
				RoleEntity.builder()
					.roleId("ROLE_FIELD_USER")
					.code("FIELD_USER")
					.name("Field User")
					.builtIn(true)
					.enabled(true)
					.build(),
				RoleEntity.builder()
					.roleId("ROLE_MONITOR")
					.code("MONITOR")
					.name("Monitor")
					.builtIn(true)
					.enabled(true)
					.build());
		when(roleRepository.findAllByOrderByBuiltInDescCreateTimeDesc()).thenReturn(roles);

		List<RoleDto> result = roleService.listRoles();

		assertEquals(5, result.size());
		assertTrue(result.stream().noneMatch(r -> "SUPER_ADMIN".equals(r.getCode())));
		assertTrue(result.stream().allMatch(RoleDto::isBuiltIn));
	}

	@Test
	void createRole_shouldCreateAndReturn() {
		when(roleRepository.existsByCode("CUSTOM")).thenReturn(false);
		when(roleRepository.save(any(RoleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		CreateRoleRequest req = CreateRoleRequest.builder()
			.code("CUSTOM")
			.name("Custom Role")
			.description("A test role")
			.dataScope("ALL")
			.build();

		RoleDto result = roleService.createRole(req);

		assertEquals("CUSTOM", result.getCode());
		assertEquals("Custom Role", result.getName());
		assertFalse(result.isBuiltIn());
		assertTrue(result.isEnabled());
		verify(roleRepository).save(any(RoleEntity.class));
	}

	@Test
	void createRole_duplicateCode_shouldThrow() {
		when(roleRepository.existsByCode("ADMIN")).thenReturn(true);

		CreateRoleRequest req = CreateRoleRequest.builder().code("ADMIN").name("Dup").build();

		BusinessException ex = assertThrows(BusinessException.class, () -> roleService.createRole(req));
		assertEquals(ErrorCode.ROLE_CODE_DUPLICATE, ex.getErrorCode());
	}

	@Test
	void updateRole_shouldUpdateAndReturn() {
		RoleEntity entity = RoleEntity.builder()
			.roleId("ROLE_X")
			.code("X")
			.name("Old")
			.builtIn(false)
			.enabled(true)
			.build();
		when(roleRepository.findById("ROLE_X")).thenReturn(Optional.of(entity));
		when(roleRepository.save(any(RoleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		UpdateRoleRequest req = UpdateRoleRequest.builder()
			.name("New Name")
			.description("Updated")
			.enabled(true)
			.dataScope("THIS_LEVEL")
			.build();

		RoleDto result = roleService.updateRole("ROLE_X", req);

		assertEquals("New Name", result.getName());
		assertEquals("THIS_LEVEL", result.getDataScope());
	}

	@Test
	void updateRole_notFound_shouldThrow() {
		when(roleRepository.findById("ROLE_INVALID")).thenReturn(Optional.empty());

		UpdateRoleRequest req = UpdateRoleRequest.builder().name("X").enabled(true).build();

		BusinessException ex = assertThrows(BusinessException.class, () -> roleService.updateRole("ROLE_INVALID", req));
		assertEquals(ErrorCode.ROLE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void toggleEnabled_shouldFlipFlag() {
		RoleEntity entity = RoleEntity.builder()
			.roleId("ROLE_X")
			.code("X")
			.name("X")
			.builtIn(false)
			.enabled(true)
			.build();
		when(roleRepository.findById("ROLE_X")).thenReturn(Optional.of(entity));
		when(roleRepository.save(any(RoleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		roleService.toggleEnabled("ROLE_X", false);

		assertFalse(entity.getEnabled());
		verify(roleRepository).save(entity);
	}

	@Test
	void assignPermissions_shouldReplaceAllPermissions() {
		mockSuperAdmin();

		RoleEntity role = RoleEntity.builder()
			.roleId("ROLE_ADMIN")
			.code("ADMIN")
			.name("Admin")
			.builtIn(true)
			.enabled(true)
			.build();
		when(roleRepository.findById("ROLE_ADMIN")).thenReturn(Optional.of(role));

		List<PermissionEntity> perms = List.of(PermissionEntity.builder()
			.permissionId("P1")
			.code("USER_LIST")
			.name("User list")
			.groupName("User")
			.build());
		when(permissionRepository.findAllById(List.of("P1"))).thenReturn(perms);

		AssignRolePermissionsRequest req = AssignRolePermissionsRequest.builder().permissionIds(List.of("P1")).build();

		RolePermissionListDto result = roleService.assignPermissions("ROLE_ADMIN", req);

		assertEquals("ROLE_ADMIN", result.getRoleId());
		assertEquals(1, result.getPermissions().size());
		verify(rolePermissionRepository).deleteByRoleId("ROLE_ADMIN");
		verify(rolePermissionRepository).saveAll(anyList());
	}

	@Test
	void getRolePermissions_shouldReturnPermissionsForRole() {
		RoleEntity role = RoleEntity.builder()
			.roleId("ROLE_ADMIN")
			.code("ADMIN")
			.name("Admin")
			.builtIn(true)
			.enabled(true)
			.build();
		when(roleRepository.findById("ROLE_ADMIN")).thenReturn(Optional.of(role));

		List<RolePermissionEntity> rps = List.of(
				RolePermissionEntity.builder().roleId("ROLE_ADMIN").permissionId("PERM_USER_LIST").build(),
				RolePermissionEntity.builder().roleId("ROLE_ADMIN").permissionId("PERM_DEPT_LIST").build());
		when(rolePermissionRepository.findByRoleId("ROLE_ADMIN")).thenReturn(rps);

		List<PermissionEntity> perms = List.of(
				PermissionEntity.builder()
					.permissionId("PERM_USER_LIST")
					.code("USER_LIST")
					.name("User list")
					.groupName("User management")
					.build(),
				PermissionEntity.builder()
					.permissionId("PERM_DEPT_LIST")
					.code("DEPT_LIST")
					.name("Department list")
					.groupName("Department management")
					.build());
		when(permissionRepository.findAllById(List.of("PERM_USER_LIST", "PERM_DEPT_LIST"))).thenReturn(perms);

		RolePermissionListDto result = roleService.getRolePermissions("ROLE_ADMIN");

		assertEquals("ROLE_ADMIN", result.getRoleId());
		assertEquals("ADMIN", result.getRoleCode());
		assertEquals(2, result.getPermissions().size());
	}

	@Test
	void getRolePermissions_roleNotFound_shouldThrow() {
		when(roleRepository.findById("ROLE_INVALID")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> roleService.getRolePermissions("ROLE_INVALID"));
		assertEquals(ErrorCode.ROLE_NOT_FOUND, ex.getErrorCode());
	}

}
