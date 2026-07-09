package com.taipei.iot.vms.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.vms.dto.VmsCameraRequest;
import com.taipei.iot.vms.dto.VmsCameraResponse;
import com.taipei.iot.vms.dto.VmsServerRequest;
import com.taipei.iot.vms.dto.VmsServerResponse;
import com.taipei.iot.vms.service.VmsAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

/**
 * VMS 管理 CRUD API。
 *
 * <p>
 * 所有端點需 {@code VMS_MANAGE} 權限（除 listCameras 可用 {@code VMS_VIEW}）。
 * </p>
 */
@RestController
@RequestMapping("/v1/auth/vms")
@RequiredArgsConstructor
@Tag(name = "VMS Admin", description = "VMS 伺服器與攝影機管理 CRUD")
public class VmsAdminController {

	private final VmsAdminService vmsAdminService;

	// ── Servers ────────────────────────────────────────────

	@GetMapping("/servers")
	@PreAuthorize("hasAuthority('VMS_MANAGE')")
	@Operation(summary = "VMS 伺服器列表")
	public BaseResponse<List<VmsServerResponse>> listServers() {
		return BaseResponse.success(vmsAdminService.listServers());
	}

	@GetMapping("/servers/{id}")
	@PreAuthorize("hasAuthority('VMS_MANAGE')")
	@Operation(summary = "取得單一 VMS 伺服器")
	public BaseResponse<VmsServerResponse> getServer(@PathVariable Long id) {
		return BaseResponse.success(vmsAdminService.getServer(id));
	}

	@PostMapping("/servers")
	@PreAuthorize("hasAuthority('VMS_MANAGE')")
	@Operation(summary = "新增 VMS 伺服器")
	public BaseResponse<VmsServerResponse> createServer(@Valid @RequestBody VmsServerRequest request) {
		return BaseResponse.success(vmsAdminService.createServer(request));
	}

	@PutMapping("/servers/{id}")
	@PreAuthorize("hasAuthority('VMS_MANAGE')")
	@Operation(summary = "更新 VMS 伺服器")
	public BaseResponse<VmsServerResponse> updateServer(@PathVariable Long id,
			@Valid @RequestBody VmsServerRequest request) {
		return BaseResponse.success(vmsAdminService.updateServer(id, request));
	}

	@DeleteMapping("/servers/{id}")
	@PreAuthorize("hasAuthority('VMS_MANAGE')")
	@Operation(summary = "刪除 VMS 伺服器（軟刪除）")
	public BaseResponse<Void> deleteServer(@PathVariable Long id) {
		vmsAdminService.deleteServer(id);
		return BaseResponse.success(null);
	}

	@PostMapping("/servers/{id}/test-connection")
	@PreAuthorize("hasAuthority('VMS_MANAGE')")
	@Operation(summary = "測試 VMS 連線")
	public BaseResponse<VmsServerResponse> testConnection(@PathVariable Long id) {
		return BaseResponse.success(vmsAdminService.testConnection(id));
	}

	// ── Cameras ────────────────────────────────────────────

	@GetMapping("/cameras/admin")
	@PreAuthorize("hasAuthority('VMS_MANAGE')")
	@Operation(summary = "攝影機列表（管理用，可依 server 篩選）")
	public BaseResponse<List<VmsCameraResponse>> listCameras(@RequestParam(required = false) Long serverId) {
		return BaseResponse.success(vmsAdminService.listCameras(serverId));
	}

	@PostMapping("/cameras")
	@PreAuthorize("hasAuthority('VMS_MANAGE')")
	@Operation(summary = "新增攝影機映射")
	public BaseResponse<VmsCameraResponse> createCamera(@Valid @RequestBody VmsCameraRequest request) {
		return BaseResponse.success(vmsAdminService.createCamera(request));
	}

	@DeleteMapping("/cameras/{id}")
	@PreAuthorize("hasAuthority('VMS_MANAGE')")
	@Operation(summary = "刪除攝影機映射")
	public BaseResponse<Void> deleteCamera(@PathVariable Long id) {
		vmsAdminService.deleteCamera(id);
		return BaseResponse.success(null);
	}

}
