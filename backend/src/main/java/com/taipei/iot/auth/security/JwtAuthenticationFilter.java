package com.taipei.iot.auth.security;

import com.taipei.iot.common.enums.SecurityEvent;
import com.taipei.iot.common.util.JwtClaimKeys;
import com.taipei.iot.common.util.SecurityLogger;
import com.taipei.iot.tenant.TenantContext;
import com.taipei.iot.tenant.TenantEnabledCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtUtil jwtUtil;

	private final TenantEnabledCache tenantEnabledCache;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String token = extractBearerToken(request);
		if (token != null) {
			try {
				Claims claims = jwtUtil.parseToken(token);
				String userId = claims.get(JwtClaimKeys.USER_ID, String.class);
				String tenantId = claims.get(JwtClaimKeys.TENANT_ID, String.class);

				@SuppressWarnings("unchecked")
				List<String> roles = claims.get("roles", List.class);
				if (roles == null) {
					roles = Collections.emptyList();
				}

				List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>(roles.stream()
					.map(role -> new SimpleGrantedAuthority("ROLE_" + role))
					.collect(Collectors.toList()));

				// Also extract permission codes so @PreAuthorize("hasAuthority('XXX')")
				// works
				@SuppressWarnings("unchecked")
				List<String> permissions = claims.get("permissions", List.class);
				if (permissions != null) {
					permissions.stream().map(SimpleGrantedAuthority::new).forEach(authorities::add);
				}

				UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null,
						authorities);

				// Store extra claims (deptId, dataScope, tenantId) in Authentication
				// details
				Map<String, Object> details = new HashMap<>();
				details.put(JwtClaimKeys.TENANT_ID, tenantId);
				details.put(JwtClaimKeys.DEPT_ID, claims.get(JwtClaimKeys.DEPT_ID));
				details.put(JwtClaimKeys.DATA_SCOPE, claims.get(JwtClaimKeys.DATA_SCOPE, String.class));
				// [ADR-007] Phase 1.1.1 — propagate token scope so ScopeEnforcementFilter
				// (Phase 1.1.2) can validate path prefix vs scope without re-parsing the
				// JWT.
				details.put(JwtClaimKeys.SCOPE, claims.get(JwtClaimKeys.SCOPE, String.class));
				auth.setDetails(details);

				SecurityContextHolder.getContext().setAuthentication(auth);

				// Set TenantContext
				if (tenantId != null) {
					// 即時拒絕已停用場域的請求（不等 access token 自然過期）
					if (tenantEnabledCache.isTenantDisabled(tenantId)) {
						response.setStatus(HttpServletResponse.SC_FORBIDDEN);
						response.setContentType("application/json;charset=UTF-8");
						response.getWriter().write("{\"errorCode\":\"10024\",\"message\":\"場域已停用\"}");
						return;
					}
					TenantContext.setCurrentTenantId(tenantId);
					// [Phase B] SUPER_ADMIN 在 tenant context 下操作 → 標記為代操
					if (roles.contains("SUPER_ADMIN")) {
						TenantContext.setImpersonator(userId);
					}
				}
				else if (roles.contains("SUPER_ADMIN")) {
					TenantContext.setSystemContext();
				}

			}
			catch (ExpiredJwtException e) {
				SecurityLogger.warn(SecurityEvent.JWT_INVALID, request.getRemoteAddr(), "reason=expired",
						"uri=" + request.getRequestURI());
				log.debug("JWT expired for request {}: {}", request.getRequestURI(), e.getMessage());
			}
			catch (JwtException e) {
				SecurityLogger.warn(SecurityEvent.JWT_INVALID, request.getRemoteAddr(), "reason=malformed",
						"uri=" + request.getRequestURI());
				log.debug("Invalid JWT for request {}: {}", request.getRequestURI(), e.getMessage());
			}
			catch (Exception e) {
				SecurityLogger.warn(SecurityEvent.JWT_INVALID, request.getRemoteAddr(), "reason=unexpected",
						"uri=" + request.getRequestURI(), "exceptionType=" + e.getClass().getSimpleName());
				log.warn("Unexpected exception in JWT filter for {}: {}", request.getRequestURI(), e.getMessage());
			}
		}

		try {
			filterChain.doFilter(request, response);
		}
		finally {
			// 無論請求路徑為何（包含非 /v1/** 路徑）均清除 TenantContext，
			// 防止 Tomcat 執行緒池重用導致 ThreadLocal 殘留負載到後續請求
			TenantContext.clear();
		}
	}

	private String extractBearerToken(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			return header.substring(7);
		}
		return null;
	}

}
