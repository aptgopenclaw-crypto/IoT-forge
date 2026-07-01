package com.taipei.iot.ingest.decoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link TelemetryDecoderRegistry} 單元測試：依格式索引、未知/空白回退至預設、無預設時拋錯。
 */
class TelemetryDecoderRegistryTest {

	private final CanonicalTelemetryDecoder canonical = new CanonicalTelemetryDecoder(new ObjectMapper());

	@Test
	void get_byExactFormat_returnsMatchingDecoder() {
		TelemetryDecoderRegistry registry = new TelemetryDecoderRegistry(List.of(canonical));

		assertSame(canonical, registry.get("canonical"));
		assertSame(canonical, registry.get("CANONICAL")); // 大小寫不敏感
	}

	@Test
	void get_unknownOrBlankFormat_fallsBackToDefault() {
		TelemetryDecoderRegistry registry = new TelemetryDecoderRegistry(List.of(canonical));

		assertSame(canonical, registry.get("vendor-x"));
		assertSame(canonical, registry.get(""));
		assertSame(canonical, registry.get(null));
	}

	@Test
	void getDefault_returnsCanonical() {
		TelemetryDecoderRegistry registry = new TelemetryDecoderRegistry(List.of(canonical));

		assertEquals(TelemetryDecoderRegistry.DEFAULT_FORMAT, registry.getDefault().format());
	}

	@Test
	void getDefault_whenNoDefaultRegistered_throws() {
		TelemetryPayloadDecoder onlyVendor = new TelemetryPayloadDecoder() {
			@Override
			public String format() {
				return "vendor-only";
			}

			@Override
			public List<DecodedReading> decode(byte[] payload) {
				return List.of();
			}
		};
		TelemetryDecoderRegistry registry = new TelemetryDecoderRegistry(List.of(onlyVendor));

		assertThrows(IllegalStateException.class, registry::getDefault);
	}

}
