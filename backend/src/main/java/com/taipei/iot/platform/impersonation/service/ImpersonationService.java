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
import com.taipei.iot.tenant.TenantContext;
import com.taipei.iot.tenant.TenantEntity;
import com.taipei.iot.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Impersonation service (ADR-002 / Phase 1).
 *
 * <p>
 * Issues a short-lived IMPERSONATION-scope JWT that lets a SUPER_ADMIN act inside a
 * target tenant for up to 60 minutes. The session row is the audit anchor:
 * {@code user_event_log.impersonation_session_id} (V63) and {@code impersonated_by} (V60)
 * point back here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImpersonationService {

	private static final String TARGET_TENANT_ADMIN_ROLE_ID = "ROLE_ADMIN";

	private final ImpersonationSessionRepository sessionRepository;

	private final TenantRepository tenantRepository;

	private final UserRepository userRepository;

	private final PermissionRepository permissionRepository;

	private final UserTenantMappingRepository userTenantMappingRepository;

	private final NotificationService notificationService;

	private final JwtUtil jwtUtil;

	@Transactional
	public ImpersonationTokenResponse create(CreateImpersonationRequest req, String operatorUserId) {
		// Defence-in-depth: @PreAuthorize already gates PLATFORM_IMPERSONATE.
		if (req.getDurationMinutes() == null || req.getDurationMinutes() < 1 || req.getDurationMinutes() > 60) {
			throw new BusinessException(ErrorCode.IMPERSONATION_DURATION_INVALID);
		}

		UserEntity operator = userRepository.findById(operatorUserId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		TenantEntity tenant = tenantRepository.findById(req.getTenantId())
			.orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND));
		if (!Boolean.TRUE.equals(tenant.getEnabled())) {
			throw new BusinessException(ErrorCode.TENANT_DISABLED);
		}

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime expiresAt = now.plusMinutes(req.getDurationMinutes());

		String sessionId = UUID.randomUUID().toString();
		ImpersonationSessionEntity session = ImpersonationSessionEntity.builder()
			.id(sessionId)
			.operatorUserId(operatorUserId)
			.targetTenantId(tenant.getTenantId())
			.reason(req.getReason())
			.status(ImpersonationSessionEntity.STATUS_ACTIVE)
			.startedAt(now)
			.expiresAt(expiresAt)
			.build();
		sessionRepository.save(session);

		// [Phase 3 / 3.1.5] IMPERSONATION token：
		// - scope=IMPERSONATION（由 JwtUtil.generateImpersonationAccessToken 強制設定）
		// - tenantId = 目標租戶
		// - roles 保留 SUPER_ADMIN，讓 JwtAuthenticationFilter 仍可識別為「代操中」並
		// 設定 TenantContext.setImpersonator()；操作識別由 impersonation_session_id +
		// originalUserId 提供。
		// - permissions 套用「目標租戶 ROLE_ADMIN」的權限集（不再給 super_admin 全權），
		// 違規請求仍由 ScopeEnforcementFilter（enforce mode）兜底。
		List<String> permissions = permissionRepository.findCodesByRoleAndTenant(TARGET_TENANT_ADMIN_ROLE_ID,
				tenant.getTenantId());
		long expiresAtEpoch = expiresAt.toEpochSecond(ZoneOffset.UTC);
		String accessToken = jwtUtil.generateImpersonationAccessToken(operator.getUserId(), operator.getEmail(),
				tenant.getTenantId(), List.of("SUPER_ADMIN"), null, permissions, "ALL", operatorUserId, sessionId,
				expiresAtEpoch);

		log.info("[Impersonation] operator={} target={} session={} reason={} duration={}m", operatorUserId,
				tenant.getTenantId(), sessionId, req.getReason(), req.getDurationMinutes());

		// [1.1.7] Best-effort notify all ROLE_ADMIN of target tenant. Failure
		// must not block impersonation issuance — NotificationService.send is
		// already best-effort per channel, but wrap in try/catch as defence.
		notifyTargetTenantAdmins(operator, tenant, sessionId, req);

		return ImpersonationTokenResponse.builder()
			.accessToken(accessToken)
			.sessionId(sessionId)
			.targetTenantId(tenant.getTenantId())
			.expiresAt(expiresAt)
			.scope("IMPERSONATION")
			.build();
	}

	@Transactional
	public void revoke(String sessionId, String operatorUserId) {
		ImpersonationSessionEntity session = sessionRepository.findById(sessionId)
			.orElseThrow(() -> new BusinessException(ErrorCode.IMPERSONATION_SESSION_NOT_FOUND));

		if (!session.getOperatorUserId().equals(operatorUserId)) {
			throw new BusinessException(ErrorCode.IMPERSONATION_NOT_OWNER);
		}

		if (!ImpersonationSessionEntity.STATUS_ACTIVE.equals(session.getStatus())) {
			// Idempotent: already revoked/expired → no-op.
			return;
		}

		session.setStatus(ImpersonationSessionEntity.STATUS_REVOKED);
		session.setRevokedAt(LocalDateTime.now());
		session.setRevokedByUserId(operatorUserId);
		sessionRepository.save(session);

		log.info("[Impersonation] revoked session={} by={}", sessionId, operatorUserId);
	}

	@Transactional(readOnly = true)
	public List<ImpersonationSessionDto> listByOperator(String operatorUserId, String statusFilter) {
		List<ImpersonationSessionEntity> rows = (statusFilter == null || statusFilter.isBlank())
				? sessionRepository.findByOperatorUserIdOrderByStartedAtDesc(operatorUserId)
				: sessionRepository.findByOperatorUserIdAndStatusOrderByStartedAtDesc(operatorUserId, statusFilter);

		// Batch-resolve tenant names.
		List<String> tenantIds = rows.stream().map(ImpersonationSessionEntity::getTargetTenantId).distinct().toList();
		Map<String, String> tenantNames = tenantRepository.findAllById(tenantIds)
			.stream()
			.collect(Collectors.toMap(TenantEntity::getTenantId, TenantEntity::getTenantName));

		LocalDateTime now = LocalDateTime.now();
		return rows.stream().map(s -> toDto(s, tenantNames, now)).collect(Collectors.toList());
	}

	private void notifyTargetTenantAdmins(UserEntity operator, TenantEntity tenant, String sessionId,
			CreateImpersonationRequest req) {
		try {
			// Cross-tenant lookup: operator is SUPER_ADMIN whose TenantContext
			// does not equal target tenantId. Use system context to bypass the
			// tenant filter.
			List<UserTenantMappingEntity> adminMappings = TenantContext
				.runInSystemContext(() -> userTenantMappingRepository
					.findByTenantIdAndRoleIdAndEnabledTrue(tenant.getTenantId(), TARGET_TENANT_ADMIN_ROLE_ID));
			List<String> adminUserIds = adminMappings.stream()
				.map(UserTenantMappingEntity::getUserId)
				.distinct()
				.toList();
			if (adminUserIds.isEmpty()) {
				log.info("[Impersonation] no ROLE_ADMIN in target tenant={}; skip notification", tenant.getTenantId());
				return;
			}
			String title = "平台管理員進入代操模式";
			String content = String.format("%s (%s) 已進入貴場域進行代操，為期 %d 分鐘。原因：%s",
					operator.getDisplayName() != null ? operator.getDisplayName() : operator.getEmail(),
					operator.getEmail(), req.getDurationMinutes(), req.getReason());
			NotificationPayload payload = NotificationPayload.builder()
				.tenantId(tenant.getTenantId())
				.userIds(adminUserIds)
				.type(NotificationType.ALERT)
				.title(title)
				.content(content)
				.refType(NotificationRefType.IMPERSONATION)
				.refId(sessionId)
				.build();
			notificationService.send(payload);
			log.info("[Impersonation] notified {} admin(s) of tenant={} for session={}", adminUserIds.size(),
					tenant.getTenantId(), sessionId);
		}
		catch (Exception e) {
			log.warn("[Impersonation] failed to notify target tenant admins (session={}): {}", sessionId,
					e.getMessage());
		}
	}

	private ImpersonationSessionDto toDto(ImpersonationSessionEntity s, Map<String, String> tenantNames,
			LocalDateTime now) {
		// Lazy EXPIRED display: rows still status=ACTIVE but past expires_at
		// are surfaced as EXPIRED at read time (background sweeper not in Phase 1).
		String status = s.getStatus();
		if (ImpersonationSessionEntity.STATUS_ACTIVE.equals(status) && s.getExpiresAt() != null
				&& now.isAfter(s.getExpiresAt())) {
			status = ImpersonationSessionEntity.STATUS_EXPIRED;
		}
		return ImpersonationSessionDto.builder()
			.id(s.getId())
			.operatorUserId(s.getOperatorUserId())
			.targetTenantId(s.getTargetTenantId())
			.targetTenantName(Optional.ofNullable(tenantNames.get(s.getTargetTenantId())).orElse(""))
			.reason(s.getReason())
			.status(status)
			.startedAt(s.getStartedAt())
			.expiresAt(s.getExpiresAt())
			.revokedAt(s.getRevokedAt())
			.build();
	}

}
