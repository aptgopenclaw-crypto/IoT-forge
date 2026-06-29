package com.taipei.iot.notification.controller;

import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.config.CorsProperties;
import com.taipei.iot.config.SecurityConfig;
import com.taipei.iot.notification.dto.NotificationResponse;
import com.taipei.iot.notification.dto.UnreadCountResponse;
import com.taipei.iot.notification.enums.NotificationType;
import com.taipei.iot.notification.service.NotificationService;
import com.taipei.iot.tenant.TenantEnabledCache;
import com.taipei.iot.common.dto.PageResponse;
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

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class NotificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificationService notificationService;

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
		claimsMap.put("sub", userId);
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(token)).thenReturn(claims);
	}

	// --- TC-00-015: GET /v1/auth/notifications ---

	@Test
	void list_shouldReturn200ForAuthenticatedUser() throws Exception {
		mockJwtValid(validToken(), "u1", "T1", List.of("VIEWER"));

		NotificationResponse resp = buildResponse(1L, NotificationType.INFO, false);
		PageResponse<NotificationResponse> page = PageResponse.<NotificationResponse>builder()
			.content(List.of(resp))
			.totalElements(1)
			.totalPages(1)
			.page(0)
			.size(20)
			.build();
		when(notificationService.list(eq("u1"), eq(0), eq(20))).thenReturn(page);

		mockMvc
			.perform(get("/v1/auth/notifications").header("Authorization", "Bearer " + validToken())
				.param("page", "0")
				.param("size", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.content[0].title").value("Test title"));
	}

	@Test
	void list_shouldReturn401WithoutToken() throws Exception {
		mockMvc.perform(get("/v1/auth/notifications")).andExpect(status().isUnauthorized());
	}

	// --- TC-00-016: GET /v1/auth/notifications/todos ---

	@Test
	void listTodos_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "u1", "T1", List.of("VIEWER"));

		PageResponse<NotificationResponse> page = PageResponse.<NotificationResponse>builder()
			.content(List.of())
			.totalElements(0)
			.totalPages(0)
			.page(0)
			.size(20)
			.build();
		when(notificationService.listTodos(eq("u1"), eq(0), eq(20))).thenReturn(page);

		mockMvc
			.perform(get("/v1/auth/notifications/todos").header("Authorization", "Bearer " + validToken())
				.param("page", "0")
				.param("size", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));
	}

	// --- TC-00-017: GET /v1/auth/notifications/unread-count ---

	@Test
	void unreadCount_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "u1", "T1", List.of("VIEWER"));

		when(notificationService.unreadCount("u1")).thenReturn(UnreadCountResponse.builder().count(5).build());

		mockMvc.perform(get("/v1/auth/notifications/unread-count").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.count").value(5));
	}

	// --- TC-00-018: PATCH /v1/auth/notifications/{id}/read ---

	@Test
	void markRead_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "u1", "T1", List.of("VIEWER"));
		doNothing().when(notificationService).markRead("u1", 1L);

		mockMvc.perform(patch("/v1/auth/notifications/1/read").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));

		verify(notificationService).markRead("u1", 1L);
	}

	// --- TC-00-019: PATCH /v1/auth/notifications/read-all ---

	@Test
	void markAllRead_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "u1", "T1", List.of("VIEWER"));
		doNothing().when(notificationService).markAllRead("u1");

		mockMvc.perform(patch("/v1/auth/notifications/read-all").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));

		verify(notificationService).markAllRead("u1");
	}

	private NotificationResponse buildResponse(Long id, NotificationType type, boolean read) {
		return NotificationResponse.builder()
			.id(id)
			.type(type)
			.title("Test title")
			.content("Test content")
			.read(read)
			.createdAt(LocalDateTime.now())
			.build();
	}

}
