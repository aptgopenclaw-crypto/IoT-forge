package com.taipei.iot.setting.controller;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.setting.dto.SystemSettingDto;
import com.taipei.iot.setting.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/auth/system-settings")
@RequiredArgsConstructor
@Validated
@Tag(name = "SystemSetting", description = "系統設定：查詢與管理平台級設定值")
public class SystemSettingController {

	private final SystemSettingService settingService;

	@GetMapping
	@PreAuthorize("hasAuthority('SYSTEM_SETTINGS_VIEW')")
	@Operation(summary = "查詢系統設定列表", description = "回傳目前所有可查詢的系統設定項目")
	public BaseResponse<List<SystemSettingDto>> listSettings() {
		return BaseResponse.success(settingService.findAllSettings());
	}

	@PutMapping("/{key}")
	@PreAuthorize("hasAuthority('SYSTEM_SETTINGS_MANAGE')")
	@AuditEvent(AuditEventType.UPDATE_SETTING)
	@Operation(summary = "更新系統設定", description = "依 key 更新單一系統設定值")
	public BaseResponse<SystemSettingDto> updateSetting(@PathVariable @NotBlank String key,
			@RequestParam @NotBlank @Size(max = 500) String value) {
		return BaseResponse.success(settingService.updateSetting(key, value));
	}

	@GetMapping("/idle-timeout")
	@Operation(summary = "取得閒置逾時時間", description = "回傳目前系統的 idle timeout 分鐘數")
	public BaseResponse<Integer> getIdleTimeout() {
		return BaseResponse.success(settingService.getIdleTimeoutMinutes());
	}

	@PutMapping("/idle-timeout")
	@PreAuthorize("hasAuthority('SYSTEM_SETTINGS_MANAGE')")
	@AuditEvent(AuditEventType.UPDATE_SETTING)
	@Operation(summary = "更新閒置逾時時間", description = "更新系統 idle timeout 分鐘數")
	public BaseResponse<Integer> updateIdleTimeout(@RequestParam @Min(1) @Max(480) int minutes) {
		return BaseResponse.success(settingService.updateIdleTimeoutMinutes(minutes));
	}

}
