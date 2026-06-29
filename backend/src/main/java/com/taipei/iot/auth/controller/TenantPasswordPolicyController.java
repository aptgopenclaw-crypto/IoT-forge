package com.taipei.iot.auth.controller;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.auth.policy.PasswordPolicyService;
import com.taipei.iot.auth.policy.dto.PasswordPolicyDto;
import com.taipei.iot.auth.policy.dto.UpdatePasswordPolicyRequest;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Tenant-scoped password-policy endpoints.
 *
 * <ul>
 * <li>{@code GET /} — effective merged policy (tenant override ∪ platform default)</li>
 * <li>{@code GET /tenant} — raw tenant overrides only (for "is this customised?"
 * badges)</li>
 * <li>{@code PUT /tenant} — upsert a single override (enforces platform lower bound)</li>
 * <li>{@code DELETE /tenant/{key}} — remove one override, falling back to platform
 * default</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/auth/password-policy")
@RequiredArgsConstructor
@Validated
@Tag(name = "PasswordPolicy", description = "租戶密碼政策：查詢有效規則 / 管理租戶覆寫")
public class TenantPasswordPolicyController {

	private final PasswordPolicyService policyService;

	@GetMapping
	@Operation(summary = "取得有效密碼政策", description = "回傳租戶覆寫與平台預設合併後的最終有效密碼政策")
	public BaseResponse<PasswordPolicyDto> getEffective() {
		return BaseResponse.success(policyService.getEffective(TenantContext.getCurrentTenantId()));
	}

	@GetMapping("/tenant")
	@PreAuthorize("hasAuthority('PASSWORD_POLICY_MANAGE')")
	@Operation(summary = "查詢租戶密碼政策覆寫", description = "回傳目前租戶已設定的密碼政策覆寫項目")
	public BaseResponse<Map<String, String>> getTenantOverrides() {
		return BaseResponse.success(policyService.getTenantOverrides(TenantContext.getCurrentTenantId()));
	}

	@PutMapping("/tenant")
	@PreAuthorize("hasAuthority('PASSWORD_POLICY_MANAGE')")
	@AuditEvent(AuditEventType.UPDATE_PASSWORD_POLICY)
	@Operation(summary = "更新租戶密碼政策覆寫", description = "新增或更新單一租戶密碼政策覆寫值，並套用平台最低限制")
	public BaseResponse<Void> updateTenantOverride(@Valid @RequestBody UpdatePasswordPolicyRequest req) {
		policyService.updateTenantOverride(TenantContext.getCurrentTenantId(), req);
		return BaseResponse.success(null);
	}

	@DeleteMapping("/tenant/{key}")
	@PreAuthorize("hasAuthority('PASSWORD_POLICY_MANAGE')")
	@AuditEvent(AuditEventType.UPDATE_PASSWORD_POLICY)
	@Operation(summary = "刪除租戶密碼政策覆寫", description = "移除指定 key 的租戶覆寫，回落到平台預設值")
	public BaseResponse<Void> deleteTenantOverride(@PathVariable @NotBlank String key) {
		policyService.deleteTenantOverride(TenantContext.getCurrentTenantId(), key);
		return BaseResponse.success(null);
	}

}
