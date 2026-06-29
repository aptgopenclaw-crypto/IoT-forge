package com.taipei.iot.user.controller;

import com.taipei.iot.common.annotation.PaginationParams;
import com.taipei.iot.common.annotation.DeprecatedApi;
import com.taipei.iot.common.dto.PageQuery;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.user.dto.request.AddTenantRoleRequest;
import com.taipei.iot.user.dto.request.CreateUserRequest;
import com.taipei.iot.user.dto.request.UpdateUserRequest;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.user.dto.response.UserListItemDto;
import com.taipei.iot.user.dto.response.UserTenantMappingDto;
import com.taipei.iot.user.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/auth/users")
@RequiredArgsConstructor
@Tag(name = "UserAdmin", description = "使用者管理：查詢、建立、更新、停用與租戶角色映射")
public class UserAdminController {

	private final UserAdminService userAdminService;

	@GetMapping
	@PreAuthorize("hasAuthority('USER_LIST')")
	@Operation(summary = "取得使用者列表", description = "以分頁方式回傳使用者清單，可選 keyword 關鍵字搜尋")
	public BaseResponse<PageResponse<UserListItemDto>> listUsers(@PaginationParams(maxSize = 500) PageQuery pageQuery,
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long deptId) {
		return BaseResponse
			.success(userAdminService.listUsers(pageQuery.getPage(), pageQuery.getSize(), keyword, deptId));
	}

	@GetMapping("/{userId}")
	@PreAuthorize("hasAuthority('USER_LIST')")
	@Operation(summary = "查詢單筆使用者", description = "依使用者 ID 回傳單筆使用者資料")
	public BaseResponse<UserListItemDto> getUser(@PathVariable @Pattern(regexp = "^[a-f0-9\\-]{36}$") String userId) {
		return BaseResponse.success(userAdminService.getUser(userId));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('USER_CREATE')")
	@Operation(summary = "建立使用者", description = "建立新的後台使用者資料")
	public BaseResponse<UserListItemDto> createUser(Authentication authentication,
			@Valid @RequestBody CreateUserRequest req) {
		String adminUserId = (String) authentication.getPrincipal();
		return BaseResponse.success(userAdminService.createUser(adminUserId, req));
	}

	@PutMapping("/{userId}")
	@PreAuthorize("hasAuthority('USER_UPDATE')")
	@Operation(summary = "更新使用者", description = "更新指定使用者的基本資料")
	public BaseResponse<UserListItemDto> updateUser(Authentication authentication,
			@PathVariable @Pattern(regexp = "^[a-f0-9\\-]{36}$") String userId,
			@Valid @RequestBody UpdateUserRequest req) {
		String adminUserId = (String) authentication.getPrincipal();
		return BaseResponse.success(userAdminService.updateUser(adminUserId, userId, req));
	}

	@DeleteMapping("/{userId}")
	@PreAuthorize("hasAuthority('USER_DISABLE')")
	@Operation(summary = "停用使用者", description = "停用指定使用者帳號")
	public BaseResponse<Void> disableUser(Authentication authentication,
			@PathVariable @Pattern(regexp = "^[a-f0-9\\-]{36}$") String userId) {
		String adminUserId = (String) authentication.getPrincipal();
		userAdminService.disableUser(adminUserId, userId);
		return BaseResponse.success(null);
	}

	@PatchMapping("/{userId}/soft-delete")
	@PreAuthorize("hasAuthority('USER_DELETE')")
	@Operation(summary = "軟刪除使用者", description = "將指定使用者標記為已刪除")
	public BaseResponse<Void> softDeleteUser(Authentication authentication,
			@PathVariable @Pattern(regexp = "^[a-f0-9\\-]{36}$") String userId) {
		String adminUserId = (String) authentication.getPrincipal();
		userAdminService.softDeleteUser(adminUserId, userId);
		return BaseResponse.success(null);
	}

	@GetMapping("/{userId}/tenant-roles")
	@PreAuthorize("hasAuthority('PLATFORM_USER_TENANT_MAPPING')")
	@Deprecated
	@DeprecatedApi(successor = "/v1/platform/users/{userId}/tenant-roles")
	@Operation(summary = "[Deprecated] 列出使用者租戶角色映射", deprecated = true,
			description = "請改用 GET /v1/platform/users/{userId}/tenant-roles")
	public BaseResponse<List<UserTenantMappingDto>> getUserTenantMappings(
			@PathVariable @Pattern(regexp = "^[a-f0-9\\-]{36}$") String userId) {
		return BaseResponse.success(userAdminService.getUserTenantMappings(userId));
	}

	@PostMapping("/{userId}/tenant-roles")
	@PreAuthorize("hasAuthority('PLATFORM_USER_TENANT_MAPPING')")
	@Deprecated
	@DeprecatedApi(successor = "/v1/platform/users/{userId}/tenant-roles")
	@Operation(summary = "[Deprecated] 為使用者新增租戶角色映射", deprecated = true,
			description = "請改用 POST /v1/platform/users/{userId}/tenant-roles")
	public BaseResponse<UserTenantMappingDto> addTenantRole(Authentication authentication,
			@PathVariable @Pattern(regexp = "^[a-f0-9\\-]{36}$") String userId,
			@Valid @RequestBody AddTenantRoleRequest req) {
		String adminUserId = (String) authentication.getPrincipal();
		return BaseResponse.success(userAdminService.addTenantRole(adminUserId, userId, req));
	}

	@DeleteMapping("/{userId}/tenant-roles/{mappingId}")
	@PreAuthorize("hasAuthority('PLATFORM_USER_TENANT_MAPPING')")
	@Deprecated
	@DeprecatedApi(successor = "/v1/platform/users/{userId}/tenant-roles/{mappingId}")
	@Operation(summary = "[Deprecated] 移除使用者租戶角色映射", deprecated = true,
			description = "請改用 DELETE /v1/platform/users/{userId}/tenant-roles/{mappingId}")
	public BaseResponse<Void> removeTenantRole(Authentication authentication,
			@PathVariable @Pattern(regexp = "^[a-f0-9\\-]{36}$") String userId, @PathVariable Long mappingId) {
		String adminUserId = (String) authentication.getPrincipal();
		userAdminService.removeTenantRole(adminUserId, userId, mappingId);
		return BaseResponse.success(null);
	}

}
