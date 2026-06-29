package com.taipei.iot.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.auth.dto.request.ForgotPasswordRequest;
import com.taipei.iot.auth.dto.request.LoginRequest;
import com.taipei.iot.auth.dto.request.ResetPasswordRequest;
import com.taipei.iot.auth.dto.request.SelectTenantRequest;
import com.taipei.iot.auth.dto.request.SwitchTenantRequest;
import com.taipei.iot.auth.dto.response.CaptchaResponse;
import com.taipei.iot.auth.dto.response.LoginResult;
import com.taipei.iot.auth.dto.response.TenantOption;
import com.taipei.iot.auth.dto.response.TokenResult;
import com.taipei.iot.auth.dto.response.UserInfoDto;
import com.taipei.iot.auth.security.JwtAuthenticationFilter;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.auth.service.AuthService;
import com.taipei.iot.auth.service.CaptchaService;
import com.taipei.iot.auth.service.TurnstileService;
import com.taipei.iot.auth.service.UserSessionService;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.config.CorsProperties;
import com.taipei.iot.config.SecurityConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private CaptchaService captchaService;

	@MockitoBean
	private TurnstileService turnstileService;

	@MockitoBean
	private UserSessionService userSessionService;

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
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", userId);
		claimsMap.put("tenantId", tenantId);
		claimsMap.put("roles", roles);
		claimsMap.put("sub", "test@test.com");
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(token)).thenReturn(claims);
	}

	// ---- Captcha ----

	@Test
	void captcha_shouldReturn200() throws Exception {
		when(captchaService.generate()).thenReturn(
				CaptchaResponse.builder().captchaKey("key-1").captchaImage("data:image/png;base64,abc").build());

		mockMvc.perform(post("/v1/noauth/captcha"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.captchaKey").value("key-1"))
			.andExpect(jsonPath("$.body.captchaImage").value("data:image/png;base64,abc"));
	}

	// ---- Login ----

	@Test
	void login_singleTenant_success() throws Exception {
		LoginResult result = LoginResult.builder()
			.accessToken("access-token")
			.refreshToken("refresh-token")
			.needsSelection(false)
			.build();
		when(authService.login(any(), any())).thenReturn(result);

		LoginRequest request = LoginRequest.builder()
			.email("admin@test.com")
			.password("Test1234!")
			.captcha("1234")
			.captchaKey("key-1")
			.build();

		mockMvc
			.perform(post("/v1/noauth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.accessToken").value("access-token"))
			.andExpect(jsonPath("$.body.needsSelection").value(false));
	}

	@Test
	void login_multiTenant_success() throws Exception {
		LoginResult result = LoginResult.builder()
			.accessToken("temp-token")
			.needsSelection(true)
			.tenants(List.of(TenantOption.builder().tenantId("T1").tenantName("Tenant 1").build(),
					TenantOption.builder().tenantId("T2").tenantName("Tenant 2").build()))
			.build();
		when(authService.login(any(), any())).thenReturn(result);

		LoginRequest request = LoginRequest.builder()
			.email("op@test.com")
			.password("Test1234!")
			.captcha("1234")
			.captchaKey("key-1")
			.build();

		mockMvc
			.perform(post("/v1/noauth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.needsSelection").value(true))
			.andExpect(jsonPath("$.body.tenants").isArray())
			.andExpect(jsonPath("$.body.tenants.length()").value(2));
	}

	@Test
	void login_wrongPassword_returns401() throws Exception {
		when(authService.login(any(), any())).thenThrow(new BusinessException(ErrorCode.LOGIN_FAIL));

		LoginRequest request = LoginRequest.builder()
			.email("admin@test.com")
			.password("wrong")
			.captcha("1234")
			.captchaKey("key-1")
			.build();

		mockMvc
			.perform(post("/v1/noauth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.errorCode").value("10013"));
	}

	@Test
	void login_userNotFound_returns401() throws Exception {
		when(authService.login(any(), any())).thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

		LoginRequest request = LoginRequest.builder()
			.email("nope@test.com")
			.password("Test1234!")
			.captcha("1234")
			.captchaKey("key-1")
			.build();

		mockMvc
			.perform(post("/v1/noauth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("20005"));
	}

	@Test
	void login_accountLocked_returns401() throws Exception {
		when(authService.login(any(), any())).thenThrow(new BusinessException(ErrorCode.ACCOUNT_LOCKED));

		LoginRequest request = LoginRequest.builder()
			.email("locked@test.com")
			.password("Test1234!")
			.captcha("1234")
			.captchaKey("key-1")
			.build();

		mockMvc
			.perform(post("/v1/noauth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.errorCode").value("10002"));
	}

	@Test
	void login_accountDisabled_returns401() throws Exception {
		when(authService.login(any(), any())).thenThrow(new BusinessException(ErrorCode.ACCOUNT_DISABLED));

		LoginRequest request = LoginRequest.builder()
			.email("disabled@test.com")
			.password("Test1234!")
			.captcha("1234")
			.captchaKey("key-1")
			.build();

		mockMvc
			.perform(post("/v1/noauth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.errorCode").value("10003"));
	}

	@Test
	void login_captchaInvalid_returns400() throws Exception {
		when(authService.login(any(), any())).thenThrow(new BusinessException(ErrorCode.CAPTCHA_INVALID));

		LoginRequest request = LoginRequest.builder()
			.email("admin@test.com")
			.password("Test1234!")
			.captcha("wrong")
			.captchaKey("key-1")
			.build();

		mockMvc
			.perform(post("/v1/noauth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("10007"));
	}

	// ---- Select Tenant ----

	@Test
	void selectTenant_success() throws Exception {
		String token = validToken();
		mockJwtValid(token, "user-op-001", null, List.of());

		TokenResult result = TokenResult.builder().accessToken("full-token").refreshToken("refresh-token").build();
		when(authService.selectTenant(eq("user-op-001"), any(), any())).thenReturn(result);

		SelectTenantRequest request = SelectTenantRequest.builder().tenantId("TENANT_A").build();

		mockMvc
			.perform(post("/v1/auth/select-tenant").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.accessToken").value("full-token"));
	}

	@Test
	void selectTenant_invalidTenant_returns403() throws Exception {
		String token = validToken();
		mockJwtValid(token, "user-op-001", null, List.of());

		when(authService.selectTenant(eq("user-op-001"), any(), any()))
			.thenThrow(new BusinessException(ErrorCode.TENANT_ACCESS_DENIED));

		SelectTenantRequest request = SelectTenantRequest.builder().tenantId("INVALID").build();

		mockMvc
			.perform(post("/v1/auth/select-tenant").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.errorCode").value("10021"));
	}

	@Test
	void selectTenant_noToken_returns401() throws Exception {
		mockMvc
			.perform(post("/v1/auth/select-tenant").contentType(MediaType.APPLICATION_JSON)
				.content("{\"tenantId\":\"TENANT_A\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void authApi_malformedToken_returns401() throws Exception {
		when(jwtUtil.parseToken("invalid-string"))
			.thenThrow(new io.jsonwebtoken.MalformedJwtException("Not a valid JWT"));

		mockMvc
			.perform(post("/v1/auth/select-tenant").header("Authorization", "Bearer invalid-string")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"tenantId\":\"TENANT_A\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.errorCode").value("10001"));
	}

	// ---- Switch Tenant ----

	@Test
	void switchTenant_success() throws Exception {
		String token = validToken();
		mockJwtValid(token, "user-op-001", "TENANT_A", List.of("OPERATOR"));

		TokenResult result = TokenResult.builder().accessToken("new-token").refreshToken("new-refresh").build();
		when(authService.switchTenant(eq("user-op-001"), any(), any())).thenReturn(result);

		SwitchTenantRequest request = SwitchTenantRequest.builder().tenantId("TENANT_B").build();

		mockMvc
			.perform(post("/v1/auth/switch-tenant").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.accessToken").value("new-token"));
	}

	@Test
	void switchTenant_noMapping_returns403() throws Exception {
		String token = validToken();
		mockJwtValid(token, "user-op-001", "TENANT_A", List.of("OPERATOR"));

		when(authService.switchTenant(eq("user-op-001"), any(), any()))
			.thenThrow(new BusinessException(ErrorCode.TENANT_ACCESS_DENIED));

		SwitchTenantRequest request = SwitchTenantRequest.builder().tenantId("INVALID").build();

		mockMvc
			.perform(post("/v1/auth/switch-tenant").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.errorCode").value("10021"));
	}

	// ---- Refresh Token ----

	@Test
	void refreshToken_success() throws Exception {
		TokenResult result = TokenResult.builder().accessToken("new-access").refreshToken("new-refresh").build();
		when(authService.refreshToken(eq("old-refresh-token"), any())).thenReturn(result);

		mockMvc.perform(post("/v1/noauth/token/refresh").cookie(new Cookie("refresh_token", "old-refresh-token")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.accessToken").value("new-access"));
	}

	@Test
	void refreshToken_noCookie_returns401() throws Exception {
		mockMvc.perform(post("/v1/noauth/token/refresh"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.errorCode").value("10004"));
	}

	// ---- Logout ----

	@Test
	void logout_success() throws Exception {
		String token = validToken();
		mockJwtValid(token, "user-admin-001", "TENANT_A", List.of("ADMIN"));

		mockMvc
			.perform(post("/v1/auth/logout").header("Authorization", "Bearer " + token)
				.cookie(new Cookie("refresh_token", "refresh")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));
	}

	// ---- User Info ----

	@Test
	void getUserInfo_success() throws Exception {
		String token = validToken();
		mockJwtValid(token, "user-admin-001", "TENANT_A", List.of("ADMIN"));

		UserInfoDto userInfo = UserInfoDto.builder()
			.userId("user-admin-001")
			.email("admin@test.com")
			.displayName("Tenant A Admin")
			.tenantId("TENANT_A")
			.tenantName("高雄市水情")
			.roles(List.of("ADMIN"))
			.permissions(List.of("DEVICE_VIEW"))
			.isSuperAdmin(false)
			.availableTenants(List.of())
			.build();
		when(authService.getCurrentUser("user-admin-001", "TENANT_A")).thenReturn(userInfo);

		mockMvc.perform(get("/v1/auth/user/info").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.userId").value("user-admin-001"))
			.andExpect(jsonPath("$.body.email").value("admin@test.com"));
	}

	// ---- Change Password (moved to UserSelfController, tested in
	// UserAdminControllerTest) ----

	// ---- Forgot Password ----

	@Test
	void forgotPassword_success() throws Exception {
		ForgotPasswordRequest request = ForgotPasswordRequest.builder().email("admin@test.com").build();

		mockMvc
			.perform(post("/v1/noauth/user/forgot-password").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));
	}

	// ---- Reset Password ----

	@Test
	void resetPassword_success() throws Exception {
		ResetPasswordRequest request = ResetPasswordRequest.builder()
			.token("valid-token")
			.newPassword("NewPass1!")
			.build();

		mockMvc
			.perform(put("/v1/noauth/user/reset-password").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));
	}

	@Test
	void resetPassword_invalidToken_returns400() throws Exception {
		doThrow(new BusinessException(ErrorCode.RESET_PASSWORD_INVALID_TOKEN)).when(authService)
			.resetPassword(any(), any());

		ResetPasswordRequest request = ResetPasswordRequest.builder()
			.token("expired-token")
			.newPassword("NewPass1!")
			.build();

		mockMvc
			.perform(put("/v1/noauth/user/reset-password").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("20014"));
	}

}
