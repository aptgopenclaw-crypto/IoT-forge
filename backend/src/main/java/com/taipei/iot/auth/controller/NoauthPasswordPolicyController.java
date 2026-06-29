package com.taipei.iot.auth.controller;

import com.taipei.iot.auth.policy.PasswordPolicyService;
import com.taipei.iot.auth.policy.dto.PasswordPolicyDto;
import com.taipei.iot.common.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint that lets the login / reset-password / change-password pages fetch the
 * human-readable password rule list. No authentication required.
 *
 * <p>
 * When {@code tenantId} is omitted, the platform default policy is returned; pages that
 * know the tenant (e.g. reset-password landing pre-fills tenantId from the token) pass it
 * through to get the tenant's effective rules.
 */
@RestController
@RequiredArgsConstructor
public class NoauthPasswordPolicyController {

	private final PasswordPolicyService policyService;

	@GetMapping("/v1/noauth/password-policy/describe")
	public BaseResponse<PasswordPolicyDto> describe(
			@RequestParam(value = "tenantId", required = false) String tenantId) {
		return BaseResponse.success(policyService.getEffective(tenantId));
	}

}
