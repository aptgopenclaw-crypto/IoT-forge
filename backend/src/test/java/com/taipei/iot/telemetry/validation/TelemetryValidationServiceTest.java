package com.taipei.iot.telemetry.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.common.schema.port.SchemaProviderPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryValidationServiceTest {

	@Mock
	private SchemaProviderPort schemaProviderPort;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private TelemetryValidationService service;

	private JsonNode schema() {
		return objectMapper.valueToTree(Map.of("type", "object", "properties",
				Map.of("temperature", Map.of("type", "number", "minimum", -40, "maximum", 85)), "required",
				java.util.List.of("temperature")));
	}

	private TelemetryValidationService service() {
		if (service == null) {
			service = new TelemetryValidationService(schemaProviderPort, objectMapper);
		}
		return service;
	}

	@Test
	void validate_conformingValues_returnsValid() {
		when(schemaProviderPort.getTelemetrySchema("STREET_LIGHT")).thenReturn(Optional.of(schema()));

		TelemetryValidationResult result = service().validate("STREET_LIGHT", Map.of("temperature", 25.5));

		assertTrue(result.valid());
		assertTrue(result.errors().isEmpty());
	}

	@Test
	void validate_outOfRangeValue_returnsInvalidWithErrors() {
		when(schemaProviderPort.getTelemetrySchema("STREET_LIGHT")).thenReturn(Optional.of(schema()));

		TelemetryValidationResult result = service().validate("STREET_LIGHT", Map.of("temperature", 999));

		assertFalse(result.valid());
		assertFalse(result.errors().isEmpty());
	}

	@Test
	void validate_missingRequiredField_returnsInvalid() {
		when(schemaProviderPort.getTelemetrySchema("STREET_LIGHT")).thenReturn(Optional.of(schema()));

		TelemetryValidationResult result = service().validate("STREET_LIGHT", Map.of("humidity", 50));

		assertFalse(result.valid());
		assertFalse(result.errors().isEmpty());
	}

	@Test
	void validate_noSchemaDefined_returnsValidLeniently() {
		when(schemaProviderPort.getTelemetrySchema("UNKNOWN")).thenReturn(Optional.empty());

		TelemetryValidationResult result = service().validate("UNKNOWN", Map.of("anything", 1));

		assertTrue(result.valid());
	}

}
