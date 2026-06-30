package com.taipei.iot.auth.port;

import com.taipei.iot.auth.policy.PasswordPolicyResolver;
import com.taipei.iot.common.auth.port.PasswordPolicyProvider;
import com.taipei.iot.common.policy.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link PasswordPolicyProvider} 的 auth 模組實作，委派給 {@link PasswordPolicyResolver}。
 */
@Component
@RequiredArgsConstructor
public class AuthPasswordPolicyProvider implements PasswordPolicyProvider {

	private final PasswordPolicyResolver passwordPolicyResolver;

	@Override
	public PasswordPolicy resolve(String tenantId) {
		return passwordPolicyResolver.resolve(tenantId);
	}

}
