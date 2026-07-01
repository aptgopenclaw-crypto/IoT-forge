package com.taipei.iot.telemetry.ingest;

import com.taipei.iot.common.device.port.DeviceLookupPort;
import com.taipei.iot.common.device.port.DeviceRef;
import com.taipei.iot.common.event.TelemetryReceivedEvent;
import com.taipei.iot.telemetry.storage.TelemetryReading;
import com.taipei.iot.telemetry.storage.TelemetryStore;
import com.taipei.iot.telemetry.validation.TelemetryValidationResult;
import com.taipei.iot.telemetry.validation.TelemetryValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionServiceImplTest {

	@Mock
	private DeviceLookupPort deviceLookupPort;

	@Mock
	private TelemetryValidationService validationService;

	@Mock
	private TelemetryStore telemetryStore;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private TelemetryIngestionServiceImpl service;

	private TelemetryIngestRequest request(String deviceCode) {
		return new TelemetryIngestRequest(deviceCode, "T1", Instant.parse("2026-06-30T10:00:00Z"),
				Map.of("temperature", 25.5), TelemetrySource.MQTT, "client-1", Map.of("raw", true));
	}

	private DeviceRef deviceRef() {
		return new DeviceRef(42L, "DEV-1", "STREET_LIGHT", "T1");
	}

	@Test
	void ingest_validRequest_storesAndPublishesEvent() {
		when(deviceLookupPort.resolve("DEV-1", "T1")).thenReturn(Optional.of(deviceRef()));
		when(validationService.validate("STREET_LIGHT", Map.of("temperature", 25.5)))
			.thenReturn(TelemetryValidationResult.passed());

		TelemetryIngestResult result = service.ingest(request("DEV-1"));

		assertTrue(result.success());
		assertEquals(42L, result.deviceId());

		ArgumentCaptor<TelemetryReading> reading = ArgumentCaptor.forClass(TelemetryReading.class);
		verify(telemetryStore).save(reading.capture());
		assertEquals("T1", reading.getValue().tenantId());
		assertEquals(42L, reading.getValue().deviceId());
		assertEquals("MQTT", reading.getValue().source());

		ArgumentCaptor<TelemetryReceivedEvent> event = ArgumentCaptor.forClass(TelemetryReceivedEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertEquals(42L, event.getValue().deviceId());
		assertEquals("STREET_LIGHT", event.getValue().deviceType());
	}

	@Test
	void ingest_deviceNotFound_rejectsWithoutStoreOrEvent() {
		when(deviceLookupPort.resolve("UNKNOWN", "T1")).thenReturn(Optional.empty());

		TelemetryIngestResult result = service.ingest(
				new TelemetryIngestRequest("UNKNOWN", "T1", Instant.now(), Map.of("temperature", 1), null, null, null));

		assertFalse(result.success());
		assertEquals("60001", result.errorCode());
		verifyNoInteractions(validationService, telemetryStore, eventPublisher);
	}

	@Test
	void ingest_validationFailure_rejectsWithoutStoreOrEvent() {
		when(deviceLookupPort.resolve("DEV-1", "T1")).thenReturn(Optional.of(deviceRef()));
		when(validationService.validate("STREET_LIGHT", Map.of("temperature", 25.5)))
			.thenReturn(TelemetryValidationResult.failed(List.of("temperature: out of range")));

		TelemetryIngestResult result = service.ingest(request("DEV-1"));

		assertFalse(result.success());
		assertEquals("88060", result.errorCode());
		assertEquals(List.of("temperature: out of range"), result.validationErrors());
		verify(telemetryStore, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void ingest_nullTsAndSource_defaultsApplied() {
		when(deviceLookupPort.resolve("DEV-1", "T1")).thenReturn(Optional.of(deviceRef()));
		when(validationService.validate(any(), any())).thenReturn(TelemetryValidationResult.passed());

		service.ingest(new TelemetryIngestRequest("DEV-1", "T1", null, Map.of("v", 1), null, null, null));

		ArgumentCaptor<TelemetryReading> reading = ArgumentCaptor.forClass(TelemetryReading.class);
		verify(telemetryStore).save(reading.capture());
		assertEquals("MQTT", reading.getValue().source());
		assertTrue(reading.getValue().ts() != null);
	}

	@Test
	void ingestBatch_partialFailure_returnsPerItemResults() {
		when(deviceLookupPort.resolve("DEV-1", "T1")).thenReturn(Optional.of(deviceRef()));
		when(deviceLookupPort.resolve("UNKNOWN", "T1")).thenReturn(Optional.empty());
		when(validationService.validate("STREET_LIGHT", Map.of("temperature", 25.5)))
			.thenReturn(TelemetryValidationResult.passed());

		List<TelemetryIngestResult> results = service
			.ingestBatch(List.of(request("DEV-1"), new TelemetryIngestRequest("UNKNOWN", "T1", Instant.now(),
					Map.of("temperature", 25.5), null, null, null)));

		assertEquals(2, results.size());
		assertTrue(results.get(0).success());
		assertFalse(results.get(1).success());
		verify(telemetryStore).save(any());
	}

}
