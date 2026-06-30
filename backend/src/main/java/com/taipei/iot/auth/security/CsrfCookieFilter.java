package com.taipei.iot.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.enums.SecurityEvent;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.common.util.SecurityLogger;
import com.taipei.iot.common.config.CorsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * CSRF protection filter for cookie-bearing mutation endpoints.
 *
 * <p>
 * The {@code refresh_token} is delivered as an httpOnly cookie with {@code SameSite=Lax}.
 * SameSite already prevents cross-site POST from including the cookie, but this filter
 * adds defence-in-depth by validating the {@code Origin} header (or {@code Referer} as
 * fallback) to ensure the request originates from a known frontend origin.
 *
 * <p>
 * <b>Protected endpoints</b> (all POST — the cookie is read from the request):
 * <ul>
 * <li>{@code POST /v1/noauth/token/refresh} — refresh token rotation</li>
 * <li>{@code POST /v1/auth/logout} — session termination</li>
 * <li>{@code POST /v1/auth/idle-logout} — idle timeout logout</li>
 * </ul>
 *
 * <p>
 * Legitimate browser requests always carry at least one of {@code Origin} or
 * {@code Referer}. Requests with neither are rejected (a common CSRF pattern — direct
 * {@code <form>} POST from an attacker's page would not set a meaningful Origin).
 *
 * @see CorsProperties
 * @see SecurityConfig#filterChain
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsrfCookieFilter extends OncePerRequestFilter {

	private final CorsProperties corsProperties;

	private final ObjectMapper objectMapper;

	/** Exact path matches for cookie-bearing POST endpoints. */
	private static final List<String> PROTECTED_PATHS = List.of("/v1/noauth/token/refresh", "/v1/auth/logout",
			"/v1/auth/idle-logout");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		String method = request.getMethod();
		String path = request.getRequestURI();

		// Only protect cookie-bearing mutation POST endpoints.
		if (!HttpMethod.POST.matches(method) || !PROTECTED_PATHS.contains(path)) {
			chain.doFilter(request, response);
			return;
		}

		// Skip CSRF check when no refresh_token cookie is present — no cookie means
		// no CSRF risk (the attacker's page would need the cookie to cause harm).
		if (!hasRefreshTokenCookie(request)) {
			chain.doFilter(request, response);
			return;
		}

		String origin = request.getHeader("Origin");
		String referer = request.getHeader("Referer");

		if (isValidOrigin(origin) || isValidReferer(referer)) {
			chain.doFilter(request, response);
			return;
		}

		// CSRF validation failed — log and reject.
		SecurityLogger.warn(SecurityEvent.CSRF_ATTEMPT, request.getRemoteAddr(), "path=" + path, "origin=" + origin,
				"referer=" + referer);
		log.warn("CSRF validation failed for {} {} — origin={}, referer={}", method, path, origin, referer);

		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		String body = objectMapper.writeValueAsString(BaseResponse.fail(ErrorCode.CSRF_VALIDATION_FAILED));
		response.getWriter().write(body);
	}

	/**
	 * Check if the Origin header value matches one of the configured allowed origins.
	 * <p>
	 * The literal string {@code "null"} (sent by some privacy browsers for opaque
	 * origins) is explicitly rejected.
	 */
	private boolean isValidOrigin(String origin) {
		if (origin == null || origin.isBlank() || "null".equals(origin)) {
			return false;
		}
		return Arrays.stream(corsProperties.getAllowedOrigins()).anyMatch(origin::equalsIgnoreCase);
	}

	/**
	 * Extract the origin from the Referer URL and check against allowed origins.
	 * <p>
	 * Referer is always a full URL (e.g. {@code https://example.com/path}) so we parse it
	 * and compare only the origin part.
	 */
	private boolean isValidReferer(String referer) {
		if (referer == null || referer.isBlank()) {
			return false;
		}
		try {
			URI uri = URI.create(referer);
			if (uri.getScheme() == null || uri.getAuthority() == null) {
				return false;
			}
			String origin = uri.getScheme() + "://" + uri.getAuthority();
			return Arrays.stream(corsProperties.getAllowedOrigins()).anyMatch(origin::equalsIgnoreCase);
		}
		catch (Exception e) {
			log.debug("Failed to parse Referer URL: {}", referer, e);
			return false;
		}
	}

	/**
	 * Check if the request carries a {@code refresh_token} cookie.
	 * <p>
	 * If the cookie is absent there is no CSRF risk — the attacker's forged request would
	 * not include the cookie, so the protected endpoint would see no refresh_token and
	 * have nothing to act on.
	 */
	private boolean hasRefreshTokenCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return false;
		}
		for (Cookie c : cookies) {
			if ("refresh_token".equals(c.getName())) {
				return true;
			}
		}
		return false;
	}

}
