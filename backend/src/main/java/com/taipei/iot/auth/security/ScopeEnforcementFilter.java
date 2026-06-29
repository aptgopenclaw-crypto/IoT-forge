package com.taipei.iot.auth.security;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.enums.SecurityEvent;
import com.taipei.iot.common.util.JwtClaimKeys;
import com.taipei.iot.common.util.SecurityLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * [Platform/Tenant Separation ADR-007] Validates that the {@code scope} claim carried in
 * the JWT matches the URL path prefix of the request.
 *
 * <p>
 * <b>Phase 3.1.4 — enforce mode (default):</b> mismatches return HTTP 403 with
 * {@link ErrorCode#SCOPE_FORBIDDEN}. The mode is controlled by the property
 * {@code app.security.scope-enforcement.mode}:
 * <ul>
 * <li>{@code enforce} (default) — reject mismatches with 403</li>
 * <li>{@code warning} — Phase 1.1.2 legacy behaviour: log only, pass through (kept as a
 * rollback escape hatch per phased plan §3.3)</li>
 * </ul>
 *
 * <p>
 * Rules:
 * <ul>
 * <li>{@code /v1/platform/**} → requires {@link TokenScope#PLATFORM}</li>
 * <li>{@code /v1/auth/**} → requires {@link TokenScope#TENANT} or
 * {@link TokenScope#IMPERSONATION}</li>
 * <li><b>Bootstrap / cross-scope auth endpoints</b> are exempt from the
 * {@code /v1/auth/**} rule because they are part of the login exchange or are otherwise
 * scope-agnostic by design (e.g. a super_admin holding a PLATFORM-scoped temporary token
 * must still be able to call {@code /v1/auth/select-tenant} to exchange it for a
 * tenant-bound access token):
 * <ul>
 * <li>{@code /v1/auth/select-tenant}</li>
 * <li>{@code /v1/auth/switch-tenant}</li>
 * <li>{@code /v1/auth/logout}</li>
 * <li>{@code /v1/auth/idle-logout}</li>
 * </ul>
 * </li>
 * <li>{@code /v1/noauth/**} and anything else → not checked</li>
 * </ul>
 *
 * <p>
 * Legacy tokens issued before Phase 1.1.1 do not carry a {@code scope} claim. Per ADR-007
 * they are treated as {@link TokenScope#TENANT} — which means in enforce mode a legacy
 * token hitting {@code /v1/platform/**} is rejected.
 *
 * <p>
 * This filter runs <i>after</i> {@link JwtAuthenticationFilter} so the
 * {@link Authentication} details map is already populated with the scope.
 */
@Slf4j
@Component
public class ScopeEnforcementFilter extends OncePerRequestFilter {

	static final String PLATFORM_PREFIX = "/v1/platform/";
	static final String AUTH_PREFIX = "/v1/auth/";
	static final String MODE_ENFORCE = "enforce";
	static final String MODE_WARNING = "warning";

	/**
	 * Auth-prefixed endpoints that any authenticated scope (PLATFORM / TENANT /
	 * IMPERSONATION) may call. These are bootstrap or cross-scope utility endpoints that
	 * pre-date the scope split or are inherently part of the scope-exchange flow itself.
	 *
	 * <p>
	 * {@link #AUTH_SCOPE_AGNOSTIC_EXACT} matches the request path verbatim.
	 * {@link #AUTH_SCOPE_AGNOSTIC_PREFIXES} matches any path whose start equals one of
	 * the entries — used for resource collections with a path variable (e.g.
	 * {@code /v1/auth/sessions/{sessionId}}).
	 */
	static final Set<String> AUTH_SCOPE_AGNOSTIC_EXACT = Set.of("/v1/auth/select-tenant", "/v1/auth/switch-tenant",
			"/v1/auth/logout", "/v1/auth/idle-logout",
			// "Who am I" — relevant to every scope including PLATFORM super_admin.
			"/v1/auth/user/info",
			// Session list (current device discovery) — used by the topbar
			// regardless of scope.
			"/v1/auth/sessions",
			// "My menus" — sidebar bootstrap. PLATFORM super_admin sees a
			// platform-only subtree; ordinary users see tenant menus. Both
			// hit the same endpoint, so it must be scope-agnostic.
			"/v1/auth/menus/my",
			// Menu CRUD / system-settings: shared admin functions. Both
			// PLATFORM super_admin (menus 103 "選單管理" / 104 "系統設定" in
			// the PLATFORM sidebar) and tenant admin reach the same
			// endpoints. @PreAuthorize on each method gates by permission
			// code (MENU_LIST, SYSTEM_SETTINGS_VIEW, etc.) so this stays
			// safe.
			"/v1/auth/menus", "/v1/auth/menus/tree", "/v1/auth/system-settings");

	static final Set<String> AUTH_SCOPE_AGNOSTIC_PREFIXES = Set.of(
			// DELETE /v1/auth/sessions/{sessionId} — revoke own session.
			"/v1/auth/sessions/",
			// PUT /v1/auth/menus/{id}, DELETE /v1/auth/menus/{id},
			// PATCH /v1/auth/menus/{id}/visible — see exact-match note above.
			"/v1/auth/menus/",
			// GET /v1/auth/system-settings/idle-timeout etc. — see above.
			"/v1/auth/system-settings/");

	/** Back-compat alias for tests that referenced the original Set name. */
	@Deprecated
	static final Set<String> AUTH_SCOPE_AGNOSTIC = AUTH_SCOPE_AGNOSTIC_EXACT;

	private static final Set<TokenScope> AUTH_ALLOWED = EnumSet.of(TokenScope.TENANT, TokenScope.IMPERSONATION);

	private final boolean enforce;

	public ScopeEnforcementFilter(@Value("${app.security.scope-enforcement.mode:enforce}") String mode) {
		this.enforce = !MODE_WARNING.equalsIgnoreCase(mode);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		boolean rejected = false;
		try {
			rejected = check(request, response);
		}
		catch (Exception e) {
			// Defensive — never break the request chain due to an internal error in this
			// filter.
			log.debug("ScopeEnforcementFilter check failed silently for path={}", request.getRequestURI(), e);
		}
		// If check() wrote to the response (e.g. wrote the 403 JSON via getWriter()) and
		// then threw an IOException (e.g. client disconnect), `rejected` is still false
		// due
		// to the catch above. Calling filterChain.doFilter() would then trigger a second
		// write via Jackson's getOutputStream() → IllegalStateException.
		// Guard: if the response is already committed, there is nothing more to do.
		if (rejected || response.isCommitted()) {
			return;
		}
		filterChain.doFilter(request, response);
	}

	/**
	 * @return {@code true} when the request was rejected (caller must NOT call
	 * {@code chain.doFilter}); {@code false} otherwise.
	 */
	private boolean check(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String path = request.getRequestURI();
		if (path == null) {
			return false;
		}
		boolean isPlatformPath = path.startsWith(PLATFORM_PREFIX);
		boolean isAuthPath = path.startsWith(AUTH_PREFIX);
		if (!isPlatformPath && !isAuthPath) {
			return false; // /v1/noauth/**, /actuator/**, /ws/**, etc.
		}
		// Bootstrap / cross-scope auth endpoints: any authenticated scope is OK.
		if (isAuthPath && isAuthScopeAgnostic(path)) {
			return false;
		}

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || !(auth.getDetails() instanceof Map<?, ?> detailsMap)) {
			// Unauthenticated / malformed details → SecurityConfig handles 401, do not
			// interfere.
			return false;
		}

		String scopeClaim = detailsMap.get(JwtClaimKeys.SCOPE) instanceof String s ? s : null;
		TokenScope scope = TokenScope.fromClaim(scopeClaim);
		TokenScope effective = scope != null ? scope : TokenScope.TENANT;

		boolean ok = isPlatformPath ? effective == TokenScope.PLATFORM : AUTH_ALLOWED.contains(effective);

		if (ok) {
			return false;
		}

		String mode = enforce ? MODE_ENFORCE : MODE_WARNING;
		SecurityLogger.warn(SecurityEvent.SCOPE_MISMATCH, request.getRemoteAddr(), "path=" + path,
				"method=" + request.getMethod(), "scope=" + (scopeClaim != null ? scopeClaim : "<none>"),
				"user=" + auth.getName(), "mode=" + mode);

		if (!enforce) {
			return false;
		}

		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter()
			.write("{\"errorCode\":\"" + ErrorCode.SCOPE_FORBIDDEN.getCode() + "\",\"message\":\""
					+ ErrorCode.SCOPE_FORBIDDEN.getMessage() + "\"}");
		return true;
	}

	private static boolean isAuthScopeAgnostic(String path) {
		if (AUTH_SCOPE_AGNOSTIC_EXACT.contains(path)) {
			return true;
		}
		for (String prefix : AUTH_SCOPE_AGNOSTIC_PREFIXES) {
			if (path.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

}
