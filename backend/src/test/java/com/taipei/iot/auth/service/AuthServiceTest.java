package com.taipei.iot.auth.service;

import com.taipei.iot.auth.dto.request.ForgotPasswordRequest;
import com.taipei.iot.auth.dto.request.LoginRequest;
import com.taipei.iot.auth.dto.request.ResetPasswordRequest;
import com.taipei.iot.auth.dto.request.SelectTenantRequest;
import com.taipei.iot.auth.dto.request.SwitchTenantRequest;
import com.taipei.iot.auth.dto.response.LoginResult;
import com.taipei.iot.auth.dto.response.TokenResult;
import com.taipei.iot.auth.dto.response.UserInfoDto;
import com.taipei.iot.audit.repository.UserEventLogRepository;
import com.taipei.iot.auth.entity.RoleEntity;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.entity.UserResetPasswordTokenEntity;
import com.taipei.iot.auth.entity.UserTenantMappingEntity;
import com.taipei.iot.auth.repository.ChangePasswordLogRepository;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.auth.repository.UserResetPasswordTokenRepository;
import com.taipei.iot.auth.repository.UserTenantMappingRepository;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.auth.service.impl.AuthServiceImpl;
import com.taipei.iot.rbac.repository.PermissionRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.user.repository.PasswordHistoryRepository;
import com.taipei.iot.user.service.PasswordValidator;
import com.taipei.iot.tenant.TenantContext;
import com.taipei.iot.tenant.TenantEntity;
import com.taipei.iot.tenant.TenantRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

	@InjectMocks
	private AuthServiceImpl authService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserTenantMappingRepository userTenantMappingRepository;

	@Mock
	private UserEventLogRepository userEventLogRepository;

	@Mock
	private ChangePasswordLogRepository changePasswordLogRepository;

	@Mock
	private UserResetPasswordTokenRepository userResetPasswordTokenRepository;

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private CaptchaService captchaService;

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private EntityManager entityManager;

	@Mock
	private HttpServletRequest httpRequest;

	@Mock
	private PasswordResetMailService passwordResetMailService;

	@Mock
	private PasswordValidator passwordValidator;

	@Mock
	private PasswordHistoryRepository passwordHistoryRepository;

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ValueOperations<String, String> redisValueOps;

	@Mock
	private PermissionRepository permissionRepository;

	@Mock
	private com.taipei.iot.auth.service.impl.ResetPasswordTokenClaimer resetPasswordTokenClaimer;

	@Mock
	private com.taipei.iot.auth.repository.UserSessionRepository userSessionRepository;

	@Mock
	private com.taipei.iot.auth.policy.PasswordExpiryChecker passwordExpiryChecker;

	@Mock
	private com.taipei.iot.auth.provider.AuthenticationDispatcher authenticationDispatcher;

	private UserEntity singleTenantUser;

	private UserEntity multiTenantUser;

	private UserEntity superAdmin;

	private UserEntity disabledUser;

	private UserEntity lockedUser;

	private RoleEntity adminRole;

	private RoleEntity operatorRole;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(authService, "entityManager", entityManager);
		ReflectionTestUtils.setField(authService, "maxFailCount", 5);
		ReflectionTestUtils.setField(authService, "lockDurationMinutes", 10);

		// [Phase 3] Default: password is not expired and no force-change pending,
		// so the existing login tests retain pre-Phase-3 behaviour.
		org.mockito.Mockito
			.when(passwordExpiryChecker.check(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(com.taipei.iot.auth.policy.PasswordExpiryStatus.OK);

		adminRole = RoleEntity.builder().roleId("ROLE_ADMIN").code("ADMIN").name("場域管理者").build();
		operatorRole = RoleEntity.builder().roleId("ROLE_OPERATOR").code("OPERATOR").name("維運人員").build();

		singleTenantUser = UserEntity.builder()
			.userId("user-admin-001")
			.email("admin@test.com")
			.passwordHash("$2a$10$hash")
			.displayName("Tenant A Admin")
			.enabled(true)
			.locked(false)
			.loginFailCount(0)
			.isSuperAdmin(false)
			.build();

		multiTenantUser = UserEntity.builder()
			.userId("user-op-001")
			.email("operator@test.com")
			.passwordHash("$2a$10$hash")
			.displayName("Multi-Tenant Operator")
			.enabled(true)
			.locked(false)
			.loginFailCount(0)
			.isSuperAdmin(false)
			.build();

		superAdmin = UserEntity.builder()
			.userId("user-super-001")
			.email("super@test.com")
			.passwordHash("$2a$10$hash")
			.displayName("Super Admin")
			.enabled(true)
			.locked(false)
			.loginFailCount(0)
			.isSuperAdmin(true)
			.build();

		disabledUser = UserEntity.builder()
			.userId("user-disabled-001")
			.email("disabled@test.com")
			.passwordHash("$2a$10$hash")
			.displayName("Disabled User")
			.enabled(false)
			.locked(false)
			.loginFailCount(0)
			.isSuperAdmin(false)
			.build();

		lockedUser = UserEntity.builder()
			.userId("user-locked-001")
			.email("locked@test.com")
			.passwordHash("$2a$10$hash")
			.displayName("Locked User")
			.enabled(true)
			.locked(true)
			.loginFailCount(5)
			.isSuperAdmin(false)
			.lockedAt(LocalDateTime.now())
			.build();
	}

	private LoginRequest buildLoginRequest(String email) {
		return LoginRequest.builder().email(email).password("Test1234!").captcha("1234").captchaKey("key-123").build();
	}

	@Test
	void login_singleTenant_success() {
		LoginRequest request = buildLoginRequest("admin@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);

		// Dispatcher returns success
		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenReturn(com.taipei.iot.auth.provider.AuthenticationResult.builder()
				.localUserId("user-admin-001")
				.email("admin@test.com")
				.displayName("Tenant A Admin")
				.build());
		when(userRepository.findById("user-admin-001")).thenReturn(Optional.of(singleTenantUser));

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.userId("user-admin-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_ADMIN")
			.deptId(1L)
			.enabled(true)
			.build();
		mapping.setRole(adminRole);

		when(userTenantMappingRepository.findByUserIdAndEnabledTrue("user-admin-001")).thenReturn(List.of(mapping));

		TenantEntity tenantA = new TenantEntity();
		tenantA.setTenantId("TENANT_A");
		tenantA.setTenantCode("KHH_WATER");
		tenantA.setTenantName("高雄市水情");
		tenantA.setEnabled(true);
		when(tenantRepository.findAllById(anySet())).thenReturn(List.of(tenantA));

		when(permissionRepository.findCodesByRoleAndTenant(eq("ROLE_ADMIN"), any()))
			.thenReturn(List.of("DEVICE_VIEW", "DEVICE_CREATE"));

		when(jwtUtil.generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any()))
			.thenReturn("access-token");
		when(jwtUtil.generateRefreshToken(any(), any(), any())).thenReturn("refresh-token");

		LoginResult result = authService.login(request, httpRequest);

		assertNotNull(result);
		assertEquals("access-token", result.getAccessToken());
		assertEquals("refresh-token", result.getRefreshToken());
		assertFalse(result.isNeedsSelection());
	}

	@Test
	void login_multiTenant_success() {
		LoginRequest request = buildLoginRequest("operator@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);

		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenReturn(com.taipei.iot.auth.provider.AuthenticationResult.builder()
				.localUserId("user-op-001")
				.email("operator@test.com")
				.displayName("Multi-Tenant Operator")
				.build());
		when(userRepository.findById("user-op-001")).thenReturn(Optional.of(multiTenantUser));

		UserTenantMappingEntity m1 = UserTenantMappingEntity.builder()
			.userId("user-op-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_OPERATOR")
			.enabled(true)
			.build();
		m1.setRole(operatorRole);
		UserTenantMappingEntity m2 = UserTenantMappingEntity.builder()
			.userId("user-op-001")
			.tenantId("TENANT_B")
			.roleId("ROLE_OPERATOR")
			.enabled(true)
			.build();
		m2.setRole(operatorRole);

		when(userTenantMappingRepository.findByUserIdAndEnabledTrue("user-op-001")).thenReturn(List.of(m1, m2));

		TenantEntity tenantA = new TenantEntity();
		tenantA.setTenantId("TENANT_A");
		tenantA.setTenantCode("KHH_WATER");
		tenantA.setTenantName("高雄市水情");
		tenantA.setEnabled(true);
		TenantEntity tenantB = new TenantEntity();
		tenantB.setTenantId("TENANT_B");
		tenantB.setTenantCode("NTPC_WATER");
		tenantB.setTenantName("新北市水情");
		tenantB.setEnabled(true);

		when(tenantRepository.findAllById(anySet())).thenReturn(List.of(tenantA, tenantB));

		Query deptQuery = mock(Query.class);
		when(entityManager.createNativeQuery(contains("dept_info"))).thenReturn(deptQuery);
		when(deptQuery.setParameter(eq("deptId"), any())).thenReturn(deptQuery);
		when(deptQuery.getResultList()).thenReturn(Collections.emptyList());

		when(jwtUtil.generateTemporaryToken(any(), any(), eq(false))).thenReturn("temp-token");

		LoginResult result = authService.login(request, httpRequest);

		assertNotNull(result);
		assertTrue(result.isNeedsSelection());
		assertEquals("temp-token", result.getAccessToken());
		assertNull(result.getRefreshToken());
		assertNotNull(result.getTenants());
		assertEquals(2, result.getTenants().size());
	}

	@Test
	void login_superAdmin_success() {
		LoginRequest request = buildLoginRequest("super@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);

		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenReturn(com.taipei.iot.auth.provider.AuthenticationResult.builder()
				.localUserId("user-super-001")
				.email("super@test.com")
				.displayName("Super Admin")
				.build());
		when(userRepository.findById("user-super-001")).thenReturn(Optional.of(superAdmin));

		// [Phase 5 / ADR-007] super_admin login now bypasses select-tenant
		// entirely: backend issues a PLATFORM-scoped access token + refresh
		// token directly. permissionRepository is called with tenantId=null so
		// only the global PLATFORM_* permissions (V65) come back.
		when(permissionRepository.findCodesByRoleAndTenant(eq("ROLE_SUPER_ADMIN"),
				org.mockito.ArgumentMatchers.isNull()))
			.thenReturn(java.util.List.of("PLATFORM_TENANT_MANAGE", "PLATFORM_PASSWORD_POLICY_MANAGE",
					"PLATFORM_USER_TENANT_MAPPING", "PLATFORM_IMPERSONATE"));
		when(jwtUtil.generateAccessToken(org.mockito.ArgumentMatchers.eq("user-super-001"),
				org.mockito.ArgumentMatchers.eq("super@test.com"), org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.eq(java.util.List.of("SUPER_ADMIN")),
				org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyList(),
				org.mockito.ArgumentMatchers.eq("ALL"),
				org.mockito.ArgumentMatchers.eq(com.taipei.iot.auth.security.TokenScope.PLATFORM)))
			.thenReturn("super-platform-token");
		when(jwtUtil.generateRefreshToken(org.mockito.ArgumentMatchers.eq("user-super-001"),
				org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString()))
			.thenReturn("super-refresh-token");

		LoginResult result = authService.login(request, httpRequest);

		assertNotNull(result);
		assertFalse(result.isNeedsSelection());
		assertTrue(result.isSuperAdmin());
		assertEquals("super-platform-token", result.getAccessToken());
		assertEquals("super-refresh-token", result.getRefreshToken());
		// tenants list is no longer populated — super_admin lands on /platform/tenants
		// and uses the impersonation flow to act inside any tenant.
		assertNull(result.getTenants());
		// tenantRepository.findByEnabledTrue() must NOT be called any more.
		org.mockito.Mockito.verify(tenantRepository, org.mockito.Mockito.never()).findByEnabledTrue();
	}

	@Test
	void login_wrongPassword_throwsLoginFail() {
		LoginRequest request = buildLoginRequest("admin@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);
		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenThrow(new BusinessException(ErrorCode.LOGIN_FAIL));

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, httpRequest));
		assertEquals(ErrorCode.LOGIN_FAIL, ex.getErrorCode());
	}

	@Test
	void login_userNotFound_throwsUserNotFound() {
		LoginRequest request = buildLoginRequest("nonexistent@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);
		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, httpRequest));
		assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void login_accountDisabled_throwsAccountDisabled() {
		LoginRequest request = buildLoginRequest("disabled@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);
		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenThrow(new BusinessException(ErrorCode.ACCOUNT_DISABLED));

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, httpRequest));
		assertEquals(ErrorCode.ACCOUNT_DISABLED, ex.getErrorCode());
	}

	@Test
	void login_accountLocked_throwsAccountLocked() {
		LoginRequest request = buildLoginRequest("locked@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);
		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenThrow(new BusinessException(ErrorCode.ACCOUNT_LOCKED));

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, httpRequest));
		assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
	}

	@Test
	void login_noTenantMapping_throwsTenantAccessDenied() {
		LoginRequest request = buildLoginRequest("admin@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);
		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenReturn(com.taipei.iot.auth.provider.AuthenticationResult.builder()
				.localUserId("user-admin-001")
				.email("admin@test.com")
				.displayName("Tenant A Admin")
				.build());
		when(userRepository.findById("user-admin-001")).thenReturn(Optional.of(singleTenantUser));
		when(userTenantMappingRepository.findByUserIdAndEnabledTrue("user-admin-001"))
			.thenReturn(Collections.emptyList());

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, httpRequest));
		assertEquals(ErrorCode.TENANT_ACCESS_DENIED, ex.getErrorCode());
	}

	/**
	 * 場域被停用時，其轄下「唯一場域」帳號不得登入：拒於 buildPostAuthLoginResult 階段， 不應發出任何 access token（避免 token
	 * 被簽發後又立刻被 JwtAuthenticationFilter 擋掉 的雞肋情況）。
	 */
	@Test
	void login_singleTenantButTenantDisabled_throwsTenantAccessDenied() {
		LoginRequest request = buildLoginRequest("admin@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);
		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenReturn(com.taipei.iot.auth.provider.AuthenticationResult.builder()
				.localUserId("user-admin-001")
				.email("admin@test.com")
				.displayName("Tenant A Admin")
				.build());
		when(userRepository.findById("user-admin-001")).thenReturn(Optional.of(singleTenantUser));

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.userId("user-admin-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_ADMIN")
			.deptId(1L)
			.enabled(true)
			.build();
		mapping.setRole(adminRole);
		when(userTenantMappingRepository.findByUserIdAndEnabledTrue("user-admin-001")).thenReturn(List.of(mapping));

		// 場域被停用 (tenant.enabled = false)
		TenantEntity disabledTenant = new TenantEntity();
		disabledTenant.setTenantId("TENANT_A");
		disabledTenant.setEnabled(false);
		when(tenantRepository.findAllById(anySet())).thenReturn(List.of(disabledTenant));

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, httpRequest));
		assertEquals(ErrorCode.TENANT_ACCESS_DENIED, ex.getErrorCode());

		// 不應產生任何 access / refresh token
		verify(jwtUtil, never()).generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any());
		verify(jwtUtil, never()).generateRefreshToken(any(), any(), any());
	}

	/**
	 * 多場域帳號：停用的場域應從候選清單中過濾，使用者只看得到「目前仍啟用」的場域。
	 */
	@Test
	void login_multiTenant_disabledTenantsAreFilteredFromOptions() {
		LoginRequest request = buildLoginRequest("operator@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);
		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenReturn(com.taipei.iot.auth.provider.AuthenticationResult.builder()
				.localUserId("user-op-001")
				.email("operator@test.com")
				.displayName("Multi-Tenant Operator")
				.build());
		when(userRepository.findById("user-op-001")).thenReturn(Optional.of(multiTenantUser));

		UserTenantMappingEntity m1 = UserTenantMappingEntity.builder()
			.userId("user-op-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_OPERATOR")
			.enabled(true)
			.build();
		m1.setRole(operatorRole);
		UserTenantMappingEntity m2 = UserTenantMappingEntity.builder()
			.userId("user-op-001")
			.tenantId("TENANT_B")
			.roleId("ROLE_OPERATOR")
			.enabled(true)
			.build();
		m2.setRole(operatorRole);
		when(userTenantMappingRepository.findByUserIdAndEnabledTrue("user-op-001")).thenReturn(List.of(m1, m2));

		TenantEntity tenantA = new TenantEntity();
		tenantA.setTenantId("TENANT_A");
		tenantA.setTenantCode("KHH_WATER");
		tenantA.setTenantName("高雄市水情");
		tenantA.setEnabled(true);
		TenantEntity tenantB = new TenantEntity();
		tenantB.setTenantId("TENANT_B");
		tenantB.setTenantCode("NTPC_WATER");
		tenantB.setTenantName("新北市水情");
		tenantB.setEnabled(false); // 已停用
		when(tenantRepository.findAllById(anySet())).thenReturn(List.of(tenantA, tenantB));

		Query deptQuery = mock(Query.class);
		when(entityManager.createNativeQuery(contains("dept_info"))).thenReturn(deptQuery);
		when(deptQuery.setParameter(eq("deptId"), any())).thenReturn(deptQuery);
		when(deptQuery.getResultList()).thenReturn(Collections.emptyList());

		// 過濾後只剩 1 個 → 直接走 single tenant fast path (full JWT)，不再 needsSelection
		when(jwtUtil.generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any()))
			.thenReturn("access-token");
		when(jwtUtil.generateRefreshToken(any(), any(), any())).thenReturn("refresh-token");

		LoginResult result = authService.login(request, httpRequest);

		assertNotNull(result);
		assertFalse(result.isNeedsSelection());
		assertEquals("access-token", result.getAccessToken());
	}

	/**
	 * 多場域帳號全部場域都被停用 → 拒絕登入。
	 */
	@Test
	void login_allTenantsDisabled_throwsTenantAccessDenied() {
		LoginRequest request = buildLoginRequest("operator@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);
		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenReturn(com.taipei.iot.auth.provider.AuthenticationResult.builder()
				.localUserId("user-op-001")
				.email("operator@test.com")
				.displayName("Multi-Tenant Operator")
				.build());
		when(userRepository.findById("user-op-001")).thenReturn(Optional.of(multiTenantUser));

		UserTenantMappingEntity m1 = UserTenantMappingEntity.builder()
			.userId("user-op-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_OPERATOR")
			.enabled(true)
			.build();
		m1.setRole(operatorRole);
		when(userTenantMappingRepository.findByUserIdAndEnabledTrue("user-op-001")).thenReturn(List.of(m1));

		TenantEntity tenantA = new TenantEntity();
		tenantA.setTenantId("TENANT_A");
		tenantA.setEnabled(false);
		when(tenantRepository.findAllById(anySet())).thenReturn(List.of(tenantA));

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, httpRequest));
		assertEquals(ErrorCode.TENANT_ACCESS_DENIED, ex.getErrorCode());

		verify(jwtUtil, never()).generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any());
	}

	// ─── [Phase 3] Password expiry / force-change at login ───────────────────

	@Test
	void login_passwordExpired_returnsForceChangeToken_noRegularAccessToken() {
		LoginRequest request = buildLoginRequest("admin@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);
		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenReturn(com.taipei.iot.auth.provider.AuthenticationResult.builder()
				.localUserId("user-admin-001")
				.email("admin@test.com")
				.displayName("Tenant A Admin")
				.build());
		when(userRepository.findById("user-admin-001")).thenReturn(Optional.of(singleTenantUser));
		when(userTenantMappingRepository.findByUserIdAndEnabledTrue("user-admin-001"))
			.thenReturn(Collections.emptyList()); // tenant lookup happens before expiry
													// check

		// Override the default OK stub for this test
		when(passwordExpiryChecker.check(eq(singleTenantUser), any()))
			.thenReturn(com.taipei.iot.auth.policy.PasswordExpiryStatus.EXPIRED);
		when(jwtUtil.generatePasswordChangeToken("user-admin-001", "admin@test.com")).thenReturn("pwd-change-token");

		LoginResult result = authService.login(request, httpRequest);

		assertNotNull(result);
		assertTrue(result.isPasswordChangeRequired());
		assertEquals("pwd-change-token", result.getAccessToken());
		assertNull(result.getRefreshToken());
		// Critically: regular access token must NOT be issued before the user changes
		// their password.
		verify(jwtUtil, never()).generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void login_forceChangePasswordFlagSet_returnsForceChangeToken() {
		// forceChangePassword=true on the entity → checker returns FORCE_CHANGE
		LoginRequest request = buildLoginRequest("admin@test.com");
		when(captchaService.verify("key-123", "1234")).thenReturn(true);
		when(authenticationDispatcher.dispatch(any(com.taipei.iot.auth.provider.AuthenticationRequest.class)))
			.thenReturn(com.taipei.iot.auth.provider.AuthenticationResult.builder()
				.localUserId("user-admin-001")
				.email("admin@test.com")
				.displayName("Tenant A Admin")
				.build());
		when(userRepository.findById("user-admin-001")).thenReturn(Optional.of(singleTenantUser));
		when(userTenantMappingRepository.findByUserIdAndEnabledTrue("user-admin-001"))
			.thenReturn(Collections.emptyList());

		when(passwordExpiryChecker.check(eq(singleTenantUser), any()))
			.thenReturn(com.taipei.iot.auth.policy.PasswordExpiryStatus.FORCE_CHANGE);
		when(jwtUtil.generatePasswordChangeToken(any(), any())).thenReturn("pwd-change-token");

		LoginResult result = authService.login(request, httpRequest);

		assertTrue(result.isPasswordChangeRequired());
		assertEquals("pwd-change-token", result.getAccessToken());
	}

	@Test
	void forceChangePassword_validToken_singleTenant_issuesFullTokens() {
		// Mock parsed JWT claims for a valid password-change token
		io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
		when(claims.get("temporary", Boolean.class)).thenReturn(Boolean.TRUE);
		when(claims.get("purpose", String.class)).thenReturn("password_change");
		when(claims.get("uid", String.class)).thenReturn("user-admin-001");
		when(jwtUtil.parseToken("good-token")).thenReturn(claims);

		when(userRepository.findById("user-admin-001")).thenReturn(Optional.of(singleTenantUser));

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.userId("user-admin-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_ADMIN")
			.deptId(1L)
			.enabled(true)
			.build();
		mapping.setRole(adminRole);
		when(userTenantMappingRepository.findByUserIdAndEnabledTrue("user-admin-001")).thenReturn(List.of(mapping));

		TenantEntity tenantA = new TenantEntity();
		tenantA.setTenantId("TENANT_A");
		tenantA.setEnabled(true);
		when(tenantRepository.findAllById(anySet())).thenReturn(List.of(tenantA));

		when(permissionRepository.findCodesByRoleAndTenant(eq("ROLE_ADMIN"), any())).thenReturn(List.of("DEVICE_VIEW"));

		when(passwordEncoder.encode("NewPass!23")).thenReturn("$2a$10$newhash");
		when(jwtUtil.generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any()))
			.thenReturn("new-access");
		when(jwtUtil.generateRefreshToken(any(), any(), any())).thenReturn("new-refresh");

		com.taipei.iot.auth.dto.request.ForceChangePasswordRequest body = new com.taipei.iot.auth.dto.request.ForceChangePasswordRequest();
		body.setNewPassword("NewPass!23");

		LoginResult result = authService.forceChangePassword("good-token", body, httpRequest);

		assertNotNull(result);
		assertEquals("new-access", result.getAccessToken());
		assertEquals("new-refresh", result.getRefreshToken());
		assertFalse(result.isPasswordChangeRequired());
		// Password fields must be updated
		assertEquals("$2a$10$newhash", singleTenantUser.getPasswordHash());
		assertFalse(singleTenantUser.getForceChangePassword());
		assertNotNull(singleTenantUser.getPasswordChangedAt());
		// Policy validation must have run with the resolved single-tenant id
		verify(passwordValidator).validate(eq("TENANT_A"), eq("NewPass!23"), any());
		verify(passwordValidator).checkNotRecentlyUsed(eq("TENANT_A"), eq("user-admin-001"), eq("NewPass!23"));
	}

	@Test
	void forceChangePassword_wrongPurposeClaim_rejected() {
		// A regular tenant-selection temporary token (purpose absent) must NOT be
		// accepted
		io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
		when(claims.get("temporary", Boolean.class)).thenReturn(Boolean.TRUE);
		when(claims.get("purpose", String.class)).thenReturn(null);
		when(jwtUtil.parseToken("wrong-token")).thenReturn(claims);

		com.taipei.iot.auth.dto.request.ForceChangePasswordRequest body = new com.taipei.iot.auth.dto.request.ForceChangePasswordRequest();
		body.setNewPassword("NewPass!23");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.forceChangePassword("wrong-token", body, httpRequest));
		assertEquals(ErrorCode.FORCE_CHANGE_PASSWORD_TOKEN_INVALID, ex.getErrorCode());
	}

	@Test
	void forceChangePassword_unparseableToken_rejected() {
		when(jwtUtil.parseToken("bad-token")).thenThrow(new RuntimeException("malformed"));

		com.taipei.iot.auth.dto.request.ForceChangePasswordRequest body = new com.taipei.iot.auth.dto.request.ForceChangePasswordRequest();
		body.setNewPassword("NewPass!23");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.forceChangePassword("bad-token", body, httpRequest));
		assertEquals(ErrorCode.FORCE_CHANGE_PASSWORD_TOKEN_INVALID, ex.getErrorCode());
	}

	@Test
	void selectTenant_success() {
		when(userRepository.findById("user-op-001")).thenReturn(Optional.of(multiTenantUser));

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.userId("user-op-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_OPERATOR")
			.enabled(true)
			.build();
		mapping.setRole(operatorRole);
		when(userTenantMappingRepository.findByUserIdAndTenantId("user-op-001", "TENANT_A"))
			.thenReturn(Optional.of(mapping));

		TenantEntity tenantA = new TenantEntity();
		tenantA.setTenantId("TENANT_A");
		tenantA.setEnabled(true);
		when(tenantRepository.findById("TENANT_A")).thenReturn(Optional.of(tenantA));

		when(permissionRepository.findCodesByRoleAndTenant(eq("ROLE_OPERATOR"), eq("TENANT_A")))
			.thenReturn(List.of("DEVICE_VIEW"));

		when(jwtUtil.generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any()))
			.thenReturn("full-access-token");
		when(jwtUtil.generateRefreshToken(any(), any(), any())).thenReturn("refresh-token");

		SelectTenantRequest request = SelectTenantRequest.builder().tenantId("TENANT_A").build();
		TokenResult result = authService.selectTenant("user-op-001", request, httpRequest);

		assertEquals("full-access-token", result.getAccessToken());
		assertEquals("refresh-token", result.getRefreshToken());
	}

	@Test
	void selectTenant_invalidTenant_throwsTenantAccessDenied() {
		when(userRepository.findById("user-op-001")).thenReturn(Optional.of(multiTenantUser));
		when(userTenantMappingRepository.findByUserIdAndTenantId("user-op-001", "INVALID"))
			.thenReturn(Optional.empty());

		SelectTenantRequest request = SelectTenantRequest.builder().tenantId("INVALID").build();
		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.selectTenant("user-op-001", request, httpRequest));
		assertEquals(ErrorCode.TENANT_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	void switchTenant_success() {
		when(userRepository.findById("user-op-001")).thenReturn(Optional.of(multiTenantUser));

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.userId("user-op-001")
			.tenantId("TENANT_B")
			.roleId("ROLE_OPERATOR")
			.enabled(true)
			.build();
		mapping.setRole(operatorRole);
		when(userTenantMappingRepository.findByUserIdAndTenantId("user-op-001", "TENANT_B"))
			.thenReturn(Optional.of(mapping));

		TenantEntity tenantB = new TenantEntity();
		tenantB.setTenantId("TENANT_B");
		tenantB.setEnabled(true);
		when(tenantRepository.findById("TENANT_B")).thenReturn(Optional.of(tenantB));

		when(permissionRepository.findCodesByRoleAndTenant(eq("ROLE_OPERATOR"), eq("TENANT_B")))
			.thenReturn(List.of("DEVICE_VIEW"));

		when(jwtUtil.generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any()))
			.thenReturn("new-access-token");
		when(jwtUtil.generateRefreshToken(any(), any(), any())).thenReturn("new-refresh-token");

		SwitchTenantRequest request = SwitchTenantRequest.builder().tenantId("TENANT_B").build();
		TokenResult result = authService.switchTenant("user-op-001", request, httpRequest);

		assertEquals("new-access-token", result.getAccessToken());
		assertEquals("new-refresh-token", result.getRefreshToken());
	}

	@Test
	void switchTenant_noMapping_throwsTenantAccessDenied() {
		when(userRepository.findById("user-op-001")).thenReturn(Optional.of(multiTenantUser));
		when(userTenantMappingRepository.findByUserIdAndTenantId("user-op-001", "INVALID"))
			.thenReturn(Optional.empty());

		SwitchTenantRequest request = SwitchTenantRequest.builder().tenantId("INVALID").build();
		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.switchTenant("user-op-001", request, httpRequest));
		assertEquals(ErrorCode.TENANT_ACCESS_DENIED, ex.getErrorCode());
	}

	/**
	 * [Phase 3 / ADR-001] 3.1.1：super_admin 選擇租戶後，permissions 必須來自
	 * {@code role_permissions} 表（透過 findCodesByRoleAndTenant），不得再使用 「回傳所有
	 * permission」的舊旁路（findAllCodesOrderByCode）。
	 */
	@Test
	void selectTenant_superAdmin_resolvesPermissionsFromRolePermissions_noBypass() {
		when(userRepository.findById("user-super-001")).thenReturn(Optional.of(superAdmin));

		TenantEntity tenantA = new TenantEntity();
		tenantA.setTenantId("TENANT_A");
		tenantA.setEnabled(true);
		when(tenantRepository.findById("TENANT_A")).thenReturn(Optional.of(tenantA));

		// V65 種入：ROLE_SUPER_ADMIN 綁定的 4 個 PLATFORM_* 權限（tenant_id = NULL）
		when(permissionRepository.findCodesByRoleAndTenant(eq("ROLE_SUPER_ADMIN"), eq("TENANT_A")))
			.thenReturn(List.of("PLATFORM_TENANT_MANAGE", "PLATFORM_PASSWORD_POLICY_MANAGE",
					"PLATFORM_USER_TENANT_MAPPING", "PLATFORM_IMPERSONATE"));

		when(jwtUtil.generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any()))
			.thenReturn("super-tenant-access-token");
		when(jwtUtil.generateRefreshToken(any(), any(), any())).thenReturn("super-refresh-token");

		SelectTenantRequest request = SelectTenantRequest.builder().tenantId("TENANT_A").build();
		TokenResult result = authService.selectTenant("user-super-001", request, httpRequest);

		assertEquals("super-tenant-access-token", result.getAccessToken());
		// 必須走 role_permissions 查詢
		verify(permissionRepository).findCodesByRoleAndTenant("ROLE_SUPER_ADMIN", "TENANT_A");
		// 舊旁路（findAllCodesOrderByCode）不得在 super_admin 流程被呼叫
		verify(permissionRepository, never()).findAllCodesOrderByCode();
	}

	/**
	 * 一般使用者選擇已停用場域 → 拒絕，不發 token。
	 */
	@Test
	void selectTenant_tenantDisabled_throwsTenantAccessDenied() {
		when(userRepository.findById("user-op-001")).thenReturn(Optional.of(multiTenantUser));

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.userId("user-op-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_OPERATOR")
			.enabled(true)
			.build();
		mapping.setRole(operatorRole);
		when(userTenantMappingRepository.findByUserIdAndTenantId("user-op-001", "TENANT_A"))
			.thenReturn(Optional.of(mapping));

		TenantEntity disabled = new TenantEntity();
		disabled.setTenantId("TENANT_A");
		disabled.setEnabled(false);
		when(tenantRepository.findById("TENANT_A")).thenReturn(Optional.of(disabled));

		SelectTenantRequest request = SelectTenantRequest.builder().tenantId("TENANT_A").build();
		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.selectTenant("user-op-001", request, httpRequest));
		assertEquals(ErrorCode.TENANT_ACCESS_DENIED, ex.getErrorCode());

		verify(jwtUtil, never()).generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any());
	}

	/**
	 * 已登入使用者切換到已停用場域 → 拒絕。
	 */
	@Test
	void switchTenant_tenantDisabled_throwsTenantAccessDenied() {
		when(userRepository.findById("user-op-001")).thenReturn(Optional.of(multiTenantUser));

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.userId("user-op-001")
			.tenantId("TENANT_B")
			.roleId("ROLE_OPERATOR")
			.enabled(true)
			.build();
		mapping.setRole(operatorRole);
		when(userTenantMappingRepository.findByUserIdAndTenantId("user-op-001", "TENANT_B"))
			.thenReturn(Optional.of(mapping));

		TenantEntity disabled = new TenantEntity();
		disabled.setTenantId("TENANT_B");
		disabled.setEnabled(false);
		when(tenantRepository.findById("TENANT_B")).thenReturn(Optional.of(disabled));

		SwitchTenantRequest request = SwitchTenantRequest.builder().tenantId("TENANT_B").build();
		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.switchTenant("user-op-001", request, httpRequest));
		assertEquals(ErrorCode.TENANT_ACCESS_DENIED, ex.getErrorCode());

		verify(jwtUtil, never()).generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void refreshToken_success() {
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", "user-admin-001");
		claimsMap.put("tenantId", "TENANT_A"); // refresh token 帶入 tenantId
		claimsMap.put("type", "refresh"); // token 類型驗證需要此欄位
		claimsMap.put("sub", "user-admin-001");
		claimsMap.put("exp", new java.util.Date(System.currentTimeMillis() + 3600000));
		claimsMap.put("iat", new java.util.Date());
		Claims claims = new DefaultClaims(claimsMap);

		when(jwtUtil.parseToken("valid-refresh-token")).thenReturn(claims);
		when(userRepository.findById("user-admin-001")).thenReturn(Optional.of(singleTenantUser));

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.userId("user-admin-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_ADMIN")
			.deptId(1L)
			.enabled(true)
			.build();
		mapping.setRole(adminRole);
		// 新逐輯：用 findByUserIdAndTenantId 保證租戶不漂移
		when(userTenantMappingRepository.findByUserIdAndTenantId("user-admin-001", "TENANT_A"))
			.thenReturn(Optional.of(mapping));

		when(permissionRepository.findCodesByRoleAndTenant(eq("ROLE_ADMIN"), eq("TENANT_A")))
			.thenReturn(List.of("DEVICE_VIEW"));

		when(jwtUtil.generateAccessToken(any(), any(), any(), any(), any(), any(), any(), any()))
			.thenReturn("new-access-token");
		when(jwtUtil.generateRefreshToken(any(), any(), any())).thenReturn("new-refresh-token");

		TokenResult result = authService.refreshToken("valid-refresh-token", httpRequest);

		assertEquals("new-access-token", result.getAccessToken());
		assertEquals("new-refresh-token", result.getRefreshToken());
	}

	@Test
	void refreshToken_invalid_throwsRefreshTokenInvalid() {
		when(jwtUtil.parseToken("bad-token")).thenThrow(new RuntimeException("invalid"));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.refreshToken("bad-token", httpRequest));
		assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, ex.getErrorCode());
	}

	@Test
	void getCurrentUser_success() {
		// getCurrentUser 是認證後 API，TenantContext 由 interceptor 設定
		TenantContext.setCurrentTenantId("TENANT_A");
		try {
			when(userRepository.findById("user-admin-001")).thenReturn(Optional.of(singleTenantUser));

			UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
				.userId("user-admin-001")
				.tenantId("TENANT_A")
				.roleId("ROLE_ADMIN")
				.deptId(1L)
				.enabled(true)
				.build();
			mapping.setRole(adminRole);
			when(userTenantMappingRepository.findByUserIdAndEnabledTrue("user-admin-001")).thenReturn(List.of(mapping));

			TenantEntity tenant = new TenantEntity();
			tenant.setTenantId("TENANT_A");
			tenant.setTenantCode("KHH_WATER");
			tenant.setTenantName("高雄市水情");
			when(tenantRepository.findById("TENANT_A")).thenReturn(Optional.of(tenant));

			when(permissionRepository.findCodesByRoleAndTenant(eq("ROLE_ADMIN"), eq("TENANT_A")))
				.thenReturn(List.of("DEVICE_VIEW", "DEVICE_CREATE"));

			// 批次載入 dept 名稱（batchResolveDeptNames使用 IN :deptIds）
			Query deptBatchQuery = mock(Query.class);
			when(entityManager.createNativeQuery(contains("IN :deptIds"))).thenReturn(deptBatchQuery);
			when(deptBatchQuery.setParameter(eq("deptIds"), any())).thenReturn(deptBatchQuery);
			when(deptBatchQuery.getResultList()).thenReturn(Collections.singletonList(new Object[] { 1L, "水利工程科" }));

			// 單一 dept 查詢（resolveDeptName 使用 = :deptId）
			Query deptSingleQuery = mock(Query.class);
			when(entityManager.createNativeQuery(contains("dept_id = :deptId"))).thenReturn(deptSingleQuery);
			when(deptSingleQuery.setParameter(eq("deptId"), any())).thenReturn(deptSingleQuery);
			when(deptSingleQuery.getResultList()).thenReturn(List.of("水利工程科"));

			UserInfoDto userInfo = authService.getCurrentUser("user-admin-001", "TENANT_A");

			assertEquals("user-admin-001", userInfo.getUserId());
			assertEquals("admin@test.com", userInfo.getEmail());
			assertEquals("TENANT_A", userInfo.getTenantId());
			assertEquals("高雄市水情", userInfo.getTenantName());
			assertFalse(userInfo.isSuperAdmin());
			assertEquals(List.of("ADMIN"), userInfo.getRoles());
		}
		finally {
			TenantContext.clear();
		}
	}

	// ---- TC-01-031: forgotPassword — generates token and sends email ----

	@Test
	void forgotPassword_existingUser_generatesTokenAndSendsEmail() {
		ForgotPasswordRequest request = ForgotPasswordRequest.builder().email("admin@test.com").build();
		when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(singleTenantUser));

		authService.forgotPassword(request);

		verify(userResetPasswordTokenRepository).save(any(UserResetPasswordTokenEntity.class));
		verify(passwordResetMailService).send(eq("admin@test.com"), any(), any());
	}

	@Test
	void forgotPassword_nonExistentUser_silentSuccess() {
		ForgotPasswordRequest request = ForgotPasswordRequest.builder().email("nobody@test.com").build();
		when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

		authService.forgotPassword(request);

		verify(userResetPasswordTokenRepository, never()).save(any());
		verify(passwordResetMailService, never()).send(any(), any(), any());
	}

	// ---- TC-01-032: resetPassword — validates and resets ----

	@Test
	void resetPassword_validToken_resetsPasswordAndSavesHistory() {
		String tokenValue = "valid-token-uuid";
		UserResetPasswordTokenEntity tokenEntity = UserResetPasswordTokenEntity.builder()
			.tokenId("tid-1")
			.userId("user-admin-001")
			.tokenHash("hash-for-" + tokenValue)
			.expiredAt(LocalDateTime.now().plusMinutes(15))
			.used(false)
			.build();

		ResetPasswordRequest request = ResetPasswordRequest.builder()
			.token(tokenValue)
			.newPassword("NewPass1234")
			.build();

		// [v2 N-5] 「先消耗」走 ResetPasswordTokenClaimer（REQUIRES_NEW），回 true 表示本 request 成功
		// claim token
		when(resetPasswordTokenClaimer.claim(anyString())).thenReturn(true);
		when(userResetPasswordTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(tokenEntity));
		when(userRepository.findById("user-admin-001")).thenReturn(Optional.of(singleTenantUser));
		when(passwordEncoder.encode("NewPass1234")).thenReturn("encoded-hash");

		authService.resetPassword(request, httpRequest);

		verify(resetPasswordTokenClaimer).claim(anyString());
		verify(passwordValidator).validate(any(), eq("NewPass1234"), any());
		verify(passwordValidator).checkNotRecentlyUsed(any(), eq("user-admin-001"), eq("NewPass1234"));
		verify(passwordHistoryRepository).save(any());
		verify(changePasswordLogRepository).save(argThat(log -> "RESET".equals(log.getChangeType())));
	}

	// ---- TC-01-033: resetPassword — expired token ----

	@Test
	void resetPassword_expiredToken_throwsException() {
		String tokenValue = "expired-token";
		ResetPasswordRequest request = ResetPasswordRequest.builder()
			.token(tokenValue)
			.newPassword("NewPass1234")
			.build();

		// claim 回 false 表示 token 不存在、已過期或已被使用
		when(resetPasswordTokenClaimer.claim(anyString())).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.resetPassword(request, httpRequest));
		assertEquals(ErrorCode.RESET_PASSWORD_INVALID_TOKEN, ex.getErrorCode());
		verify(userResetPasswordTokenRepository, never()).findByTokenHash(anyString());
	}

	@Test
	void resetPassword_usedToken_throwsException() {
		String tokenValue = "used-token";
		ResetPasswordRequest request = ResetPasswordRequest.builder()
			.token(tokenValue)
			.newPassword("NewPass1234")
			.build();

		when(resetPasswordTokenClaimer.claim(anyString())).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.resetPassword(request, httpRequest));
		assertEquals(ErrorCode.RESET_PASSWORD_INVALID_TOKEN, ex.getErrorCode());
		verify(userResetPasswordTokenRepository, never()).findByTokenHash(anyString());
	}

	/**
	 * [v2 N-5] 即便 passwordValidator 拋出例外導致外層 @Transactional rollback， claim 已透過
	 * REQUIRES_NEW 獨立交易 commit。本 unit test 以「claim 被呼叫」作為契約， 並驗證後續密碼相關寫入沒有發生（換言之，token
	 * 在實際資料庫中已被消耗）。
	 */
	@Test
	void resetPassword_passwordValidatorFails_claimStillInvoked() {
		String tokenValue = "valid-but-weak-password";
		UserResetPasswordTokenEntity tokenEntity = UserResetPasswordTokenEntity.builder()
			.tokenId("tid-x")
			.userId("user-admin-001")
			.tokenHash("hash-x")
			.expiredAt(LocalDateTime.now().plusMinutes(15))
			.used(false)
			.build();
		ResetPasswordRequest request = ResetPasswordRequest.builder().token(tokenValue).newPassword("weak").build();

		when(resetPasswordTokenClaimer.claim(anyString())).thenReturn(true);
		when(userResetPasswordTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(tokenEntity));
		when(userRepository.findById("user-admin-001")).thenReturn(Optional.of(singleTenantUser));
		doThrow(new BusinessException(ErrorCode.RESET_PASSWORD_ERROR)).when(passwordValidator)
			.validate(any(), eq("weak"), any());

		assertThrows(BusinessException.class, () -> authService.resetPassword(request, httpRequest));

		verify(resetPasswordTokenClaimer).claim(anyString());
		verify(passwordHistoryRepository, never()).save(any());
		verify(changePasswordLogRepository, never()).save(any());
	}

	// ---- Logout / refresh-token revocation (v1 #3, N-6) ----

	@Test
	void logout_validRefreshToken_writesRevocationEntryWithRemainingTtl() {
		long ttlMs = 60_000L;
		Date exp = new Date(System.currentTimeMillis() + ttlMs);
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", "user-admin-001");
		claimsMap.put("type", "refresh");
		claimsMap.put("jti", "jti-aaa");
		claimsMap.put("sub", "user-admin-001");
		claimsMap.put("exp", exp);
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);

		when(jwtUtil.parseToken("refresh-token")).thenReturn(claims);
		when(stringRedisTemplate.opsForValue()).thenReturn(redisValueOps);

		authService.logout("refresh-token");

		verify(redisValueOps).set(eq("auth:revoked_refresh:jti-aaa"), eq("1"), longThat(v -> v > 0 && v <= ttlMs),
				eq(TimeUnit.MILLISECONDS));
	}

	@Test
	void logout_nullOrBlankToken_isSilentNoop() {
		authService.logout(null);
		authService.logout("   ");
		verifyNoInteractions(stringRedisTemplate);
	}

	@Test
	void logout_unparseableToken_isSilentNoop() {
		when(jwtUtil.parseToken("garbage")).thenThrow(new RuntimeException("bad sig"));

		authService.logout("garbage");

		verifyNoInteractions(stringRedisTemplate);
	}

	@Test
	void logout_legacyTokenWithoutJti_isSilentNoop() {
		// 舊版 refresh token 未含 jti，無法針對性撤銷；logout 不應爆錯。
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", "user-admin-001");
		claimsMap.put("type", "refresh");
		claimsMap.put("sub", "user-admin-001");
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 60_000L));
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken("legacy")).thenReturn(claims);

		authService.logout("legacy");

		verifyNoInteractions(stringRedisTemplate);
	}

	@Test
	void logout_expiredToken_doesNotWriteRedis() {
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", "user-admin-001");
		claimsMap.put("type", "refresh");
		claimsMap.put("jti", "jti-expired");
		claimsMap.put("sub", "user-admin-001");
		claimsMap.put("exp", new Date(System.currentTimeMillis() - 1_000L));
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken("expired")).thenReturn(claims);

		authService.logout("expired");

		verify(stringRedisTemplate, never()).opsForValue();
	}

	@Test
	void refreshToken_revokedJti_throwsRefreshTokenInvalid() {
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", "user-admin-001");
		claimsMap.put("tenantId", "TENANT_A");
		claimsMap.put("type", "refresh");
		claimsMap.put("jti", "jti-revoked");
		claimsMap.put("sub", "user-admin-001");
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 60_000L));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);

		when(jwtUtil.parseToken("revoked-refresh-token")).thenReturn(claims);
		when(stringRedisTemplate.hasKey("auth:revoked_refresh:jti-revoked")).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> authService.refreshToken("revoked-refresh-token", httpRequest));
		assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, ex.getErrorCode());
		verify(userRepository, never()).findById(anyString());
	}

}
