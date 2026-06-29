package com.taipei.iot.rbac.controller;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.rbac.dto.request.CreateMenuRequest;
import com.taipei.iot.rbac.dto.request.UpdateMenuRequest;
import com.taipei.iot.rbac.dto.response.MenuDto;
import com.taipei.iot.rbac.dto.response.UserMenuDto;
import com.taipei.iot.rbac.service.MenuService;
import com.taipei.iot.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/auth/menus")
@RequiredArgsConstructor
@Tag(name = "Menu", description = "選單管理：樹狀查詢、我的選單與 CRUD")
public class MenuController {

	private final MenuService menuService;

	@GetMapping("/tree")
	@PreAuthorize("hasAuthority('MENU_LIST')")
	@Operation(summary = "取得選單樹", description = "回傳完整選單樹狀結構，供管理端維護使用")
	public BaseResponse<List<MenuDto>> getMenuTree() {
		return BaseResponse.success(menuService.getMenuTree());
	}

	@GetMapping("/my")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "取得我的選單", description = "依目前登入者的角色與租戶範圍回傳可見選單")
	public BaseResponse<List<UserMenuDto>> getMyMenus(Authentication authentication) {
		// Extract role IDs from authorities (e.g., ROLE_ADMIN -> ROLE_ADMIN)
		List<String> roleIds = authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.collect(Collectors.toList());

		// [Phase B fix] SUPER_ADMIN in SYSTEM context: JwtAuthenticationFilter
		// sets the "SYSTEM" marker as tenantId. MenuService treats any non-null
		// tenantId as "in tenant context" → wrong scope. Normalise to null so
		// PLATFORM-scoped menus are returned.
		String tenantId = TenantContext.isSystemContext() ? null : TenantContext.getCurrentTenantId();
		return BaseResponse.success(menuService.getMyMenus(roleIds, tenantId));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('MENU_CREATE')")
	@AuditEvent(AuditEventType.CREATE_MENU)
	@Operation(summary = "新增選單", description = "建立新的選單節點")
	public BaseResponse<MenuDto> createMenu(@Valid @RequestBody CreateMenuRequest request) {
		return BaseResponse.success(menuService.createMenu(request));
	}

	@PutMapping
	@PreAuthorize("hasAuthority('MENU_UPDATE')")
	@AuditEvent(AuditEventType.UPDATE_MENU)
	@Operation(summary = "更新選單", description = "更新選單名稱、路由、排序、權限等資訊")
	public BaseResponse<MenuDto> updateMenu(@Valid @RequestBody UpdateMenuRequest request) {
		return BaseResponse.success(menuService.updateMenu(request));
	}

	@DeleteMapping("/{menuId}")
	@PreAuthorize("hasAuthority('MENU_DELETE')")
	@AuditEvent(AuditEventType.DELETE_MENU)
	@Operation(summary = "刪除選單", description = "依選單 ID 刪除選單節點")
	public BaseResponse<Void> deleteMenu(@PathVariable Long menuId) {
		menuService.deleteMenu(menuId);
		return BaseResponse.success(null);
	}

	@PatchMapping("/{menuId}/visible")
	@PreAuthorize("hasAuthority('MENU_UPDATE')")
	@AuditEvent(AuditEventType.TOGGLE_VISIBLE)
	@Operation(summary = "切換選單顯示狀態", description = "設定指定選單是否在前台與管理端可見")
	public BaseResponse<Void> toggleVisible(@PathVariable Long menuId, @RequestParam boolean visible) {
		menuService.toggleVisible(menuId, visible);
		return BaseResponse.success(null);
	}

}
