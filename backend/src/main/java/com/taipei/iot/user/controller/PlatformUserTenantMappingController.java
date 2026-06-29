package com.taipei.iot.user.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.user.dto.request.AddTenantRoleRequest;
import com.taipei.iot.user.dto.response.UserTenantMappingDto;
import com.taipei.iot.user.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Platform-level user / tenant-role mapping endpoints (ADR-006 / Phase 2.1.3).
 *
 * <p>
 * Replaces the three {@code tenant-roles} endpoints previously hosted on
 * {@link UserAdminController} under {@code /v1/auth/users/{userId}/tenant-roles}. Moving
 * them under {@code /v1/platform/users/{userId}/tenant-roles} makes the cross-tenant
 * nature explicit and aligns the path with the {@code PLATFORM_USER_TENANT_MAPPING}
 * authority enforced via {@link PreAuthorize}.
 *
 * <ul>
 * <li>{@code GET /v1/platform/users/{userId}/tenant-roles}</li>
 * <li>{@code POST /v1/platform/users/{userId}/tenant-roles}</li>
 * <li>{@code DELETE /v1/platform/users/{userId}/tenant-roles/{mappingId}}</li>
 * </ul>
 *
 * <p>
 * The legacy endpoints on {@link UserAdminController} are kept temporarily and will
 * receive {@code Deprecation} headers in Phase 2.1.4.
 */
@RestController
@RequestMapping("/v1/platform/users/{userId}/tenant-roles")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('PLATFORM_USER_TENANT_MAPPING')")
@Tag(name = "Platform / Users / Tenant Roles", description = "平台層跨租戶使用者角色映射管理")
public class PlatformUserTenantMappingController {

	private final UserAdminService userAdminService;

	@GetMapping
	@Operation(summary = "列出使用者所有租戶角色映射", description = "回傳指定使用者目前所有租戶角色映射清單")
	public BaseResponse<List<UserTenantMappingDto>> list(
			@PathVariable @Pattern(regexp = "^[a-f0-9\\-]{36}$") String userId) {
		return BaseResponse.success(userAdminService.getUserTenantMappings(userId));
	}

	@PostMapping
	@Operation(summary = "為使用者新增租戶角色映射", description = "為指定使用者新增一筆租戶角色映射")
	public BaseResponse<UserTenantMappingDto> add(Authentication authentication,
			@PathVariable @Pattern(regexp = "^[a-f0-9\\-]{36}$") String userId,
			@Valid @RequestBody AddTenantRoleRequest req) {
		String adminUserId = (String) authentication.getPrincipal();
		return BaseResponse.success(userAdminService.addTenantRole(adminUserId, userId, req));
	}

	@DeleteMapping("/{mappingId}")
	@Operation(summary = "移除租戶角色映射", description = "依 mappingId 移除指定使用者的租戶角色映射")
	public BaseResponse<Void> remove(Authentication authentication,
			@PathVariable @Pattern(regexp = "^[a-f0-9\\-]{36}$") String userId, @PathVariable Long mappingId) {
		String adminUserId = (String) authentication.getPrincipal();
		userAdminService.removeTenantRole(adminUserId, userId, mappingId);
		return BaseResponse.success(null);
	}

}
