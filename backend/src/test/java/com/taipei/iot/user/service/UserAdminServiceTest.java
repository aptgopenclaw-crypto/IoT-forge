package com.taipei.iot.user.service;

import com.taipei.iot.auth.entity.RoleEntity;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.entity.UserTenantMappingEntity;
import com.taipei.iot.auth.repository.RoleRepository;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.auth.repository.UserTenantMappingRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.dept.service.DataScopeHelper;
import com.taipei.iot.rbac.service.RoleService;
import com.taipei.iot.tenant.TenantContext;
import com.taipei.iot.tenant.TenantEntity;
import com.taipei.iot.tenant.TenantRepository;
import com.taipei.iot.user.dto.request.AddTenantRoleRequest;
import com.taipei.iot.user.dto.request.CreateUserRequest;
import com.taipei.iot.user.dto.request.UpdateUserRequest;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.user.dto.response.UserListItemDto;
import com.taipei.iot.user.dto.response.UserTenantMappingDto;
import com.taipei.iot.user.entity.PasswordHistoryEntity;
import com.taipei.iot.user.repository.PasswordHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAdminServiceTest {

	@InjectMocks
	private UserAdminService userAdminService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserTenantMappingRepository userTenantMappingRepository;

	@Mock
	private RoleRepository roleRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private PasswordValidator passwordValidator;

	@Mock
	private PasswordHistoryRepository passwordHistoryRepository;

	@Mock
	private UserAuditService userAuditService;

	@Mock
	private DataScopeHelper dataScopeHelper;

	@Mock
	private DeptInfoRepository deptInfoRepository;

	@Mock
	private RoleService roleService;

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private com.taipei.iot.auth.policy.PasswordPolicyResolver passwordPolicyResolver;

	private UserEntity targetUser;

	private RoleEntity viewerRole;

	private TenantEntity enabledTenant;

	@BeforeEach
	void setUp() {
		TenantContext.setCurrentTenantId("TENANT_A");

		// 預設 DataScope = ALL（不限制）
		when(dataScopeHelper.getVisibleDeptIds()).thenReturn(Collections.emptyList());
		when(dataScopeHelper.isDeptInScope(any())).thenReturn(true);
		when(roleService.isRoleAssignable(anyString())).thenReturn(true);
		enabledTenant = new TenantEntity();
		enabledTenant.setTenantId("TENANT_A");
		enabledTenant.setEnabled(true);
		when(tenantRepository.findById(anyString())).thenReturn(Optional.of(enabledTenant));

		// [Phase 3] Default policy: force-change-on-first-login=true (platform default)
		// so existing createUser tests see the new column populated but their
		// assertions on other fields remain unchanged.
		when(passwordPolicyResolver.resolve(any())).thenReturn(com.taipei.iot.auth.policy.PasswordPolicy.builder()
			.minLength(8)
			.requireUppercase(true)
			.requireLowercase(true)
			.requireDigit(true)
			.requireSpecial(true)
			.historyCount(5)
			.expireDays(90)
			.forceChangeOnFirstLogin(true)
			.forceChangeOnAdminReset(true)
			.build());

		targetUser = UserEntity.builder()
			.userId("user-target-001")
			.email("target@test.com")
			.displayName("Target User")
			.phone("0912345678")
			.enabled(true)
			.locked(false)
			.loginFailCount(0)
			.isSuperAdmin(false)
			.build();

		viewerRole = RoleEntity.builder().roleId("ROLE_VIEWER").code("VIEWER").name("檢視者").build();
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	// === createUser ===

	@Test
	void createUser_success() {
		when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
		when(passwordEncoder.encode("Password1")).thenReturn("$2a$10$encodedHash");
		when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
		when(userTenantMappingRepository.save(any(UserTenantMappingEntity.class))).thenAnswer(inv -> {
			UserTenantMappingEntity m = inv.getArgument(0);
			m.setId(1L);
			return m;
		});
		when(passwordHistoryRepository.save(any(PasswordHistoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
		when(roleRepository.findById("ROLE_VIEWER")).thenReturn(Optional.of(viewerRole));
		when(userRepository.findById(anyString())).thenAnswer(inv -> {
			String uid = inv.getArgument(0);
			UserEntity u = UserEntity.builder()
				.userId(uid)
				.email("new@test.com")
				.displayName("New User")
				.phone("0911111111")
				.enabled(true)
				.locked(false)
				.loginFailCount(0)
				.isSuperAdmin(false)
				.build();
			return Optional.of(u);
		});

		CreateUserRequest req = CreateUserRequest.builder()
			.email("new@test.com")
			.displayName("New User")
			.phone("0911111111")
			.initialPassword("Password1")
			.roleId("ROLE_VIEWER")
			.build();

		UserListItemDto result = userAdminService.createUser("user-admin-001", req);

		assertNotNull(result);
		assertEquals("new@test.com", result.getEmail());
		verify(passwordValidator).validate(any(), eq("Password1"), any());
		verify(passwordHistoryRepository).save(any(PasswordHistoryEntity.class));
		verify(userAuditService).logAction(eq("CREATE"), eq("user-admin-001"), anyString(), anyString());
	}

	@Test
	void createUser_emailDuplicate_shouldThrowUserAlreadyExists() {
		when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

		CreateUserRequest req = CreateUserRequest.builder()
			.email("existing@test.com")
			.displayName("Dup User")
			.initialPassword("Password1")
			.roleId("ROLE_VIEWER")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userAdminService.createUser("user-admin-001", req));
		assertEquals(ErrorCode.USER_ALREADY_EXISTS, ex.getErrorCode());
	}

	@Test
	void createUser_concurrentDuplicate_shouldCatchDataIntegrityViolation() {
		// Pre-check passes (no existing email), but concurrent insert causes DB
		// constraint violation
		when(userRepository.existsByEmail("race@test.com")).thenReturn(false);
		when(passwordEncoder.encode("Password1!")).thenReturn("$2a$10$encodedHash");
		when(userRepository.save(any(UserEntity.class)))
			.thenThrow(new DataIntegrityViolationException("unique constraint violation"));

		CreateUserRequest req = CreateUserRequest.builder()
			.email("race@test.com")
			.displayName("Race User")
			.initialPassword("Password1!")
			.roleId("ROLE_VIEWER")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userAdminService.createUser("user-admin-001", req));
		assertEquals(ErrorCode.USER_ALREADY_EXISTS, ex.getErrorCode());
	}

	@Test
	void createUser_weakPassword_shouldThrowResetPasswordError() {
		when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
		doThrow(new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼長度至少 8 字元")).when(passwordValidator)
			.validate(any(), eq("short"), any());

		CreateUserRequest req = CreateUserRequest.builder()
			.email("new@test.com")
			.displayName("New User")
			.initialPassword("short")
			.roleId("ROLE_VIEWER")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userAdminService.createUser("user-admin-001", req));
		assertEquals(ErrorCode.RESET_PASSWORD_ERROR, ex.getErrorCode());
	}

	// === updateUser ===

	@Test
	void updateUser_success() {
		when(userRepository.findById("user-target-001")).thenReturn(Optional.of(targetUser));
		when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
		when(userTenantMappingRepository.findByUserIdAndTenantId("user-target-001", "TENANT_A"))
			.thenReturn(Optional.empty());

		UpdateUserRequest req = UpdateUserRequest.builder().displayName("Updated Name").phone("0987654321").build();

		UserListItemDto result = userAdminService.updateUser("user-admin-001", "user-target-001", req);

		assertEquals("Updated Name", result.getDisplayName());
		verify(userAuditService).logChange(eq("UPDATE"), eq("user-admin-001"), eq("user-target-001"), anyString(),
				anyMap(), anyMap());
	}

	// === disableUser ===

	@Test
	void disableUser_success() {
		when(userRepository.findById("user-target-001")).thenReturn(Optional.of(targetUser));
		when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		userAdminService.disableUser("user-admin-001", "user-target-001");

		assertFalse(targetUser.getEnabled());
		verify(userAuditService).logAction(eq("DISABLE"), eq("user-admin-001"), eq("user-target-001"), anyString());
	}

	@Test
	void disableUser_self_shouldThrowPermissionDenied() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> userAdminService.disableUser("user-admin-001", "user-admin-001"));
		assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
	}

	// === addTenantRole ===

	@Test
	void addTenantRole_success() {
		when(userRepository.findById("user-target-001")).thenReturn(Optional.of(targetUser));
		when(userTenantMappingRepository.findByUserIdAndTenantId("user-target-001", "TENANT_A"))
			.thenReturn(Optional.empty());
		when(userTenantMappingRepository.save(any(UserTenantMappingEntity.class))).thenAnswer(inv -> {
			UserTenantMappingEntity m = inv.getArgument(0);
			m.setId(10L);
			return m;
		});
		when(roleRepository.findById("ROLE_VIEWER")).thenReturn(Optional.of(viewerRole));

		AddTenantRoleRequest req = AddTenantRoleRequest.builder().roleId("ROLE_VIEWER").build();

		UserTenantMappingDto result = userAdminService.addTenantRole("user-admin-001", "user-target-001", req);

		assertNotNull(result);
		assertEquals("TENANT_A", result.getTenantId());
		verify(userAuditService).logAction(eq("ADD_TENANT"), eq("user-admin-001"), eq("user-target-001"), anyString());
	}

	@Test
	void addTenantRole_duplicate_shouldThrowConflict() {
		when(userRepository.findById("user-target-001")).thenReturn(Optional.of(targetUser));
		UserTenantMappingEntity existing = UserTenantMappingEntity.builder()
			.id(5L)
			.userId("user-target-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_VIEWER")
			.build();
		when(userTenantMappingRepository.findByUserIdAndTenantId("user-target-001", "TENANT_A"))
			.thenReturn(Optional.of(existing));

		AddTenantRoleRequest req = AddTenantRoleRequest.builder().roleId("ROLE_VIEWER").build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userAdminService.addTenantRole("user-admin-001", "user-target-001", req));
		assertEquals(ErrorCode.USER_ALREADY_EXISTS, ex.getErrorCode());
	}

	@Test
	void addTenantRole_tenantNotFound_shouldThrow() {
		when(userRepository.findById("user-target-001")).thenReturn(Optional.of(targetUser));
		when(tenantRepository.findById("NON_EXIST_TENANT")).thenReturn(Optional.empty());

		AddTenantRoleRequest req = AddTenantRoleRequest.builder()
			.tenantId("NON_EXIST_TENANT")
			.roleId("ROLE_VIEWER")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userAdminService.addTenantRole("user-admin-001", "user-target-001", req));
		assertEquals(ErrorCode.TENANT_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void addTenantRole_tenantDisabled_shouldThrow() {
		when(userRepository.findById("user-target-001")).thenReturn(Optional.of(targetUser));
		TenantEntity disabledTenant = new TenantEntity();
		disabledTenant.setTenantId("TENANT_DISABLED");
		disabledTenant.setEnabled(false);
		when(tenantRepository.findById("TENANT_DISABLED")).thenReturn(Optional.of(disabledTenant));

		AddTenantRoleRequest req = AddTenantRoleRequest.builder()
			.tenantId("TENANT_DISABLED")
			.roleId("ROLE_VIEWER")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userAdminService.addTenantRole("user-admin-001", "user-target-001", req));
		assertEquals(ErrorCode.TENANT_DISABLED, ex.getErrorCode());
	}

	@Test
	void addTenantRole_roleNotAssignable_shouldThrowPermissionDenied() {
		when(userRepository.findById("user-target-001")).thenReturn(Optional.of(targetUser));
		when(roleService.isRoleAssignable("ROLE_SUPER")).thenReturn(false);

		AddTenantRoleRequest req = AddTenantRoleRequest.builder().roleId("ROLE_SUPER").build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userAdminService.addTenantRole("user-admin-001", "user-target-001", req));
		assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
	}

	@Test
	void addTenantRole_deptNotInScope_shouldThrowPermissionDenied() {
		when(userRepository.findById("user-target-001")).thenReturn(Optional.of(targetUser));
		when(dataScopeHelper.isDeptInScope(99L)).thenReturn(false);

		AddTenantRoleRequest req = AddTenantRoleRequest.builder().roleId("ROLE_VIEWER").deptId(99L).build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userAdminService.addTenantRole("user-admin-001", "user-target-001", req));
		assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
	}

	// === removeTenantRole ===

	@Test
	void removeTenantRole_success() {
		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.id(10L)
			.userId("user-target-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_VIEWER")
			.build();
		when(userTenantMappingRepository.findById(10L)).thenReturn(Optional.of(mapping));

		userAdminService.removeTenantRole("user-admin-001", "user-target-001", 10L);

		verify(userTenantMappingRepository).delete(mapping);
		verify(userAuditService).logAction(eq("REMOVE_TENANT"), eq("user-admin-001"), eq("user-target-001"),
				anyString());
	}

	@Test
	void removeTenantRole_notFound_shouldThrowMappingNotFound() {
		when(userTenantMappingRepository.findById(999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userAdminService.removeTenantRole("user-admin-001", "user-target-001", 999L));
		assertEquals(ErrorCode.MAPPING_NOT_FOUND, ex.getErrorCode());
	}

	// === listUsers ===

	@Test
	void listUsers_batchFetchesDeptNames_noNPlusOne() {
		// N-2: 驗證 listUsers 以 findAllById 批次取得部門名稱，而非每筆 findById
		UserTenantMappingEntity m1 = UserTenantMappingEntity.builder()
			.id(1L)
			.userId("user-target-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_VIEWER")
			.deptId(10L)
			.enabled(true)
			.build();
		m1.setUser(targetUser);
		m1.setRole(viewerRole);
		UserTenantMappingEntity m2 = UserTenantMappingEntity.builder()
			.id(2L)
			.userId("user-target-002")
			.tenantId("TENANT_A")
			.roleId("ROLE_VIEWER")
			.deptId(10L)
			.enabled(true)
			.build();
		m2.setUser(targetUser);
		m2.setRole(viewerRole);
		UserTenantMappingEntity m3 = UserTenantMappingEntity.builder()
			.id(3L)
			.userId("user-target-003")
			.tenantId("TENANT_A")
			.roleId("ROLE_VIEWER")
			.deptId(20L)
			.enabled(true)
			.build();
		m3.setUser(targetUser);
		m3.setRole(viewerRole);

		com.taipei.iot.dept.entity.DeptInfoEntity d10 = com.taipei.iot.dept.entity.DeptInfoEntity.builder()
			.deptId(10L)
			.deptName("研發部")
			.build();
		com.taipei.iot.dept.entity.DeptInfoEntity d20 = com.taipei.iot.dept.entity.DeptInfoEntity.builder()
			.deptId(20L)
			.deptName("業務部")
			.build();
		when(deptInfoRepository.findAllById(anyCollection())).thenReturn(List.of(d10, d20));

		Page<UserTenantMappingEntity> page = new PageImpl<>(List.of(m1, m2, m3), PageRequest.of(0, 20), 3);
		when(userTenantMappingRepository.findActiveByTenantId(eq("TENANT_A"), isNull(), any())).thenReturn(page);

		PageResponse<UserListItemDto> result = userAdminService.listUsers(0, 20, null, null);

		assertEquals(3, result.getContent().size());
		assertEquals("研發部", result.getContent().get(0).getDeptName());
		assertEquals("研發部", result.getContent().get(1).getDeptName());
		assertEquals("業務部", result.getContent().get(2).getDeptName());
		// 批次查詢一次即可，且絕不退化為 per-row findById
		verify(deptInfoRepository, times(1)).findAllById(anyCollection());
		verify(deptInfoRepository, never()).findById(anyLong());
	}

	@Test
	void listUsers_adminSeesOwnTenant() {
		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.id(1L)
			.userId("user-target-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_VIEWER")
			.enabled(true)
			.build();
		mapping.setUser(targetUser);
		mapping.setRole(viewerRole);

		Page<UserTenantMappingEntity> page = new PageImpl<>(List.of(mapping), PageRequest.of(0, 20), 1);
		when(userTenantMappingRepository.findActiveByTenantId(eq("TENANT_A"), isNull(), any())).thenReturn(page);

		PageResponse<UserListItemDto> result = userAdminService.listUsers(0, 20, null, null);

		assertNotNull(result);
		assertEquals(1, result.getContent().size());
		assertEquals("target@test.com", result.getContent().get(0).getEmail());
	}

	@Test
	void listUsers_superAdminSeesAll() {
		TenantContext.setSystemContext();

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.id(1L)
			.userId("user-target-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_VIEWER")
			.enabled(true)
			.build();
		mapping.setUser(targetUser);
		mapping.setRole(viewerRole);

		Page<UserTenantMappingEntity> page = new PageImpl<>(List.of(mapping), PageRequest.of(0, 20), 1);
		when(userTenantMappingRepository.findAllActive(isNull(), any())).thenReturn(page);

		PageResponse<UserListItemDto> result = userAdminService.listUsers(0, 20, null, null);

		assertNotNull(result);
		assertEquals(1, result.getContent().size());
	}

	// === DataScope 過濾測試 ===

	@Test
	void listUsers_deptAdmin_onlySeesOwnDept() {
		when(dataScopeHelper.getVisibleDeptIds()).thenReturn(List.of(10L));

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.id(1L)
			.userId("u-squad1-off1")
			.tenantId("TENANT_A")
			.roleId("ROLE_DEPT_USER")
			.deptId(10L)
			.enabled(true)
			.build();
		mapping.setUser(UserEntity.builder()
			.userId("u-squad1-off1")
			.email("squad1-a@test.com")
			.displayName("北區承辦")
			.enabled(true)
			.locked(false)
			.loginFailCount(0)
			.isSuperAdmin(false)
			.build());
		mapping.setRole(viewerRole);

		Page<UserTenantMappingEntity> page = new PageImpl<>(List.of(mapping), PageRequest.of(0, 20), 1);
		when(userTenantMappingRepository.findActiveByTenantIdAndDeptId(eq("TENANT_A"), eq(10L), isNull(), any()))
			.thenReturn(page);

		PageResponse<UserListItemDto> result = userAdminService.listUsers(0, 20, null, null);

		assertEquals(1, result.getContent().size());
		verify(userTenantMappingRepository).findActiveByTenantIdAndDeptId(eq("TENANT_A"), eq(10L), isNull(), any());
		verify(userTenantMappingRepository, never()).findActiveByTenantId(anyString(), any(), any());
	}

	@Test
	void listUsers_deptAdmin_seesMultipleDepts() {
		when(dataScopeHelper.getVisibleDeptIds()).thenReturn(List.of(1L, 10L, 11L));

		Page<UserTenantMappingEntity> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
		when(userTenantMappingRepository.findActiveByTenantIdAndDeptIdIn(eq("TENANT_A"), eq(List.of(1L, 10L, 11L)),
				isNull(), any()))
			.thenReturn(page);

		PageResponse<UserListItemDto> result = userAdminService.listUsers(0, 20, null, null);

		assertNotNull(result);
		verify(userTenantMappingRepository).findActiveByTenantIdAndDeptIdIn(eq("TENANT_A"), eq(List.of(1L, 10L, 11L)),
				isNull(), any());
	}

	// === getUserTenantMappings (N-3: tenant.enabled 過濾) ===

	@Test
	void getUserTenantMappings_shouldOnlyReturnEnabledTenantMappings() {
		TenantContext.setSystemContext();

		UserTenantMappingEntity m1 = UserTenantMappingEntity.builder()
			.id(1L)
			.userId("user-target-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_VIEWER")
			.enabled(true)
			.build();
		m1.setRole(viewerRole);
		when(userTenantMappingRepository.findByUserIdAndTenantEnabled("user-target-001")).thenReturn(List.of(m1));

		List<UserTenantMappingDto> result = userAdminService.getUserTenantMappings("user-target-001");

		assertEquals(1, result.size());
		assertEquals("TENANT_A", result.get(0).getTenantId());
		verify(userTenantMappingRepository).findByUserIdAndTenantEnabled("user-target-001");
		verify(userTenantMappingRepository, never()).findByUserId(anyString());
	}

	@Test
	void createUser_deptOutOfScope_shouldThrowPermissionDenied() {
		when(dataScopeHelper.isDeptInScope(20L)).thenReturn(false);

		CreateUserRequest req = CreateUserRequest.builder()
			.email("new@test.com")
			.displayName("New User")
			.initialPassword("Password1")
			.roleId("ROLE_DEPT_USER")
			.deptId(20L)
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userAdminService.createUser("user-admin-001", req));
		assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	void createUser_deptInScope_shouldSucceed() {
		when(dataScopeHelper.isDeptInScope(10L)).thenReturn(true);
		when(userRepository.existsByEmail("squad-new@test.com")).thenReturn(false);
		when(passwordEncoder.encode("Password1")).thenReturn("$2a$10$encodedHash");
		when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
		when(userTenantMappingRepository.save(any(UserTenantMappingEntity.class))).thenAnswer(inv -> {
			UserTenantMappingEntity m = inv.getArgument(0);
			m.setId(1L);
			return m;
		});
		when(passwordHistoryRepository.save(any(PasswordHistoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
		when(roleRepository.findById("ROLE_DEPT_USER")).thenReturn(Optional.of(viewerRole));
		when(userRepository.findById(anyString())).thenAnswer(inv -> {
			String uid = inv.getArgument(0);
			return Optional.of(UserEntity.builder()
				.userId(uid)
				.email("squad-new@test.com")
				.displayName("新承辦")
				.enabled(true)
				.locked(false)
				.loginFailCount(0)
				.isSuperAdmin(false)
				.build());
		});

		CreateUserRequest req = CreateUserRequest.builder()
			.email("squad-new@test.com")
			.displayName("新承辦")
			.initialPassword("Password1")
			.roleId("ROLE_DEPT_USER")
			.deptId(10L)
			.build();

		UserListItemDto result = userAdminService.createUser("u-squad1-mgr", req);

		assertNotNull(result);
		verify(userRepository).save(any(UserEntity.class));
	}

}
