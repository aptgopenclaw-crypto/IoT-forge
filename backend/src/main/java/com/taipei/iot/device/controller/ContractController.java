package com.taipei.iot.device.controller;

import com.taipei.iot.common.audit.annotation.AuditEvent;
import com.taipei.iot.common.audit.enums.AuditEventType;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.device.dto.ContractRequest;
import com.taipei.iot.device.dto.ContractResponse;
import com.taipei.iot.device.enums.ContractStatus;
import com.taipei.iot.device.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/contracts")
@RequiredArgsConstructor
@Tag(name = "Contract", description = "標案契約管理 CRUD")
public class ContractController {

	private final ContractService contractService;

	@GetMapping
	@PreAuthorize("hasAuthority('CONTRACT_VIEW')")
	@Operation(summary = "標案契約分頁查詢")
	public BaseResponse<PageResponse<ContractResponse>> list(@RequestParam(required = false) ContractStatus status,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Page<ContractResponse> result = contractService.list(status, keyword, PageRequest.of(page, size));
		return BaseResponse.success(toPageResponse(result));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('CONTRACT_VIEW')")
	@Operation(summary = "取得單一契約")
	public BaseResponse<ContractResponse> getById(@PathVariable Long id) {
		return BaseResponse.success(contractService.getById(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('CONTRACT_MANAGE')")
	@AuditEvent(AuditEventType.CREATE_CONTRACT)
	@Operation(summary = "新增標案契約")
	public BaseResponse<ContractResponse> create(@Valid @RequestBody ContractRequest request) {
		return BaseResponse.success(contractService.create(request));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('CONTRACT_MANAGE')")
	@AuditEvent(AuditEventType.UPDATE_CONTRACT)
	@Operation(summary = "編輯標案契約")
	public BaseResponse<ContractResponse> update(@PathVariable Long id, @Valid @RequestBody ContractRequest request) {
		return BaseResponse.success(contractService.update(id, request));
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
