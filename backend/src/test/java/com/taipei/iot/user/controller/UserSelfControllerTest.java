package com.taipei.iot.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.config.CorsProperties;
import com.taipei.iot.config.SecurityConfig;
import com.taipei.iot.tenant.TenantEnabledCache;
import com.taipei.iot.user.dto.request.ChangePasswordRequest;
import com.taipei.iot.user.service.UserSelfService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserSelfController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class UserSelfControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private UserSelfService userSelfService;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private TenantEnabledCache tenantEnabledCache;

	private static final String TOKEN = "valid.jwt.token";

	private void mockJwtValid() {
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", "user-001");
		claimsMap.put("tenantId", "TENANT_A");
		claimsMap.put("roles", List.of("USER"));
		claimsMap.put("permissions", List.of());
		claimsMap.put("sub", "test@test.com");
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
	}

	@Test
	void changePassword_blankNewPassword_shouldReturn400() throws Exception {
		mockJwtValid();

		ChangePasswordRequest req = ChangePasswordRequest.builder()
			.oldPassword("OldPass123!")
			.newPassword("   ")
			.build();

		mockMvc
			.perform(post("/v1/auth/user/change-password").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isBadRequest());

		verify(userSelfService, never()).changePassword(any(), any(), any());
	}

	@Test
	void changePassword_emptyNewPassword_shouldReturn400() throws Exception {
		mockJwtValid();

		ChangePasswordRequest req = ChangePasswordRequest.builder().oldPassword("OldPass123!").newPassword("").build();

		mockMvc
			.perform(post("/v1/auth/user/change-password").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isBadRequest());

		verify(userSelfService, never()).changePassword(any(), any(), any());
	}

	@Test
	void changePassword_nullNewPassword_shouldReturn400() throws Exception {
		mockJwtValid();

		ChangePasswordRequest req = ChangePasswordRequest.builder()
			.oldPassword("OldPass123!")
			.newPassword(null)
			.build();

		mockMvc
			.perform(post("/v1/auth/user/change-password").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isBadRequest());

		verify(userSelfService, never()).changePassword(any(), any(), any());
	}

	@Test
	void changePassword_validRequest_shouldReturn200() throws Exception {
		mockJwtValid();

		ChangePasswordRequest req = ChangePasswordRequest.builder()
			.oldPassword("OldPass123!")
			.newPassword("NewPass456!")
			.build();

		mockMvc
			.perform(post("/v1/auth/user/change-password").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));

		verify(userSelfService).changePassword(eq("user-001"), any(), any());
	}

}
