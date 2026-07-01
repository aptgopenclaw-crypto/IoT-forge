package com.taipei.iot.telemetry.query;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.taipei.iot.auth.config.SecurityConfig;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.config.CorsProperties;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.telemetry.query.dto.TelemetryFieldStats;
import com.taipei.iot.telemetry.query.dto.TelemetryLatestResponse;
import com.taipei.iot.telemetry.query.dto.TelemetryPointResponse;
import com.taipei.iot.tenant.cache.TenantEnabledCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link TelemetryController} 的 MVC slice 測試：驗證端點契約、{@code DEVICE_VIEW} 權限與 ISO instant
 * 參數綁定。租戶隔離的實際 SQL 行為由 {@code TelemetryDataServiceIntegrationTest} 打真 PG 驗證。
 */
@WebMvcTest(TelemetryController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class TelemetryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TelemetryDataService telemetryDataService;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private TenantEnabledCache tenantEnabledCache;

	private static final String TOKEN = "valid.jwt.token";

	private void mockJwt(List<String> permissions) {
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", "user-1");
		claimsMap.put("tenantId", "DEFAULT");
		claimsMap.put("roles", List.of("OPERATOR"));
		claimsMap.put("permissions", permissions);
		claimsMap.put("deptId", "1");
		claimsMap.put("dataScope", "ALL");
		claimsMap.put("sub", "user@test.com");
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
	}

	@Test
	void history_withDeviceViewPermission_returns200AndBindsInstantParams() throws Exception {
		mockJwt(List.of("DEVICE_VIEW"));
		Page<TelemetryPointResponse> page = new PageImpl<>(
				List.of(new TelemetryPointResponse(Instant.parse("2026-06-30T08:00:00Z"), Map.of("temperature", 25.5))),
				PageRequest.of(0, 100), 1);
		when(telemetryDataService.history(eq(7L), any(), any(), eq(0), eq(100))).thenReturn(page);

		mockMvc
			.perform(get("/v1/auth/telemetry/devices/7/history").param("from", "2026-06-30T00:00:00Z")
				.param("to", "2026-07-01T00:00:00Z")
				.header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.totalElements").value(1))
			.andExpect(jsonPath("$.body.content[0].values.temperature").value(25.5));

		ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
		verify(telemetryDataService).history(eq(7L), fromCaptor.capture(), toCaptor.capture(), eq(0), eq(100));
		assert fromCaptor.getValue().equals(Instant.parse("2026-06-30T00:00:00Z"));
		assert toCaptor.getValue().equals(Instant.parse("2026-07-01T00:00:00Z"));
	}

	@Test
	void latest_withDeviceViewPermission_returns200() throws Exception {
		mockJwt(List.of("DEVICE_VIEW"));
		when(telemetryDataService.latest(7L)).thenReturn(
				new TelemetryLatestResponse(7L, Instant.parse("2026-06-30T08:00:00Z"), Map.of("temperature", 30.0)));

		mockMvc.perform(get("/v1/auth/telemetry/devices/7/latest").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.deviceId").value(7))
			.andExpect(jsonPath("$.body.values.temperature").value(30.0));
	}

	@Test
	void stats_withDeviceViewPermission_returns200() throws Exception {
		mockJwt(List.of("DEVICE_VIEW"));
		when(telemetryDataService.stats(eq(7L), any(), any(), any()))
			.thenReturn(List.of(new TelemetryFieldStats("temperature", 3, 10.0, 30.0, 20.0)));

		mockMvc
			.perform(get("/v1/auth/telemetry/devices/7/stats").param("fields", "temperature")
				.header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body[0].field").value("temperature"))
			.andExpect(jsonPath("$.body[0].count").value(3))
			.andExpect(jsonPath("$.body[0].avg").value(20.0));
	}

	@Test
	void history_withoutDeviceViewPermission_returns403() throws Exception {
		mockJwt(List.of("SOME_OTHER_PERMISSION"));

		mockMvc.perform(get("/v1/auth/telemetry/devices/7/history").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isForbidden());
	}

}
