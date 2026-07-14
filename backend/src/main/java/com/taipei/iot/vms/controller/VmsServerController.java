package com.taipei.iot.vms.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.vms.dto.VmsServerDTO;
import com.taipei.iot.vms.dto.VmsServerRequest;
import com.taipei.iot.vms.service.VmsServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/auth/vms/servers")
@RequiredArgsConstructor
public class VmsServerController {

	private final VmsServerService vmsServerService;

	@GetMapping
	public BaseResponse<List<VmsServerDTO>> list() {
		return BaseResponse.success(vmsServerService.findAll());
	}

	@GetMapping("/{id}")
	public BaseResponse<VmsServerDTO> getById(@PathVariable Long id) {
		return BaseResponse.success(vmsServerService.findById(id));
	}

	@PostMapping
	public BaseResponse<VmsServerDTO> create(@Valid @RequestBody VmsServerRequest request) {
		return BaseResponse.success(vmsServerService.create(request));
	}

	@PutMapping("/{id}")
	public BaseResponse<VmsServerDTO> update(@PathVariable Long id, @Valid @RequestBody VmsServerRequest request) {
		return BaseResponse.success(vmsServerService.update(id, request));
	}

	@DeleteMapping("/{id}")
	public BaseResponse<Void> delete(@PathVariable Long id) {
		vmsServerService.delete(id);
		return BaseResponse.success(null);
	}

	@PostMapping("/{id}/test-connection")
	public BaseResponse<Void> testConnection(@PathVariable Long id) {
		vmsServerService.testConnection(id);
		return BaseResponse.success(null);
	}

}
