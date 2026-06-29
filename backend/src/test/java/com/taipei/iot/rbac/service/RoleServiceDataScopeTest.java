package com.taipei.iot.rbac.service;

import com.taipei.iot.auth.entity.RoleEntity;
import com.taipei.iot.auth.repository.RoleRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.JwtClaimKeys;
import com.taipei.iot.rbac.dto.request.AssignRolePermissionsRequest;
import com.taipei.iot.rbac.dto.request.UpdateRoleRequest;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * [RBAC N-4] 驗證 updateRole / toggleEnabled / assignPermissions 的 DataScope 二次檢查。 非
 * SUPER_ADMIN 的操作者不可操作超出自身 DataScope 的角色。
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceDataScopeTest {

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

	/**
	 * 模擬一個非 SUPER_ADMIN 使用者，DataScope = THIS_LEVEL。
	 */
	private void mockDeptAdmin(String dataScope) {
		var auth = new UsernamePasswordAuthenticationToken("dept-admin-001", null,
				List.of(new SimpleGrantedAuthority("ROLE_DEPT_ADMIN")));
		auth.setDetails(Map.of(JwtClaimKeys.DATA_SCOPE, dataScope, JwtClaimKeys.TENANT_ID, "T1"));
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private void mockSuperAdmin() {
		var auth = new UsernamePasswordAuthenticationToken("super-admin", null,
				List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private RoleEntity buildRole(String roleId, String dataScope) {
		return RoleEntity.builder()
			.roleId(roleId)
			.code("CUSTOM_" + roleId)
			.name("Custom Role")
			.builtIn(false)
			.enabled(true)
			.dataScope(dataScope)
			.build();
	}

	// --- updateRole DataScope tests ---

	@Test
	void updateRole_deptAdmin_targetRoleAllScope_shouldThrowPermissionDenied() {
		mockDeptAdmin("THIS_LEVEL");
		RoleEntity targetRole = buildRole("ROLE_GLOBAL_AUDITOR", "ALL");
		when(roleRepository.findById("ROLE_GLOBAL_AUDITOR")).thenReturn(Optional.of(targetRole));

		UpdateRoleRequest request = new UpdateRoleRequest();
		request.setName("Renamed");

		assertThatThrownBy(() -> roleService.updateRole("ROLE_GLOBAL_AUDITOR", request))
			.isInstanceOf(BusinessException.class)
			.satisfies(
					ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.PERMISSION_DENIED));
	}

	@Test
	void updateRole_deptAdmin_targetRoleThisLevelScope_shouldPass() {
		mockDeptAdmin("THIS_LEVEL");
		RoleEntity targetRole = buildRole("ROLE_DEPT_VIEWER", "THIS_LEVEL");
		when(roleRepository.findById("ROLE_DEPT_VIEWER")).thenReturn(Optional.of(targetRole));
		when(roleRepository.save(any())).thenReturn(targetRole);

		UpdateRoleRequest request = new UpdateRoleRequest();
		request.setName("Updated Name");
		request.setEnabled(true);

		var result = roleService.updateRole("ROLE_DEPT_VIEWER", request);
		assertThat(result).isNotNull();
		verify(roleRepository).save(any());
	}

	@Test
	void updateRole_superAdmin_targetRoleAllScope_shouldPass() {
		mockSuperAdmin();
		RoleEntity targetRole = buildRole("ROLE_GLOBAL_AUDITOR", "ALL");
		when(roleRepository.findById("ROLE_GLOBAL_AUDITOR")).thenReturn(Optional.of(targetRole));
		when(roleRepository.save(any())).thenReturn(targetRole);

		UpdateRoleRequest request = new UpdateRoleRequest();
		request.setName("Renamed by SA");
		request.setEnabled(true);

		var result = roleService.updateRole("ROLE_GLOBAL_AUDITOR", request);
		assertThat(result).isNotNull();
	}

	// --- toggleEnabled DataScope tests ---

	@Test
	void toggleEnabled_deptAdmin_targetRoleAllScope_shouldThrowPermissionDenied() {
		mockDeptAdmin("THIS_LEVEL");
		RoleEntity targetRole = buildRole("ROLE_GLOBAL_AUDITOR", "ALL");
		when(roleRepository.findById("ROLE_GLOBAL_AUDITOR")).thenReturn(Optional.of(targetRole));

		assertThatThrownBy(() -> roleService.toggleEnabled("ROLE_GLOBAL_AUDITOR", false))
			.isInstanceOf(BusinessException.class)
			.satisfies(
					ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.PERMISSION_DENIED));
	}

	@Test
	void toggleEnabled_deptAdmin_targetRoleThisLevelScope_shouldPass() {
		mockDeptAdmin("THIS_LEVEL_AND_BELOW");
		RoleEntity targetRole = buildRole("ROLE_DEPT_VIEWER", "THIS_LEVEL");
		when(roleRepository.findById("ROLE_DEPT_VIEWER")).thenReturn(Optional.of(targetRole));

		roleService.toggleEnabled("ROLE_DEPT_VIEWER", false);
		verify(roleRepository).save(any());
	}

	@Test
	void toggleEnabled_superAdmin_targetRoleAllScope_shouldPass() {
		mockSuperAdmin();
		RoleEntity targetRole = buildRole("ROLE_GLOBAL_AUDITOR", "ALL");
		when(roleRepository.findById("ROLE_GLOBAL_AUDITOR")).thenReturn(Optional.of(targetRole));

		roleService.toggleEnabled("ROLE_GLOBAL_AUDITOR", false);
		verify(roleRepository).save(any());
	}

	// --- assignPermissions DataScope tests ---

	@Test
	void assignPermissions_deptAdmin_targetRoleAllScope_shouldThrowPermissionDenied() {
		mockDeptAdmin("THIS_LEVEL");
		RoleEntity targetRole = buildRole("ROLE_GLOBAL_AUDITOR", "ALL");
		when(roleRepository.findById("ROLE_GLOBAL_AUDITOR")).thenReturn(Optional.of(targetRole));

		AssignRolePermissionsRequest request = new AssignRolePermissionsRequest();
		request.setPermissionIds(List.of("PERM_1"));

		assertThatThrownBy(() -> roleService.assignPermissions("ROLE_GLOBAL_AUDITOR", request))
			.isInstanceOf(BusinessException.class)
			.satisfies(
					ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.PERMISSION_DENIED));
	}

	@Test
	void assignPermissions_superAdmin_targetRoleAllScope_shouldPass() {
		mockSuperAdmin();
		RoleEntity targetRole = buildRole("ROLE_GLOBAL_AUDITOR", "ALL");
		when(roleRepository.findById("ROLE_GLOBAL_AUDITOR")).thenReturn(Optional.of(targetRole));

		var perm = com.taipei.iot.rbac.entity.PermissionEntity.builder()
			.permissionId("PERM_1")
			.code("audit:read")
			.name("Audit Read")
			.groupName("Audit")
			.build();
		when(permissionRepository.findAllById(List.of("PERM_1"))).thenReturn(List.of(perm));

		AssignRolePermissionsRequest request = new AssignRolePermissionsRequest();
		request.setPermissionIds(List.of("PERM_1"));

		var result = roleService.assignPermissions("ROLE_GLOBAL_AUDITOR", request);
		assertThat(result.getRoleId()).isEqualTo("ROLE_GLOBAL_AUDITOR");
	}

}
