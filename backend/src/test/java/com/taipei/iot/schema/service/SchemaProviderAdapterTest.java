package com.taipei.iot.schema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.schema.entity.DeviceTemplate;
import com.taipei.iot.schema.repository.DeviceTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaProviderAdapterTest {

	@Mock
	private DeviceTemplateRepository deviceTemplateRepository;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper();

	@InjectMocks
	private SchemaProviderAdapter adapter;

	private DeviceTemplate template(Map<String, Object> schema) {
		return DeviceTemplate.builder().deviceType("STREET_LIGHT").schema(schema).build();
	}

	@Test
	void getTelemetrySchema_existingTypeWithTelemetry_returnsTelemetrySection() {
		Map<String, Object> schema = Map.of("attributes", Map.of("rated_power", Map.of("type", "number")), "telemetry",
				Map.of("type", "object", "properties", Map.of("temperature", Map.of("type", "number"))));
		when(deviceTemplateRepository.findByDeviceType("STREET_LIGHT")).thenReturn(Optional.of(template(schema)));

		Optional<JsonNode> result = adapter.getTelemetrySchema("STREET_LIGHT");

		assertTrue(result.isPresent());
		assertEquals("object", result.get().get("type").asText());
		assertTrue(result.get().get("properties").has("temperature"));
	}

	@Test
	void getTelemetrySchema_typeWithEmptyTelemetry_returnsEmpty() {
		Map<String, Object> schema = Map.of("attributes", Map.of(), "telemetry", Map.of());
		when(deviceTemplateRepository.findByDeviceType("STREET_LIGHT")).thenReturn(Optional.of(template(schema)));

		assertTrue(adapter.getTelemetrySchema("STREET_LIGHT").isEmpty());
	}

	@Test
	void getTelemetrySchema_typeWithoutTelemetryKey_returnsEmpty() {
		Map<String, Object> schema = Map.of("attributes", Map.of("manufacturer", Map.of("type", "string")));
		when(deviceTemplateRepository.findByDeviceType("STREET_LIGHT")).thenReturn(Optional.of(template(schema)));

		assertTrue(adapter.getTelemetrySchema("STREET_LIGHT").isEmpty());
	}

	@Test
	void getTelemetrySchema_unknownType_returnsEmpty() {
		when(deviceTemplateRepository.findByDeviceType("UNKNOWN")).thenReturn(Optional.empty());

		assertTrue(adapter.getTelemetrySchema("UNKNOWN").isEmpty());
	}

}
