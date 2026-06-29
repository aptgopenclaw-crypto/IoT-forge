package com.taipei.iot.auth.controller;

import com.taipei.iot.auth.dto.request.ForgotPasswordRequest;
import com.taipei.iot.auth.dto.request.LoginRequest;
import com.taipei.iot.auth.dto.request.ResetPasswordRequest;
import com.taipei.iot.auth.dto.request.SelectTenantRequest;
import com.taipei.iot.auth.dto.request.SwitchTenantRequest;
import com.taipei.iot.auth.dto.response.CaptchaResponse;
import com.taipei.iot.auth.dto.response.LoginResult;
import com.taipei.iot.auth.dto.response.SessionDto;
import com.taipei.iot.auth.dto.response.TokenResult;
import com.taipei.iot.auth.dto.response.TurnstileConfigResponse;
import com.taipei.iot.auth.dto.response.UserInfoDto;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.auth.service.AuthService;
import com.taipei.iot.auth.service.CaptchaService;
import com.taipei.iot.auth.service.TurnstileService;
import com.taipei.iot.auth.service.UserSessionService;
import com.taipei.iot.common.annotation.RateLimit;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.common.util.JwtClaimKeys;
import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.enums.AuditEventType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	private final CaptchaService captchaService;

	private final TurnstileService turnstileService;

	private final UserSessionService userSessionService;

	private final JwtUtil jwtUtil;

	@Value("${captcha.turnstile.site-key:}")
	private String turnstileSiteKey;

	// [v2 N-4] 安全為預設（secure-by-default）：未設定時一律視為 true，
	// 僅本機 dev / test profile 在 application-dev.yml / application-test.yml 顯式覆寫為 false。
	@Value("${auth.cookie.secure:true}")
	private boolean cookieSecure;

	@Value("${auth.cookie.same-site:Lax}")
	private String cookieSameSite;

	@Value("${auth.cookie.domain:}")
	private String cookieDomain;

	@Value("${auth.cookie.path:/}")
	private String cookiePath;

	@Value("${auth.cookie.max-age:604800}")
	private int cookieMaxAge;

	// ---- NoAuth endpoints ----

	/** 前端呼叫此 API 判斷要顯示圖片驗證碼還是 Turnstile widget */
	@GetMapping("/v1/noauth/turnstile/config")
	public BaseResponse<TurnstileConfigResponse> getTurnstileConfig() {
		return BaseResponse.success(TurnstileConfigResponse.builder()
			.enabled(turnstileService.isEnabled())
			.siteKey(turnstileService.isEnabled() ? turnstileSiteKey : null)
			.build());
	}

	@RateLimit(key = "captcha", limit = 20, period = 60) // 同一 IP 每分鐘最多 20 次
	@PostMapping("/v1/noauth/captcha")
	public BaseResponse<CaptchaResponse> generateCaptcha() {
		CaptchaResponse response = captchaService.generate();
		return BaseResponse.success(response);
	}

	@RateLimit(key = "login", limit = 10, period = 60) // 同一 IP 每分鐘最多 10 次登入嘗試
	@PostMapping("/v1/noauth/login")
	public BaseResponse<LoginResult> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {

		LoginResult result = authService.login(request, httpRequest);

		// Set refresh token cookie if available (single-tenant direct login)
		if (result.getRefreshToken() != null) {
			setRefreshTokenCookie(httpResponse, result.getRefreshToken());
		}

		return BaseResponse.success(result);
	}

	@RateLimit(key = "refresh", limit = 30, period = 60) // 同一 IP 每分鐘最多 30 次（自動刷新需寬鬆些）
	@PostMapping("/v1/noauth/token/refresh")
	public BaseResponse<TokenResult> refreshToken(
			@CookieValue(name = "refresh_token", required = false) String refreshToken, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
		}
		TokenResult result = authService.refreshToken(refreshToken, httpRequest);
		setRefreshTokenCookie(httpResponse, result.getRefreshToken());
		return BaseResponse.success(result);
	}

	@RateLimit(key = "forgot-pwd", limit = 5, period = 300) // 同一 IP 每 5 分鐘最多 5 次（防郵件轟炸）
	@PostMapping("/v1/noauth/user/forgot-password")
	public BaseResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		authService.forgotPassword(request);
		return BaseResponse.success(null);
	}

	@RateLimit(key = "reset-pwd", limit = 5, period = 300) // 同一 IP 每 5 分鐘最多 5 次
	@PutMapping("/v1/noauth/user/reset-password")
	public BaseResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
			HttpServletRequest httpRequest) {
		authService.resetPassword(request, httpRequest);
		return BaseResponse.success(null);
	}

	/**
	 * [Phase 3] Submit a new password using the short-lived password-change token
	 * returned by {@code POST /login} when the user is flagged as expired or
	 * force-change-required. The token is carried as a Bearer header (consistent with
	 * tenant-selection); the body holds the new password.
	 */
	@RateLimit(key = "force-change-pwd", limit = 5, period = 300)
	@PostMapping("/v1/noauth/user/force-change-password")
	public BaseResponse<LoginResult> forceChangePassword(
			@Valid @RequestBody com.taipei.iot.auth.dto.request.ForceChangePasswordRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		String authHeader = httpRequest.getHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			throw new BusinessException(ErrorCode.FORCE_CHANGE_PASSWORD_TOKEN_INVALID);
		}
		String token = authHeader.substring("Bearer ".length()).trim();
		LoginResult result = authService.forceChangePassword(token, request, httpRequest);
		// If a real refresh token was issued (single-tenant success path), set it as
		// cookie.
		if (result.getRefreshToken() != null && !result.getRefreshToken().isBlank()) {
			setRefreshTokenCookie(httpResponse, result.getRefreshToken());
		}
		return BaseResponse.success(result);
	}

	// ---- Auth endpoints ----

	@PostMapping("/v1/auth/select-tenant")
	public BaseResponse<TokenResult> selectTenant(@Valid @RequestBody SelectTenantRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		String userId = getCurrentUserId();
		TokenResult result = authService.selectTenant(userId, request, httpRequest);
		setRefreshTokenCookie(httpResponse, result.getRefreshToken());
		return BaseResponse.success(result);
	}

	@PostMapping("/v1/auth/switch-tenant")
	public BaseResponse<TokenResult> switchTenant(@Valid @RequestBody SwitchTenantRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		String userId = getCurrentUserId();
		TokenResult result = authService.switchTenant(userId, request, httpRequest);
		setRefreshTokenCookie(httpResponse, result.getRefreshToken());
		return BaseResponse.success(result);
	}

	@AuditEvent(AuditEventType.LOGOUT)
	@PostMapping("/v1/auth/logout")
	public BaseResponse<Void> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken,
			HttpServletResponse httpResponse) {
		doLogout(refreshToken, httpResponse);
		return BaseResponse.success(null);
	}

	@AuditEvent(AuditEventType.IDLE_TIMEOUT_LOGOUT)
	@PostMapping("/v1/auth/idle-logout")
	public BaseResponse<Void> idleTimeoutLogout(
			@CookieValue(name = "refresh_token", required = false) String refreshToken,
			HttpServletResponse httpResponse) {
		doLogout(refreshToken, httpResponse);
		return BaseResponse.success(null);
	}

	private void doLogout(String refreshToken, HttpServletResponse httpResponse) {
		authService.logout(refreshToken);
		clearRefreshTokenCookie(httpResponse);
	}

	@GetMapping("/v1/auth/user/info")
	public BaseResponse<UserInfoDto> getUserInfo() {
		String userId = getCurrentUserId();
		String tenantId = getCurrentTenantId();
		UserInfoDto userInfo = authService.getCurrentUser(userId, tenantId);
		return BaseResponse.success(userInfo);
	}

	// ---- [v2 N-7] Session 管理（登入裝置清單／強制登出） ----

	/**
	 * 列出當前使用者所有未撤銷且未過期的 session。 透過 refresh_token cookie 的 jti 標記哪一筆是「當前裝置」。
	 */
	@GetMapping("/v1/auth/sessions")
	public BaseResponse<List<SessionDto>> listMySessions(
			@CookieValue(name = "refresh_token", required = false) String refreshToken) {
		String userId = getCurrentUserId();
		String currentJti = extractJtiSafely(refreshToken);
		return BaseResponse.success(userSessionService.listMine(userId, currentJti));
	}

	/**
	 * 強制登出指定 session（撤銷 refresh token）。僅允許 session 擁有者本人操作。
	 */
	@AuditEvent(AuditEventType.LOGOUT)
	@DeleteMapping("/v1/auth/sessions/{sessionId}")
	public BaseResponse<Void> revokeSession(@PathVariable("sessionId") String sessionId) {
		String userId = getCurrentUserId();
		userSessionService.revoke(userId, sessionId);
		return BaseResponse.success(null);
	}

	/**
	 * 從 refresh_token 安全地解出 jti；解析失敗一律回 null（前端只是用來標記當前裝置）。
	 */
	private String extractJtiSafely(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return null;
		}
		try {
			return jwtUtil.parseToken(refreshToken).getId();
		}
		catch (Exception e) {
			return null;
		}
	}

	// change-password endpoint moved to UserSelfController (USER)

	// ---- Private helpers ----

	private String getCurrentUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getPrincipal() == null) {
			throw new BusinessException(ErrorCode.ACCESS_TOKEN_INVALID);
		}
		return auth.getPrincipal().toString();
	}

	private String getCurrentTenantId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
			return (String) details.get(JwtClaimKeys.TENANT_ID);
		}
		return null;
	}

	private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
		Cookie cookie = new Cookie("refresh_token", refreshToken);
		cookie.setHttpOnly(true);
		cookie.setSecure(cookieSecure);
		cookie.setPath(cookiePath);
		cookie.setMaxAge(cookieMaxAge);
		cookie.setAttribute("SameSite", cookieSameSite);
		if (cookieDomain != null && !cookieDomain.isEmpty()) {
			cookie.setDomain(cookieDomain);
		}
		response.addCookie(cookie);
	}

	private void clearRefreshTokenCookie(HttpServletResponse response) {
		Cookie cookie = new Cookie("refresh_token", "");
		cookie.setHttpOnly(true);
		cookie.setSecure(cookieSecure);
		cookie.setPath(cookiePath);
		cookie.setMaxAge(0);
		cookie.setAttribute("SameSite", cookieSameSite);
		if (cookieDomain != null && !cookieDomain.isEmpty()) {
			cookie.setDomain(cookieDomain);
		}
		response.addCookie(cookie);
	}

}
