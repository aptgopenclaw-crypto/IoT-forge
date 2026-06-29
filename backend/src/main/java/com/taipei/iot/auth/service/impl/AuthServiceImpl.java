package com.taipei.iot.auth.service.impl;

import com.taipei.iot.auth.dto.request.ForgotPasswordRequest;
import com.taipei.iot.auth.dto.request.ForceChangePasswordRequest;
import com.taipei.iot.auth.dto.request.LoginRequest;
import com.taipei.iot.auth.dto.request.ResetPasswordRequest;
import com.taipei.iot.auth.dto.request.SelectTenantRequest;
import com.taipei.iot.auth.dto.request.SwitchTenantRequest;
import com.taipei.iot.auth.dto.response.LoginResult;
import com.taipei.iot.auth.dto.response.TenantOption;
import com.taipei.iot.auth.dto.response.TokenResult;
import com.taipei.iot.auth.dto.response.UserInfoDto;
import com.taipei.iot.audit.entity.UserEventLogEntity;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.audit.repository.UserEventLogRepository;
import com.taipei.iot.auth.entity.ChangePasswordLogEntity;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.entity.UserResetPasswordTokenEntity;
import com.taipei.iot.auth.entity.UserSessionEntity;
import com.taipei.iot.auth.entity.UserTenantMappingEntity;
import com.taipei.iot.auth.repository.ChangePasswordLogRepository;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.auth.repository.UserResetPasswordTokenRepository;
import com.taipei.iot.auth.repository.UserTenantMappingRepository;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.auth.security.TokenScope;
import com.taipei.iot.auth.policy.PasswordExpiryChecker;
import com.taipei.iot.auth.policy.PasswordExpiryStatus;
import com.taipei.iot.auth.service.AuthService;
import com.taipei.iot.auth.service.CaptchaService;
import com.taipei.iot.auth.service.PasswordResetMailService;
import com.taipei.iot.auth.service.TurnstileService;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.enums.SecurityEvent;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.SecurityLogger;
import com.taipei.iot.user.entity.PasswordHistoryEntity;
import com.taipei.iot.user.repository.PasswordHistoryRepository;
import com.taipei.iot.user.service.PasswordValidator;
import com.taipei.iot.tenant.TenantContext;
import com.taipei.iot.tenant.TenantEntity;
import com.taipei.iot.tenant.TenantRepository;
import io.jsonwebtoken.Claims;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taipei.iot.common.util.JwtClaimKeys;
import com.taipei.iot.common.util.TenantAwareQuery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

	private static final String SUPER_ADMIN_ROLE_NAME = "超級管理員";

	private final UserRepository userRepository;

	private final UserTenantMappingRepository userTenantMappingRepository;

	private final UserEventLogRepository userEventLogRepository;

	private final ChangePasswordLogRepository changePasswordLogRepository;

	private final UserResetPasswordTokenRepository userResetPasswordTokenRepository;

	private final TenantRepository tenantRepository;

	private final CaptchaService captchaService;

	private final TurnstileService turnstileService;

	private final PasswordResetMailService passwordResetMailService;

	private final PasswordValidator passwordValidator;

	private final PasswordHistoryRepository passwordHistoryRepository;

	private final JwtUtil jwtUtil;

	private final PasswordEncoder passwordEncoder;

	private final StringRedisTemplate stringRedisTemplate;

	private final com.taipei.iot.rbac.repository.PermissionRepository permissionRepository;

	private final ResetPasswordTokenClaimer resetPasswordTokenClaimer;

	private final com.taipei.iot.auth.repository.UserSessionRepository userSessionRepository;

	private final PasswordExpiryChecker passwordExpiryChecker;

	private final com.taipei.iot.auth.provider.AuthenticationDispatcher authenticationDispatcher;

	@PersistenceContext
	private EntityManager entityManager;

	@Value("${app.security.lock.max-fail-count:5}")
	private int maxFailCount;

	@Value("${app.security.lock.lock-duration-minutes:10}")
	private int lockDurationMinutes;

	@Override
	@Transactional
	public LoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
		// 1. Verify captcha（支援圖片驗證碼 或 Turnstile 兩種方式）

		log.info("Login attempt for email={} from IP={}", request.getEmail(), httpRequest.getRemoteAddr());
		verifyCaptcha(request, httpRequest);

		// 2. Delegate authentication to provider via dispatcher
		// Pre-resolve tenantId from user's mapping so the dispatcher can load the
		// correct tenant auth config (e.g. LDAP connection details) even when
		// tenantId is not present in the login request body.
		log.info("Dispatching authentication request for email={} to provider", request.getEmail());
		String resolvedTenantId = resolveLoginTenantId(request.getEmail());
		log.info("Pre-resolved tenantId for email={}: {}", request.getEmail(), resolvedTenantId);
		com.taipei.iot.auth.provider.AuthenticationRequest authReq = com.taipei.iot.auth.provider.AuthenticationRequest
			.builder()
			.identifier(request.getEmail())
			.credential(request.getPassword())
			.tenantId(resolvedTenantId)
			.build();

		com.taipei.iot.auth.provider.AuthenticationResult authResult;
		try {
			log.info("Invoking authentication dispatcher for email={}", request.getEmail());
			authResult = authenticationDispatcher.dispatch(authReq);
		}
		catch (BusinessException e) {
			// 登入失敗：寫入稽核紀錄（含 deptId，讓 DEPT_ADMIN 可看到失敗事件）
			log.info("Authentication failed for email={}: {}", request.getEmail(), e.getMessage());
			logLoginFailure(request.getEmail(), e, httpRequest);
			throw e;
		}

		// 3. Resolve local user from provider result
		log.info("Authentication successful for email={}, resolved localUserId={}", request.getEmail(),
				authResult.getLocalUserId());
		UserEntity user = userRepository.findById(authResult.getLocalUserId())
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		// 7. Query user_tenant_mapping (enabled = true) — bypass tenant filter
		log.info("Querying tenant mappings for userId={} (bypassing tenant filter)", user.getUserId());
		List<UserTenantMappingEntity> mappings = queryMappingsWithSystemContext(
				() -> userTenantMappingRepository.findByUserIdAndEnabledTrue(user.getUserId()));

		// 7b. [Phase 3] Password expiry / force-change check.
		// Per spec D-2 this runs AFTER passwordEncoder.matches() so the credential
		// itself is still considered valid; only token issuance is gated.
		// Policy tenant resolution: if the user belongs to exactly one tenant we
		// use that tenant's policy (most specific); otherwise fall back to the
		// platform default since the user has not yet selected a tenant.
		String policyTenantId = (mappings.size() == 1) ? mappings.get(0).getTenantId() : null;
		PasswordExpiryStatus expiryStatus = passwordExpiryChecker.check(user, policyTenantId);
		if (expiryStatus != PasswordExpiryStatus.OK) {
			String pwdChangeToken = jwtUtil.generatePasswordChangeToken(user.getUserId(), user.getEmail());
			log.info("login: password change required for user={} status={}", user.getEmail(), expiryStatus);
			logLoginEvent(user.getUserId(), policyTenantId, user.getEmail(), user.getDisplayName(), null,
					AuditEventType.LOGIN_SUCCESS, "Password change required (" + expiryStatus + ")", httpRequest);
			return LoginResult.builder().accessToken(pwdChangeToken).passwordChangeRequired(true).build();
		}

		// 8. Determine response
		return buildPostAuthLoginResult(user, mappings, httpRequest);
	}

	/**
	 * [Phase 3] Builds the {@link LoginResult} for an authenticated user that has already
	 * passed credential + expiry checks. Shared by {@link #login} and the post-success
	 * branch of {@link #forceChangePassword}.
	 */
	private LoginResult buildPostAuthLoginResult(UserEntity user, List<UserTenantMappingEntity> mappings,
			HttpServletRequest httpRequest) {
		if (user.getIsSuperAdmin()) {
			// [Phase 5 / ADR-007] SUPER_ADMIN → direct PLATFORM access token
			// (no tenant binding). Replaces the legacy temp-token + select-tenant
			// bootstrap: under the platform/tenant split a super_admin never
			// operates inside a tenant via select-tenant; they land on the
			// platform shell (/platform/tenants) and must use the impersonation
			// flow (Phase 4.1.9) to act inside any single tenant.
			String userId = user.getUserId();
			// ROLE_SUPER_ADMIN is bound to PLATFORM_* permissions with
			// tenant_id = NULL (V65 migration); passing tenantId=null retrieves
			// exactly those four PLATFORM_* codes.
			List<String> permissions = resolvePermissions("ROLE_SUPER_ADMIN", null);

			String accessToken = jwtUtil.generateAccessToken(userId, user.getEmail(), null, // no
																							// tenant
																							// binding
					List.of("SUPER_ADMIN"), null, // no dept
					permissions, "ALL", TokenScope.PLATFORM);
			// [v2 N-7] session/refresh pair; user_session.tenant_id allows
			// NULL (see V43 migration + UserSessionEntity Javadoc).
			String jti = UUID.randomUUID().toString();
			String refreshToken = jwtUtil.generateRefreshToken(userId, null, jti);
			recordSession(jti, userId, null, httpRequest);

			logLoginEvent(userId, null, user.getEmail(), user.getDisplayName(), null, AuditEventType.LOGIN_SUCCESS,
					"Super admin platform login", httpRequest);

			return LoginResult.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.needsSelection(false)
				.isSuperAdmin(true)
				.build();
		}

		if (mappings.isEmpty()) {
			logLoginEvent(user.getUserId(), null, user.getEmail(), user.getDisplayName(), null,
					AuditEventType.LOGIN_FAIL, "No tenant mapping", httpRequest);
			throw new BusinessException(ErrorCode.TENANT_ACCESS_DENIED);
		}

		// 已停用場域 (tenant.enabled = false) 之下的所有 mapping 一律排除：
		// 即使 user_tenant_mapping.enabled = true，整個場域被停用時其轄下帳號仍不得登入。
		// Super admin 不屬於任何場域，不受此限制。
		Set<String> tenantIds = mappings.stream().map(UserTenantMappingEntity::getTenantId).collect(Collectors.toSet());
		Map<String, TenantEntity> tenantMap = tenantRepository.findAllById(tenantIds)
			.stream()
			.collect(Collectors.toMap(TenantEntity::getTenantId, Function.identity()));
		List<UserTenantMappingEntity> activeMappings = mappings.stream().filter(m -> {
			TenantEntity t = tenantMap.get(m.getTenantId());
			return t != null && Boolean.TRUE.equals(t.getEnabled());
		}).collect(Collectors.toList());

		if (activeMappings.isEmpty()) {
			logLoginEvent(user.getUserId(), null, user.getEmail(), user.getDisplayName(), null,
					AuditEventType.LOGIN_FAIL, "All accessible tenants are disabled", httpRequest);
			throw new BusinessException(ErrorCode.TENANT_ACCESS_DENIED);
		}

		if (activeMappings.size() == 1) {
			// Single tenant → full JWT
			UserTenantMappingEntity mapping = activeMappings.get(0);
			List<String> permissions = resolvePermissions(mapping.getRoleId(), mapping.getTenantId());

			String accessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getEmail(), mapping.getTenantId(),
					List.of(mapping.getRole() != null ? mapping.getRole().getCode() : ""),
					mapping.getDeptId() != null ? mapping.getDeptId().toString() : null, permissions,
					mapping.getRole() != null ? mapping.getRole().getDataScope() : "ALL", TokenScope.TENANT);
			// [v2 N-7] 先建 session row、以其 sessionId 為 jti 簽發 refresh token
			String jti = UUID.randomUUID().toString();
			String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), mapping.getTenantId(), jti);
			recordSession(jti, user.getUserId(), mapping.getTenantId(), httpRequest);

			logLoginEvent(user.getUserId(), mapping.getTenantId(), user.getEmail(), user.getDisplayName(),
					mapping.getDeptId(), AuditEventType.LOGIN_SUCCESS, "Single tenant login", httpRequest);

			return LoginResult.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.needsSelection(false)
				.build();
		}

		// Multi-tenant → temporary token + tenant list
		String tempToken = jwtUtil.generateTemporaryToken(user.getUserId(), user.getEmail(), false);
		List<TenantOption> tenantOptions = buildTenantOptions(activeMappings);

		logLoginEvent(user.getUserId(), null, user.getEmail(), user.getDisplayName(), null,
				AuditEventType.LOGIN_SUCCESS, "Multi-tenant - needs tenant selection", httpRequest);

		return LoginResult.builder().accessToken(tempToken).needsSelection(true).tenants(tenantOptions).build();
	}

	@Override
	@Transactional
	public TokenResult selectTenant(String userId, SelectTenantRequest request, HttpServletRequest httpRequest) {
		UserEntity user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		return issueTenantToken(user, request.getTenantId(), AuditEventType.LOGIN_SUCCESS,
				user.getIsSuperAdmin() ? "Super admin selected tenant" : "Tenant selected", httpRequest, true);
	}

	@Override
	@Transactional
	public TokenResult switchTenant(String userId, SwitchTenantRequest request, HttpServletRequest httpRequest) {
		UserEntity user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		return issueTenantToken(user, request.getTenantId(), AuditEventType.TENANT_SWITCH,
				user.getIsSuperAdmin() ? "Super admin switched tenant" : "Tenant switched", httpRequest, false);
	}

	/**
	 * selectTenant 與 switchTenant 共用的核心流程：驗證租戶存取權、解析權限後 簽發 access/refresh token，並寫入 audit
	 * log。
	 *
	 * superAdminAttachDeptFromMapping = true → selectTenant 行為：super admin 嘗試 從
	 * user_tenant_mapping 帶 deptId（若有 mapping）。 superAdminAttachDeptFromMapping = false →
	 * switchTenant 行為：super admin 不查 mapping，deptId 一律為 null（沿用既有行為，避免行為漂移）。
	 */
	private TokenResult issueTenantToken(UserEntity user, String tenantId, AuditEventType event, String reason,
			HttpServletRequest httpRequest, boolean superAdminAttachDeptFromMapping) {
		String userId = user.getUserId();

		if (user.getIsSuperAdmin()) {
			// Super admin 需驗證租戶存在且已啟用
			TenantEntity requestedTenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND));
			if (!requestedTenant.getEnabled()) {
				throw new BusinessException(ErrorCode.TENANT_ACCESS_DENIED);
			}

			UserTenantMappingEntity mapping = null;
			if (superAdminAttachDeptFromMapping) {
				mapping = queryMappingsWithSystemContext(
						() -> userTenantMappingRepository.findByUserIdAndTenantId(userId, tenantId).orElse(null));
			}

			String deptIdStr = mapping != null && mapping.getDeptId() != null ? mapping.getDeptId().toString() : null;
			Long deptIdForLog = mapping != null ? mapping.getDeptId() : null;
			List<String> permissions = resolvePermissions("ROLE_SUPER_ADMIN", tenantId);

			// [ADR-007] Phase 1: super_admin entering a tenant via select/switch
			// still produces a tenant-bound token; mark as TENANT scope. Phase 3
			// will eliminate this path entirely in favour of IMPERSONATION tokens.
			String accessToken = jwtUtil.generateAccessToken(userId, user.getEmail(), tenantId, List.of("SUPER_ADMIN"),
					deptIdStr, permissions, "ALL", TokenScope.TENANT);
			// [v2 N-7] 先建 session row、以其 sessionId 為 jti 簽發 refresh token
			String superJti = UUID.randomUUID().toString();
			String refreshToken = jwtUtil.generateRefreshToken(userId, tenantId, superJti);
			recordSession(superJti, userId, tenantId, httpRequest);

			logLoginEvent(userId, tenantId, user.getEmail(), user.getDisplayName(), deptIdForLog, event, reason,
					httpRequest);

			return TokenResult.builder().accessToken(accessToken).refreshToken(refreshToken).build();
		}

		UserTenantMappingEntity mapping = queryMappingsWithSystemContext(
				() -> userTenantMappingRepository.findByUserIdAndTenantId(userId, tenantId)
					.orElseThrow(() -> new BusinessException(ErrorCode.TENANT_ACCESS_DENIED)));

		if (!mapping.getEnabled()) {
			throw new BusinessException(ErrorCode.TENANT_ACCESS_DENIED);
		}

		// 場域被停用時，其轄下帳號不得選擇 / 切換進入該場域（與 JwtAuthenticationFilter
		// 即時拒絕已停用場域請求的設計一致；此處在發 token 前先擋，避免發出無效 token）。
		TenantEntity tenant = tenantRepository.findById(mapping.getTenantId())
			.orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND));
		if (!Boolean.TRUE.equals(tenant.getEnabled())) {
			throw new BusinessException(ErrorCode.TENANT_ACCESS_DENIED);
		}

		List<String> permissions = resolvePermissions(mapping.getRoleId(), mapping.getTenantId());
		String roleCode = mapping.getRole() != null ? mapping.getRole().getCode() : "";

		String accessToken = jwtUtil.generateAccessToken(userId, user.getEmail(), mapping.getTenantId(),
				List.of(roleCode), mapping.getDeptId() != null ? mapping.getDeptId().toString() : null, permissions,
				mapping.getRole() != null ? mapping.getRole().getDataScope() : "ALL", TokenScope.TENANT);
		// [v2 N-7] 先建 session row、以其 sessionId 為 jti 簽發 refresh token
		String jti = UUID.randomUUID().toString();
		String refreshToken = jwtUtil.generateRefreshToken(userId, mapping.getTenantId(), jti);
		recordSession(jti, userId, mapping.getTenantId(), httpRequest);

		logLoginEvent(userId, mapping.getTenantId(), user.getEmail(), user.getDisplayName(), mapping.getDeptId(), event,
				reason, httpRequest);

		return TokenResult.builder().accessToken(accessToken).refreshToken(refreshToken).build();
	}

	@Override
	@Transactional
	public TokenResult refreshToken(String refreshToken, HttpServletRequest httpRequest) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
		}

		Claims claims;
		try {
			claims = jwtUtil.parseToken(refreshToken);
		}
		catch (Exception e) {
			throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
		}

		// 驗證 token 類型：防止 access token 冒用為 refresh token
		String tokenType = claims.get("type", String.class);
		if (!"refresh".equals(tokenType)) {
			throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
		}

		// 伺服器端撤銷檢查：若 jti 已被 logout / 管理員列入黑名單，則拒絕換發
		// 舊 token（未含 jti）在 7 天過渡期內仍可使用；jti 達 7 天後自動過期。
		if (isRefreshTokenRevoked(claims.getId())) {
			throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
		}

		String userId = claims.get("uid", String.class);
		if (userId == null) {
			throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
		}

		UserEntity user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

		// 從 refresh token claims 取出發行時的 tenantId，沿用以避免多租戶環境下靜默切換租戶
		String tenantId = claims.get(JwtClaimKeys.TENANT_ID, String.class);

		List<String> roles;
		String deptId;
		List<String> permissions;
		String dataScope;

		if (user.getIsSuperAdmin()) {
			// 驗證租戶仍為啟用狀態，防止 SUPER_ADMIN 在租戶停用後繼續透過 refresh 延長授權
			if (tenantId != null) {
				TenantEntity refreshTenant = tenantRepository.findById(tenantId)
					.orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
				if (!refreshTenant.getEnabled()) {
					throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
				}
			}
			roles = List.of("SUPER_ADMIN");
			deptId = null;
			dataScope = "ALL";
			permissions = tenantId != null ? resolvePermissions("ROLE_SUPER_ADMIN", tenantId) : Collections.emptyList();
		}
		else {
			if (tenantId == null) {
				throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
			}
			// 查詢 refresh token 發行時對應的 tenantId mapping，確保租戶不漂移
			UserTenantMappingEntity mapping = queryMappingsWithSystemContext(
					() -> userTenantMappingRepository.findByUserIdAndTenantId(userId, tenantId)
						.orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID)));
			if (!mapping.getEnabled()) {
				throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
			}
			roles = List.of(mapping.getRole() != null ? mapping.getRole().getCode() : "");
			deptId = mapping.getDeptId() != null ? mapping.getDeptId().toString() : null;
			dataScope = mapping.getRole() != null ? mapping.getRole().getDataScope() : "ALL";
			permissions = resolvePermissions(mapping.getRoleId(), tenantId);
		}

		// [ADR-007] Refresh re-derives scope from the user: super_admin → PLATFORM
		// when no tenant bound; else TENANT (impersonation tokens are not refreshable).
		TokenScope refreshScope = (user.getIsSuperAdmin() && tenantId == null) ? TokenScope.PLATFORM
				: TokenScope.TENANT;
		String newAccessToken = jwtUtil.generateAccessToken(userId, user.getEmail(), tenantId, roles, deptId,
				permissions, dataScope, refreshScope);
		// [v2 N-7] Refresh rotation：產生新 jti = 新 session row；舊 jti 同時寫 Redis
		// 撤銷表與標記 user_session.revoked=true，達成「全面換代」。
		String newJti = UUID.randomUUID().toString();
		String newRefreshToken = jwtUtil.generateRefreshToken(userId, tenantId, newJti);
		recordSession(newJti, userId, tenantId, httpRequest);
		rotateOldSession(claims.getId(), claims.getExpiration());

		return TokenResult.builder().accessToken(newAccessToken).refreshToken(newRefreshToken).build();
	}

	@Override
	public void logout(String refreshToken) {
		// 伺服器端撤銷：將 refresh token 的 jti 加入 Redis 黑名單，
		// TTL = token 剩餘有效秒數。同時 cookie 清除由 controller 負責。
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}
		Claims claims;
		try {
			claims = jwtUtil.parseToken(refreshToken);
		}
		catch (Exception e) {
			// 無法解析（已過期、簽名錯誤、格式異常）則不需撤銷，
			// 專高皆可避免外部透過 logout 探測 token 有效性。
			log.debug("logout: refresh token parse failed (ignored): {}", e.getMessage());
			return;
		}
		String jti = claims.getId();
		Date expiration = claims.getExpiration();
		if (jti == null || jti.isBlank() || expiration == null) {
			// 舊 token 不含 jti，無法針對性撤銷，依賴 cookie 清除 + 自然過期。
			return;
		}
		long ttlMs = expiration.getTime() - System.currentTimeMillis();
		if (ttlMs <= 0) {
			return;
		}
		try {
			stringRedisTemplate.opsForValue().set(refreshRevocationKey(jti), "1", ttlMs, TimeUnit.MILLISECONDS);
		}
		catch (Exception e) {
			// Redis 異常不應阻斷登出，但需記錄供運維追蹤。
			log.warn("logout: failed to write refresh-token revocation entry: {}", e.getMessage());
		}
		// [v2 N-7] 同步標記 user_session row 為 revoked，使「登入裝置」清單反映即時狀態。
		try {
			userSessionRepository.revokeById(jti, LocalDateTime.now());
		}
		catch (Exception e) {
			log.warn("logout: failed to revoke user_session row: {}", e.getMessage());
		}
	}

	private static String refreshRevocationKey(String jti) {
		return "auth:revoked_refresh:" + jti;
	}

	private boolean isRefreshTokenRevoked(String jti) {
		if (jti == null || jti.isBlank()) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(stringRedisTemplate.hasKey(refreshRevocationKey(jti)));
		}
		catch (Exception e) {
			// Fail-open：Redis 不可用時仍允許 refresh，避免造成全面期間無法換發 token。
			log.warn("logout: failed to check refresh-token revocation: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * [v2 N-7] 寫入一筆 user_session row，sessionId = JWT jti。 任何 IP / UA 取得失敗都不應阻斷 token
	 * 簽發；換句話說「不能追蹤不能阻止登入」。
	 */
	private void recordSession(String jti, String userId, String tenantId, HttpServletRequest httpRequest) {
		try {
			LocalDateTime now = LocalDateTime.now();
			String ip = httpRequest != null ? httpRequest.getRemoteAddr() : null;
			String ua = httpRequest != null ? httpRequest.getHeader("User-Agent") : null;
			if (ua != null && ua.length() > 512) {
				ua = ua.substring(0, 512);
			}
			long ttlMs = jwtUtil.getRefreshTokenExpirationMs();
			UserSessionEntity session = UserSessionEntity.builder()
				.sessionId(jti)
				.userId(userId)
				.tenantId(tenantId)
				.ipAddress(ip)
				.userAgent(ua)
				.issuedAt(now)
				.lastSeenAt(now)
				.expiresAt(now.plusSeconds(ttlMs / 1000))
				.revoked(false)
				.build();
			userSessionRepository.save(session);
		}
		catch (Exception e) {
			log.warn("recordSession: failed to persist user_session for jti={}: {}", jti, e.getMessage());
		}
	}

	/**
	 * [v2 N-7] Refresh rotation 時讓舊 session 失效：Redis 撤銷（TTL = 舊 token 剩餘時間） + DB 標記。 舊
	 * token 未含 jti（過渡期）或無對應 session row 都不視為錯誤。
	 */
	private void rotateOldSession(String oldJti, Date oldExpiration) {
		if (oldJti == null || oldJti.isBlank()) {
			return;
		}
		if (oldExpiration != null) {
			long ttlMs = oldExpiration.getTime() - System.currentTimeMillis();
			if (ttlMs > 0) {
				try {
					stringRedisTemplate.opsForValue()
						.set(refreshRevocationKey(oldJti), "1", ttlMs, TimeUnit.MILLISECONDS);
				}
				catch (Exception e) {
					log.warn("refresh rotation: failed to revoke old jti in Redis: {}", e.getMessage());
				}
			}
		}
		try {
			userSessionRepository.revokeById(oldJti, LocalDateTime.now());
		}
		catch (Exception e) {
			log.warn("refresh rotation: failed to revoke old user_session row: {}", e.getMessage());
		}
	}

	@Override
	@Transactional(readOnly = true)
	public UserInfoDto getCurrentUser(String userId, String tenantId) {
		UserEntity user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		List<UserTenantMappingEntity> allMappings = queryMappingsWithSystemContext(
				() -> userTenantMappingRepository.findByUserIdAndEnabledTrue(userId));

		// Build available tenants list
		List<TenantOption> availableTenants;
		if (user.getIsSuperAdmin()) {
			availableTenants = buildTenantOptionsForSuperAdmin();
		}
		else {
			availableTenants = buildTenantOptions(allMappings);
		}

		// Current tenant context
		String currentTenantName = "";
		String roleCode = "";
		Long deptId = null;
		String deptName = null;
		List<String> permissions = Collections.emptyList();
		List<String> roles = Collections.emptyList();

		if (tenantId != null) {
			TenantEntity tenant = tenantRepository.findById(tenantId).orElse(null);
			currentTenantName = tenant != null ? tenant.getTenantName() : "";

			if (user.getIsSuperAdmin()) {
				roles = List.of("SUPER_ADMIN");
				permissions = resolvePermissions("ROLE_SUPER_ADMIN", tenantId);
			}
			else {
				UserTenantMappingEntity currentMapping = allMappings.stream()
					.filter(m -> tenantId.equals(m.getTenantId()))
					.findFirst()
					.orElse(null);

				if (currentMapping != null) {
					roleCode = currentMapping.getRole() != null ? currentMapping.getRole().getCode() : "";
					roles = List.of(roleCode);
					deptId = currentMapping.getDeptId();
					deptName = resolveDeptName(deptId);
					permissions = resolvePermissions(currentMapping.getRoleId(), tenantId);
				}
			}
		}

		return UserInfoDto.builder()
			.userId(user.getUserId())
			.email(user.getEmail())
			.displayName(user.getDisplayName())
			.tenantId(tenantId)
			.tenantName(currentTenantName)
			.roles(roles)
			.deptId(deptId != null ? deptId.toString() : null)
			.deptName(deptName)
			.permissions(permissions)
			.isSuperAdmin(user.getIsSuperAdmin())
			.availableTenants(availableTenants)
			.build();
	}

	@Override
	@Transactional
	public void forgotPassword(ForgotPasswordRequest request) {
		// Silent success to avoid account enumeration
		UserEntity user = userRepository.findByEmail(request.getEmail()).orElse(null);
		if (user == null) {
			return;
		}

		String tokenValue = UUID.randomUUID().toString();
		String tokenHash = sha256Hex(tokenValue);
		UserResetPasswordTokenEntity tokenEntity = UserResetPasswordTokenEntity.builder()
			.tokenId(UUID.randomUUID().toString())
			.userId(user.getUserId())
			.tokenHash(tokenHash)
			.expiredAt(LocalDateTime.now().plusMinutes(30))
			.used(false)
			.build();
		userResetPasswordTokenRepository.save(tokenEntity);

		// Send reset email (plaintext token is only delivered via email, never persisted)
		passwordResetMailService.send(user.getEmail(), user.getDisplayName(), tokenValue);

		SecurityLogger.info(SecurityEvent.PASSWORD_RESET_REQUEST, "N/A", "email=" + user.getEmail());
		log.info("Reset password token generated and email sent for user {} (tokenId={})", user.getEmail(),
				tokenEntity.getTokenId());
	}

	@Override
	@Transactional
	public void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
		// [v1 #10 / v2 N-5] 「先消耗、再行動」：以原子性 UPDATE 先 claim token（同時驗證 used=false 與 未過期），
		// 以 rowcount 判斷是否成功。避免兩個 concurrent request 都通過舊版「先 SELECT 驗證、後 UPDATE」
		// 的驗證檢查而重複重設密碼。
		//
		// [v2 N-5] 類 claim 另走 REQUIRES_NEW 交易（見 ResetPasswordTokenClaimer），使 token 被標記為
		// used
		// 與後續密碼驗證 / 儲存解耦：即便 passwordValidator / save 失敗導致本方法的主交易 rollback，
		// claimer 的交易已先行 commit，token 仍保持 used=true，避免「同一 token 連續嘗試多組密碼」的設計缺口。
		String tokenHash = sha256Hex(request.getToken());
		if (!resetPasswordTokenClaimer.claim(tokenHash)) {
			throw new BusinessException(ErrorCode.RESET_PASSWORD_INVALID_TOKEN);
		}

		UserResetPasswordTokenEntity tokenEntity = userResetPasswordTokenRepository.findByTokenHash(tokenHash)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESET_PASSWORD_INVALID_TOKEN));

		UserEntity user = userRepository.findById(tokenEntity.getUserId())
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		// Validate password complexity and history
		// Reset-password is a no-auth flow → use platform-default policy (tenantId =
		// null).
		// Once user→tenant lookup is available here we can switch to the tenant's policy.
		passwordValidator.validate(null, request.getNewPassword(),
				new com.taipei.iot.user.service.PasswordValidator.UserContext(user.getEmail(), user.getEmail()));
		passwordValidator.checkNotRecentlyUsed(null, user.getUserId(), request.getNewPassword());

		String encodedPassword = passwordEncoder.encode(request.getNewPassword());
		user.setPasswordHash(encodedPassword);
		// [Phase 3] Reset clears expiry timer + any pending force-change flag.
		user.setPasswordChangedAt(LocalDateTime.now());
		user.setForceChangePassword(false);
		userRepository.save(user);

		// Save password history
		PasswordHistoryEntity historyEntity = PasswordHistoryEntity.builder()
			.userId(user.getUserId())
			.passwordHash(encodedPassword)
			.build();
		passwordHistoryRepository.save(historyEntity);

		ChangePasswordLogEntity logEntity = ChangePasswordLogEntity.builder()
			.userId(user.getUserId())
			.changeType("RESET")
			.ipAddress(httpRequest != null ? httpRequest.getRemoteAddr() : null)
			.build();
		changePasswordLogRepository.save(logEntity);
	}

	@Override
	@Transactional
	public LoginResult forceChangePassword(String passwordChangeToken, ForceChangePasswordRequest request,
			HttpServletRequest httpRequest) {
		// 1. Parse + validate the password-change temporary token.
		// Must (a) parse, (b) be marked temporary=true, (c) carry
		// purpose=password_change.
		// These three checks together prevent a normal access / refresh /
		// tenant-selection
		// temp token from being used to bypass the change-password gate.
		Claims claims;
		try {
			claims = jwtUtil.parseToken(passwordChangeToken);
		}
		catch (Exception e) {
			throw new BusinessException(ErrorCode.FORCE_CHANGE_PASSWORD_TOKEN_INVALID);
		}
		Boolean temporary = claims.get("temporary", Boolean.class);
		String purpose = claims.get("purpose", String.class);
		if (!Boolean.TRUE.equals(temporary) || !"password_change".equals(purpose)) {
			throw new BusinessException(ErrorCode.FORCE_CHANGE_PASSWORD_TOKEN_INVALID);
		}

		String userId = claims.get("uid", String.class);
		if (userId == null) {
			throw new BusinessException(ErrorCode.FORCE_CHANGE_PASSWORD_TOKEN_INVALID);
		}

		UserEntity user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.FORCE_CHANGE_PASSWORD_TOKEN_INVALID));

		// 2. Defensive re-checks of fundamental account state. The expiry-induced login
		// branch issued this token while the user was enabled and unlocked; if either
		// flipped in the meantime we must abort before mutating the password.
		if (!Boolean.TRUE.equals(user.getEnabled())) {
			throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
		}
		if (Boolean.TRUE.equals(user.getLocked()) && !isLockExpired(user)) {
			throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
		}

		// 3. Resolve the same policy tenant the login flow used (single-mapping → that
		// tenant,
		// otherwise platform default) so complexity / history rules stay consistent
		// between the gated login attempt and the remediation submit.
		List<UserTenantMappingEntity> mappings = queryMappingsWithSystemContext(
				() -> userTenantMappingRepository.findByUserIdAndEnabledTrue(user.getUserId()));
		String policyTenantId = (mappings.size() == 1) ? mappings.get(0).getTenantId() : null;

		passwordValidator.validate(policyTenantId, request.getNewPassword(),
				new com.taipei.iot.user.service.PasswordValidator.UserContext(user.getEmail(), user.getEmail()));
		passwordValidator.checkNotRecentlyUsed(policyTenantId, user.getUserId(), request.getNewPassword());

		// 4. Persist new password + clear expiry state.
		String encodedPassword = passwordEncoder.encode(request.getNewPassword());
		user.setPasswordHash(encodedPassword);
		user.setPasswordChangedAt(LocalDateTime.now());
		user.setForceChangePassword(false);
		userRepository.save(user);

		passwordHistoryRepository
			.save(PasswordHistoryEntity.builder().userId(user.getUserId()).passwordHash(encodedPassword).build());

		changePasswordLogRepository.save(ChangePasswordLogEntity.builder()
			.userId(user.getUserId())
			.changeType("FORCE_CHANGE")
			.ipAddress(httpRequest != null ? httpRequest.getRemoteAddr() : null)
			.build());

		logLoginEvent(user.getUserId(), policyTenantId, user.getEmail(), user.getDisplayName(), null,
				AuditEventType.FORCE_CHANGE_PASSWORD, "Password changed via force-change flow", httpRequest);

		// 5. Issue normal post-auth response (same branching as login: super admin
		// / no mapping / single tenant / multi-tenant).
		return buildPostAuthLoginResult(user, mappings, httpRequest);
	}

	// ---- Private helpers ----

	/**
	 * Compute the SHA-256 hex digest of the given input. Used to hash password reset
	 * tokens before persistence so that the database never holds a usable plaintext
	 * token. SHA-256 (without salt) is appropriate here because reset tokens are 128-bit
	 * random UUIDs (high-entropy), not user-chosen secrets.
	 */
	private static String sha256Hex(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		}
		catch (NoSuchAlgorithmException e) {
			// SHA-256 is mandated by the JRE spec; this should never happen.
			throw new IllegalStateException("SHA-256 algorithm not available", e);
		}
	}

	/**
	 * Execute a repository query with system context to bypass tenantFilter. Auth flows
	 * (login, selectTenant, switchTenant, refreshToken) are cross-tenant by nature.
	 *
	 * <p>
	 * [Tenant v2 T-13] 改委派
	 * {@link TenantContext#runInSystemContext(java.util.function.Supplier)} 統一的進/出
	 * context 管理；保留本 method 作為呼叫端語意 wrapper。
	 */
	private <T> T queryMappingsWithSystemContext(java.util.function.Supplier<T> query) {
		return TenantContext.runInSystemContext(query);
	}

	/**
	 * 驗證 CAPTCHA — 支援兩種模式：
	 * <ul>
	 * <li>Turnstile：前端傳入 turnstileToken，後端呼叫 Cloudflare API 驗證</li>
	 * <li>圖片驗證碼：前端傳入 captchaKey + captcha，後端比對 Redis 中的值</li>
	 * </ul>
	 * 如果兩種都沒傳，或驗證失敗，拋出 BusinessException。
	 */
	private void verifyCaptcha(LoginRequest request, HttpServletRequest httpRequest) {
		// 優先使用 Turnstile（如果前端傳了 turnstileToken）
		if (request.getTurnstileToken() != null && !request.getTurnstileToken().isBlank()) {
			if (!turnstileService.isEnabled()) {
				throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
			}
			String captchaClientIp = httpRequest.getRemoteAddr();
			if (!turnstileService.verify(request.getTurnstileToken(), captchaClientIp)) {
				SecurityLogger.warn(SecurityEvent.CAPTCHA_FAILED, captchaClientIp, "type=turnstile");
				throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
			}
			return;
		}

		// 退回使用圖片驗證碼
		if (request.getCaptchaKey() == null || request.getCaptchaKey().isBlank() || request.getCaptcha() == null
				|| request.getCaptcha().isBlank()) {
			SecurityLogger.warn(SecurityEvent.CAPTCHA_FAILED, httpRequest.getRemoteAddr(), "type=image",
					"reason=missing_fields");
			throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
		}
		if (!captchaService.verify(request.getCaptchaKey(), request.getCaptcha())) {
			SecurityLogger.warn(SecurityEvent.CAPTCHA_FAILED, httpRequest.getRemoteAddr(), "type=image");
			throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
		}
	}

	private boolean isLockExpired(UserEntity user) {
		if (user.getLockedAt() == null) {
			return false;
		}
		return user.getLockedAt().plusMinutes(lockDurationMinutes).isBefore(LocalDateTime.now());
	}

	/**
	 * 解析指定 role 在指定租戶下的 permission code 集合。
	 * <p>
	 * 已搬到 {@link com.taipei.iot.rbac.repository.PermissionRepository} 使用 JPQL， 不再於
	 * service 層拼接 native SQL（v1 #6 改善）。租戶隔離由 query 內的
	 * {@code (rp.tenantId IS NULL OR rp.tenantId = :tenantId)} 條件保證。
	 * </p>
	 * <p>
	 * [Phase 3 / ADR-001] 已移除 ROLE_SUPER_ADMIN 的「回傳全部 permission」旁路； super_admin 改與其他
	 * role 一樣從 {@code role_permissions} 取，預期只綁定 {@code PLATFORM_*} 四個權限（由 V63 migration
	 * 種入）。
	 * </p>
	 */
	private List<String> resolvePermissions(String roleId, String tenantId) {
		return permissionRepository.findCodesByRoleAndTenant(roleId, tenantId);
	}

	/**
	 * Resolves the tenantId for the login request by looking up the user's tenant
	 * mapping. If the user belongs to exactly one tenant, that tenantId is returned so
	 * the authentication dispatcher can load the correct tenant auth config (e.g. LDAP
	 * connection details). Returns null if user not found or has multiple tenants (tenant
	 * selection happens after auth in that case).
	 */
	private String resolveLoginTenantId(String email) {
		try {
			return userRepository.findByEmail(email).map(user -> {
				List<UserTenantMappingEntity> mappings = queryMappingsWithSystemContext(
						() -> userTenantMappingRepository.findByUserIdAndEnabledTrue(user.getUserId()));
				return (mappings.size() == 1) ? mappings.get(0).getTenantId() : null;
			}).orElse(null);
		}
		catch (Exception e) {
			log.warn("Could not pre-resolve tenantId for email={}: {}: {}", email, e.getClass().getSimpleName(),
					e.getMessage());
			return null;
		}
	}

	private String resolveDeptName(Long deptId) {
		if (deptId == null) {
			return null;
		}
		// dept_info 有 tenant_id 欄位 → 加入 tenant_id 條件防止跨租戶查詢
		// [安全修復] 原本只 WHERE dept_id = :deptId，缺少租戶隔離
		String sql = "SELECT d.dept_name FROM dept_info d " + "WHERE d.dept_id = :deptId AND d.tenant_id = :tenantId";
		Query query = TenantAwareQuery.create(entityManager, sql);
		query.setParameter("deptId", deptId);

		@SuppressWarnings("unchecked")
		List<String> results = query.getResultList();
		return results.isEmpty() ? null : results.get(0);
	}

	private List<TenantOption> buildTenantOptions(List<UserTenantMappingEntity> mappings) {
		// 批次載入所有相關 TenantEntity，避免 N+1 查詢
		Set<String> tenantIds = mappings.stream().map(UserTenantMappingEntity::getTenantId).collect(Collectors.toSet());
		Map<String, TenantEntity> tenantMap = tenantRepository.findAllById(tenantIds)
			.stream()
			.collect(Collectors.toMap(TenantEntity::getTenantId, Function.identity()));

		// 批次載入所有相關 deptName，避免 N+1 查詢
		Set<Long> deptIds = mappings.stream()
			.map(UserTenantMappingEntity::getDeptId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		Map<Long, String> deptNameMap = deptIds.isEmpty() ? Collections.emptyMap() : batchResolveDeptNames(deptIds);

		return mappings.stream().map(m -> {
			TenantEntity tenant = tenantMap.get(m.getTenantId());
			String roleName = m.getRole() != null ? m.getRole().getName() : "";
			String deptName = m.getDeptId() != null ? deptNameMap.get(m.getDeptId()) : null;

			return TenantOption.builder()
				.tenantId(m.getTenantId())
				.tenantCode(tenant != null ? tenant.getTenantCode() : "")
				.tenantName(tenant != null ? tenant.getTenantName() : "")
				.roleName(roleName)
				.deptName(deptName)
				.build();
		}).collect(Collectors.toList());
	}

	private List<TenantOption> buildTenantOptionsForSuperAdmin() {
		return tenantRepository.findByEnabledTrue()
			.stream()
			.map(t -> TenantOption.builder()
				.tenantId(t.getTenantId())
				.tenantCode(t.getTenantCode())
				.tenantName(t.getTenantName())
				.roleName(SUPER_ADMIN_ROLE_NAME)
				.build())
			.collect(Collectors.toList());
	}

	@SuppressWarnings("unchecked")
	private Map<Long, String> batchResolveDeptNames(Set<Long> deptIds) {
		// dept_info 有 tenant_id 欄位 → 加入 tenant_id 條件防止跨租戶查詢
		// [安全修復] 原本只 WHERE dept_id IN :deptIds，缺少租戶隔離
		String sql = "SELECT d.dept_id, d.dept_name FROM dept_info d "
				+ "WHERE d.dept_id IN :deptIds AND d.tenant_id = :tenantId";
		List<Object[]> rows = TenantAwareQuery.create(entityManager, sql)
			.setParameter("deptIds", deptIds)
			.getResultList();
		return rows.stream().collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> (String) r[1]));
	}

	private void logLoginEvent(String userId, String tenantId, String email, String displayName, Long deptId,
			AuditEventType eventType, String detail, HttpServletRequest request) {
		UserEventLogEntity entity = new UserEventLogEntity();
		entity.setTenantId(tenantId);
		entity.setUserId(userId);
		entity.setUsername(email);
		entity.setUserLabel(displayName);
		entity.setEmail(email);
		entity.setEventType(eventType.getValue());
		entity.setEventDesc(eventType.getCategory().getValue());
		entity.setApiEndpoint(request != null ? request.getRequestURI() : null);
		entity.setErrorCode(eventType.errorCode());
		entity.setMessage(detail);
		entity.setIpAddress(request != null ? request.getRemoteAddr() : null);
		entity.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
		entity.setExecutionTime(0L);
		entity.setDeptId(deptId);
		entity.setCreateTime(LocalDateTime.now());

		// 使用 SYSTEM context 繞過 tenant filter 存檔（登入時可能尚無 tenant context）
		// [Tenant v2 T-13] 改用 TenantContext.runInSystemContext 集中還原邏輯。
		TenantContext.runInSystemContext(() -> userEventLogRepository.save(entity));
	}

	/**
	 * 登入失敗時寫入稽核紀錄。若帳號存在，帶入該帳號的 tenantId 與 deptId， 讓 DEPT_ADMIN 可在稽核畫面看到「我的部門有帳號被嘗試登入」。
	 */
	private void logLoginFailure(String email, BusinessException ex, HttpServletRequest httpRequest) {
		try {
			UserEntity user = userRepository.findByEmail(email).orElse(null);
			if (user == null) {
				// 帳號不存在 → 仍記錄，但無 userId/tenantId/deptId
				logLoginEvent(null, null, email, null, null, AuditEventType.LOGIN_FAIL, ex.getErrorCode().getCode(),
						httpRequest);
				return;
			}
			// 帳號存在 → 查第一個 mapping 取 tenantId + deptId
			List<UserTenantMappingEntity> mappings = queryMappingsWithSystemContext(
					() -> userTenantMappingRepository.findByUserIdAndEnabledTrue(user.getUserId()));
			if (mappings.isEmpty()) {
				logLoginEvent(user.getUserId(), null, email, user.getDisplayName(), null, AuditEventType.LOGIN_FAIL,
						ex.getErrorCode().getCode(), httpRequest);
			}
			else {
				// 為每個所屬場域各寫一筆（讓各場域 DEPT_ADMIN 都能看到）
				for (UserTenantMappingEntity mapping : mappings) {
					logLoginEvent(user.getUserId(), mapping.getTenantId(), email, user.getDisplayName(),
							mapping.getDeptId(), AuditEventType.LOGIN_FAIL, ex.getErrorCode().getCode(), httpRequest);
				}
			}
		}
		catch (Exception logEx) {
			log.warn("Failed to log login failure audit for email={}: {}", email, logEx.getMessage());
		}
	}

}
