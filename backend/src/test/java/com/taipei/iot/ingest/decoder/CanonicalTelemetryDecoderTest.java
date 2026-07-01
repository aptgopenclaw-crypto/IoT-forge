package com.taipei.iot.ingest.decoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CanonicalTelemetryDecoder} 單元測試：單筆、陣列、寬鬆物件、無效 JSON / ts。
 */
class CanonicalTelemetryDecoderTest {

	private final CanonicalTelemetryDecoder decoder = new CanonicalTelemetryDecoder(new ObjectMapper());

	private static byte[] bytes(String json) {
		return json.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void decode_singleObjectWithValuesWrapper() {
		List<DecodedReading> readings = decoder
			.decode(bytes("{\"deviceCode\":\"D1\",\"ts\":\"2026-06-30T08:00:00Z\",\"values\":{\"temperature\":25.5}}"));

		assertEquals(1, readings.size());
		DecodedReading reading = readings.get(0);
		assertEquals("D1", reading.deviceCode());
		assertEquals(Instant.parse("2026-06-30T08:00:00Z"), reading.ts());
		assertEquals(25.5, reading.values().get("temperature"));
	}

	@Test
	void decode_arrayYieldsMultipleReadings() {
		List<DecodedReading> readings = decoder.decode(
				bytes("[{\"deviceCode\":\"D1\",\"values\":{\"v\":1}},{\"deviceCode\":\"D2\",\"values\":{\"v\":2}}]"));

		assertEquals(2, readings.size());
		assertEquals("D1", readings.get(0).deviceCode());
		assertEquals("D2", readings.get(1).deviceCode());
	}

	@Test
	void decode_lenientObjectWithoutValuesWrapper_excludesMetaKeys() {
		List<DecodedReading> readings = decoder
			.decode(bytes("{\"deviceCode\":\"D1\",\"temperature\":25.5,\"lux\":800}"));

		assertEquals(1, readings.size());
		DecodedReading reading = readings.get(0);
		assertEquals(25.5, reading.values().get("temperature"));
		assertEquals(800, reading.values().get("lux"));
		assertTrue(!reading.values().containsKey("deviceCode"));
	}

	@Test
	void decode_missingTs_yieldsNullTs() {
		List<DecodedReading> readings = decoder.decode(bytes("{\"values\":{\"v\":1}}"));

		assertEquals(1, readings.size());
		assertNull(readings.get(0).ts());
		assertNull(readings.get(0).deviceCode());
	}

	@Test
	void decode_emptyPayload_returnsEmptyList() {
		assertTrue(decoder.decode(new byte[0]).isEmpty());
	}

	@Test
	void decode_invalidJson_throws() {
		assertThrows(IllegalArgumentException.class, () -> decoder.decode(bytes("not-json")));
	}

	@Test
	void decode_invalidTs_throws() {
		assertThrows(IllegalArgumentException.class,
				() -> decoder.decode(bytes("{\"ts\":\"not-a-timestamp\",\"values\":{\"v\":1}}")));
	}

}
