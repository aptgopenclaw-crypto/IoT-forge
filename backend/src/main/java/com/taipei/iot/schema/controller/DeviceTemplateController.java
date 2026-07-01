package com.taipei.iot.schema.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.schema.dto.DeviceTemplateResponse;
import com.taipei.iot.schema.service.DeviceTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/auth/device-templates")
@RequiredArgsConstructor
@Tag(name = "DeviceTemplate", description = "設備模板管理 — JSON Schema 動態欄位定義")
public class DeviceTemplateController {

	private final DeviceTemplateService templateService;

	@GetMapping
	@PreAuthorize("hasAuthority('DEVICE_VIEW')")
	@Operation(summary = "列出所有設備模板", description = "回傳所有已定義的 device_type 及其版本資訊")
	public BaseResponse<List<DeviceTemplateResponse>> listDeviceTypes() {
		return BaseResponse.success(templateService.listDeviceTypes());
	}

	@GetMapping("/names")
	@PreAuthorize("hasAuthority('DEVICE_VIEW')")
	@Operation(summary = "列出所有設備類型名稱", description = "僅回傳 device_type 字串清單，供前端下拉選單使用")
	public BaseResponse<List<String>> listDeviceTypeNames() {
		return BaseResponse.success(templateService.listDeviceTypeNames());
	}

	@GetMapping("/{deviceType}/schema")
	@PreAuthorize("hasAuthority('DEVICE_VIEW')")
	@Operation(summary = "取得設備模板 Schema", description = "回傳指定設備類型的 JSON Schema 定義")
	public BaseResponse<Map<String, Object>> getSchema(@PathVariable String deviceType) {
		return BaseResponse.success(templateService.getSchema(deviceType));
	}

	@PutMapping("/{deviceType}/schema")
	@PreAuthorize("hasAuthority('DEVICE_TEMPLATE_MANAGE')")
	@Operation(summary = "更新設備模板 Schema", description = "若 deviceType 不存在則自動建立")
	public BaseResponse<Map<String, Object>> updateSchema(@PathVariable String deviceType,
			@RequestBody Map<String, Object> schema) {
		return BaseResponse.success(templateService.updateSchema(deviceType, schema));
	}

	@GetMapping("/{deviceType}/schema/attributes")
	@PreAuthorize("hasAuthority('DEVICE_VIEW')")
	@Operation(summary = "取得設備模板 attributes Schema", description = "回傳靜態屬性欄位定義段")
	public BaseResponse<Map<String, Object>> getAttributesSchema(@PathVariable String deviceType) {
		return BaseResponse.success(templateService.getAttributesSchema(deviceType));
	}

	@PutMapping("/{deviceType}/schema/attributes")
	@PreAuthorize("hasAuthority('DEVICE_TEMPLATE_MANAGE')")
	@Operation(summary = "更新設備模板 attributes Schema", description = "僅更新靜態屬性段，保留 telemetry 段；deviceType 不存在則自動建立")
	public BaseResponse<Map<String, Object>> updateAttributesSchema(@PathVariable String deviceType,
			@RequestBody Map<String, Object> attributesSchema) {
		return BaseResponse.success(templateService.updateAttributesSchema(deviceType, attributesSchema));
	}

	@GetMapping("/{deviceType}/schema/telemetry")
	@PreAuthorize("hasAuthority('DEVICE_VIEW')")
	@Operation(summary = "取得設備模板 telemetry Schema", description = "回傳時序量測欄位定義段")
	public BaseResponse<Map<String, Object>> getTelemetrySchema(@PathVariable String deviceType) {
		return BaseResponse.success(templateService.getTelemetrySchema(deviceType));
	}

	@PutMapping("/{deviceType}/schema/telemetry")
	@PreAuthorize("hasAuthority('DEVICE_TEMPLATE_MANAGE')")
	@Operation(summary = "更新設備模板 telemetry Schema", description = "僅更新時序量測段，保留 attributes 段；deviceType 不存在則自動建立")
	public BaseResponse<Map<String, Object>> updateTelemetrySchema(@PathVariable String deviceType,
			@RequestBody Map<String, Object> telemetrySchema) {
		return BaseResponse.success(templateService.updateTelemetrySchema(deviceType, telemetrySchema));
	}

	@DeleteMapping("/{deviceType}")
	@PreAuthorize("hasAuthority('DEVICE_TEMPLATE_MANAGE')")
	@Operation(summary = "刪除設備模板", description = "僅在無 Device 使用該類型時可刪除")
	public BaseResponse<Void> deleteDeviceType(@PathVariable String deviceType) {
		templateService.deleteDeviceType(deviceType);
		return BaseResponse.success(null);
	}

}
