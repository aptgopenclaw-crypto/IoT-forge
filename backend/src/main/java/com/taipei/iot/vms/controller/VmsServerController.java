package com.taipei.iot.vms.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.vms.dto.VmsServerDTO;
import com.taipei.iot.vms.dto.VmsServerRequest;
import com.taipei.iot.vms.service.VmsServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/auth/vms/servers")
@RequiredArgsConstructor
public class VmsServerController {

	private final VmsServerService vmsServerService;

	@GetMapping
	@PreAuthorize("hasAuthority('VMS_SERVER')")
	public BaseResponse<List<VmsServerDTO>> list() {
		return BaseResponse.success(vmsServerService.findAll());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('VMS_SERVER')")
	public BaseResponse<VmsServerDTO> getById(@PathVariable Long id) {
		return BaseResponse.success(vmsServerService.findById(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('VMS_SERVER')")
	public BaseResponse<VmsServerDTO> create(@Valid @RequestBody VmsServerRequest request) {
		return BaseResponse.success(vmsServerService.create(request));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('VMS_SERVER')")
	public BaseResponse<VmsServerDTO> update(@PathVariable Long id, @Valid @RequestBody VmsServerRequest request) {
		return BaseResponse.success(vmsServerService.update(id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('VMS_SERVER')")
	public BaseResponse<Void> delete(@PathVariable Long id) {
		vmsServerService.delete(id);
		return BaseResponse.success(null);
	}

	@PostMapping("/{id}/test-connection")
	@PreAuthorize("hasAuthority('VMS_SERVER')")
	public BaseResponse<Void> testConnection(@PathVariable Long id) {
		vmsServerService.testConnection(id);
		return BaseResponse.success(null);
	}

}
