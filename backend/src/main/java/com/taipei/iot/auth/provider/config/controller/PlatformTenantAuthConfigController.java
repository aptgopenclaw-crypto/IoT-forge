package com.taipei.iot.auth.provider.config.controller;

import com.taipei.iot.auth.provider.config.dto.TenantAuthConfigRequest;
import com.taipei.iot.auth.provider.config.dto.TenantAuthConfigResponse;
import com.taipei.iot.auth.provider.config.service.TenantAuthConfigService;
import com.taipei.iot.common.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-level tenant auth-config endpoints (ADR-006 / Phase 2.1.2).
 *
 * <p>
 * Replaces {@link TenantAuthConfigController} which resolved {@code tenantId} implicitly
 * from {@link com.taipei.iot.tenant.TenantContext}. The new path carries {@code tenantId}
 * as a path variable, making cross-tenant management explicit and removing the
 * SUPER_ADMIN "context switch" requirement.
 *
 * <ul>
 * <li>{@code GET /v1/platform/tenants/{tenantId}/auth-config}</li>
 * <li>{@code PUT /v1/platform/tenants/{tenantId}/auth-config}</li>
 * <li>{@code DELETE /v1/platform/tenants/{tenantId}/auth-config}</li>
 * <li>{@code POST /v1/platform/tenants/{tenantId}/auth-config/test-connection}</li>
 * </ul>
 *
 * <p>
 * The legacy {@link TenantAuthConfigController} is kept temporarily and will get
 * {@code Deprecation} headers in Phase 2.1.4.
 */
@RestController
@RequestMapping("/v1/platform/tenants/{tenantId}/auth-config")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('PLATFORM_TENANT_MANAGE')")
@Tag(name = "Platform / Tenants / Auth Config", description = "平台層設定指定租戶的認證方式（LOCAL / LDAP / OIDC / SAML）")
public class PlatformTenantAuthConfigController {

	private final TenantAuthConfigService service;

	@GetMapping
	@Operation(summary = "取得指定租戶的認證設定")
	public BaseResponse<TenantAuthConfigResponse> get(@PathVariable String tenantId) {
		return BaseResponse.success(service.getByTenantId(tenantId));
	}

	@PutMapping
	@Operation(summary = "建立 / 更新指定租戶的認證設定")
	public BaseResponse<TenantAuthConfigResponse> createOrUpdate(@PathVariable String tenantId,
			@Valid @RequestBody TenantAuthConfigRequest request) {
		return BaseResponse.success(service.createOrUpdate(tenantId, request));
	}

	@DeleteMapping
	@Operation(summary = "刪除指定租戶認證設定", description = "刪除後恢復為 LOCAL 預設")
	public BaseResponse<Void> delete(@PathVariable String tenantId) {
		service.deleteByTenantId(tenantId);
		return BaseResponse.success(null);
	}

	@PostMapping("/test-connection")
	@Operation(summary = "測試外部 IdP 連線", description = "不寫入 DB，僅儲試驗證提供的設定是否可連線")
	public BaseResponse<Boolean> testConnection(@PathVariable String tenantId,
			@Valid @RequestBody TenantAuthConfigRequest request) {
		boolean result = service.testConnection(tenantId, request);
		return BaseResponse.success(result);
	}

}
