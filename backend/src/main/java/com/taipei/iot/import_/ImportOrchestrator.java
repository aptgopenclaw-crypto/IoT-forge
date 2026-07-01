package com.taipei.iot.import_;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.import_.config.ImportProperties;
import com.taipei.iot.import_.parser.FileParser;
import com.taipei.iot.import_.parser.FileParserFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 匯入編排引擎 — 通用匯入流程：解析、驗證、寫入。
 *
 * <p>
 * 不與任何特定實體耦合，所有實體邏輯委派給 {@link ImportStrategy}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportOrchestrator {

	private final FileParserFactory fileParserFactory;

	private final ImportProperties importProperties;

	/**
	 * 解析並驗證上傳檔案，回傳驗證結果。 有錯誤時不回滾 — 由 caller 決定是否回 400。
	 */
	public <T> ImportResult<T> parseAndValidate(MultipartFile file, ImportStrategy<T> strategy) {
		// 1. 檢查空檔案
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.DEVICE_IMPORT_FILE_EMPTY);
		}

		// 2. 解析副檔名
		String originalName = file.getOriginalFilename();
		String extension = extractExtension(originalName);

		// 3. 檢查副檔名是否允許
		String allowed = importProperties.getAllowedExtensions();
		if (!List.of(allowed.split(",")).contains(extension)) {
			throw new BusinessException(ErrorCode.DEVICE_IMPORT_FILE_FORMAT);
		}

		// 4. 解析檔案
		FileParser parser = fileParserFactory.getParser(extension);
		List<Map<String, String>> rawRows;
		try {
			rawRows = parser.parse(file.getInputStream());
		}
		catch (IOException e) {
			throw new BusinessException(ErrorCode.DEVICE_IMPORT_FILE_FORMAT, "無法讀取檔案內容");
		}

		// 5. 檢查筆數上限
		if (rawRows.size() > importProperties.getMaxRows()) {
			throw new BusinessException(ErrorCode.DEVICE_IMPORT_MAX_ROWS_EXCEEDED,
					"匯入筆數 " + rawRows.size() + " 超過單次上限 " + importProperties.getMaxRows() + " 筆");
		}

		if (rawRows.isEmpty()) {
			return ImportResult.success(List.of());
		}

		// 6. 檢查標題列
		Set<String> expected = strategy.expectedHeaders();
		Set<String> actual = rawRows.get(0).keySet();
		if (!actual.containsAll(expected)) {
			throw new BusinessException(ErrorCode.DEVICE_IMPORT_HEADER_MISMATCH,
					"缺少必要欄位：" + expected.stream().filter(h -> !actual.contains(h)).toList());
		}

		// 7. mapToDto + 逐筆驗證
		List<ImportError> allErrors = new ArrayList<>();
		List<T> dtos = new ArrayList<>();
		for (int i = 0; i < rawRows.size(); i++) {
			T dto = strategy.mapToDto(rawRows.get(i));
			dtos.add(dto);
			allErrors.addAll(strategy.validate(dto, i + 2)); // rowNum: Excel 列號
																// (header=1, data
																// start=2)
		}

		// 8. 批次驗證
		allErrors.addAll(strategy.batchValidate(dtos));

		// 9. 回傳結果
		if (!allErrors.isEmpty()) {
			log.warn("Import validation failed: {} errors for {} rows", allErrors.size(), rawRows.size());
			return ImportResult.failure(allErrors);
		}
		return ImportResult.success(dtos);
	}

	/**
	 * 執行匯入（寫入資料庫）。
	 */
	public <T> ImportResponse execute(ImportResult<T> result, ImportStrategy<T> strategy) {
		if (result.hasErrors()) {
			return ImportResponse.builder()
				.entityType(strategy.getEntityType())
				.totalRows(result.getValidRows().size())
				.successCount(0)
				.errors(result.getErrors())
				.build();
		}

		List<T> rows = result.getValidRows();
		strategy.beforeAll(rows);
		strategy.saveAll(rows);
		strategy.afterAll(rows);

		log.info("Import {} success: {} rows imported", strategy.getEntityType(), rows.size());
		return ImportResponse.builder()
			.entityType(strategy.getEntityType())
			.totalRows(rows.size())
			.successCount(rows.size())
			.errors(List.of())
			.build();
	}

	private String extractExtension(String filename) {
		if (filename == null || !filename.contains(".")) {
			throw new BusinessException(ErrorCode.DEVICE_IMPORT_FILE_FORMAT);
		}
		return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
	}

}
