package com.taipei.iot.import_.circuit;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.import_.ImportError;
import com.taipei.iot.import_.ImportOrchestrator;
import com.taipei.iot.import_.ImportResponse;
import com.taipei.iot.import_.ImportResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/v1/auth/circuits/import")
@RequiredArgsConstructor
@Tag(name = "Circuit Import", description = "迴路批次匯入（Excel/CSV）")
public class CircuitImportController {

	private static final List<String> TEMPLATE_HEADERS = List.of("circuit_number", "circuit_name", "taipower_account",
			"usage_type", "panel_box_device_id");

	private static final List<String> ERROR_REPORT_HEADERS = List.of("列", "欄位", "原始值", "錯誤說明");

	private final ImportOrchestrator importOrchestrator;

	private final CircuitImportStrategy circuitImportStrategy;

	@PostMapping
	@PreAuthorize("hasAuthority('CIRCUIT_MANAGE')")
	@Operation(summary = "匯入迴路", description = "上傳 Excel (.xlsx) 或 CSV 檔案，先驗證全部再整批寫入")
	public BaseResponse<ImportResponse> importCircuits(@RequestParam("file") MultipartFile file) {
		ImportResult<CircuitImportRow> result = importOrchestrator.parseAndValidate(file, circuitImportStrategy);

		if (result.hasErrors()) {
			ImportResponse errorBody = ImportResponse.builder()
				.entityType("circuit")
				.totalRows(result.getValidRows().size() + result.getErrors().size())
				.successCount(0)
				.errors(result.getErrors())
				.build();
			return BaseResponse.<ImportResponse>builder()
				.errorCode(ErrorCode.CIRCUIT_IMPORT_VALIDATION_FAILED.getCode())
				.errorMsg(ErrorCode.CIRCUIT_IMPORT_VALIDATION_FAILED.getMessage())
				.timestamp(System.currentTimeMillis() / 1000)
				.body(errorBody)
				.build();
		}

		ImportResponse response = importOrchestrator.execute(result, circuitImportStrategy);
		return BaseResponse.success(response);
	}

	@GetMapping("/template")
	@PreAuthorize("hasAuthority('CIRCUIT_MANAGE')")
	@Operation(summary = "下載匯入範本", description = "回傳含標題列的 .xlsx 或 .csv 範本")
	public ResponseEntity<byte[]> downloadTemplate(@RequestParam(defaultValue = "xlsx") String format)
			throws IOException {
		if ("csv".equalsIgnoreCase(format)) {
			return downloadCsvTemplate();
		}
		return downloadXlsxTemplate();
	}

	@PostMapping("/error-report")
	@PreAuthorize("hasAuthority('CIRCUIT_MANAGE')")
	@Operation(summary = "下載錯誤報告", description = "將驗證錯誤包裝為 CSV 提供下載")
	public ResponseEntity<byte[]> downloadErrorReport(@RequestBody ErrorReportRequest request) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
			baos.write(0xEF);
			baos.write(0xBB);
			baos.write(0xBF);
			writer.println(String.join(",", ERROR_REPORT_HEADERS));
			for (ImportError err : request.errors()) {
				writer.println(csvEscape(err.getRow()) + "," + csvEscape(err.getField()) + ","
						+ csvEscape(err.getValue()) + "," + csvEscape(err.getMessage()));
			}
			writer.flush();
		}
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"circuit-import-error-report.csv\"")
			.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
			.body(baos.toByteArray());
	}

	private ResponseEntity<byte[]> downloadXlsxTemplate() throws IOException {
		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("迴路匯入範本");
			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < TEMPLATE_HEADERS.size(); i++) {
				headerRow.createCell(i).setCellValue(TEMPLATE_HEADERS.get(i));
			}
			Row exampleRow = sheet.createRow(1);
			exampleRow.createCell(0).setCellValue("CR-001");
			exampleRow.createCell(1).setCellValue("北區迴路");

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			workbook.write(baos);
			return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"circuit-import-template.xlsx\"")
				.contentType(
						MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.body(baos.toByteArray());
		}
	}

	private ResponseEntity<byte[]> downloadCsvTemplate() throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
			baos.write(0xEF);
			baos.write(0xBB);
			baos.write(0xBF);
			writer.println(String.join(",", TEMPLATE_HEADERS));
			writer.println("CR-001,北區迴路,,路燈,");
			writer.flush();
		}
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"circuit-import-template.csv\"")
			.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
			.body(baos.toByteArray());
	}

	private static String csvEscape(Object value) {
		if (value == null)
			return "";
		String s = value.toString();
		if (!s.isEmpty() && (s.charAt(0) == '=' || s.charAt(0) == '+' || s.charAt(0) == '-' || s.charAt(0) == '@')) {
			s = "\t" + s;
		}
		if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
			s = "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}

	public record ErrorReportRequest(String originalFileName, List<String> headers, List<List<String>> rows,
			List<ImportError> errors) {
	}

}
