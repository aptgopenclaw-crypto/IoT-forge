package com.taipei.iot.ingest.source.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.device.entity.Device;
import com.taipei.iot.device.enums.DeviceStatus;
import com.taipei.iot.device.repository.DeviceRepository;
import com.taipei.iot.ingest.client.DeviceExternalRef;
import com.taipei.iot.ingest.client.DeviceExternalRefRepository;
import com.taipei.iot.ingest.client.TelemetryIngestClient;
import com.taipei.iot.ingest.client.TelemetryIngestClientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP ingest 端對端測試（打真 PG）：API key 認證、外部碼映射、批次、認證失敗、停用憑證。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class TelemetryIngestControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private TelemetryIngestClientRepository clientRepository;

	@Autowired
	private DeviceExternalRefRepository externalRefRepository;

	@Autowired
	private DeviceRepository deviceRepository;

	private String deviceCode;

	@BeforeEach
	void seed() {
		String suffix = String.valueOf(System.nanoTime());
		this.deviceCode = "DEV-HTTP-" + suffix;

		TenantContext.setCurrentTenantId("DEFAULT");
		deviceRepository.save(Device.builder()
			.tenantId("DEFAULT")
			.deviceType("STREET_LIGHT")
			.deviceCode(deviceCode)
			.status(DeviceStatus.ACTIVE)
			.build());
		TenantContext.clear();

		clientRepository.save(TelemetryIngestClient.builder()
			.tenantId("DEFAULT")
			.clientName("vendor-a")
			.apiKey("key-" + suffix)
			.secretHash(passwordEncoder.encode("s3cret"))
			.enabled(true)
			.build());

		clientRepository.save(TelemetryIngestClient.builder()
			.tenantId("DEFAULT")
			.clientName("vendor-disabled")
			.apiKey("key-disabled-" + suffix)
			.secretHash(passwordEncoder.encode("s3cret"))
			.enabled(false)
			.build());

		externalRefRepository.save(DeviceExternalRef.builder()
			.tenantId("DEFAULT")
			.externalCode("EXT-" + suffix)
			.deviceCode(deviceCode)
			.enabled(true)
			.build());

		this.apiKey = "key-" + suffix;
		this.disabledApiKey = "key-disabled-" + suffix;
		this.externalCode = "EXT-" + suffix;
	}

	private String apiKey;

	private String disabledApiKey;

	private String externalCode;

	@AfterEach
	void cleanup() {
		TenantContext.clear();
	}

	@Test
	void ingest_withDeviceCode_succeeds() throws Exception {
		String body = objectMapper.writeValueAsString(
				Map.of("deviceCode", deviceCode, "ts", "2026-06-30T08:00:00Z", "values", Map.of("temperature", 25.5)));

		mockMvc
			.perform(post("/v1/ingest/telemetry").header("X-API-Key", apiKey)
				.header("X-API-Secret", "s3cret")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.success").value(true))
			.andExpect(jsonPath("$.body.deviceCode").value(deviceCode));
	}

	@Test
	void ingest_withExternalCode_resolvesMappingAndSucceeds() throws Exception {
		String body = objectMapper
			.writeValueAsString(Map.of("externalCode", externalCode, "values", Map.of("temperature", 20.0)));

		mockMvc
			.perform(post("/v1/ingest/telemetry").header("X-API-Key", apiKey)
				.header("X-API-Secret", "s3cret")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.success").value(true))
			.andExpect(jsonPath("$.body.deviceCode").value(deviceCode));
	}

	@Test
	void ingest_batch_processesEachIndependently() throws Exception {
		String body = objectMapper
			.writeValueAsString(List.of(Map.of("deviceCode", deviceCode, "values", Map.of("temperature", 25.5)),
					Map.of("externalCode", "NOPE-UNMAPPED", "values", Map.of("temperature", 9.9))));

		mockMvc
			.perform(post("/v1/ingest/telemetry/batch").header("X-API-Key", apiKey)
				.header("X-API-Secret", "s3cret")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body[0].success").value(true))
			.andExpect(jsonPath("$.body[1].success").value(false))
			.andExpect(jsonPath("$.body[1].errorCode").value("88072"));
	}

	@Test
	void ingest_withWrongSecret_returns401() throws Exception {
		String body = objectMapper
			.writeValueAsString(Map.of("deviceCode", deviceCode, "values", Map.of("temperature", 25.5)));

		mockMvc
			.perform(post("/v1/ingest/telemetry").header("X-API-Key", apiKey)
				.header("X-API-Secret", "wrong")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.errorCode").value("88070"));
	}

	@Test
	void ingest_withDisabledClient_returns403() throws Exception {
		String body = objectMapper
			.writeValueAsString(Map.of("deviceCode", deviceCode, "values", Map.of("temperature", 25.5)));

		mockMvc
			.perform(post("/v1/ingest/telemetry").header("X-API-Key", disabledApiKey)
				.header("X-API-Secret", "s3cret")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.errorCode").value("88071"));
	}

	@Test
	void ingest_withoutCredentials_returns401() throws Exception {
		String body = objectMapper
			.writeValueAsString(Map.of("deviceCode", deviceCode, "values", Map.of("temperature", 25.5)));

		mockMvc.perform(post("/v1/ingest/telemetry").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isUnauthorized());
	}

}
