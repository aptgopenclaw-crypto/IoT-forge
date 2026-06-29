package com.taipei.iot.auth.provider.config.controller;

import com.taipei.iot.auth.provider.config.dto.TenantAuthConfigRequest;
import com.taipei.iot.auth.provider.config.dto.TenantAuthConfigResponse;
import com.taipei.iot.auth.provider.config.service.TenantAuthConfigService;
import com.taipei.iot.common.annotation.DeprecatedApi;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Tenant authentication configuration endpoints.
 *
 * <ul>
 * <li>{@code GET /} — get current tenant's auth config</li>
 * <li>{@code PUT /} — create or update auth config</li>
 * <li>{@code DELETE /} — delete config (revert to LOCAL)</li>
 * <li>{@code POST /test-connection} — test external IdP connectivity</li>
 * </ul>
 *
 * @deprecated [Platform/Tenant Separation 2.1.4] Superseded by
 * {@link PlatformTenantAuthConfigController} at
 * {@code /v1/platform/tenants/{tenantId}/auth-config}. Responses carry
 * {@code Deprecation: true} and {@code Link: ...; rel="successor-version"} headers (RFC
 * 8594).
 */
@Deprecated
@DeprecatedApi(successor = "/v1/platform/tenants/{tenantId}/auth-config")
@RestController
@RequestMapping("/v1/auth/tenant-auth-config")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('PLATFORM_TENANT_MANAGE')")
@Tag(name = "Tenant / Auth Config (Deprecated)",
		description = "舊端點、從 JWT 解析租戶；已被 `/v1/platform/tenants/{tenantId}/auth-config` 取代")
public class TenantAuthConfigController {

	private final TenantAuthConfigService service;

	@GetMapping
	@Operation(summary = "[Deprecated] 取得當前租戶認證設定", deprecated = true,
			description = "請改用 GET /v1/platform/tenants/{tenantId}/auth-config")
	public BaseResponse<TenantAuthConfigResponse> get() {
		String tenantId = TenantContext.getCurrentTenantId();
		return BaseResponse.success(service.getByTenantId(tenantId));
	}

	@PutMapping
	@Operation(summary = "[Deprecated] 建立 / 更新當前租戶認證設定", deprecated = true,
			description = "請改用 PUT /v1/platform/tenants/{tenantId}/auth-config")
	public BaseResponse<TenantAuthConfigResponse> createOrUpdate(@Valid @RequestBody TenantAuthConfigRequest request) {
		String tenantId = TenantContext.getCurrentTenantId();
		return BaseResponse.success(service.createOrUpdate(tenantId, request));
	}

	@DeleteMapping
	@Operation(summary = "[Deprecated] 刪除當前租戶認證設定", deprecated = true,
			description = "請改用 DELETE /v1/platform/tenants/{tenantId}/auth-config")
	public BaseResponse<Void> delete() {
		String tenantId = TenantContext.getCurrentTenantId();
		service.deleteByTenantId(tenantId);
		return BaseResponse.success(null);
	}

	@PostMapping("/test-connection")
	@Operation(summary = "[Deprecated] 測試當前租戶認證連線", deprecated = true,
			description = "請改用 POST /v1/platform/tenants/{tenantId}/auth-config/test-connection")
	public BaseResponse<Boolean> testConnection(@Valid @RequestBody TenantAuthConfigRequest request) {
		String tenantId = TenantContext.getCurrentTenantId();
		boolean result = service.testConnection(tenantId, request);
		return BaseResponse.success(result);
	}

}
