package com.taipei.iot.import_.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvParserTest {

	private final CsvParser parser = new CsvParser();

	@Test
	void parse_shouldReturnRows() {
		String csv = "device_code,device_name,device_type\nSL-001,燈具1,STREET_LIGHT\nSL-002,燈具2,STREET_LIGHT";
		List<Map<String, String>> rows = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
		assertEquals(2, rows.size());
		assertEquals("SL-001", rows.get(0).get("device_code"));
		assertEquals("STREET_LIGHT", rows.get(0).get("device_type"));
	}

	@Test
	void parse_emptyContent_shouldReturnEmptyList() {
		String csv = "device_code\n";
		List<Map<String, String>> rows = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
		assertTrue(rows.isEmpty());
	}

	@Test
	void supportedExtension_shouldReturnCsv() {
		assertEquals("csv", parser.supportedExtension());
	}

}
