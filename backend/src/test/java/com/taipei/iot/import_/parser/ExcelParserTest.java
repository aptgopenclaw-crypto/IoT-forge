package com.taipei.iot.import_.parser;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExcelParserTest {

	private final ExcelParser parser = new ExcelParser();

	@Test
	void parse_shouldReturnRows() throws IOException {
		byte[] content = createExcel(new String[] { "device_code", "device_name", "device_type" },
				new String[] { "SL-001", "燈具1", "STREET_LIGHT" }, new String[] { "SL-002", "燈具2", "STREET_LIGHT" });

		List<Map<String, String>> rows = parser.parse(new ByteArrayInputStream(content));
		assertEquals(2, rows.size());
		assertEquals("SL-001", rows.get(0).get("device_code"));
		assertEquals("燈具1", rows.get(0).get("device_name"));
	}

	@Test
	void parse_emptySheet_shouldReturnEmptyList() throws IOException {
		byte[] content = createExcel(new String[] { "device_code" }, new String[] {});
		List<Map<String, String>> rows = parser.parse(new ByteArrayInputStream(content));
		assertTrue(rows.isEmpty());
	}

	@Test
	void supportedExtension_shouldReturnXlsx() {
		assertEquals("xlsx", parser.supportedExtension());
	}

	private byte[] createExcel(String[] headers, String[]... data) throws IOException {
		try (Workbook wb = new XSSFWorkbook()) {
			Sheet sheet = wb.createSheet("Sheet1");
			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				headerRow.createCell(i).setCellValue(headers[i]);
			}
			for (int r = 0; r < data.length; r++) {
				Row row = sheet.createRow(r + 1);
				for (int c = 0; c < data[r].length; c++) {
					row.createCell(c).setCellValue(data[r][c]);
				}
			}
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			wb.write(baos);
			return baos.toByteArray();
		}
	}

}
