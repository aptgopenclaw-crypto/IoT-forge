package com.taipei.iot.import_.parser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
public class ExcelParser implements FileParser {

	@Override
	public String supportedExtension() {
		return "xlsx";
	}

	@Override
	public List<Map<String, String>> parse(InputStream inputStream) {
		try (Workbook workbook = new XSSFWorkbook(inputStream)) {
			Sheet sheet = workbook.getSheetAt(0);
			List<Map<String, String>> result = new ArrayList<>();

			Row headerRow = sheet.getRow(0);
			if (headerRow == null) {
				return result;
			}
			int colCount = headerRow.getLastCellNum();
			String[] headers = new String[colCount];
			for (int i = 0; i < colCount; i++) {
				headers[i] = getCellStringValue(headerRow.getCell(i)).toLowerCase();
			}

			for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
				Row row = sheet.getRow(rowIdx);
				if (row == null)
					continue;
				Map<String, String> rowMap = new LinkedHashMap<>();
				for (int colIdx = 0; colIdx < colCount; colIdx++) {
					rowMap.put(headers[colIdx], getCellStringValue(row.getCell(colIdx)));
				}
				// 略過全空列
				if (rowMap.values().stream().allMatch(v -> v == null || v.isBlank())) {
					continue;
				}
				result.add(rowMap);
			}
			return result;
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to parse Excel file", e);
		}
	}

	private String getCellStringValue(Cell cell) {
		if (cell == null)
			return "";
		return switch (cell.getCellType()) {
			case STRING -> cell.getStringCellValue().trim();
			case NUMERIC -> {
				if (DateUtil.isCellDateFormatted(cell)) {
					yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
				}
				double val = cell.getNumericCellValue();
				if (val == Math.floor(val) && !Double.isInfinite(val)) {
					yield String.valueOf((long) val);
				}
				yield String.valueOf(val);
			}
			case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
			case FORMULA -> {
				try {
					yield String.valueOf((long) cell.getNumericCellValue());
				}
				catch (Exception e) {
					try {
						yield cell.getStringCellValue();
					}
					catch (Exception e2) {
						yield cell.getCellFormula();
					}
				}
			}
			default -> "";
		};
	}

}
