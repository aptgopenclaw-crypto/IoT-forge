package com.taipei.iot.vms.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.vms.dto.VmsCameraMappingDTO;
import com.taipei.iot.vms.dto.VmsCameraMappingRequest;
import com.taipei.iot.vms.service.VmsCameraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/auth/vms/cameras")
@RequiredArgsConstructor
public class VmsCameraController {

	private final VmsCameraService vmsCameraService;

	@GetMapping
	@PreAuthorize("hasAuthority('VMS_CAMERA')")
	public BaseResponse<List<VmsCameraMappingDTO>> list() {
		return BaseResponse.success(vmsCameraService.findAll());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('VMS_CAMERA')")
	public BaseResponse<VmsCameraMappingDTO> getById(@PathVariable Long id) {
		return BaseResponse.success(vmsCameraService.findById(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('VMS_CAMERA')")
	public BaseResponse<VmsCameraMappingDTO> create(@Valid @RequestBody VmsCameraMappingRequest request) {
		return BaseResponse.success(vmsCameraService.create(request));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('VMS_CAMERA')")
	public BaseResponse<VmsCameraMappingDTO> update(@PathVariable Long id,
			@Valid @RequestBody VmsCameraMappingRequest request) {
		return BaseResponse.success(vmsCameraService.update(id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('VMS_CAMERA')")
	public BaseResponse<Void> delete(@PathVariable Long id) {
		vmsCameraService.delete(id);
		return BaseResponse.success(null);
	}

	@PostMapping("/sync/{serverId}")
	@PreAuthorize("hasAuthority('VMS_CAMERA')")
	public BaseResponse<List<Map<String, Object>>> syncFromServer(@PathVariable Long serverId) {
		return BaseResponse.success(vmsCameraService.syncCamerasFromServer(serverId));
	}

}
