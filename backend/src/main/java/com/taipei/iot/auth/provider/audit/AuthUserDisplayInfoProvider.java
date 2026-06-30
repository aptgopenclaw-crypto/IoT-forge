package com.taipei.iot.auth.provider.audit;

import com.taipei.iot.user.repository.UserRepository;
import com.taipei.iot.common.audit.port.UserDisplayInfo;
import com.taipei.iot.common.audit.port.UserDisplayInfoProvider;
import com.taipei.iot.common.tenant.RunInSystemTenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link UserDisplayInfoProvider} 的 auth 模組實作。
 *
 * <p>
 * 由 {@code auth} 模組擁有 {@code UserRepository}，提供 {@code audit} 模組所需的 使用者顯示資訊，而不讓
 * {@code audit} 直接依賴 {@code auth}。
 */
@Component
@RequiredArgsConstructor
public class AuthUserDisplayInfoProvider implements UserDisplayInfoProvider {

	private final UserRepository userRepository;

	@Override
	@RunInSystemTenantContext
	public Optional<UserDisplayInfo> findByUserId(String userId) {
		return userRepository.findById(userId).map(u -> new UserDisplayInfo(u.getDisplayName(), u.getEmail()));
	}

}
