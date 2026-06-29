package com.taipei.iot.auth.controller;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.auth.policy.PasswordPolicyService;
import com.taipei.iot.auth.policy.dto.UpdatePasswordPolicyRequest;
import com.taipei.iot.common.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Platform-level password policy CRUD (SUPER_ADMIN only).
 *
 * <p>
 * Reads/writes the rows stored under
 * {@link com.taipei.iot.auth.policy.PasswordPolicyResolver#PLATFORM_SENTINEL}. These
 * values act as the lower bound that tenant admins must respect (see spec D-4).
 */
@RestController
@RequestMapping("/v1/platform/password-policy")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PLATFORM_PASSWORD_POLICY_MANAGE')")
public class PlatformPasswordPolicyController {

	private final PasswordPolicyService policyService;

	@GetMapping
	public BaseResponse<Map<String, String>> getPlatformDefaults() {
		return BaseResponse.success(policyService.getPlatformDefaults());
	}

	@PutMapping
	@AuditEvent(AuditEventType.UPDATE_PLATFORM_PASSWORD_POLICY)
	public BaseResponse<Void> update(@Valid @RequestBody UpdatePasswordPolicyRequest req) {
		policyService.updatePlatformDefault(req);
		return BaseResponse.success(null);
	}

}
