package com.taipei.iot.vms.controller;

import com.taipei.iot.common.config.CorsProperties;
import com.taipei.iot.vms.enums.VmsType;
import com.taipei.iot.vms.service.VmsEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link VmsWebhookController} 端點測試。
 */
@WebMvcTest(VmsWebhookController.class)
@Import({ com.taipei.iot.common.exception.GlobalExceptionHandler.class,
		com.taipei.iot.common.config.CorsProperties.class })
@TestPropertySource(properties = { "vms.media-server.api-url=http://localhost:8080",
		"vms.media-server.public-url=http://localhost:8080" })
class VmsWebhookControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VmsEventService vmsEventService;

	@MockitoBean
	private com.taipei.iot.auth.security.JwtUtil jwtUtil;

	@MockitoBean
	private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private com.taipei.iot.tenant.cache.TenantEnabledCache tenantEnabledCache;

	@Test
	@DisplayName("POST /v1/vms/webhook/NX_WITNESS → 200 + 轉發 service")
	void nxWebhook_returns200() throws Exception {
		String payload = """
				{"eventType":"MOTION_DETECT","cameraId":"cam-001",
				"occurredAt":"2026-07-01T08:00:00Z","payload":{"zone":"A1"}}
				""";

		mockMvc
			.perform(post("/v1/vms/webhook/NX_WITNESS").with(csrf())
				.with(user("system"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));

		verify(vmsEventService).processWebhook(VmsType.NX_WITNESS, payload);
	}

	@Test
	@DisplayName("POST /v1/vms/webhook/unknown → 200（不拋錯）")
	void unknownVmsType_returns200() throws Exception {
		mockMvc
			.perform(post("/v1/vms/webhook/UNKNOWN_VMS").with(csrf())
				.with(user("system"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));
	}

}
