package com.taipei.iot.platform.announcement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.config.CorsProperties;
import com.taipei.iot.config.SecurityConfig;
import com.taipei.iot.platform.announcement.dto.PlatformAnnouncementRequest;
import com.taipei.iot.platform.announcement.dto.PlatformAnnouncementResponse;
import com.taipei.iot.platform.announcement.service.PlatformAnnouncementService;
import com.taipei.iot.tenant.TenantEnabledCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Nested;
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

@WebMvcTest({ PlatformAnnouncementController.class, PlatformAnnouncementReadController.class })
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class PlatformAnnouncementControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private PlatformAnnouncementService service;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private TenantEnabledCache tenantEnabledCache;

	private static final String TOKEN = "valid-token";

	private static final String AUTH_HEADER = "Bearer " + TOKEN;

	// ─── helpers ─────────────────────────────────────────────────────────────

	private void mockJwt(String userId, List<String> permissions) {
		mockJwt(userId, permissions, "PLATFORM");
	}

	private void mockJwt(String userId, List<String> permissions, String scope) {
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", userId);
		claimsMap.put("permissions", permissions);
		claimsMap.put("sub", userId);
		claimsMap.put("scope", scope);
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
	}

	private PlatformAnnouncementRequest validRequest() {
		return PlatformAnnouncementRequest.builder()
			.title("系統維護通知")
			.content("<p>將於凌晨 2:00 進行維護</p>")
			.status("PUBLISHED")
			.category("MAINTENANCE")
			.build();
	}

	private PageResponse<PlatformAnnouncementResponse> emptyPage() {
		return PageResponse.<PlatformAnnouncementResponse>builder()
			.content(List.of())
			.totalElements(0)
			.totalPages(0)
			.page(0)
			.size(10)
			.build();
	}

	private PlatformAnnouncementResponse sampleResponse() {
		return PlatformAnnouncementResponse.builder()
			.id(1L)
			.title("系統維護通知")
			.content("<p>維護</p>")
			.status("PUBLISHED")
			.category("MAINTENANCE")
			.createdByName("Super Admin")
			.createdAt(LocalDateTime.now())
			.build();
	}

	// ═════════════════════════════════════════════════════════════════════════
	// GET /v1/platform/announcements (管理端列表)
	// ═════════════════════════════════════════════════════════════════════════

	@Nested
	class ListAdminTests {

		@Test
		void list_withPermission_returnsOk() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			when(service.listAdmin(any(), any(), any(), eq(0), eq(10))).thenReturn(emptyPage());

			mockMvc.perform(get("/v1/platform/announcements").header("Authorization", AUTH_HEADER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errorCode").value("00000"));
		}

		@Test
		void list_withoutAuth_returns401() throws Exception {
			mockMvc.perform(get("/v1/platform/announcements")).andExpect(status().isUnauthorized());
		}

		@Test
		void list_withoutPermission_returns403() throws Exception {
			mockJwt("user-1", List.of("ANNOUNCEMENT_VIEW"));

			mockMvc.perform(get("/v1/platform/announcements").header("Authorization", AUTH_HEADER))
				.andExpect(status().isForbidden());
		}

		@Test
		void list_withFilters_passesParams() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			when(service.listAdmin(eq("DRAFT"), eq("SYSTEM"), eq("維護"), eq(1), eq(20))).thenReturn(emptyPage());

			mockMvc
				.perform(get("/v1/platform/announcements").param("statusFilter", "DRAFT")
					.param("category", "SYSTEM")
					.param("keyword", "維護")
					.param("page", "1")
					.param("size", "20")
					.header("Authorization", AUTH_HEADER))
				.andExpect(status().isOk());
		}

	}

	// ═════════════════════════════════════════════════════════════════════════
	// GET /v1/platform/announcements/{id} (單筆詳情)
	// ═════════════════════════════════════════════════════════════════════════

	@Nested
	class GetByIdTests {

		@Test
		void getById_withPermission_returnsOk() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			when(service.getById(1L)).thenReturn(sampleResponse());

			mockMvc.perform(get("/v1/platform/announcements/1").header("Authorization", AUTH_HEADER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.id").value(1))
				.andExpect(jsonPath("$.body.title").value("系統維護通知"));
		}

		@Test
		void getById_notFound_returns404() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			when(service.getById(99L)).thenThrow(new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

			mockMvc.perform(get("/v1/platform/announcements/99").header("Authorization", AUTH_HEADER))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("50001"));
		}

	}

	// ═════════════════════════════════════════════════════════════════════════
	// POST /v1/platform/announcements (新增)
	// ═════════════════════════════════════════════════════════════════════════

	@Nested
	class CreateTests {

		@Test
		void create_withPermission_returnsOk() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			when(service.create(any())).thenReturn(sampleResponse());

			mockMvc
				.perform(post("/v1/platform/announcements").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.id").value(1));
		}

		@Test
		void create_withoutPermission_returns403() throws Exception {
			mockJwt("user-1", List.of("ANNOUNCEMENT_VIEW"));

			mockMvc
				.perform(post("/v1/platform/announcements").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isForbidden());
		}

		@Test
		void create_blankTitle_returnsBadRequest() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			PlatformAnnouncementRequest req = validRequest();
			req.setTitle("");

			mockMvc
				.perform(post("/v1/platform/announcements").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isBadRequest());
		}

		@Test
		void create_blankContent_returnsBadRequest() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			PlatformAnnouncementRequest req = validRequest();
			req.setContent("");

			mockMvc
				.perform(post("/v1/platform/announcements").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isBadRequest());
		}

		@Test
		void create_invalidStatus_returnsBadRequest() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			PlatformAnnouncementRequest req = validRequest();
			req.setStatus("INVALID");

			mockMvc
				.perform(post("/v1/platform/announcements").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isBadRequest());
		}

		@Test
		void create_invalidCategory_returnsBadRequest() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			PlatformAnnouncementRequest req = validRequest();
			req.setCategory("EVENT"); // not valid for platform (only
										// SYSTEM/MAINTENANCE/GENERAL)

			mockMvc
				.perform(post("/v1/platform/announcements").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isBadRequest());
		}

		@Test
		void create_nullCategory_returnsOk() throws Exception {
			// category 可為 null（service 預設為 SYSTEM）
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			when(service.create(any())).thenReturn(sampleResponse());

			PlatformAnnouncementRequest req = validRequest();
			req.setCategory(null);

			mockMvc
				.perform(post("/v1/platform/announcements").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk());
		}

	}

	// ═════════════════════════════════════════════════════════════════════════
	// PUT /v1/platform/announcements/{id} (編輯)
	// ═════════════════════════════════════════════════════════════════════════

	@Nested
	class UpdateTests {

		@Test
		void update_withPermission_returnsOk() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			when(service.update(eq(1L), any())).thenReturn(sampleResponse());

			mockMvc
				.perform(put("/v1/platform/announcements/1").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.id").value(1));
		}

		@Test
		void update_withoutPermission_returns403() throws Exception {
			mockJwt("user-1", List.of("ANNOUNCEMENT_VIEW"));

			mockMvc
				.perform(put("/v1/platform/announcements/1").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isForbidden());
		}

		@Test
		void update_notFound_returns404() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			when(service.update(eq(99L), any())).thenThrow(new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

			mockMvc
				.perform(put("/v1/platform/announcements/99").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isNotFound());
		}

	}

	// ═════════════════════════════════════════════════════════════════════════
	// DELETE /v1/platform/announcements/{id} (刪除)
	// ═════════════════════════════════════════════════════════════════════════

	@Nested
	class DeleteTests {

		@Test
		void delete_withPermission_returnsOk() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			doNothing().when(service).delete(1L);

			mockMvc.perform(delete("/v1/platform/announcements/1").header("Authorization", AUTH_HEADER))
				.andExpect(status().isOk());
		}

		@Test
		void delete_withoutPermission_returns403() throws Exception {
			mockJwt("user-1", List.of("ANNOUNCEMENT_VIEW"));

			mockMvc.perform(delete("/v1/platform/announcements/1").header("Authorization", AUTH_HEADER))
				.andExpect(status().isForbidden());
		}

		@Test
		void delete_notFound_returns404() throws Exception {
			mockJwt("sa-1", List.of("PLATFORM_ANNOUNCEMENT_MANAGE"));
			doThrow(new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND)).when(service).delete(99L);

			mockMvc.perform(delete("/v1/platform/announcements/99").header("Authorization", AUTH_HEADER))
				.andExpect(status().isNotFound());
		}

	}

	// ═════════════════════════════════════════════════════════════════════════
	// GET /v1/auth/platform-announcements (租戶端唯讀)
	// ═════════════════════════════════════════════════════════════════════════

	@Nested
	class TenantReadTests {

		@Test
		void list_authenticated_returnsOk() throws Exception {
			mockJwt("user-1", List.of("ANNOUNCEMENT_VIEW"), "TENANT");
			when(service.listPublished(isNull(), eq(0), eq(10))).thenReturn(emptyPage());

			mockMvc.perform(get("/v1/auth/platform-announcements").header("Authorization", AUTH_HEADER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errorCode").value("00000"));
		}

		@Test
		void list_unauthenticated_returns401() throws Exception {
			mockMvc.perform(get("/v1/auth/platform-announcements")).andExpect(status().isUnauthorized());
		}

		@Test
		void list_withCategoryFilter_passesParam() throws Exception {
			mockJwt("user-1", List.of("ANNOUNCEMENT_VIEW"), "TENANT");
			when(service.listPublished(eq("MAINTENANCE"), eq(0), eq(10))).thenReturn(emptyPage());

			mockMvc
				.perform(get("/v1/auth/platform-announcements").param("category", "MAINTENANCE")
					.header("Authorization", AUTH_HEADER))
				.andExpect(status().isOk());
		}

	}

}
