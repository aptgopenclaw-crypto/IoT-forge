package com.taipei.iot.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.auth.security.JwtAuthenticationFilter;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.config.CorsProperties;
import com.taipei.iot.config.SecurityConfig;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.user.dto.response.UserListItemDto;
import com.taipei.iot.user.service.UserAdminService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.taipei.iot.common.interceptor.RateLimitInterceptor;
import com.taipei.iot.tenant.TenantInterceptor;
import com.taipei.iot.tenant.TenantEnabledCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserAdminController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class UserAdminControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private UserAdminService userAdminService;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private TenantEnabledCache tenantEnabledCache;

	private String validToken() {
		return "valid.jwt.token";
	}

	private void mockJwtValid(String token, String userId, String tenantId, List<String> roles) {
		mockJwtValid(token, userId, tenantId, roles, List.of());
	}

	private void mockJwtValid(String token, String userId, String tenantId, List<String> roles,
			List<String> permissions) {
		mockJwtValid(token, userId, tenantId, roles, permissions, "TENANT");
	}

	private void mockJwtValid(String token, String userId, String tenantId, List<String> roles,
			List<String> permissions, String scope) {
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", userId);
		claimsMap.put("tenantId", tenantId);
		claimsMap.put("roles", roles);
		claimsMap.put("permissions", permissions);
		claimsMap.put("scope", scope);
		claimsMap.put("sub", "test@test.com");
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(token)).thenReturn(claims);
	}

	@Test
	void listUsers_adminRole_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"),
				List.of("USER_LIST", "USER_CREATE", "USER_UPDATE", "USER_DISABLE", "USER_DELETE"));

		PageResponse<UserListItemDto> pageResponse = PageResponse.<UserListItemDto>builder()
			.content(List.of())
			.totalElements(0)
			.totalPages(0)
			.page(0)
			.size(20)
			.build();
		when(userAdminService.listUsers(0, 20, null, null)).thenReturn(pageResponse);

		mockMvc.perform(get("/v1/auth/users").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));
	}

	@Test
	void listUsers_viewerRole_shouldReturn403() throws Exception {
		mockJwtValid(validToken(), "user-viewer-001", "TENANT_A", List.of("VIEWER"));

		mockMvc.perform(get("/v1/auth/users").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isForbidden());
	}

	@Test
	void listUsers_noAuth_shouldReturn401() throws Exception {
		mockMvc.perform(get("/v1/auth/users")).andExpect(status().isUnauthorized());
	}

	// ─── [Phase 3 / 3.1.6] super_admin × ScopeEnforcementFilter × impersonation ──

	/**
	 * super_admin 持 PLATFORM scope token 直接呼叫 tenant API `/v1/auth/users`：
	 * ScopeEnforcementFilter（3.1.4 enforce mode）必須回 403/10031， 且 UserAdminService 不能被呼叫。
	 */
	@Test
	void listUsers_superAdminWithPlatformScope_shouldReturn403_byScopeFilter() throws Exception {
		mockJwtValid(validToken(), "user-super-001", null, List.of("SUPER_ADMIN"),
				List.of("PLATFORM_TENANT_MANAGE", "PLATFORM_IMPERSONATE"), "PLATFORM");

		mockMvc.perform(get("/v1/auth/users").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.errorCode").value("10031"));

		verify(userAdminService, never()).listUsers(anyInt(), anyInt(), any(), any());
	}

	/**
	 * super_admin 持 IMPERSONATION scope token（3.1.5 由 ImpersonationService.create 簽發）呼叫
	 * tenant API `/v1/auth/users`： 1. ScopeEnforcementFilter 放行（AUTH_ALLOWED 含
	 * IMPERSONATION） 2. JwtAuthenticationFilter 因 roles=[SUPER_ADMIN] + tenantId 非 null，
	 * 在請求生命週期內呼叫 TenantContext.setImpersonator(operatorUserId) 3. 下游 service 取得到
	 * `TenantContext.getImpersonator()` = 該 super_admin uid， 供 UserAuditService /
	 * BaseLoggerAspect 寫入 audit 的 impersonated_by 欄位
	 */
	@Test
	void listUsers_impersonationToken_shouldReturn200_andSetImpersonatorMarker() throws Exception {
		String superAdminUid = "user-super-001";
		mockJwtValid(validToken(), superAdminUid, "TENANT_A", List.of("SUPER_ADMIN"), List.of("USER_LIST"),
				"IMPERSONATION");

		java.util.concurrent.atomic.AtomicReference<String> capturedImpersonator = new java.util.concurrent.atomic.AtomicReference<>();
		java.util.concurrent.atomic.AtomicReference<String> capturedTenantId = new java.util.concurrent.atomic.AtomicReference<>();
		when(userAdminService.listUsers(0, 20, null, null)).thenAnswer(inv -> {
			capturedImpersonator.set(com.taipei.iot.tenant.TenantContext.getImpersonator());
			capturedTenantId.set(com.taipei.iot.tenant.TenantContext.getCurrentTenantId());
			return PageResponse.<UserListItemDto>builder()
				.content(List.of())
				.totalElements(0)
				.totalPages(0)
				.page(0)
				.size(20)
				.build();
		});

		mockMvc.perform(get("/v1/auth/users").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));

		// impersonator marker 必須在 service 執行當下已被設定為發起代操的 super_admin uid，
		// 這即是 UserInfoLogEntity.impersonated_by / UserEventLogEntity.impersonated_by 的來源。
		org.junit.jupiter.api.Assertions.assertEquals(superAdminUid, capturedImpersonator.get(),
				"TenantContext.impersonator should equal the super_admin uid during the request");
		org.junit.jupiter.api.Assertions.assertEquals("TENANT_A", capturedTenantId.get(),
				"TenantContext.currentTenantId should equal the target tenantId");
	}

}
