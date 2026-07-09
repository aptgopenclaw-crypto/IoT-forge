package com.taipei.iot.vms.controller;

import com.taipei.iot.auth.config.SecurityConfig;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.config.CorsProperties;
import com.taipei.iot.common.dept.port.VisibleDeptScopeProvider;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.vms.dto.CameraLiveResponse;
import com.taipei.iot.vms.dto.CameraPlaybackResponse;
import com.taipei.iot.vms.dto.PtzCommand;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.enums.CameraStatus;
import com.taipei.iot.vms.enums.VmsAuthType;
import com.taipei.iot.vms.enums.VmsType;
import com.taipei.iot.vms.repository.VmsCameraRepository;
import com.taipei.iot.vms.service.VmsStreamService;
import com.taipei.iot.tenant.cache.TenantEnabledCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link VmsController} MVC slice 測試：端點契約 + VMS_VIEW/VMS_MANAGE 權限。
 */
@WebMvcTest(VmsController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class VmsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VmsStreamService vmsStreamService;

	@MockitoBean
	private VmsCameraRepository vmsCameraRepository;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private TenantEnabledCache tenantEnabledCache;

	@MockitoBean
	private VisibleDeptScopeProvider visibleDeptScopeProvider;

	private static final String TOKEN = "valid.jwt.token";

	@BeforeEach
	void setUp() {
		when(visibleDeptScopeProvider.getVisibleDeptIds()).thenReturn(Collections.emptyList());
	}

	private void mockJwt(List<String> permissions) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("uid", "user-1");
		claims.put("tenantId", "DEFAULT");
		claims.put("roles", List.of("ADMIN"));
		claims.put("permissions", permissions);
		claims.put("deptId", "1");
		claims.put("dataScope", "ALL");
		claims.put("sub", "admin@test.com");
		claims.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claims.put("iat", new Date());
		Claims c = new DefaultClaims(claims);
		when(jwtUtil.parseToken(TOKEN)).thenReturn(c);
	}

	private VmsCamera stubCamera() {
		var server = VmsServer.builder()
			.id(1L)
			.tenantId("DEFAULT")
			.name("Srv")
			.vmsType(VmsType.NX_WITNESS)
			.baseUrl("http://srv")
			.authType(VmsAuthType.BASIC)
			.build();
		return VmsCamera.builder()
			.id(1L)
			.tenantId("DEFAULT")
			.server(server)
			.vmsCameraId("cam-001")
			.displayName("入口")
			.status(CameraStatus.ONLINE)
			.build();
	}

	// ── GET /cameras ─────────────────────────────────────────────

	@Test
	void listCameras_withVmsView_returns200() throws Exception {
		mockJwt(List.of("VMS_VIEW"));
		when(vmsCameraRepository.findByTenantId("DEFAULT")).thenReturn(List.of(stubCamera()));

		mockMvc.perform(get("/v1/auth/vms/cameras").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body[0].vmsCameraId").value("cam-001"))
			.andExpect(jsonPath("$.body[0].displayName").value("入口"))
			.andExpect(jsonPath("$.body[0].serverId").value(1));
	}

	@Test
	void listCameras_withoutPermission_returns403() throws Exception {
		mockJwt(List.of("OTHER_PERM"));

		mockMvc.perform(get("/v1/auth/vms/cameras").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isForbidden());
	}

	// ── GET /cameras/{id}/live ───────────────────────────────────

	@Test
	void getLiveStream_withVmsView_returns200() throws Exception {
		mockJwt(List.of("VMS_VIEW"));
		var response = new CameraLiveResponse(1L, "入口", "http://play.url/webrtc?streamId=1",
				Instant.now().plusSeconds(300), CameraStatus.ONLINE);
		when(vmsStreamService.getLiveStream(1L)).thenReturn(response);

		mockMvc.perform(get("/v1/auth/vms/cameras/1/live").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.playUrl").value("http://play.url/webrtc?streamId=1"))
			.andExpect(jsonPath("$.body.cameraId").value(1));
	}

	@Test
	void getLiveStream_cameraNotFound_returns404() throws Exception {
		mockJwt(List.of("VMS_VIEW"));
		when(vmsStreamService.getLiveStream(999L))
			.thenThrow(new BusinessException(ErrorCode.VMS_CAMERA_NOT_FOUND, "攝影機不存在"));

		mockMvc.perform(get("/v1/auth/vms/cameras/999/live").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("88101"));
	}

	// ── GET /cameras/{id}/playback ───────────────────────────────

	@Test
	void getPlayback_withVmsView_returns200() throws Exception {
		mockJwt(List.of("VMS_VIEW"));
		var start = Instant.parse("2026-07-01T00:00:00Z");
		var end = Instant.parse("2026-07-01T01:00:00Z");
		var response = new CameraPlaybackResponse(1L, "入口", "http://play.url/webrtc?streamId=1&playback", start, end,
				CameraStatus.ONLINE);
		when(vmsStreamService.getPlayback(eq(1L), any(Instant.class), any(Instant.class))).thenReturn(response);

		mockMvc
			.perform(get("/v1/auth/vms/cameras/1/playback").header("Authorization", "Bearer " + TOKEN)
				.param("startTime", "2026-07-01T00:00:00Z")
				.param("endTime", "2026-07-01T01:00:00Z"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.playUrl").value("http://play.url/webrtc?streamId=1&playback"));
	}

	@Test
	void getPlayback_withoutPermission_returns403() throws Exception {
		mockJwt(List.of("VMS_MANAGE"));

		mockMvc
			.perform(get("/v1/auth/vms/cameras/1/playback").header("Authorization", "Bearer " + TOKEN)
				.param("startTime", "2026-07-01T00:00:00Z")
				.param("endTime", "2026-07-01T01:00:00Z"))
			.andExpect(status().isForbidden());
	}

	// ── POST /cameras/{id}/ptz ──────────────────────────────────

	@Test
	void controlPtz_withVmsManage_returns200() throws Exception {
		mockJwt(List.of("VMS_MANAGE"));

		mockMvc
			.perform(post("/v1/auth/vms/cameras/1/ptz").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"direction":"RIGHT","speed":50}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));

		verify(vmsStreamService).controlPtz(eq(1L), any(PtzCommand.class));
	}

	@Test
	void controlPtz_withoutPermission_returns403() throws Exception {
		mockJwt(List.of("VMS_VIEW"));

		mockMvc
			.perform(post("/v1/auth/vms/cameras/1/ptz").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"direction":"RIGHT","speed":50}
						"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void controlPtz_invalidDirection_returns400() throws Exception {
		mockJwt(List.of("VMS_MANAGE"));

		mockMvc
			.perform(post("/v1/auth/vms/cameras/1/ptz").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"speed":50}
						"""))
			.andExpect(status().isBadRequest());
	}

}
