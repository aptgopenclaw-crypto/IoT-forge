package com.taipei.iot.import_.parser;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class CsvParser implements FileParser {

	@Override
	public String supportedExtension() {
		return "csv";
	}

	@Override
	public List<Map<String, String>> parse(InputStream inputStream) {
		try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
				CSVParser csvParser = new CSVParser(reader,
						CSVFormat.DEFAULT.builder()
							.setHeader()
							.setSkipHeaderRecord(true)
							.setTrim(true)
							.setIgnoreEmptyLines(true)
							.build())) {

			List<Map<String, String>> result = new ArrayList<>();
			Map<String, Integer> headerMap = csvParser.getHeaderMap();
			// 統一 header 為 lowercase
			Map<String, String> headerLowerMap = new HashMap<>();
			if (headerMap != null) {
				for (String h : headerMap.keySet()) {
					headerLowerMap.put(h.toLowerCase(), h);
				}
			}

			for (CSVRecord record : csvParser) {
				Map<String, String> rowMap = new LinkedHashMap<>();
				for (Map.Entry<String, String> entry : headerLowerMap.entrySet()) {
					String originalHeader = entry.getValue();
					String value;
					try {
						value = record.get(originalHeader);
					}
					catch (IllegalArgumentException e) {
						// 該列缺少此欄位（如尾端欄位遺漏），以空字串取代
						value = "";
					}
					rowMap.put(entry.getKey(), value);
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
			throw new BusinessException(ErrorCode.DEVICE_IMPORT_FILE_FORMAT, "Failed to parse CSV file", e);
		}
	}

}
