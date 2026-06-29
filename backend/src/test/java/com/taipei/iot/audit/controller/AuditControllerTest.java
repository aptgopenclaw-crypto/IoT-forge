package com.taipei.iot.audit.controller;

import com.taipei.iot.audit.dto.AuditQueryRequest;
import com.taipei.iot.audit.dto.UserEventLogDto;
import com.taipei.iot.audit.service.AuditService;
import com.taipei.iot.config.CorsProperties;
import com.taipei.iot.config.SecurityConfig;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.auth.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.taipei.iot.common.interceptor.RateLimitInterceptor;
import com.taipei.iot.tenant.TenantInterceptor;
import com.taipei.iot.tenant.TenantEnabledCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class AuditControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuditService auditService;

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
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", userId);
		claimsMap.put("tenantId", tenantId);
		claimsMap.put("roles", roles);
		claimsMap.put("permissions", permissions);
		claimsMap.put("sub", "test@test.com");
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(token)).thenReturn(claims);
	}

	@Test
	void getUserUsageHistory_shouldReturn200ForAdmin() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"),
				List.of("AUDIT_LIST", "LOGIN_LOG_LIST"));

		UserEventLogDto dto = buildDto("LOGIN", "USER_AUTH");
		Page<UserEventLogDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);
		when(auditService.getUserUsageHistory(any(AuditQueryRequest.class), anyBoolean(), any())).thenReturn(page);

		mockMvc
			.perform(get("/v1/auth/audit/user/usage/history").header("Authorization", "Bearer " + validToken())
				.param("pageSize", "20")
				.param("page", "0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.content[0].eventType").value("LOGIN"));
	}

	@Test
	void getUserUsageHistory_shouldReturn200ForSuperAdmin() throws Exception {
		mockJwtValid(validToken(), "user-super-001", null, List.of("SUPER_ADMIN"),
				List.of("AUDIT_LIST", "LOGIN_LOG_LIST"));

		Page<UserEventLogDto> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
		when(auditService.getUserUsageHistory(any(AuditQueryRequest.class), anyBoolean(), any())).thenReturn(page);

		mockMvc
			.perform(get("/v1/auth/audit/user/usage/history").header("Authorization", "Bearer " + validToken())
				.param("pageSize", "20")
				.param("page", "0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));
	}

	@Test
	void getUserUsageHistory_shouldReturn403ForViewer() throws Exception {
		mockJwtValid(validToken(), "user-viewer-001", "TENANT_A", List.of("VIEWER"));

		mockMvc
			.perform(get("/v1/auth/audit/user/usage/history").header("Authorization", "Bearer " + validToken())
				.param("pageSize", "20")
				.param("page", "0"))
			.andExpect(status().isForbidden());
	}

	@Test
	void getMyLoginLog_shouldReturn200ForAnyAuthenticatedUser() throws Exception {
		mockJwtValid(validToken(), "u-admin-001", "TENANT_A", List.of("VIEWER"));

		UserEventLogDto dto = buildDto("LOGIN", "USER_AUTH");
		Page<UserEventLogDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);
		when(auditService.getMyEventLogs(any(), any(), any(), any())).thenReturn(page);

		mockMvc
			.perform(get("/v1/auth/audit/user/login/my").header("Authorization", "Bearer " + validToken())
				.param("pageSize", "20")
				.param("page", "0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.content[0].eventType").value("LOGIN"));
	}

	@Test
	void getMyLoginLog_shouldReturn401WithoutAuth() throws Exception {
		mockMvc.perform(get("/v1/auth/audit/user/login/my").param("pageSize", "20").param("page", "0"))
			.andExpect(status().isUnauthorized());
	}

	private UserEventLogDto buildDto(String eventType, String eventDesc) {
		return UserEventLogDto.builder()
			.userEventLogPk(1L)
			.userId("u-admin-001")
			.username("admin@test.com")
			.userLabel("Admin User")
			.email("admin@test.com")
			.eventType(eventType)
			.eventDesc(eventDesc)
			.apiEndpoint("/v1/noauth/token")
			.errorCode("00000")
			.ipAddress("192.168.1.100")
			.executionTime(120L)
			.createTime(LocalDateTime.of(2026, 4, 1, 9, 0))
			.build();
	}

	// ---- Export ----

	@Test
	void exportCsv_shouldReturn200ForAdmin() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"),
				List.of("AUDIT_LIST", "LOGIN_LOG_LIST"));

		UserEventLogDto dto = buildDto("LOGIN", "USER_AUTH");
		when(auditService.queryForExport(any(AuditQueryRequest.class), anyBoolean())).thenReturn(List.of(dto));

		mockMvc
			.perform(get("/v1/auth/audit/user/usage/history/export").header("Authorization", "Bearer " + validToken())
				.param("format", "csv"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
			.andExpect(header().string("Content-Disposition", "attachment; filename=audit-logs.csv"));
	}

	@Test
	void exportXlsx_shouldReturn200ForAdmin() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"),
				List.of("AUDIT_LIST", "LOGIN_LOG_LIST"));

		when(auditService.queryForExport(any(AuditQueryRequest.class), anyBoolean())).thenReturn(List.of());

		mockMvc
			.perform(get("/v1/auth/audit/user/usage/history/export").header("Authorization", "Bearer " + validToken())
				.param("format", "xlsx"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type",
					org.hamcrest.Matchers
						.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
			.andExpect(header().string("Content-Disposition", "attachment; filename=audit-logs.xlsx"));
	}

	@Test
	void export_shouldReturn403ForViewer() throws Exception {
		mockJwtValid(validToken(), "user-viewer-001", "TENANT_A", List.of("VIEWER"));

		mockMvc
			.perform(get("/v1/auth/audit/user/usage/history/export").header("Authorization", "Bearer " + validToken())
				.param("format", "csv"))
			.andExpect(status().isForbidden());
	}

	@Test
	void export_shouldReturn401WithoutAuth() throws Exception {
		mockMvc.perform(get("/v1/auth/audit/user/usage/history/export").param("format", "csv"))
			.andExpect(status().isUnauthorized());
	}

}
