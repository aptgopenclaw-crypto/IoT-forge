package com.taipei.iot.tenant.controller;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.common.annotation.RateLimit;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.tenant.dto.CreateTenantRequest;
import com.taipei.iot.tenant.dto.TenantDto;
import com.taipei.iot.tenant.dto.UpdateTenantRequest;
import com.taipei.iot.tenant.service.TenantAdminService;
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
@RequestMapping("/v1/platform/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PLATFORM_TENANT_MANAGE')")
@Tag(name = "Platform / Tenants", description = "平台層租戶管理（建立、編輯、啟用切換）；需 PLATFORM_TENANT_MANAGE 權限")
public class TenantAdminController {

	private final TenantAdminService tenantAdminService;

	@GetMapping
	@RateLimit(key = "admin-tenant-list", limit = 60, period = 60)
	@Operation(summary = "列出所有租戶", description = "回傳系統內全部租戶（包含停用狀態）")
	public BaseResponse<List<TenantDto>> listTenants() {
		return BaseResponse.success(tenantAdminService.listTenants());
	}

	// [Tenant v2 T-11] SUPER_ADMIN token 若外洩，仍需限制寫操作頻率避免：
	// - 大量 createTenant 撐爆 DB
	// - 連續 toggleEnabled 製造 TenantEnabledCache 抖動 / Pub/Sub 風暴
	@PostMapping
	@RateLimit(key = "admin-tenant-create", limit = 10, period = 60)
	@AuditEvent(AuditEventType.CREATE_TENANT)
	@Operation(summary = "建立新租戶", description = "建立新的平台租戶資料，並初始化其基本狀態")
	public BaseResponse<TenantDto> createTenant(@Valid @RequestBody CreateTenantRequest request) {
		return BaseResponse.success(tenantAdminService.createTenant(request));
	}

	@PutMapping("/{tenantId}")
	@RateLimit(key = "admin-tenant-update", limit = 30, period = 60)
	@AuditEvent(AuditEventType.UPDATE_TENANT)
	@Operation(summary = "編輯指定租戶基本資料", description = "更新租戶名稱、描述等基本資訊")
	public BaseResponse<TenantDto> updateTenant(@PathVariable String tenantId,
			@Valid @RequestBody UpdateTenantRequest request) {
		return BaseResponse.success(tenantAdminService.updateTenant(tenantId, request));
	}

	@PatchMapping("/{tenantId}/enabled")
	@RateLimit(key = "admin-tenant-toggle", limit = 20, period = 60)
	@AuditEvent(AuditEventType.TOGGLE_TENANT_ENABLED)
	@Operation(summary = "切換租戶啟用狀態", description = "true=啟用、false=停用；異動會即時推送 TenantEnabledCache 失效事件")
	public BaseResponse<Void> toggleEnabled(@PathVariable String tenantId, @RequestParam boolean enabled) {
		tenantAdminService.toggleEnabled(tenantId, enabled);
		return BaseResponse.success(null);
	}

}
