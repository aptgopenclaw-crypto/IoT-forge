package com.taipei.iot.rbac.controller;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.rbac.dto.request.AssignRolePermissionsRequest;
import com.taipei.iot.rbac.dto.request.CreateRoleRequest;
import com.taipei.iot.rbac.dto.request.UpdateRoleRequest;
import com.taipei.iot.rbac.dto.response.RoleDto;
import com.taipei.iot.rbac.dto.response.RolePermissionListDto;
import com.taipei.iot.rbac.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/v1/auth/roles")
@RequiredArgsConstructor
@Tag(name = "Role", description = "角色管理：查詢、建立、更新、啟用與權限指派")
public class RoleController {

	private final RoleService roleService;

	@GetMapping
	@PreAuthorize("hasAuthority('ROLE_LIST')")
	@Operation(summary = "取得角色列表", description = "回傳目前可見的角色清單")
	public BaseResponse<List<RoleDto>> listRoles() {
		return BaseResponse.success(roleService.listRoles());
	}

	@GetMapping("/assignable")
	@PreAuthorize("hasAuthority('ROLE_LIST')")
	@Operation(summary = "取得可指派角色列表", description = "回傳可供指派給使用者的角色清單")
	public BaseResponse<List<RoleDto>> listAssignableRoles() {
		return BaseResponse.success(roleService.listAssignableRoles());
	}

	@PostMapping
	@PreAuthorize("hasAuthority('ROLE_CREATE')")
	@AuditEvent(AuditEventType.CREATE_ROLE)
	@Operation(summary = "建立角色", description = "建立新的角色資料")
	public BaseResponse<RoleDto> createRole(@Valid @RequestBody CreateRoleRequest request) {
		return BaseResponse.success(roleService.createRole(request));
	}

	@PutMapping("/{roleId}")
	@PreAuthorize("hasAuthority('ROLE_UPDATE')")
	@AuditEvent(AuditEventType.UPDATE_ROLE)
	@Operation(summary = "更新角色", description = "更新指定角色的名稱、描述、啟用狀態與資料範圍")
	public BaseResponse<RoleDto> updateRole(@PathVariable String roleId,
			@Valid @RequestBody UpdateRoleRequest request) {
		return BaseResponse.success(roleService.updateRole(roleId, request));
	}

	@PatchMapping("/{roleId}/enabled")
	@PreAuthorize("hasAuthority('ROLE_UPDATE')")
	@AuditEvent(AuditEventType.TOGGLE_ROLE_ENABLED)
	@Operation(summary = "切換角色啟用狀態", description = "啟用或停用指定角色")
	public BaseResponse<Void> toggleEnabled(@PathVariable String roleId, @RequestParam boolean enabled) {
		roleService.toggleEnabled(roleId, enabled);
		return BaseResponse.success(null);
	}

	@GetMapping("/{roleId}/permissions")
	@PreAuthorize("hasAuthority('ROLE_LIST')")
	@Operation(summary = "查詢角色權限", description = "回傳指定角色目前綁定的權限清單")
	public BaseResponse<RolePermissionListDto> getRolePermissions(@PathVariable String roleId) {
		return BaseResponse.success(roleService.getRolePermissions(roleId));
	}

	@PutMapping("/{roleId}/permissions")
	@PreAuthorize("hasAuthority('ROLE_ASSIGN_PERM')")
	@AuditEvent(AuditEventType.ASSIGN_ROLE_PERMISSIONS)
	@Operation(summary = "指派角色權限", description = "覆寫指定角色的權限清單")
	public BaseResponse<RolePermissionListDto> assignPermissions(@PathVariable String roleId,
			@Valid @RequestBody AssignRolePermissionsRequest request) {
		return BaseResponse.success(roleService.assignPermissions(roleId, request));
	}

}
