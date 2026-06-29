package com.taipei.iot.auth.security;

import com.taipei.iot.common.util.JwtClaimKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * [Phase 1.1.2 / Phase 3.1.4 / ADR-007] Verifies that {@link ScopeEnforcementFilter}
 * accepts/rejects path × scope combinations correctly.
 *
 * <p>
 * Two execution modes are covered:
 * <ul>
 * <li><b>enforce</b> (default) — mismatches return HTTP 403 with {@code errorCode=10031}
 * and {@code chain.doFilter} is NOT invoked.</li>
 * <li><b>warning</b> — kept as rollback escape hatch; mismatches are logged only and the
 * chain proceeds.</li>
 * </ul>
 */
class ScopeEnforcementFilterTest {

	private ScopeEnforcementFilter filter;

	private ScopeEnforcementFilter warningFilter;

	private FilterChain chain;

	private MockHttpServletResponse response;

	@BeforeEach
	void setUp() {
		filter = new ScopeEnforcementFilter("enforce");
		warningFilter = new ScopeEnforcementFilter("warning");
		chain = mock(FilterChain.class);
		response = new MockHttpServletResponse();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	// ─── Path-prefix matching ────────────────────────────────────────────────

	@Test
	void platformPath_withPlatformScope_passesThroughAnd200() throws Exception {
		authenticateWithScope("user-super-001", "PLATFORM");
		MockHttpServletRequest req = newRequest("/v1/platform/tenants");

		filter.doFilterInternal(req, response, chain);

		verify(chain, times(1)).doFilter(req, response);
		// Warning mode: response is never touched by this filter.
		assertEquals(200, response.getStatus());
	}

	@Test
	void authPath_withTenantScope_passesThrough() throws Exception {
		authenticateWithScope("user-admin-001", "TENANT");
		MockHttpServletRequest req = newRequest("/v1/auth/users");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	@Test
	void authPath_withImpersonationScope_passesThrough() throws Exception {
		authenticateWithScope("user-super-001", "IMPERSONATION");
		MockHttpServletRequest req = newRequest("/v1/auth/users");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	// ─── Enforce-mode behaviour on mismatch (Phase 3.1.4) ────────────────────

	@Test
	void platformPath_withTenantScope_isMismatch_rejectsWith403() throws Exception {
		// This is the "tenant user tries to hit platform API" case.
		authenticateWithScope("user-admin-001", "TENANT");
		MockHttpServletRequest req = newRequest("/v1/platform/tenants");

		filter.doFilterInternal(req, response, chain);

		// Enforce mode: chain must NOT be called.
		org.mockito.Mockito.verifyNoInteractions(chain);
		assertEquals(403, response.getStatus());
		assertEquals("application/json;charset=UTF-8", response.getContentType());
		org.assertj.core.api.Assertions.assertThat(response.getContentAsString()).contains("\"errorCode\":\"10031\"");
	}

	@Test
	void authPath_withPlatformScope_isMismatch_rejectsWith403() throws Exception {
		// This is the "super_admin hits a tenant API" case Phase 3 rejects.
		authenticateWithScope("user-super-001", "PLATFORM");
		MockHttpServletRequest req = newRequest("/v1/auth/users");

		filter.doFilterInternal(req, response, chain);

		org.mockito.Mockito.verifyNoInteractions(chain);
		assertEquals(403, response.getStatus());
		org.assertj.core.api.Assertions.assertThat(response.getContentAsString()).contains("\"errorCode\":\"10031\"");
	}

	// ─── Warning-mode behaviour on mismatch (rollback escape hatch) ──────────

	@Test
	void warningMode_platformPath_withTenantScope_isMismatch_butStillPassesThrough() throws Exception {
		authenticateWithScope("user-admin-001", "TENANT");
		MockHttpServletRequest req = newRequest("/v1/platform/tenants");

		warningFilter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
		assertEquals(200, response.getStatus());
	}

	@Test
	void warningMode_authPath_withPlatformScope_isMismatch_butStillPassesThrough() throws Exception {
		authenticateWithScope("user-super-001", "PLATFORM");
		MockHttpServletRequest req = newRequest("/v1/auth/users");

		warningFilter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
		assertEquals(200, response.getStatus());
	}

	// ─── Backward-compat: legacy tokens without scope claim ──────────────────

	@Test
	void authPath_withMissingScope_treatedAsTenant_passesThrough() throws Exception {
		authenticateWithScope("user-admin-001", null);
		MockHttpServletRequest req = newRequest("/v1/auth/users");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	@Test
	void platformPath_withMissingScope_treatedAsTenant_isMismatch_rejectsWith403() throws Exception {
		// Legacy tokens (no scope claim) default to TENANT → cannot reach /v1/platform/**
		// in enforce mode.
		authenticateWithScope("user-admin-001", null);
		MockHttpServletRequest req = newRequest("/v1/platform/tenants");

		filter.doFilterInternal(req, response, chain);

		org.mockito.Mockito.verifyNoInteractions(chain);
		assertEquals(403, response.getStatus());
	}

	@Test
	void authPath_withUnknownScopeValue_treatedAsTenant_passesThrough() throws Exception {
		authenticateWithScope("user-admin-001", "BOGUS_VALUE");
		MockHttpServletRequest req = newRequest("/v1/auth/users");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	// ─── Skip cases ──────────────────────────────────────────────────────────

	@Test
	void noauthPath_isAlwaysSkipped_evenWithoutAuthentication() throws Exception {
		// No SecurityContextHolder.setContext — anonymous request
		MockHttpServletRequest req = newRequest("/v1/noauth/login");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	@Test
	void nonV1Path_isSkipped() throws Exception {
		authenticateWithScope("user-admin-001", "PLATFORM"); // even with mismatching
																// scope
		MockHttpServletRequest req = newRequest("/actuator/health");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	@Test
	void guardedPath_withoutAuthentication_isSkipped() throws Exception {
		// SecurityConfig will issue 401; this filter must not interfere.
		MockHttpServletRequest req = newRequest("/v1/auth/users");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	// ─── Bootstrap / cross-scope auth endpoints (login exchange flow) ────────
	// [Bug fix] A super_admin's temporary login token carries scope=PLATFORM
	// (see JwtUtil.generateTemporaryToken). It must still be able to call
	// /v1/auth/select-tenant to exchange the temp token for a tenant-bound
	// access token. Same applies to switch-tenant / logout / idle-logout
	// which are scope-agnostic by design.

	@Test
	void selectTenant_withPlatformScope_isAllowed() throws Exception {
		authenticateWithScope("user-super-001", "PLATFORM");
		MockHttpServletRequest req = newRequest("/v1/auth/select-tenant");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
		assertEquals(200, response.getStatus());
	}

	@Test
	void switchTenant_withPlatformScope_isAllowed() throws Exception {
		authenticateWithScope("user-super-001", "PLATFORM");
		MockHttpServletRequest req = newRequest("/v1/auth/switch-tenant");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	@Test
	void logout_withPlatformScope_isAllowed() throws Exception {
		authenticateWithScope("user-super-001", "PLATFORM");
		MockHttpServletRequest req = newRequest("/v1/auth/logout");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	@Test
	void idleLogout_withPlatformScope_isAllowed() throws Exception {
		authenticateWithScope("user-super-001", "PLATFORM");
		MockHttpServletRequest req = newRequest("/v1/auth/idle-logout");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	@Test
	void selectTenant_withTenantScope_stillAllowed() throws Exception {
		// Multi-tenant user (non-super_admin) also hits this endpoint with TENANT scope.
		authenticateWithScope("user-multi-001", "TENANT");
		MockHttpServletRequest req = newRequest("/v1/auth/select-tenant");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	// [Phase 5] /v1/auth/user/info and /v1/auth/sessions* are also
	// cross-scope: the topbar / who-am-I lookup is identical for PLATFORM
	// super_admin and ordinary tenant users.

	@Test
	void userInfo_withPlatformScope_isAllowed() throws Exception {
		authenticateWithScope("user-super-001", "PLATFORM");
		MockHttpServletRequest req = newRequest("/v1/auth/user/info");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
		assertEquals(200, response.getStatus());
	}

	@Test
	void sessionsList_withPlatformScope_isAllowed() throws Exception {
		authenticateWithScope("user-super-001", "PLATFORM");
		MockHttpServletRequest req = newRequest("/v1/auth/sessions");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	@Test
	void sessionsRevoke_withPlatformScope_isAllowed() throws Exception {
		authenticateWithScope("user-super-001", "PLATFORM");
		MockHttpServletRequest req = newRequest("/v1/auth/sessions/sess-abc-123");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	@Test
	void myMenus_withPlatformScope_isAllowed() throws Exception {
		authenticateWithScope("user-super-001", "PLATFORM");
		MockHttpServletRequest req = newRequest("/v1/auth/menus/my");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	// ─── Defensive: never throws on malformed Authentication details ─────────

	@Test
	void authenticationWithNonMapDetails_isSkippedSafely() throws Exception {
		UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken("user-x", null,
				Collections.emptyList());
		token.setDetails("not-a-map");
		SecurityContextHolder.getContext().setAuthentication(token);

		MockHttpServletRequest req = newRequest("/v1/platform/tenants");

		filter.doFilterInternal(req, response, chain);

		verify(chain).doFilter(req, response);
	}

	@Test
	void chainExceptionPropagates_butFilterStillCalledChainOnce() throws Exception {
		authenticateWithScope("user-admin-001", "TENANT");
		MockHttpServletRequest req = newRequest("/v1/auth/users");
		// Even if the downstream throws, the filter should already have called the chain.
		org.mockito.Mockito.doThrow(new java.io.IOException("downstream"))
			.when(chain)
			.doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

		try {
			filter.doFilterInternal(req, response, chain);
			org.junit.jupiter.api.Assertions.fail("expected IOException");
		}
		catch (java.io.IOException expected) {
			// ok
		}
		verify(chain).doFilter(req, response);
	}

	// ─── Helpers ─────────────────────────────────────────────────────────────

	private void authenticateWithScope(String userId, String scope) {
		Map<String, Object> details = new HashMap<>();
		details.put(JwtClaimKeys.TENANT_ID, "TENANT_A");
		details.put(JwtClaimKeys.SCOPE, scope);
		UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(userId, null, List.of());
		token.setDetails(details);
		SecurityContextHolder.getContext().setAuthentication(token);
	}

	private MockHttpServletRequest newRequest(String uri) {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setRequestURI(uri);
		req.setMethod("GET");
		req.setRemoteAddr("127.0.0.1");
		return req;
	}

}
