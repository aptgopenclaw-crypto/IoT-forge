package com.taipei.iot.audit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Iterator;
import java.util.Map;

public final class PayloadSanitizer {

	private static final String MASK = "***";

	private static final int MAX_LENGTH = 2000;

	private static final ObjectMapper MAPPER;

	static {
		MAPPER = new ObjectMapper();
		MAPPER.registerModule(new JavaTimeModule());
		MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	private PayloadSanitizer() {
	}

	public static String sanitize(Object[] args) {
		if (args == null || args.length == 0) {
			return null;
		}
		try {
			String json = MAPPER.writeValueAsString(args);
			JsonNode node = MAPPER.readTree(json);
			maskSensitiveFields(node);
			String result = MAPPER.writeValueAsString(node);
			if (result.length() > MAX_LENGTH) {
				return result.substring(0, MAX_LENGTH);
			}
			return result;
		}
		catch (Exception e) {
			return "[sanitize-error]";
		}
	}

	private static void maskSensitiveFields(JsonNode node) {
		if (node == null) {
			return;
		}
		if (node.isObject()) {
			ObjectNode obj = (ObjectNode) node;
			Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> entry = fields.next();
				if (isSensitive(entry.getKey())) {
					obj.put(entry.getKey(), MASK);
				}
				else {
					maskSensitiveFields(entry.getValue());
				}
			}
		}
		else if (node.isArray()) {
			for (JsonNode child : node) {
				maskSensitiveFields(child);
			}
		}
	}

	private static boolean isSensitive(String fieldName) {
		String lower = fieldName.toLowerCase();
		return lower.contains("password") || lower.contains("secret") || lower.contains("token")
				|| lower.equals("authorization");
	}

}
