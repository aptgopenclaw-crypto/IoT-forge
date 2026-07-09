package com.taipei.iot.vms.controller;

import com.taipei.iot.vms.service.StreamTokenService;
import com.taipei.iot.vms.service.StreamTokenService.TokenValidation;
import com.taipei.iot.vms.service.ZlMediaKitClient;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link VmsStreamHookController} ZLMediaKit hook 端點測試。
 */
@WebMvcTest(VmsStreamHookController.class)
@Import(com.taipei.iot.common.exception.GlobalExceptionHandler.class)
@TestPropertySource(properties = { "vms.media-server.api-url=http://localhost:8080",
		"vms.media-server.public-url=http://localhost:8080" })
class VmsStreamHookControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StreamTokenService streamTokenService;

	@MockitoBean
	private ZlMediaKitClient zlMediaKitClient;

	@Test
	@DisplayName("on_play 有效 token → 回傳 code=0")
	void onPlay_validToken() throws Exception {
		when(streamTokenService.validateToken("valid-token")).thenReturn(new TokenValidation(1L, "tenant-1"));

		mockMvc.perform(post("/v1/vms/stream-hook/on_play").contentType(MediaType.APPLICATION_JSON).content("""
				{"app":"vms","stream":"vms_1","params":{"token":"valid-token"}}
				""")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
	}

	@Test
	@DisplayName("on_play 無效 token → 回傳 code=-1")
	void onPlay_invalidToken() throws Exception {
		when(streamTokenService.validateToken("bad-token"))
			.thenThrow(new com.taipei.iot.common.exception.BusinessException(
					com.taipei.iot.common.enums.ErrorCode.VMS_STREAM_TOKEN_INVALID, "無效"));

		mockMvc.perform(post("/v1/vms/stream-hook/on_play").contentType(MediaType.APPLICATION_JSON).content("""
				{"app":"vms","stream":"vms_1","params":{"token":"bad-token"}}
				""")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(-1));
	}

	@Test
	@DisplayName("on_play 缺少 token → 回傳 code=-1")
	void onPlay_missingToken() throws Exception {
		mockMvc.perform(post("/v1/vms/stream-hook/on_play").contentType(MediaType.APPLICATION_JSON).content("""
				{"app":"vms","stream":"vms_1"}
				""")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(-1));
	}

	@Test
	@DisplayName("on_stream_none_reader → 關閉串流 + code=0")
	void onStreamNoneReader_closesStream() throws Exception {
		mockMvc
			.perform(post("/v1/vms/stream-hook/on_stream_none_reader").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"app":"vms","stream":"vms_1"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0));

		verify(zlMediaKitClient).closeStream("vms_1");
	}

}
