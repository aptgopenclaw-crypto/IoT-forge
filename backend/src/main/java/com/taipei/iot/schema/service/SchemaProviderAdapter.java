package com.taipei.iot.schema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.common.schema.port.SchemaProviderPort;
import com.taipei.iot.schema.entity.DeviceTemplate;
import com.taipei.iot.schema.repository.DeviceTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@code schema}-module adapter for {@link SchemaProviderPort}.
 *
 * <p>
 * 自 {@code device_templates.schema}（已拆分為 {@code attributes} / {@code telemetry} 兩段，見
 * {@code V3__split_device_template_schema.sql}）取出 {@code telemetry} 段，供上層消費者（telemetry
 * 驗證、event-rule 欄位白名單）單向取用，毋須相依 {@code schema} 內部。
 */
@Component
@RequiredArgsConstructor
public class SchemaProviderAdapter implements SchemaProviderPort {

	private final DeviceTemplateRepository deviceTemplateRepository;

	private final ObjectMapper objectMapper;

	@Override
	public Optional<JsonNode> getTelemetrySchema(String deviceType) {
		return deviceTemplateRepository.findByDeviceType(deviceType)
			.map(DeviceTemplate::getSchema)
			.map(objectMapper::<JsonNode>valueToTree)
			.map(root -> root.get("telemetry"))
			.filter(telemetry -> telemetry != null && telemetry.isObject() && !telemetry.isEmpty());
	}

}
