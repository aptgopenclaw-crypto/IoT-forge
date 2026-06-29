package com.taipei.iot.assettransfer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.assettransfer.dto.AssetTransferActionRequest;
import com.taipei.iot.assettransfer.dto.AssetTransferCreateRequest;
import com.taipei.iot.assettransfer.dto.AssetTransferRejectRequest;
import com.taipei.iot.assettransfer.dto.AssetTransferResponse;
import com.taipei.iot.assettransfer.enums.AssetTransferStatus;
import com.taipei.iot.assettransfer.service.AssetTransferService;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.config.CorsProperties;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.config.SecurityConfig;
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

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssetTransferController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class AssetTransferControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AssetTransferService service;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private TenantEnabledCache tenantEnabledCache;

	private static final String TOKEN = "valid-token";

	private static final String AUTH_HEADER = "Bearer " + TOKEN;

	// ─── helpers ─────────────────────────────────────────────────────────────

	private void mockJwt(String userId, String tenantId, List<String> roles, List<String> permissions) {
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", userId);
		claimsMap.put("tenantId", tenantId);
		claimsMap.put("roles", roles);
		claimsMap.put("permissions", permissions);
		claimsMap.put("sub", userId);
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3_600_000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
	}

	private AssetTransferResponse stubResponse() {
		return new AssetTransferResponse(1L, "AT-TEST001", "user-001", "測試使用者", 100L, "IT部門", "AC-001", "Laptop",
				"INTERNAL", null, null, BigDecimal.valueOf(50000), AssetTransferStatus.DRAFT, null, null, null,
				"user-001", null, null, null, null, false);
	}

	// ═══════════════════════════════════════════════════════════════════════
	// POST /v1/auth/asset-transfer/create
	// ═══════════════════════════════════════════════════════════════════════

	@Nested
	class CreateEndpointTests {

		@Test
		void create_withPermission_returns200() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_CREATE"));
			when(service.create(any(AssetTransferCreateRequest.class), anyString())).thenReturn(stubResponse());

			AssetTransferCreateRequest req = new AssetTransferCreateRequest("AC-001", "Laptop", "INTERNAL", 100L, null,
					"reason", BigDecimal.valueOf(50000));

			mockMvc
				.perform(post("/v1/auth/asset-transfer/create").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errorCode").value("00000"))
				.andExpect(jsonPath("$.body.status").value("DRAFT"));
		}

		@Test
		void create_withoutToken_returns401() throws Exception {
			AssetTransferCreateRequest req = new AssetTransferCreateRequest("AC-001", "Laptop", "INTERNAL", 100L, null,
					"reason", BigDecimal.valueOf(50000));

			mockMvc
				.perform(post("/v1/auth/asset-transfer/create").contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isUnauthorized());
		}

		@Test
		void create_withoutPermission_returns403() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ANNOUNCEMENT_VIEW"));

			AssetTransferCreateRequest req = new AssetTransferCreateRequest("AC-001", "Laptop", "INTERNAL", 100L, null,
					"reason", BigDecimal.valueOf(50000));

			mockMvc
				.perform(post("/v1/auth/asset-transfer/create").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isForbidden());
		}

		@Test
		void create_blankAssetCode_returns400() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_CREATE"));

			// assetCode blank — violates @NotBlank
			Map<String, Object> body = new HashMap<>();
			body.put("assetCode", "");
			body.put("assetName", "Laptop");
			body.put("transferType", "INTERNAL");
			body.put("departmentId", 100);

			mockMvc
				.perform(post("/v1/auth/asset-transfer/create").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isBadRequest());
		}

		@Test
		void create_invalidTransferType_returns400() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_CREATE"));

			Map<String, Object> body = new HashMap<>();
			body.put("assetCode", "AC-001");
			body.put("assetName", "Laptop");
			body.put("transferType", "INVALID_TYPE"); // violates @Pattern
			body.put("departmentId", 100);

			mockMvc
				.perform(post("/v1/auth/asset-transfer/create").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isBadRequest());
		}

		@Test
		void create_missingDepartmentId_returns400() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_CREATE"));

			Map<String, Object> body = new HashMap<>();
			body.put("assetCode", "AC-001");
			body.put("assetName", "Laptop");
			body.put("transferType", "INTERNAL");
			// no departmentId — violates @NotNull

			mockMvc
				.perform(post("/v1/auth/asset-transfer/create").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isBadRequest());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════
	// POST /v1/auth/asset-transfer/create-and-submit
	// ═══════════════════════════════════════════════════════════════════════

	@Nested
	class CreateAndSubmitEndpointTests {

		@Test
		void createAndSubmit_withPermission_returns200() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_CREATE"));
			when(service.createAndSubmit(any(AssetTransferCreateRequest.class), anyString()))
				.thenReturn(new AssetTransferResponse(1L, "AT-TEST001", "user-001", "測試使用者", 100L, "IT部門", "AC-001",
						"Laptop", "INTERNAL", null, null, BigDecimal.valueOf(50000), AssetTransferStatus.PROCESSING,
						"approver-001", "審核者", null, "user-001", null, null, null, null, false));

			AssetTransferCreateRequest req = new AssetTransferCreateRequest("AC-001", "Laptop", "INTERNAL", 100L, null,
					"reason", BigDecimal.valueOf(50000));

			mockMvc
				.perform(post("/v1/auth/asset-transfer/create-and-submit").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.status").value("PROCESSING"));
		}

		@Test
		void createAndSubmit_withoutToken_returns401() throws Exception {
			AssetTransferCreateRequest req = new AssetTransferCreateRequest("AC-001", "Laptop", "INTERNAL", 100L, null,
					"reason", BigDecimal.valueOf(50000));

			mockMvc
				.perform(post("/v1/auth/asset-transfer/create-and-submit").contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isUnauthorized());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════
	// POST /v1/auth/asset-transfer/submit/{id}
	// ═══════════════════════════════════════════════════════════════════════

	@Nested
	class SubmitEndpointTests {

		@Test
		void submit_withPermission_returns200() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_CREATE"));
			when(service.submit(anyLong(), anyString())).thenReturn(stubResponse());

			mockMvc.perform(post("/v1/auth/asset-transfer/submit/1").header("Authorization", AUTH_HEADER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errorCode").value("00000"));
		}

		@Test
		void submit_withoutToken_returns401() throws Exception {
			mockMvc.perform(post("/v1/auth/asset-transfer/submit/1")).andExpect(status().isUnauthorized());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════
	// POST /v1/auth/asset-transfer/approve/{id}
	// ═══════════════════════════════════════════════════════════════════════

	@Nested
	class ApproveEndpointTests {

		private AssetTransferResponse approvedResp() {
			return new AssetTransferResponse(1L, "AT-TEST001", "user-001", "測試使用者", 100L, "IT部門", "AC-001", "Laptop",
					"INTERNAL", null, null, BigDecimal.valueOf(50000), AssetTransferStatus.COMPLETED, null, null, null,
					"user-001", null, null, "approver-001", null, false);
		}

		@Test
		void approve_withPermission_returns200() throws Exception {
			mockJwt("approver-001", "T1", List.of("ROLE_ADMIN"), List.of("ASSET_TRANSFER_APPROVE"));
			when(service.approve(anyLong(), anyString(), anyString())).thenReturn(approvedResp());

			AssetTransferActionRequest req = new AssetTransferActionRequest("ok");

			mockMvc
				.perform(post("/v1/auth/asset-transfer/approve/1").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.status").value("COMPLETED"));
		}

		@Test
		void approve_withoutPermission_returns403() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_CREATE"));

			AssetTransferActionRequest req = new AssetTransferActionRequest("ok");

			mockMvc
				.perform(post("/v1/auth/asset-transfer/approve/1").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isForbidden());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════
	// POST /v1/auth/asset-transfer/reject/{id}
	// ═══════════════════════════════════════════════════════════════════════

	@Nested
	class RejectEndpointTests {

		@Test
		void reject_withPermission_returns200() throws Exception {
			mockJwt("approver-001", "T1", List.of("ROLE_ADMIN"), List.of("ASSET_TRANSFER_APPROVE"));
			when(service.reject(anyLong(), anyString(), anyString(), anyString())).thenReturn(stubResponse());

			AssetTransferRejectRequest req = new AssetTransferRejectRequest("缺件", "step-applicant");

			mockMvc
				.perform(post("/v1/auth/asset-transfer/reject/1").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk());
		}

		@Test
		void reject_missingTargetStepId_returns400() throws Exception {
			mockJwt("approver-001", "T1", List.of("ROLE_ADMIN"), List.of("ASSET_TRANSFER_APPROVE"));

			// targetStepId is @NotBlank
			Map<String, String> body = new HashMap<>();
			body.put("comment", "缺件");
			// no targetStepId

			mockMvc
				.perform(post("/v1/auth/asset-transfer/reject/1").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isBadRequest());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════
	// POST /v1/auth/asset-transfer/resubmit/{id}
	// ═══════════════════════════════════════════════════════════════════════

	@Nested
	class ResubmitEndpointTests {

		@Test
		void resubmit_withPermission_returns200() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_CREATE"));
			when(service.resubmit(anyLong(), anyString(), anyString())).thenReturn(stubResponse());

			AssetTransferActionRequest req = new AssetTransferActionRequest("補件完成");

			mockMvc
				.perform(post("/v1/auth/asset-transfer/resubmit/1").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════
	// POST /v1/auth/asset-transfer/cancel/{id}
	// ═══════════════════════════════════════════════════════════════════════

	@Nested
	class CancelEndpointTests {

		@Test
		void cancel_withPermission_returns200() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_CREATE"));
			when(service.cancel(anyLong(), anyString(), anyString())).thenReturn(stubResponse());

			AssetTransferActionRequest req = new AssetTransferActionRequest("業務調整取消");

			mockMvc
				.perform(post("/v1/auth/asset-transfer/cancel/1").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk());
		}

		@Test
		void cancel_withoutPermission_returns403() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_VIEW"));

			AssetTransferActionRequest req = new AssetTransferActionRequest("取消");

			mockMvc
				.perform(post("/v1/auth/asset-transfer/cancel/1").header("Authorization", AUTH_HEADER)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isForbidden());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════
	// GET /v1/auth/asset-transfer/my
	// ═══════════════════════════════════════════════════════════════════════

	@Nested
	class MyApplicationsEndpointTests {

		@Test
		void myApplications_withPermission_returns200() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_VIEW"));
			when(service.getMyApplications(anyString())).thenReturn(List.of(stubResponse()));

			mockMvc.perform(get("/v1/auth/asset-transfer/my").header("Authorization", AUTH_HEADER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body").isArray())
				.andExpect(jsonPath("$.body[0].applicationNo").value("AT-TEST001"));
		}

		@Test
		void myApplications_withoutToken_returns401() throws Exception {
			mockMvc.perform(get("/v1/auth/asset-transfer/my")).andExpect(status().isUnauthorized());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════
	// GET /v1/auth/asset-transfer/pending
	// ═══════════════════════════════════════════════════════════════════════

	@Nested
	class PendingEndpointTests {

		@Test
		void pending_withApprovePermission_returns200() throws Exception {
			mockJwt("approver-001", "T1", List.of("ROLE_ADMIN"), List.of("ASSET_TRANSFER_APPROVE"));
			when(service.getPendingTasks(anyString())).thenReturn(List.of(stubResponse()));

			mockMvc.perform(get("/v1/auth/asset-transfer/pending").header("Authorization", AUTH_HEADER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body").isArray());
		}

		@Test
		void pending_withoutPermission_returns403() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_VIEW"));

			mockMvc.perform(get("/v1/auth/asset-transfer/pending").header("Authorization", AUTH_HEADER))
				.andExpect(status().isForbidden());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════
	// GET /v1/auth/asset-transfer/{id}
	// ═══════════════════════════════════════════════════════════════════════

	@Nested
	class GetByIdEndpointTests {

		@Test
		void getById_withPermission_returns200() throws Exception {
			mockJwt("user-001", "T1", List.of("ROLE_USER"), List.of("ASSET_TRANSFER_VIEW"));
			when(service.getById(eq(1L), anyString())).thenReturn(stubResponse());

			mockMvc.perform(get("/v1/auth/asset-transfer/1").header("Authorization", AUTH_HEADER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.id").value(1));
		}

	}

}
