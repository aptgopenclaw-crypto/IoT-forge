package com.taipei.iot.ingest.decoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 預設 canonical JSON 解碼器。接受下列三種形態：
 * <ul>
 * <li>單筆物件：{@code {"deviceCode":"D1","ts":"2026-06-30T08:00:00Z","values":{...}}}</li>
 * <li>多筆陣列：{@code [ {...}, {...} ]}</li>
 * <li>寬鬆物件（無 {@code values} 包裹）：整個物件視為 {@code values}</li>
 * </ul>
 * {@code deviceCode}、{@code ts} 皆為選填；{@code ts} 須為 ISO-8601 instant。
 */
@Component
public class CanonicalTelemetryDecoder implements TelemetryPayloadDecoder {

	public static final String FORMAT = "canonical";

	private final ObjectMapper objectMapper;

	public CanonicalTelemetryDecoder(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public String format() {
		return FORMAT;
	}

	@Override
	public List<DecodedReading> decode(byte[] payload) {
		if (payload == null || payload.length == 0) {
			return List.of();
		}
		JsonNode root;
		try {
			root = objectMapper.readTree(payload);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Invalid telemetry JSON payload: " + ex.getMessage(), ex);
		}
		if (root == null || root.isNull()) {
			return List.of();
		}
		List<DecodedReading> readings = new ArrayList<>();
		if (root.isArray()) {
			for (JsonNode element : root) {
				readings.add(toReading(element));
			}
		}
		else {
			readings.add(toReading(root));
		}
		return readings;
	}

	private DecodedReading toReading(JsonNode node) {
		if (node == null || !node.isObject()) {
			throw new IllegalArgumentException("Telemetry reading must be a JSON object");
		}
		String deviceCode = node.hasNonNull("deviceCode") ? node.get("deviceCode").asText() : null;
		Instant ts = parseTs(node.get("ts"));
		JsonNode valuesNode = node.get("values");
		Map<String, Object> values = (valuesNode != null && valuesNode.isObject()) ? toMap(valuesNode)
				: toMapExcludingMeta(node);
		return new DecodedReading(deviceCode, ts, values);
	}

	private Instant parseTs(JsonNode tsNode) {
		if (tsNode == null || tsNode.isNull()) {
			return null;
		}
		try {
			return Instant.parse(tsNode.asText());
		}
		catch (DateTimeParseException ex) {
			throw new IllegalArgumentException("Invalid telemetry ts (expected ISO-8601 instant): " + tsNode.asText(),
					ex);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> toMap(JsonNode node) {
		return objectMapper.convertValue(node, Map.class);
	}

	/** 寬鬆模式：無 {@code values} 包裹時，將整個物件當 values，但排除 deviceCode/ts 等保留欄位。 */
	private Map<String, Object> toMapExcludingMeta(JsonNode node) {
		Map<String, Object> values = new LinkedHashMap<>();
		Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> entry = fields.next();
			String key = entry.getKey();
			if ("deviceCode".equals(key) || "ts".equals(key)) {
				continue;
			}
			values.put(key, objectMapper.convertValue(entry.getValue(), Object.class));
		}
		return values;
	}

}
