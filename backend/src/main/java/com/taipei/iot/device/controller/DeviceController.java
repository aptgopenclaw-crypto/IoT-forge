package com.taipei.iot.device.controller;

import com.taipei.iot.common.audit.annotation.AuditEvent;
import com.taipei.iot.common.audit.enums.AuditEventType;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.device.dto.DeviceRequest;
import com.taipei.iot.device.dto.DeviceResponse;
import com.taipei.iot.device.dto.DeviceStatsResponse;
import com.taipei.iot.device.enums.DeviceStatus;
import com.taipei.iot.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/devices")
@RequiredArgsConstructor
@Tag(name = "Device", description = "IoT 設備管理 CRUD + 設備組合樹 + 報廢")
public class DeviceController {

	private final DeviceService deviceService;

	@GetMapping
	@PreAuthorize("hasAuthority('DEVICE_VIEW')")
	@Operation(summary = "設備分頁查詢", description = "支援 deviceType / status / keyword 過濾")
	public BaseResponse<PageResponse<DeviceResponse>> list(@RequestParam(required = false) String deviceType,
			@RequestParam(required = false) DeviceStatus status, @RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		Page<DeviceResponse> result = deviceService.listDevices(deviceType, status, keyword,
				PageRequest.of(page, size));
		return BaseResponse.success(toPageResponse(result));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('DEVICE_VIEW')")
	@Operation(summary = "取得單一設備", description = "回傳設備明細（含子元件）")
	public BaseResponse<DeviceResponse> getById(@PathVariable Long id) {
		return BaseResponse.success(deviceService.getById(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('DEVICE_MANAGE')")
	@AuditEvent(AuditEventType.CREATE_DEVICE)
	@Operation(summary = "新增設備", description = "需符合 DeviceTemplate schema 驗證")
	public BaseResponse<DeviceResponse> create(@Valid @RequestBody DeviceRequest request) {
		return BaseResponse.success(deviceService.create(request));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('DEVICE_MANAGE')")
	@AuditEvent(AuditEventType.UPDATE_DEVICE)
	@Operation(summary = "編輯設備")
	public BaseResponse<DeviceResponse> update(@PathVariable Long id, @Valid @RequestBody DeviceRequest request) {
		return BaseResponse.success(deviceService.update(id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('DEVICE_MANAGE')")
	@AuditEvent(AuditEventType.DELETE_DEVICE)
	@Operation(summary = "刪除設備", description = "設備下有子元件時不可刪除")
	public BaseResponse<Void> delete(@PathVariable Long id) {
		deviceService.delete(id);
		return BaseResponse.success(null);
	}

	@PostMapping("/{id}/decommission")
	@PreAuthorize("hasAuthority('DEVICE_MANAGE')")
	@AuditEvent(AuditEventType.UPDATE_DEVICE)
	@Operation(summary = "報廢設備", description = "將設備狀態標記為 DECOMMISSIONED")
	public BaseResponse<Void> decommission(@PathVariable Long id) {
		deviceService.decommission(id);
		return BaseResponse.success(null);
	}

	@GetMapping("/tree/{id}")
	@PreAuthorize("hasAuthority('DEVICE_VIEW')")
	@Operation(summary = "取得設備組合樹", description = "遞迴回傳父設備下的所有子元件")
	public BaseResponse<DeviceResponse> getTree(@PathVariable Long id) {
		return BaseResponse.success(deviceService.getDeviceTree(id));
	}

	@GetMapping("/stats")
	@PreAuthorize("hasAuthority('DEVICE_VIEW')")
	@Operation(summary = "設備統計摘要", description = "依類型/狀態分類統計、在線率、未結工單數")
	public BaseResponse<DeviceStatsResponse> getStats() {
		return BaseResponse.success(deviceService.getStats());
	}

	private <T> PageResponse<T> toPageResponse(Page<T> page) {
		return PageResponse.<T>builder()
			.content(page.getContent())
			.totalElements(page.getTotalElements())
			.totalPages(page.getTotalPages())
			.page(page.getNumber())
			.size(page.getSize())
			.build();
	}

}
