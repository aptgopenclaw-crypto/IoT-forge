package com.taipei.iot.import_.device;

import com.taipei.iot.auth.config.SecurityConfig;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.config.CorsProperties;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.import_.ImportError;
import com.taipei.iot.import_.ImportOrchestrator;
import com.taipei.iot.import_.ImportResponse;
import com.taipei.iot.import_.ImportResult;
import com.taipei.iot.tenant.cache.TenantEnabledCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeviceImportController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class DeviceImportControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ImportOrchestrator importOrchestrator;

	@MockitoBean
	private DeviceImportStrategy deviceImportStrategy;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private TenantEnabledCache tenantEnabledCache;

	private static final String TOKEN = "valid-token";

	private static final String AUTH_HEADER = "Bearer " + TOKEN;

	private void mockJwtValid(List<String> permissions) {
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", "user01");
		claimsMap.put("tenantId", "TENANT_A");
		claimsMap.put("roles", List.of("ADMIN"));
		claimsMap.put("permissions", permissions);
		claimsMap.put("sub", "user01");
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600_000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
	}

	@Test
	void importDevices_success_shouldReturn200() throws Exception {
		mockJwtValid(List.of("DEVICE_MANAGE"));
		when(importOrchestrator.parseAndValidate(any(), any())).thenReturn(ImportResult.success(List.of()));
		when(importOrchestrator.execute(any(), any())).thenReturn(
				ImportResponse.builder().entityType("device").totalRows(2).successCount(2).errors(List.of()).build());

		MockMultipartFile file = new MockMultipartFile("file", "devices.xlsx", MediaType.MULTIPART_FORM_DATA_VALUE,
				"fake-content".getBytes());

		mockMvc.perform(multipart("/v1/auth/devices/import").file(file).header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.successCount").value(2));
	}

	@Test
	void importDevices_validationFailure_shouldReturnErrorBody() throws Exception {
		mockJwtValid(List.of("DEVICE_MANAGE"));
		when(importOrchestrator.parseAndValidate(any(), any())).thenReturn(ImportResult
			.failure(List.of(ImportError.builder().row(3).field("device_code").value("").message("必填").build())));

		MockMultipartFile file = new MockMultipartFile("file", "devices.xlsx", MediaType.MULTIPART_FORM_DATA_VALUE,
				"fake-content".getBytes());

		mockMvc.perform(multipart("/v1/auth/devices/import").file(file).header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.body.successCount").value(0));
	}

	@Test
	void downloadTemplate_shouldReturnXlsx() throws Exception {
		mockJwtValid(List.of("DEVICE_MANAGE"));
		mockMvc
			.perform(get("/v1/auth/devices/import/template").param("format", "xlsx")
				.header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", "attachment; filename=\"device-import-template.xlsx\""));
	}

	@Test
	void downloadTemplate_shouldReturnCsv() throws Exception {
		mockJwtValid(List.of("DEVICE_MANAGE"));
		mockMvc
			.perform(
					get("/v1/auth/devices/import/template").param("format", "csv").header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", "attachment; filename=\"device-import-template.csv\""));
	}

}
