package com.taipei.iot.setting.controller;

import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.config.CorsProperties;
import com.taipei.iot.config.SecurityConfig;
import com.taipei.iot.setting.dto.SystemSettingDto;
import com.taipei.iot.setting.service.SystemSettingService;
import com.taipei.iot.tenant.TenantEnabledCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemSettingController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class SystemSettingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SystemSettingService settingService;

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
		claimsMap.put("deptId", "1");
		claimsMap.put("dataScope", "ALL");
		claimsMap.put("sub", "test@test.com");
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(token)).thenReturn(claims);
	}

	// ---- GET idle-timeout ----

	@Test
	void getIdleTimeout_authenticated_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-001", "TENANT_A", List.of("VIEWER"));
		when(settingService.getIdleTimeoutMinutes()).thenReturn(15);

		mockMvc.perform(get("/v1/auth/system-settings/idle-timeout").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body").value(15));
	}

	@Test
	void getIdleTimeout_noToken_shouldReturn401() throws Exception {
		mockMvc.perform(get("/v1/auth/system-settings/idle-timeout")).andExpect(status().isUnauthorized());
	}

	// ---- PUT idle-timeout ----

	@Test
	void updateIdleTimeout_adminRole_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"), List.of("SYSTEM_SETTINGS_MANAGE"));
		when(settingService.updateIdleTimeoutMinutes(30)).thenReturn(30);

		mockMvc
			.perform(put("/v1/auth/system-settings/idle-timeout").header("Authorization", "Bearer " + validToken())
				.param("minutes", "30"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body").value(30));
	}

	@Test
	void updateIdleTimeout_viewerRole_shouldReturn403() throws Exception {
		mockJwtValid(validToken(), "user-viewer-001", "TENANT_A", List.of("VIEWER"));

		mockMvc
			.perform(put("/v1/auth/system-settings/idle-timeout").header("Authorization", "Bearer " + validToken())
				.param("minutes", "30"))
			.andExpect(status().isForbidden());
	}

	@Test
	void updateIdleTimeout_noToken_shouldReturn401() throws Exception {
		mockMvc.perform(put("/v1/auth/system-settings/idle-timeout").param("minutes", "30"))
			.andExpect(status().isUnauthorized());
	}

	// ---- GET / (list all) ----

	@Test
	void listSettings_adminRole_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"), List.of("SYSTEM_SETTINGS_VIEW"));
		var dto = SystemSettingDto.builder()
			.settingKey("idle_timeout_minutes")
			.settingValue("15")
			.description("Idle timeout")
			.build();
		when(settingService.findAllSettings()).thenReturn(List.of(dto));

		mockMvc.perform(get("/v1/auth/system-settings").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body[0].settingKey").value("idle_timeout_minutes"))
			.andExpect(jsonPath("$.body[0].settingValue").value("15"));
	}

	@Test
	void listSettings_viewerRole_shouldReturn403() throws Exception {
		mockJwtValid(validToken(), "user-viewer-001", "TENANT_A", List.of("VIEWER"));

		mockMvc.perform(get("/v1/auth/system-settings").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isForbidden());
	}

	// ---- PUT /{key} (generic update) ----

	@Test
	void updateSetting_adminRole_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"), List.of("SYSTEM_SETTINGS_MANAGE"));
		var dto = SystemSettingDto.builder()
			.settingKey("idle_timeout_minutes")
			.settingValue("30")
			.description("Idle timeout")
			.build();
		when(settingService.updateSetting("idle_timeout_minutes", "30")).thenReturn(dto);

		mockMvc
			.perform(put("/v1/auth/system-settings/idle_timeout_minutes")
				.header("Authorization", "Bearer " + validToken())
				.param("value", "30"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.settingValue").value("30"));
	}

	@Test
	void updateSetting_viewerRole_shouldReturn403() throws Exception {
		mockJwtValid(validToken(), "user-viewer-001", "TENANT_A", List.of("VIEWER"));

		mockMvc
			.perform(put("/v1/auth/system-settings/idle_timeout_minutes")
				.header("Authorization", "Bearer " + validToken())
				.param("value", "30"))
			.andExpect(status().isForbidden());
	}

	// ---- [N-3] Validation error path tests ----

	@Test
	void updateSetting_valueOver500Chars_shouldReturn400() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"), List.of("SYSTEM_SETTINGS_MANAGE"));
		String longValue = "x".repeat(501);

		mockMvc
			.perform(put("/v1/auth/system-settings/idle_timeout_minutes")
				.header("Authorization", "Bearer " + validToken())
				.param("value", longValue))
			.andExpect(status().isBadRequest());
	}

	@Test
	void updateSetting_blankValue_shouldReturn400() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"), List.of("SYSTEM_SETTINGS_MANAGE"));

		mockMvc
			.perform(put("/v1/auth/system-settings/idle_timeout_minutes")
				.header("Authorization", "Bearer " + validToken())
				.param("value", "   "))
			.andExpect(status().isBadRequest());
	}

	@Test
	void updateSetting_invalidValue_serviceThrows_shouldReturnError() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"), List.of("SYSTEM_SETTINGS_MANAGE"));
		when(settingService.updateSetting("idle_timeout_minutes", "abc")).thenThrow(
				new BusinessException(ErrorCode.SETTING_INVALID_VALUE, "idle_timeout_minutes must be a valid integer"));

		mockMvc
			.perform(put("/v1/auth/system-settings/idle_timeout_minutes")
				.header("Authorization", "Bearer " + validToken())
				.param("value", "abc"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void updateSetting_unknownKey_serviceThrows_shouldReturnError() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"), List.of("SYSTEM_SETTINGS_MANAGE"));
		when(settingService.updateSetting("unknown_key", "value"))
			.thenThrow(new BusinessException(ErrorCode.SETTING_INVALID_VALUE, "Unknown setting key: unknown_key"));

		mockMvc
			.perform(put("/v1/auth/system-settings/unknown_key").header("Authorization", "Bearer " + validToken())
				.param("value", "value"))
			.andExpect(status().isBadRequest());
	}

}
