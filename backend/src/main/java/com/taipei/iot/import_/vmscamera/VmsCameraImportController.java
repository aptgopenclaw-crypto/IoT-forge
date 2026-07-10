package com.taipei.iot.import_.vmscamera;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.import_.ImportOrchestrator;
import com.taipei.iot.import_.ImportResponse;
import com.taipei.iot.import_.ImportResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/auth/vms/cameras/import")
@RequiredArgsConstructor
@Tag(name = "VMS Camera Import", description = "VMS 攝影機 CSV/XLSX 匯入")
public class VmsCameraImportController {

	private final ImportOrchestrator orchestrator;

	private final VmsCameraImportStrategy strategy;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('VMS_MANAGE')")
	@Operation(summary = "匯入 VMS 攝影機", description = "上傳 CSV/XLSX 檔案，批次新增攝影機")
	public BaseResponse<ImportResponse> importCameras(@RequestParam("file") MultipartFile file) {
		ImportResult<VmsCameraImportRow> result = orchestrator.parseAndValidate(file, strategy);

		if (result.hasErrors()) {
			ImportResponse errorBody = ImportResponse.builder()
				.entityType("vms-camera")
				.totalRows(result.getValidRows().size() + result.getErrors().size())
				.successCount(0)
				.errors(result.getErrors())
				.build();
			return BaseResponse.<ImportResponse>builder()
				.errorCode(ErrorCode.DEVICE_IMPORT_VALIDATION_FAILED.getCode())
				.errorMsg(ErrorCode.DEVICE_IMPORT_VALIDATION_FAILED.getMessage())
				.timestamp(System.currentTimeMillis() / 1000)
				.body(errorBody)
				.build();
		}

		ImportResponse response = orchestrator.execute(result, strategy);
		return BaseResponse.success(response);
	}

}
