package com.taipei.iot.telemetry.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.taipei.iot.common.schema.port.SchemaProviderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 以 {@code device_templates.schema.telemetry} 對遙測 {@code values} 做 JSON Schema 驗證。
 *
 * <p>
 * 透過 {@link SchemaProviderPort} 取得 telemetry schema（毋須相依 {@code schema} 內部）。若該 deviceType
 * 未定義 telemetry schema，採寬鬆策略放行（valid），由設備模板管理者後續補上定義。
 */
@Service
@RequiredArgsConstructor
public class TelemetryValidationService {

	private final SchemaProviderPort schemaProviderPort;

	private final ObjectMapper objectMapper;

	private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

	public TelemetryValidationResult validate(String deviceType, Map<String, Object> values) {
		Optional<JsonNode> telemetrySchema = schemaProviderPort.getTelemetrySchema(deviceType);
		if (telemetrySchema.isEmpty()) {
			return TelemetryValidationResult.passed();
		}

		JsonSchema schema = schemaFactory.getSchema(telemetrySchema.get());
		JsonNode data = objectMapper.valueToTree(values != null ? values : Map.of());
		Set<ValidationMessage> messages = schema.validate(data);
		if (messages.isEmpty()) {
			return TelemetryValidationResult.passed();
		}

		List<String> errors = messages.stream()
			.map(ValidationMessage::getMessage)
			.sorted(Comparator.naturalOrder())
			.toList();
		return TelemetryValidationResult.failed(errors);
	}

}
