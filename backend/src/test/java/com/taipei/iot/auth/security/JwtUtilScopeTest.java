package com.taipei.iot.auth.security;

import com.taipei.iot.common.util.JwtClaimKeys;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * [Phase 1.1.1 / ADR-007] Verifies that {@link JwtUtil} writes the {@code scope} claim on
 * every issued token, and that the {@link TokenScope} enum round-trips correctly through
 * serialization.
 */
class JwtUtilScopeTest {

	// 256-bit / 32-byte HMAC secret (printable ASCII, 32 chars exactly)
	private static final String SECRET = "test-secret-32-bytes-abcdefghijkl";

	private static final long ACCESS_TTL = 60_000L;

	private static final long REFRESH_TTL = 600_000L;

	private static final long TEMP_TTL = 30_000L;

	private JwtUtil jwtUtil;

	@BeforeEach
	void setUp() {
		jwtUtil = new JwtUtil(SECRET, ACCESS_TTL, REFRESH_TTL, TEMP_TTL);
	}

	// ─── generateAccessToken: scope claim is required and written through ────

	@Test
	void generateAccessToken_withPlatformScope_writesPlatformClaim() {
		String token = jwtUtil.generateAccessToken("user-super-001", "super@test.com", null, List.of("SUPER_ADMIN"),
				null, List.of("PLATFORM_TENANT_MANAGE"), "ALL", TokenScope.PLATFORM);

		Claims claims = jwtUtil.parseToken(token);
		assertEquals("PLATFORM", claims.get(JwtClaimKeys.SCOPE, String.class));
		assertEquals(TokenScope.PLATFORM, TokenScope.fromClaim(claims.get(JwtClaimKeys.SCOPE, String.class)));
		assertNull(claims.get(JwtClaimKeys.TENANT_ID, String.class));
	}

	@Test
	void generateAccessToken_withTenantScope_writesTenantClaim() {
		String token = jwtUtil.generateAccessToken("user-admin-001", "admin@test.com", "TENANT_A", List.of("ADMIN"),
				"1", List.of("USER_LIST"), "ALL", TokenScope.TENANT);

		Claims claims = jwtUtil.parseToken(token);
		assertEquals("TENANT", claims.get(JwtClaimKeys.SCOPE, String.class));
		assertEquals("TENANT_A", claims.get(JwtClaimKeys.TENANT_ID, String.class));
	}

	@Test
	void generateAccessToken_nullScope_throwsIllegalArgument() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> jwtUtil.generateAccessToken("u", "u@e", "T", List.of("ADMIN"), null, List.of(), "ALL", null));
		assertTrue(ex.getMessage().toLowerCase().contains("scope"));
	}

	// ─── generateTemporaryToken: scope derived from isSuperAdmin flag ────────

	@Test
	void generateTemporaryToken_superAdmin_isPlatformScope() {
		String token = jwtUtil.generateTemporaryToken("user-super-001", "super@test.com", true);
		Claims claims = jwtUtil.parseToken(token);

		assertEquals("PLATFORM", claims.get(JwtClaimKeys.SCOPE, String.class));
		assertEquals(Boolean.TRUE, claims.get("temporary", Boolean.class));
		assertEquals(Boolean.TRUE, claims.get("isSuperAdmin", Boolean.class));
	}

	@Test
	void generateTemporaryToken_multiTenantUser_isTenantScope() {
		String token = jwtUtil.generateTemporaryToken("user-op-001", "op@test.com", false);
		Claims claims = jwtUtil.parseToken(token);

		assertEquals("TENANT", claims.get(JwtClaimKeys.SCOPE, String.class));
		assertEquals(Boolean.TRUE, claims.get("temporary", Boolean.class));
		assertEquals(Boolean.FALSE, claims.get("isSuperAdmin", Boolean.class));
	}

	// ─── generateImpersonationAccessToken: writes IMPERSONATION + payload ────

	@Test
	void generateImpersonationAccessToken_writesImpersonationClaimAndContext() {
		long expiresAt = System.currentTimeMillis() / 1000 + 600;

		String token = jwtUtil.generateImpersonationAccessToken("user-super-001", "super@test.com", "TENANT_A",
				List.of("ADMIN"), null, List.of("USER_LIST"), "ALL", "user-super-001", "imp-abc123", expiresAt);

		Claims claims = jwtUtil.parseToken(token);
		assertEquals("IMPERSONATION", claims.get(JwtClaimKeys.SCOPE, String.class));
		assertEquals("TENANT_A", claims.get(JwtClaimKeys.TENANT_ID, String.class));

		@SuppressWarnings("unchecked")
		Map<String, Object> impersonation = (Map<String, Object>) claims.get(JwtClaimKeys.IMPERSONATION);
		assertNotNull(impersonation, "impersonation claim must be present");
		assertEquals("user-super-001", impersonation.get("originalUserId"));
		assertEquals("imp-abc123", impersonation.get("sessionId"));
		// JJWT decodes numeric claims as Integer when small enough, Long otherwise.
		Object expiresAtClaim = impersonation.get("expiresAt");
		assertTrue(expiresAtClaim instanceof Number, "expiresAt must serialize as a number");
		assertEquals(expiresAt, ((Number) expiresAtClaim).longValue());
	}

	// ─── TokenScope.fromClaim parse rules ────────────────────────────────────

	@Test
	void tokenScope_fromClaim_unknownValueReturnsNull() {
		assertNull(TokenScope.fromClaim("BOGUS"));
		assertNull(TokenScope.fromClaim(""));
		assertNull(TokenScope.fromClaim(null));
	}

	@Test
	void tokenScope_fromClaim_validValuesRoundTrip() {
		assertEquals(TokenScope.PLATFORM, TokenScope.fromClaim("PLATFORM"));
		assertEquals(TokenScope.TENANT, TokenScope.fromClaim("TENANT"));
		assertEquals(TokenScope.IMPERSONATION, TokenScope.fromClaim("IMPERSONATION"));
	}

}
