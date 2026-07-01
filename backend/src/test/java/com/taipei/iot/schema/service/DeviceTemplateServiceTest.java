package com.taipei.iot.schema.service;

import com.taipei.iot.common.device.port.DeviceTypeUsageGuard;
import com.taipei.iot.schema.entity.DeviceTemplate;
import com.taipei.iot.schema.repository.DeviceTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTemplateServiceTest {

	@Mock
	private DeviceTemplateRepository deviceTemplateRepository;

	@Mock
	private DeviceTypeUsageGuard deviceTypeUsageGuard;

	@InjectMocks
	private DeviceTemplateService service;

	@Test
	void updateTelemetrySchema_preservesAttributesSection_andBumpsVersion() {
		Map<String, Object> existing = new HashMap<>();
		existing.put("attributes", Map.of("rated_power", Map.of("type", "number")));
		existing.put("telemetry", Map.of());
		DeviceTemplate template = DeviceTemplate.builder()
			.deviceType("STREET_LIGHT")
			.schema(existing)
			.version(3)
			.build();
		when(deviceTemplateRepository.findByDeviceType("STREET_LIGHT")).thenReturn(Optional.of(template));

		Map<String, Object> newTelemetry = Map.of("type", "object", "properties",
				Map.of("temperature", Map.of("type", "number")));
		Map<String, Object> returned = service.updateTelemetrySchema("STREET_LIGHT", newTelemetry);

		assertEquals(newTelemetry, returned);

		ArgumentCaptor<DeviceTemplate> captor = ArgumentCaptor.forClass(DeviceTemplate.class);
		verify(deviceTemplateRepository).save(captor.capture());
		Map<String, Object> saved = captor.getValue().getSchema();
		assertEquals(newTelemetry, saved.get("telemetry"));
		assertEquals(Map.of("rated_power", Map.of("type", "number")), saved.get("attributes"));
		assertEquals(4, captor.getValue().getVersion());
	}

	@Test
	void updateAttributesSchema_unknownType_createsTemplateWithBothSections() {
		when(deviceTemplateRepository.findByDeviceType("NEW_TYPE")).thenReturn(Optional.empty());

		Map<String, Object> attributes = Map.of("manufacturer", Map.of("type", "string"));
		service.updateAttributesSchema("NEW_TYPE", attributes);

		ArgumentCaptor<DeviceTemplate> captor = ArgumentCaptor.forClass(DeviceTemplate.class);
		verify(deviceTemplateRepository).save(captor.capture());
		Map<String, Object> saved = captor.getValue().getSchema();
		assertEquals(attributes, saved.get("attributes"));
		assertTrue(saved.containsKey("telemetry"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void getTelemetrySchema_returnsTelemetrySection() {
		Map<String, Object> schema = Map.of("attributes", Map.of(), "telemetry",
				Map.of("type", "object", "properties", Map.of("voltage", Map.of("type", "number"))));
		when(deviceTemplateRepository.findByDeviceType("STREET_LIGHT"))
			.thenReturn(Optional.of(DeviceTemplate.builder().deviceType("STREET_LIGHT").schema(schema).build()));

		Map<String, Object> result = service.getTelemetrySchema("STREET_LIGHT");

		assertEquals("object", result.get("type"));
		assertTrue(((Map<String, Object>) result.get("properties")).containsKey("voltage"));
	}

}
