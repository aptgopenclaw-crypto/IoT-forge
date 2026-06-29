package com.taipei.iot.rbac.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.rbac.dto.response.PermissionDto;
import com.taipei.iot.rbac.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/auth/permissions")
@RequiredArgsConstructor
@Tag(name = "Permission", description = "權限查詢：列出系統可用的權限清單")
public class PermissionController {

	private final PermissionService permissionService;

	@GetMapping
	@PreAuthorize("hasAuthority('ROLE_LIST')")
	@Operation(summary = "取得權限列表", description = "回傳系統目前定義的所有權限資料")
	public BaseResponse<List<PermissionDto>> listPermissions() {
		return BaseResponse.success(permissionService.listPermissions());
	}

}
