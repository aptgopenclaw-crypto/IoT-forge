package com.taipei.iot.announcement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.announcement.dto.AnnouncementRequest;
import com.taipei.iot.announcement.dto.AnnouncementResponse;
import com.taipei.iot.announcement.dto.UnreadCountResponse;
import com.taipei.iot.announcement.service.AnnouncementReadService;
import com.taipei.iot.announcement.service.AnnouncementService;
import com.taipei.iot.announcement.service.AnnouncementAttachmentService;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.config.CorsProperties;
import com.taipei.iot.config.SecurityConfig;
import com.taipei.iot.tenant.TenantEnabledCache;
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

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnnouncementController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class AnnouncementControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AnnouncementService announcementService;

	@MockitoBean
	private AnnouncementReadService announcementReadService;

	@MockitoBean
	private AnnouncementAttachmentService announcementAttachmentService;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private TenantEnabledCache tenantEnabledCache;

	private static final String TOKEN = "valid-token";

	private static final String AUTH_HEADER = "Bearer " + TOKEN;

	// ─── helpers ─────────────────────────────────────────────────────────────

	private void mockJwtValid(String userId, String tenantId, List<String> roles, List<String> permissions) {
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", userId);
		claimsMap.put("tenantId", tenantId);
		claimsMap.put("roles", roles);
		claimsMap.put("permissions", permissions);
		claimsMap.put("sub", userId);
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
	}

	private AnnouncementRequest validRequest() {
		return AnnouncementRequest.builder()
			.title("Test Announcement")
			.content("Test content")
			.status("PUBLISHED")
			.scope("ALL")
			.pinned(false)
			.build();
	}

	private PageResponse<AnnouncementResponse> emptyPage() {
		return PageResponse.<AnnouncementResponse>builder()
			.content(List.of())
			.totalElements(0)
			.totalPages(0)
			.page(0)
			.size(10)
			.build();
	}

	// ─── GET /v1/auth/announcements (前台) ──────────────────────────────────

	@Test
	void list_authenticated_returnsOk() throws Exception {
		mockJwtValid("user-1", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));
		when(announcementService.listVisible(isNull(), eq(0), eq(10), any())).thenReturn(emptyPage());

		mockMvc.perform(get("/v1/auth/announcements").header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));
	}

	@Test
	void list_unauthenticated_returns401() throws Exception {
		mockMvc.perform(get("/v1/auth/announcements")).andExpect(status().isUnauthorized());
	}

	// ─── GET /v1/auth/announcements/admin ──────────────────────────────────

	@Test
	void listAdmin_withPermission_returnsOk() throws Exception {
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		when(announcementService.listAdmin(eq("ALL"), isNull(), isNull(), eq(0), eq(10), any()))
			.thenReturn(emptyPage());

		mockMvc.perform(get("/v1/auth/announcements/admin").header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));
	}

	@Test
	void listAdmin_withoutPermission_returns403() throws Exception {
		mockJwtValid("user-1", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));

		mockMvc.perform(get("/v1/auth/announcements/admin").header("Authorization", AUTH_HEADER))
			.andExpect(status().isForbidden());
	}

	@Test
	void listAdmin_withKeywordAndFilter_passesParams() throws Exception {
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		when(announcementService.listAdmin(eq("DRAFT"), isNull(), eq("test"), eq(1), eq(20), any()))
			.thenReturn(emptyPage());

		mockMvc
			.perform(get("/v1/auth/announcements/admin").param("statusFilter", "DRAFT")
				.param("keyword", "test")
				.param("page", "1")
				.param("size", "20")
				.header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk());
	}

	// ─── GET /v1/auth/announcements/{id} ────────────────────────────────────

	@Test
	void getById_authenticated_returnsOk() throws Exception {
		mockJwtValid("user-1", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));
		AnnouncementResponse resp = AnnouncementResponse.builder()
			.id(1L)
			.title("Hello")
			.content("World")
			.status("PUBLISHED")
			.scope("ALL")
			.build();
		when(announcementService.getById(eq(1L), eq(false), any())).thenReturn(resp);

		mockMvc.perform(get("/v1/auth/announcements/1").header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.title").value("Hello"));
	}

	@Test
	void getById_withManagePermission_passesTrue() throws Exception {
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		AnnouncementResponse resp = AnnouncementResponse.builder()
			.id(1L)
			.title("Draft")
			.content("C")
			.status("DRAFT")
			.scope("ALL")
			.build();
		when(announcementService.getById(eq(1L), eq(true), any())).thenReturn(resp);

		mockMvc.perform(get("/v1/auth/announcements/1").header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.status").value("DRAFT"));
	}

	// ─── POST /v1/auth/announcements ────────────────────────────────────────

	@Test
	void create_withPermission_returnsOk() throws Exception {
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		AnnouncementResponse resp = AnnouncementResponse.builder().id(1L).title("Test Announcement").build();
		when(announcementService.create(any())).thenReturn(resp);

		mockMvc
			.perform(post("/v1/auth/announcements").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(validRequest())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.id").value(1));
	}

	@Test
	void create_withoutPermission_returns403() throws Exception {
		mockJwtValid("user-1", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));

		mockMvc
			.perform(post("/v1/auth/announcements").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(validRequest())))
			.andExpect(status().isForbidden());
	}

	// ─── POST validation ────────────────────────────────────────────────────

	@Test
	void create_invalidStatus_returnsBadRequest() throws Exception {
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		AnnouncementRequest req = validRequest();
		req.setStatus("INVALID");

		mockMvc
			.perform(post("/v1/auth/announcements").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void create_invalidScope_returnsBadRequest() throws Exception {
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		AnnouncementRequest req = validRequest();
		req.setScope("INVALID_SCOPE");

		mockMvc
			.perform(post("/v1/auth/announcements").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void create_blankTitle_returnsBadRequest() throws Exception {
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		AnnouncementRequest req = validRequest();
		req.setTitle("");

		mockMvc
			.perform(post("/v1/auth/announcements").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void create_blankContent_returnsBadRequest() throws Exception {
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		AnnouncementRequest req = validRequest();
		req.setContent("");

		mockMvc
			.perform(post("/v1/auth/announcements").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void create_expireBeforePublish_returnsBadRequest() throws Exception {
		// 跨欄位驗證：expireAt 早於 publishAt 應被 @AssertTrue 攔下
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		AnnouncementRequest req = validRequest();
		LocalDateTime now = LocalDateTime.now();
		req.setPublishAt(now.plusDays(2));
		req.setExpireAt(now.plusDays(1));

		mockMvc
			.perform(post("/v1/auth/announcements").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isBadRequest());

		verify(announcementService, never()).create(any());
	}

	@Test
	void create_expireEqualsPublish_returnsBadRequest() throws Exception {
		// expireAt == publishAt（同一時刻）也應被擋下（必須嚴格晚於）
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		AnnouncementRequest req = validRequest();
		LocalDateTime at = LocalDateTime.now().plusDays(1);
		req.setPublishAt(at);
		req.setExpireAt(at);

		mockMvc
			.perform(post("/v1/auth/announcements").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void create_neverExpire_returnsOk() throws Exception {
		// expireAt 為 null（永不過期）應通過跨欄位驗證
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		when(announcementService.create(any())).thenReturn(AnnouncementResponse.builder().id(1L).build());

		AnnouncementRequest req = validRequest();
		req.setPublishAt(LocalDateTime.now().plusDays(1));
		req.setExpireAt(null);

		mockMvc
			.perform(post("/v1/auth/announcements").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isOk());
	}

	// ─── PUT /v1/auth/announcements/{id} ────────────────────────────────────

	@Test
	void update_withPermission_returnsOk() throws Exception {
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		AnnouncementResponse resp = AnnouncementResponse.builder().id(1L).title("Updated").build();
		when(announcementService.update(eq(1L), any())).thenReturn(resp);

		mockMvc
			.perform(put("/v1/auth/announcements/1").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(validRequest())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.title").value("Updated"));
	}

	@Test
	void update_withoutPermission_returns403() throws Exception {
		mockJwtValid("user-1", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));

		mockMvc
			.perform(put("/v1/auth/announcements/1").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(validRequest())))
			.andExpect(status().isForbidden());
	}

	@Test
	void update_versionConflict_returns409() throws Exception {
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		when(announcementService.update(eq(1L), any())).thenThrow(new com.taipei.iot.common.exception.BusinessException(
				com.taipei.iot.common.enums.ErrorCode.ANNOUNCEMENT_VERSION_CONFLICT));

		AnnouncementRequest req = validRequest();
		req.setVersion(0L);

		mockMvc
			.perform(put("/v1/auth/announcements/1").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("50002"));
	}

	// ─── DELETE /v1/auth/announcements/{id} ──────────────────────────────────

	@Test
	void delete_withPermission_returnsOk() throws Exception {
		mockJwtValid("admin-1", "T1", List.of("ROLE_ADMIN"), List.of("ANNOUNCEMENT_MANAGE"));
		doNothing().when(announcementService).delete(1L);

		mockMvc.perform(delete("/v1/auth/announcements/1").header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk());
	}

	@Test
	void delete_withoutPermission_returns403() throws Exception {
		mockJwtValid("user-1", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));

		mockMvc.perform(delete("/v1/auth/announcements/1").header("Authorization", AUTH_HEADER))
			.andExpect(status().isForbidden());
	}

	// ─── GET /v1/auth/announcements/unread-count ────────────────────────────

	@Test
	void getUnreadCount_authenticated_returnsOk() throws Exception {
		mockJwtValid("user-1", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));
		when(announcementReadService.getUnreadCount()).thenReturn(UnreadCountResponse.builder().count(3).build());

		mockMvc.perform(get("/v1/auth/announcements/unread-count").header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.count").value(3));
	}

	// ─── POST /v1/auth/announcements/{id}/read ──────────────────────────────

	@Test
	void markAsRead_authenticated_returnsOk() throws Exception {
		mockJwtValid("user-1", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));
		doNothing().when(announcementReadService).markAsRead(1L);

		mockMvc.perform(post("/v1/auth/announcements/1/read").header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk());
	}

	// ─── POST /v1/auth/announcements/read-all ───────────────────────────────

	@Test
	void markAllAsRead_authenticated_returnsOk() throws Exception {
		mockJwtValid("user-1", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));
		doNothing().when(announcementReadService).markAllAsRead();

		mockMvc.perform(post("/v1/auth/announcements/read-all").header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk());
	}

	// ─── page/size validation ───────────────────────────────────────────────

	@Test
	void list_sizeExceedsMax_returnsBadRequest() throws Exception {
		mockJwtValid("user-1", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));

		mockMvc.perform(get("/v1/auth/announcements").param("size", "200").header("Authorization", AUTH_HEADER))
			.andExpect(status().isBadRequest());
	}

	@Test
	void list_negativeSize_returnsBadRequest() throws Exception {
		mockJwtValid("user-1", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));

		mockMvc.perform(get("/v1/auth/announcements").param("size", "0").header("Authorization", AUTH_HEADER))
			.andExpect(status().isBadRequest());
	}

	@Test
	void list_negativePage_returnsBadRequest() throws Exception {
		mockJwtValid("user-1", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));

		mockMvc.perform(get("/v1/auth/announcements").param("page", "-1").header("Authorization", AUTH_HEADER))
			.andExpect(status().isBadRequest());
	}

}
