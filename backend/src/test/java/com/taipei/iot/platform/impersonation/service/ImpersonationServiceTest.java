package com.taipei.iot.platform.impersonation.service;

import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.entity.UserTenantMappingEntity;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.auth.repository.UserTenantMappingRepository;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.notification.dto.NotificationPayload;
import com.taipei.iot.notification.enums.NotificationRefType;
import com.taipei.iot.notification.enums.NotificationType;
import com.taipei.iot.notification.service.NotificationService;
import com.taipei.iot.platform.impersonation.dto.CreateImpersonationRequest;
import com.taipei.iot.platform.impersonation.dto.ImpersonationSessionDto;
import com.taipei.iot.platform.impersonation.dto.ImpersonationTokenResponse;
import com.taipei.iot.platform.impersonation.entity.ImpersonationSessionEntity;
import com.taipei.iot.platform.impersonation.repository.ImpersonationSessionRepository;
import com.taipei.iot.rbac.repository.PermissionRepository;
import com.taipei.iot.tenant.TenantEntity;
import com.taipei.iot.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImpersonationServiceTest {

	@InjectMocks
	private ImpersonationService service;

	@Mock
	private ImpersonationSessionRepository sessionRepository;

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private PermissionRepository permissionRepository;

	@Mock
	private UserTenantMappingRepository userTenantMappingRepository;

	@Mock
	private NotificationService notificationService;

	@Mock
	private JwtUtil jwtUtil;

	private static final String OPERATOR = "user-super-001";

	private static final String TENANT = "TENANT_A";

	private CreateImpersonationRequest req;

	private UserEntity operator;

	private TenantEntity tenant;

	@BeforeEach
	void setUp() {
		req = new CreateImpersonationRequest();
		req.setTenantId(TENANT);
		req.setReason("debug login issue ticket #123");
		req.setDurationMinutes(15);

		operator = UserEntity.builder().userId(OPERATOR).email("super@example.com").isSuperAdmin(true).build();

		tenant = new TenantEntity();
		tenant.setTenantId(TENANT);
		tenant.setTenantName("Tenant A");
		tenant.setEnabled(true);
	}

	@Test
	void create_success_persistsActiveSessionAndIssuesToken() {
		when(userRepository.findById(OPERATOR)).thenReturn(Optional.of(operator));
		when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
		// [Phase 3 / 3.1.5] permissions 來自目標租戶 ROLE_ADMIN（非 super_admin 全權）。
		when(permissionRepository.findCodesByRoleAndTenant("ROLE_ADMIN", TENANT))
			.thenReturn(List.of("USER_LIST", "DEPT_LIST"));
		when(jwtUtil.generateImpersonationAccessToken(anyString(), anyString(), anyString(), anyList(), isNull(),
				anyList(), anyString(), anyString(), anyString(), anyLong()))
			.thenReturn("imp-token");
		when(userTenantMappingRepository.findByTenantIdAndRoleIdAndEnabledTrue(TENANT, "ROLE_ADMIN"))
			.thenReturn(List.of());

		ImpersonationTokenResponse resp = service.create(req, OPERATOR);

		assertEquals("imp-token", resp.getAccessToken());
		assertEquals(TENANT, resp.getTargetTenantId());
		assertEquals("IMPERSONATION", resp.getScope());
		assertNotNull(resp.getSessionId());

		// 確認永遠不再呼叫舊的全域權限查詢。
		verify(permissionRepository, never()).findAllCodesOrderByCode();
		verify(permissionRepository).findCodesByRoleAndTenant("ROLE_ADMIN", TENANT);

		ArgumentCaptor<ImpersonationSessionEntity> captor = ArgumentCaptor.forClass(ImpersonationSessionEntity.class);
		verify(sessionRepository).save(captor.capture());
		ImpersonationSessionEntity saved = captor.getValue();
		assertEquals(OPERATOR, saved.getOperatorUserId());
		assertEquals(TENANT, saved.getTargetTenantId());
		assertEquals(ImpersonationSessionEntity.STATUS_ACTIVE, saved.getStatus());
		assertEquals(req.getReason(), saved.getReason());
		assertNotNull(saved.getStartedAt());
		assertNotNull(saved.getExpiresAt());
		assertTrue(saved.getExpiresAt().isAfter(saved.getStartedAt()));
	}

	/**
	 * [Phase 3 / 3.1.5] 驗證 impersonation token 帶的 permissions 與目標租戶 ROLE_ADMIN 完全一致；roles
	 * 仍為 SUPER_ADMIN（讓 JwtAuthenticationFilter 設 impersonator marker），但
	 * scope=IMPERSONATION 且 tenantId 為目標租戶。
	 */
	@Test
	void create_jwtClaimsCarryTargetTenantRoleAdminPermissions() {
		when(userRepository.findById(OPERATOR)).thenReturn(Optional.of(operator));
		when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
		List<String> tenantAdminPerms = List.of("USER_LIST", "USER_CREATE", "DEPT_LIST", "ROLE_LIST");
		when(permissionRepository.findCodesByRoleAndTenant("ROLE_ADMIN", TENANT)).thenReturn(tenantAdminPerms);
		when(jwtUtil.generateImpersonationAccessToken(anyString(), anyString(), anyString(), anyList(), isNull(),
				anyList(), anyString(), anyString(), anyString(), anyLong()))
			.thenReturn("imp-token");
		when(userTenantMappingRepository.findByTenantIdAndRoleIdAndEnabledTrue(TENANT, "ROLE_ADMIN"))
			.thenReturn(List.of());

		service.create(req, OPERATOR);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> rolesCap = ArgumentCaptor.forClass(List.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> permsCap = ArgumentCaptor.forClass(List.class);
		ArgumentCaptor<String> tenantCap = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> dataScopeCap = ArgumentCaptor.forClass(String.class);

		verify(jwtUtil).generateImpersonationAccessToken(eq(OPERATOR), eq("super@example.com"), tenantCap.capture(),
				rolesCap.capture(), isNull(), permsCap.capture(), dataScopeCap.capture(), eq(OPERATOR), anyString(),
				anyLong());

		assertEquals(TENANT, tenantCap.getValue());
		assertEquals(List.of("SUPER_ADMIN"), rolesCap.getValue());
		assertEquals(tenantAdminPerms, permsCap.getValue());
		assertEquals("ALL", dataScopeCap.getValue());
	}

	/**
	 * [Phase 3 / 3.1.5] 防迴歸：若目標租戶 ROLE_ADMIN 在 DB 未綁任何權限（理論上不應 發生），impersonation token
	 * 也只應拿到空權限集，而非 fallback 到全域 SUPER_ADMIN 權限。
	 */
	@Test
	void create_whenTargetTenantRoleAdminHasNoPermissions_tokenGetsEmptyPermissions() {
		when(userRepository.findById(OPERATOR)).thenReturn(Optional.of(operator));
		when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
		when(permissionRepository.findCodesByRoleAndTenant("ROLE_ADMIN", TENANT)).thenReturn(List.of());
		when(jwtUtil.generateImpersonationAccessToken(anyString(), anyString(), anyString(), anyList(), isNull(),
				anyList(), anyString(), anyString(), anyString(), anyLong()))
			.thenReturn("imp-token");
		when(userTenantMappingRepository.findByTenantIdAndRoleIdAndEnabledTrue(TENANT, "ROLE_ADMIN"))
			.thenReturn(List.of());

		service.create(req, OPERATOR);

		verify(permissionRepository, never()).findAllCodesOrderByCode();
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> permsCap = ArgumentCaptor.forClass(List.class);
		verify(jwtUtil).generateImpersonationAccessToken(anyString(), anyString(), anyString(), anyList(), isNull(),
				permsCap.capture(), anyString(), anyString(), anyString(), anyLong());
		assertTrue(permsCap.getValue().isEmpty());
	}

	@Test
	void create_durationOutOfRange_throws() {
		req.setDurationMinutes(120);
		BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req, OPERATOR));
		assertEquals(ErrorCode.IMPERSONATION_DURATION_INVALID, ex.getErrorCode());
		verify(sessionRepository, never()).save(any());
	}

	@Test
	void create_tenantNotFound_throws() {
		when(userRepository.findById(OPERATOR)).thenReturn(Optional.of(operator));
		when(tenantRepository.findById(TENANT)).thenReturn(Optional.empty());
		BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req, OPERATOR));
		assertEquals(ErrorCode.TENANT_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void create_tenantDisabled_throws() {
		tenant.setEnabled(false);
		when(userRepository.findById(OPERATOR)).thenReturn(Optional.of(operator));
		when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
		BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req, OPERATOR));
		assertEquals(ErrorCode.TENANT_DISABLED, ex.getErrorCode());
	}

	@Test
	void revoke_success_marksRevoked() {
		ImpersonationSessionEntity active = ImpersonationSessionEntity.builder()
			.id("sess-1")
			.operatorUserId(OPERATOR)
			.targetTenantId(TENANT)
			.reason("r")
			.status(ImpersonationSessionEntity.STATUS_ACTIVE)
			.startedAt(LocalDateTime.now().minusMinutes(5))
			.expiresAt(LocalDateTime.now().plusMinutes(10))
			.build();
		when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(active));

		service.revoke("sess-1", OPERATOR);

		assertEquals(ImpersonationSessionEntity.STATUS_REVOKED, active.getStatus());
		assertEquals(OPERATOR, active.getRevokedByUserId());
		assertNotNull(active.getRevokedAt());
		verify(sessionRepository).save(active);
	}

	@Test
	void revoke_notOwner_throws() {
		ImpersonationSessionEntity active = ImpersonationSessionEntity.builder()
			.id("sess-1")
			.operatorUserId("someone-else")
			.targetTenantId(TENANT)
			.reason("r")
			.status(ImpersonationSessionEntity.STATUS_ACTIVE)
			.startedAt(LocalDateTime.now())
			.expiresAt(LocalDateTime.now().plusMinutes(1))
			.build();
		when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(active));

		BusinessException ex = assertThrows(BusinessException.class, () -> service.revoke("sess-1", OPERATOR));
		assertEquals(ErrorCode.IMPERSONATION_NOT_OWNER, ex.getErrorCode());
		verify(sessionRepository, never()).save(any());
	}

	@Test
	void revoke_notFound_throws() {
		when(sessionRepository.findById("missing")).thenReturn(Optional.empty());
		BusinessException ex = assertThrows(BusinessException.class, () -> service.revoke("missing", OPERATOR));
		assertEquals(ErrorCode.IMPERSONATION_SESSION_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void revoke_alreadyRevoked_isIdempotent() {
		ImpersonationSessionEntity revoked = ImpersonationSessionEntity.builder()
			.id("sess-1")
			.operatorUserId(OPERATOR)
			.targetTenantId(TENANT)
			.reason("r")
			.status(ImpersonationSessionEntity.STATUS_REVOKED)
			.startedAt(LocalDateTime.now())
			.expiresAt(LocalDateTime.now().plusMinutes(1))
			.revokedAt(LocalDateTime.now())
			.revokedByUserId(OPERATOR)
			.build();
		when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(revoked));

		service.revoke("sess-1", OPERATOR);

		verify(sessionRepository, never()).save(any());
	}

	@Test
	void list_marksExpiredLazily() {
		ImpersonationSessionEntity expiredButActive = ImpersonationSessionEntity.builder()
			.id("sess-1")
			.operatorUserId(OPERATOR)
			.targetTenantId(TENANT)
			.reason("r")
			.status(ImpersonationSessionEntity.STATUS_ACTIVE)
			.startedAt(LocalDateTime.now().minusHours(2))
			.expiresAt(LocalDateTime.now().minusHours(1))
			.build();
		when(sessionRepository.findByOperatorUserIdOrderByStartedAtDesc(OPERATOR))
			.thenReturn(List.of(expiredButActive));
		when(tenantRepository.findAllById(anyList())).thenReturn(List.of(tenant));

		List<ImpersonationSessionDto> result = service.listByOperator(OPERATOR, null);

		assertEquals(1, result.size());
		assertEquals(ImpersonationSessionEntity.STATUS_EXPIRED, result.get(0).getStatus());
		assertEquals("Tenant A", result.get(0).getTargetTenantName());
	}

	@Test
	void create_notifiesAllTargetTenantAdmins() {
		UserTenantMappingEntity m1 = new UserTenantMappingEntity();
		m1.setUserId("admin-1");
		m1.setTenantId(TENANT);
		UserTenantMappingEntity m2 = new UserTenantMappingEntity();
		m2.setUserId("admin-2");
		m2.setTenantId(TENANT);

		when(userRepository.findById(OPERATOR)).thenReturn(Optional.of(operator));
		when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
		when(permissionRepository.findCodesByRoleAndTenant("ROLE_ADMIN", TENANT)).thenReturn(List.of("USER_LIST"));
		when(jwtUtil.generateImpersonationAccessToken(anyString(), anyString(), anyString(), anyList(), isNull(),
				anyList(), anyString(), anyString(), anyString(), anyLong()))
			.thenReturn("imp-token");
		when(userTenantMappingRepository.findByTenantIdAndRoleIdAndEnabledTrue(TENANT, "ROLE_ADMIN"))
			.thenReturn(List.of(m1, m2));

		service.create(req, OPERATOR);

		ArgumentCaptor<NotificationPayload> cap = ArgumentCaptor.forClass(NotificationPayload.class);
		verify(notificationService).send(cap.capture());
		NotificationPayload p = cap.getValue();
		assertEquals(TENANT, p.getTenantId());
		assertEquals(List.of("admin-1", "admin-2"), p.getUserIds());
		assertEquals(NotificationType.ALERT, p.getType());
		assertEquals(NotificationRefType.IMPERSONATION, p.getRefType());
		assertNotNull(p.getRefId());
		assertTrue(p.getContent().contains(req.getReason()));
	}

	@Test
	void create_notificationFailure_doesNotBreakImpersonation() {
		when(userRepository.findById(OPERATOR)).thenReturn(Optional.of(operator));
		when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
		when(permissionRepository.findCodesByRoleAndTenant("ROLE_ADMIN", TENANT)).thenReturn(List.of("USER_LIST"));
		when(jwtUtil.generateImpersonationAccessToken(anyString(), anyString(), anyString(), anyList(), isNull(),
				anyList(), anyString(), anyString(), anyString(), anyLong()))
			.thenReturn("imp-token");
		when(userTenantMappingRepository.findByTenantIdAndRoleIdAndEnabledTrue(TENANT, "ROLE_ADMIN"))
			.thenThrow(new RuntimeException("db down"));

		ImpersonationTokenResponse resp = service.create(req, OPERATOR);

		assertEquals("imp-token", resp.getAccessToken());
		verify(sessionRepository).save(any());
		verify(notificationService, never()).send(any());
	}

	@Test
	void create_noAdminsInTargetTenant_skipsNotification() {
		when(userRepository.findById(OPERATOR)).thenReturn(Optional.of(operator));
		when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
		when(permissionRepository.findCodesByRoleAndTenant("ROLE_ADMIN", TENANT)).thenReturn(List.of("USER_LIST"));
		when(jwtUtil.generateImpersonationAccessToken(anyString(), anyString(), anyString(), anyList(), isNull(),
				anyList(), anyString(), anyString(), anyString(), anyLong()))
			.thenReturn("imp-token");
		when(userTenantMappingRepository.findByTenantIdAndRoleIdAndEnabledTrue(TENANT, "ROLE_ADMIN"))
			.thenReturn(List.of());

		service.create(req, OPERATOR);

		verify(notificationService, never()).send(any());
	}

}
