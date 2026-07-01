package com.taipei.iot.import_.device;

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
@RequestMapping("/v1/auth/devices/import")
@RequiredArgsConstructor
@Tag(name = "Device Import", description = "设备批次汇入（Excel/CSV）")
public class DeviceImportController {

	private static final List<String> TEMPLATE_HEADERS = List.of("device_type", "device_code", "device_name", "twd97_x",
			"twd97_y", "lng", "lat", "elevation", "dept_name", "contract_name", "property_owner", "installed_at",
			"parent_device_code", "mount_position", "connectivity_type", "circuit_number");

	private static final List<String> ERROR_REPORT_HEADERS = List.of("列", "字段", "原始值", "错误说明");

	private final ImportOrchestrator importOrchestrator;

	private final DeviceImportStrategy deviceImportStrategy;

	@PostMapping
	@PreAuthorize("hasAuthority('DEVICE_MANAGE')")
	@Operation(summary = "汇入设备", description = "上传 Excel (.xlsx) 或 CSV 档案，先验证全部再整批写入")
	public BaseResponse<ImportResponse> importDevices(@RequestParam("file") MultipartFile file) {
		ImportResult<DeviceImportRow> result = importOrchestrator.parseAndValidate(file, deviceImportStrategy);

		if (result.hasErrors()) {
			return BaseResponse.fail(ErrorCode.DEVICE_IMPORT_VALIDATION_FAILED);
		}

		ImportResponse response = importOrchestrator.execute(result, deviceImportStrategy);
		return BaseResponse.success(response);
	}

	@GetMapping("/template")
	@PreAuthorize("hasAuthority('DEVICE_MANAGE')")
	@Operation(summary = "下载汇入范本", description = "回传含标题列的 .xlsx 或 .csv 范本")
	public ResponseEntity<byte[]> downloadTemplate(@RequestParam(defaultValue = "xlsx") String format)
			throws IOException {

		if ("csv".equalsIgnoreCase(format)) {
			return downloadCsvTemplate();
		}
		return downloadXlsxTemplate();
	}

	@PostMapping("/error-report")
	@PreAuthorize("hasAuthority('DEVICE_MANAGE')")
	@Operation(summary = "下载错误报告", description = "将验证错误包装为 CSV 提供下载")
	public ResponseEntity<byte[]> downloadErrorReport(@RequestBody ErrorReportRequest request) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
			// BOM for Excel compatibility
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
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"import-error-report.csv\"")
			.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
			.body(baos.toByteArray());
	}

	private ResponseEntity<byte[]> downloadXlsxTemplate() throws IOException {
		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("设备汇入范本");
			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < TEMPLATE_HEADERS.size(); i++) {
				headerRow.createCell(i).setCellValue(TEMPLATE_HEADERS.get(i));
			}
			// 第二列填入范例资料（选择性）
			Row exampleRow = sheet.createRow(1);
			exampleRow.createCell(0).setCellValue("STREET_LIGHT");
			exampleRow.createCell(1).setCellValue("SL-TEMPLATE-001");

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			workbook.write(baos);
			return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"device-import-template.xlsx\"")
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
			writer.println("STREET_LIGHT,SL-TEMPLATE-001");
			writer.flush();
		}

		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"device-import-template.csv\"")
			.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
			.body(baos.toByteArray());
	}

	private static String csvEscape(Object value) {
		if (value == null)
			return "";
		String s = value.toString();
		// CSV injection prevention: prefix =/+/-/@ with tab
		if (!s.isEmpty() && (s.charAt(0) == '=' || s.charAt(0) == '+' || s.charAt(0) == '-' || s.charAt(0) == '@')) {
			s = "\t" + s;
		}
		if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
			s = "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}

	/**
	 * Request DTO for error report generation.
	 */
	public record ErrorReportRequest(String originalFileName, List<String> headers, List<List<String>> rows,
			List<ImportError> errors) {
	}

}
