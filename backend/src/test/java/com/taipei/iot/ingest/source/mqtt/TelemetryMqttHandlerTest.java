package com.taipei.iot.ingest.source.mqtt;

import com.taipei.iot.ingest.decoder.CanonicalTelemetryDecoder;
import com.taipei.iot.ingest.decoder.TelemetryDecoderRegistry;
import com.taipei.iot.telemetry.ingest.TelemetryIngestRequest;
import com.taipei.iot.telemetry.ingest.TelemetryIngestResult;
import com.taipei.iot.telemetry.ingest.TelemetryIngestionService;
import com.taipei.iot.telemetry.ingest.TelemetrySource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TelemetryMqttHandler} 單元測試：topic 取設備碼、收斂為 source=MQTT 請求、非法 topic 略過。
 */
@ExtendWith(MockitoExtension.class)
class TelemetryMqttHandlerTest {

	@Mock
	private TelemetryIngestionService ingestionService;

	private TelemetryMqttHandler handler() {
		TelemetryDecoderRegistry registry = new TelemetryDecoderRegistry(
				java.util.List.of(new CanonicalTelemetryDecoder(new ObjectMapper())));
		return new TelemetryMqttHandler(registry, ingestionService, "DEFAULT");
	}

	private static byte[] bytes(String json) {
		return json.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void handle_extractsDeviceCodeFromTopic_andIngestsAsMqtt() {
		when(ingestionService.ingest(any())).thenReturn(TelemetryIngestResult.success("LAMP-001", 1L));

		handler().handle("device/LAMP-001/telemetry",
				bytes("{\"ts\":\"2026-06-30T08:00:00Z\",\"values\":{\"temperature\":25.5}}"));

		ArgumentCaptor<TelemetryIngestRequest> captor = ArgumentCaptor.forClass(TelemetryIngestRequest.class);
		verify(ingestionService).ingest(captor.capture());
		TelemetryIngestRequest request = captor.getValue();
		assertEquals("LAMP-001", request.deviceCode());
		assertEquals("DEFAULT", request.tenantId());
		assertEquals(TelemetrySource.MQTT, request.source());
		assertEquals(25.5, request.values().get("temperature"));
	}

	@Test
	void handle_payloadDeviceCodeOverridesTopic() {
		when(ingestionService.ingest(any())).thenReturn(TelemetryIngestResult.success("BODY-CODE", 1L));

		handler().handle("device/TOPIC-CODE/telemetry", bytes("{\"deviceCode\":\"BODY-CODE\",\"values\":{\"v\":1}}"));

		ArgumentCaptor<TelemetryIngestRequest> captor = ArgumentCaptor.forClass(TelemetryIngestRequest.class);
		verify(ingestionService).ingest(captor.capture());
		assertEquals("BODY-CODE", captor.getValue().deviceCode());
	}

	@Test
	void handle_nonMatchingTopicWithoutDeviceCode_skips() {
		handler().handle("random/topic", bytes("{\"values\":{\"v\":1}}"));

		verify(ingestionService, never()).ingest(any());
	}

	@Test
	void handle_invalidPayload_doesNotThrow_andSkips() {
		handler().handle("device/LAMP-001/telemetry", bytes("not-json"));

		verify(ingestionService, never()).ingest(any());
	}

}
