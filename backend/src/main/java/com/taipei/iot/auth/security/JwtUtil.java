package com.taipei.iot.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.taipei.iot.common.util.JwtClaimKeys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtil {

	private static final int MIN_SECRET_BYTES = 32; // 256 bits for HMAC-SHA256

	private final SecretKey key;

	private final long accessTokenExpiration;

	private final long refreshTokenExpiration;

	private final long temporaryTokenExpiration;

	public JwtUtil(@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-expiration}") long accessTokenExpiration,
			@Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
			@Value("${jwt.temporary-token-expiration}") long temporaryTokenExpiration) {

		byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (256 bits). "
					+ "Set JWT_SECRET environment variable with a secure random value.");
		}

		this.key = Keys.hmacShaKeyFor(secretBytes);
		this.accessTokenExpiration = accessTokenExpiration;
		this.refreshTokenExpiration = refreshTokenExpiration;
		this.temporaryTokenExpiration = temporaryTokenExpiration;
	}

	public String generateAccessToken(String userId, String email, String tenantId, List<String> roles, String deptId,
			List<String> permissions, String dataScope, TokenScope scope) {
		if (scope == null) {
			throw new IllegalArgumentException("TokenScope is required for access token");
		}
		Map<String, Object> claims = new HashMap<>();
		claims.put("uid", userId);
		claims.put("tenantId", tenantId);
		claims.put("roles", roles);
		claims.put("deptId", deptId);
		claims.put("permissions", permissions);
		claims.put("dataScope", dataScope);
		claims.put(JwtClaimKeys.SCOPE, scope.name());

		return Jwts.builder()
			.claims(claims)
			.subject(userId) // subject 統一使用 userId
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
			.signWith(key)
			.compact();
	}

	/**
	 * [Platform/Tenant Separation ADR-007] Overload for issuing impersonation access
	 * tokens. Includes the {@code impersonation} claim object so downstream filters /
	 * audit can identify the originating super_admin.
	 */
	public String generateImpersonationAccessToken(String userId, String email, String tenantId, List<String> roles,
			String deptId, List<String> permissions, String dataScope, String originalUserId, String sessionId,
			long expiresAtEpochSeconds) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("uid", userId);
		claims.put("tenantId", tenantId);
		claims.put("roles", roles);
		claims.put("deptId", deptId);
		claims.put("permissions", permissions);
		claims.put("dataScope", dataScope);
		claims.put(JwtClaimKeys.SCOPE, TokenScope.IMPERSONATION.name());

		Map<String, Object> impersonation = new HashMap<>();
		impersonation.put("originalUserId", originalUserId);
		impersonation.put("sessionId", sessionId);
		impersonation.put("expiresAt", expiresAtEpochSeconds);
		claims.put(JwtClaimKeys.IMPERSONATION, impersonation);

		return Jwts.builder()
			.claims(claims)
			.subject(userId)
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
			.signWith(key)
			.compact();
	}

	public String generateTemporaryToken(String userId, String email, boolean isSuperAdmin) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("uid", userId);
		claims.put("tenantId", null);
		claims.put("roles", List.of());
		claims.put("temporary", true);
		claims.put("isSuperAdmin", isSuperAdmin);
		// [ADR-007] Temporary tenant-selection tokens are PLATFORM-scoped
		// (super_admin choosing target) or TENANT-scoped (multi-tenant user
		// choosing among their own tenants). Either way the token is not yet
		// bound to a tenant, so PLATFORM-style routing applies until exchange.
		claims.put(JwtClaimKeys.SCOPE, (isSuperAdmin ? TokenScope.PLATFORM : TokenScope.TENANT).name());

		return Jwts.builder()
			.claims(claims)
			.subject(userId) // subject 統一使用 userId
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + temporaryTokenExpiration))
			.signWith(key)
			.compact();
	}

	/**
	 * [Phase 3] Short-lived single-purpose token issued at the end of a successful
	 * authentication when the user must change their password before receiving a normal
	 * access token (expired / admin-forced).
	 *
	 * <p>
	 * The token is bound to a {@code purpose=password_change} claim so the
	 * force-change-password endpoint can reject normal access / tenant-selection tokens,
	 * and rejected paths cannot be reached with a leaked regular temp token. Same TTL as
	 * the standard temporary token (a few minutes).
	 */
	public String generatePasswordChangeToken(String userId, String email) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("uid", userId);
		claims.put("email", email);
		claims.put("temporary", true);
		claims.put("purpose", "password_change");

		return Jwts.builder()
			.claims(claims)
			.subject(userId)
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + temporaryTokenExpiration))
			.signWith(key)
			.compact();
	}

	public String generateRefreshToken(String userId, String tenantId) {
		return generateRefreshToken(userId, tenantId, UUID.randomUUID().toString());
	}

	/**
	 * [v2 N-7] 由呼叫端提供 jti，使「user_session row 寫入」與「JWT 簽發」共用同一識別子， 便於後續 logout / 撤銷對齊
	 * Redis revocation list 與 user_session 表。
	 */
	public String generateRefreshToken(String userId, String tenantId, String jti) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("uid", userId);
		claims.put("type", "refresh");
		// 記錄發行時的 tenantId，refreshToken() 換新 token 時沿用，避免跨租戶漂移
		claims.put("tenantId", tenantId);

		// jti = unique token identifier; required so logout/rotation can revoke
		// a single refresh token via the Redis revocation list without having to
		// invalidate every token issued to the user.
		return Jwts.builder()
			.claims(claims)
			.id(jti)
			.subject(userId)
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
			.signWith(key)
			.compact();
	}

	/** Refresh token TTL（毫秒），讓 service 計算 user_session.expires_at */
	public long getRefreshTokenExpirationMs() {
		return refreshTokenExpiration;
	}

	public Claims parseToken(String token) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}

	public boolean isTokenExpired(String token) {
		try {
			Claims claims = parseToken(token);
			return claims.getExpiration().before(new Date());
		}
		catch (Exception e) {
			return true;
		}
	}

}
