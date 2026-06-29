package com.taipei.iot.auth.provider.local;

import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.provider.AuthType;
import com.taipei.iot.auth.provider.AuthenticationProvider;
import com.taipei.iot.auth.provider.AuthenticationRequest;
import com.taipei.iot.auth.provider.AuthenticationResult;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.enums.SecurityEvent;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.SecurityLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * LOCAL authentication provider: email + BCrypt password. Extracted from AuthServiceImpl
 * steps 2-6.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocalAuthProvider implements AuthenticationProvider {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	@Value("${app.security.lock.max-fail-count:5}")
	private int maxFailCount;

	@Value("${app.security.lock.lock-duration-minutes:10}")
	private int lockDurationMinutes;

	@Override
	public AuthType getType() {
		return AuthType.LOCAL;
	}

	@Override
	public AuthenticationResult authenticate(AuthenticationRequest request, String configJson) {
		String email = request.getIdentifier();
		String password = request.getCredential();

		// 1. Find user by email
		UserEntity user = userRepository.findByEmail(email).orElse(null);
		if (user == null) {
			SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, null, "email=" + email, "reason=user_not_found");
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}

		// 2. Check enabled
		if (!user.getEnabled()) {
			SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, null, "email=" + user.getEmail(),
					"reason=account_disabled");
			throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
		}

		// 3. Check locked (with auto-unlock)
		if (user.getLocked()) {
			if (isLockExpired(user)) {
				user.setLocked(false);
				user.setLockedAt(null);
				user.setLoginFailCount(0);
				userRepository.save(user);
			}
			else {
				SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, null, "email=" + user.getEmail(),
						"reason=account_locked");
				throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
			}
		}

		// 4. Verify password
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			int newFailCount = user.getLoginFailCount() + 1;
			user.setLoginFailCount(newFailCount);

			if (newFailCount >= maxFailCount) {
				user.setLocked(true);
				user.setLockedAt(LocalDateTime.now());
			}

			userRepository.save(user);
			SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, null, "email=" + user.getEmail(), "reason=bad_password",
					"failCount=" + newFailCount);
			throw new BusinessException(ErrorCode.LOGIN_FAIL);
		}

		// 5. Success — reset fail count
		user.setLoginFailCount(0);
		user.setLastLoginAt(LocalDateTime.now());
		userRepository.save(user);

		return AuthenticationResult.builder()
			.localUserId(user.getUserId())
			.email(user.getEmail())
			.displayName(user.getDisplayName())
			.build();
	}

	private boolean isLockExpired(UserEntity user) {
		if (user.getLockedAt() == null) {
			return false;
		}
		return user.getLockedAt().plusMinutes(lockDurationMinutes).isBefore(LocalDateTime.now());
	}

}
